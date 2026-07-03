package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.DiffMetadata
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toMetadata
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream

private const val SHELL_TIMEOUT_MAX_MS = 600_000L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024
private const val DEFAULT_READ_LINE_LIMIT = 2000

val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read" to false,
    "workspace_write" to false,
    "workspace_edit" to false,
    "workspace_bash" to true,
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

private class WorkspaceToolState {
    val readFiles = mutableSetOf<String>()
    var cwd: String = ""
}

suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val approvalOverrides = workspaceRepository.getById(workspaceId)?.toolApprovalOverrides().orEmpty()
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, approvalOverrides)

    val state = WorkspaceToolState()
    if (!cwd.isNullOrBlank()) {
        state.cwd = cwd.removePrefix("/workspace/").removePrefix("/workspace")
    }

    return listOf(
        createReadFileTool(workspaceId, ::needsApproval, workspaceRepository, state),
        createWriteFileTool(workspaceId, ::needsApproval, workspaceRepository, state),
        createEditFileTool(workspaceId, ::needsApproval, workspaceRepository),
        createShellTool(workspaceId, ::needsApproval, workspaceRepository, state),
    )
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

private fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

private fun createReadFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    state: WorkspaceToolState,
) = Tool(
    name = "workspace_read",
    description = "Reads a file from the local filesystem. You can access any file directly by using this tool\n\nUsage:\n- The file_path parameter must be an absolute path, not a relative path\n- By default, it reads up to 2000 lines starting from the beginning of the file\n- When you already know which part of the file you need, only read that part. This can be important for larger files\n- Results are returned using cat -n format, with line numbers starting at 1\n- This tool allows read images (eg PNG, JPG, etc)\n- Do NOT re-read a file you just edited to verify. Edit/Write would have errored if the change failed",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("file_path", buildJsonObject {
                    put("type", "string")
                    put("description", "The absolute path to the file to read")
                })
                put("offset", buildJsonObject {
                    put("type", "integer")
                    put("description", "The line number to start reading from. Only provide if the file is too large to read at once")
                })
                put("limit", buildJsonObject {
                    put("type", "integer")
                    put("description", "The number of lines to read. Only provide if the file is too large to read at once")
                })
            },
            required = listOf("file_path"),
        )
    },
    needsApproval = { needsApproval("workspace_read") },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("file_path")
        state.readFiles.add(path)

        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(workspaceId, path)
        } else {
            val text = workspaceRepository.readTextInRootfs(workspaceId, path)
            val allLines = text.split("\n")
            val totalLines = allLines.size

            val offset = params["offset"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val limit = params["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: DEFAULT_READ_LINE_LIMIT

            val startIndex = offset.coerceIn(0, totalLines)
            val endIndex = (startIndex + limit).coerceAtMost(totalLines)
            val slice = allLines.subList(startIndex, endIndex)

            val formatted = StringBuilder()
            for (i in slice.indices) {
                val lineNum = startIndex + i + 1
                formatted.append(String.format("%6d\t%s\n", lineNum, slice[i]))
            }

            if (endIndex < totalLines) {
                formatted.append("\n[File has $totalLines lines. Showing lines ${startIndex + 1} to $endIndex. Use offset=$endIndex to continue reading.]")
            }

            listOf(UIMessagePart.Text(formatted.toString()))
        }
    },
)

private fun createWriteFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    state: WorkspaceToolState,
) = Tool(
    name = "workspace_write",
    description = "Writes a file to the local filesystem\n\nUsage:\n- This tool will overwrite the existing file if there is one at the provided path\n- If this is an existing file, you MUST use the Read tool first to read the file's contents. This tool will fail if you did not read the file first\n- Prefer the Edit tool for modifying existing files it only sends the new files (*.md) or README files unless explicitly requested by the user\n- NEVER write emojis unless the user explicitly requests them",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("file_path", buildJsonObject {
                    put("type", "string")
                    put("description", "The absolute path to the file to write (must be absolute, not relative)")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "The content to write to the file")
                })
            },
            required = listOf("file_path", "content"),
        )
    },
    needsApproval = { needsApproval("workspace_write") || it.pathOutsideWritableRoots("file_path") },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("file_path")

        if (workspaceRepository.fileExistsInRootfs(workspaceId, path) && path !in state.readFiles) {
            error("You must read the file at $path with the Read tool before writing to it.")
        }

        val content = params.string("content") ?: error("content is required")
        state.readFiles.add(path)
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, content, overwrite = true)
        listOf(UIMessagePart.Text(entry.toJson().toString()))
    },
)

private fun createEditFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_edit",
    description = "Performs exact string replacements in files\n\nUsage:\n- You must use your Read at least once in the conversation before editing. This tool will error if you attempt an edit without reading the file\n- When editing text from Read output, ensure you preserve the exact indentation (tabs/spaces) as it appears AFTER the line number prefix. The line number prefix format is: line number + tab. Everything after that is the actual file content to match. Never include any part of the line number prefix in the old_string or new_string\n- ALWAYS prefer editing existing files in the codebase. NEVER write new files unless explicitly required\n- NEVER write emojis unless the user explicitly requests them\n- The edit will FAIL if old_string is not unique in the file. Either provide a larger string with more surrounding context to make it unique or use replace_all to change every instance of old_string\n- Use replace_all for replacing and renaming strings across the file. This parameter is useful if you want to rename a variable for instance",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("file_path", buildJsonObject {
                    put("type", "string")
                    put("description", "The absolute path to the file to modify")
                })
                put("old_string", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to replace")
                })
                put("new_string", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to replace it with (must be different from old_string)")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Replace all occurrences of old_string (default false)")
                })
            },
            required = listOf("file_path", "old_string", "new_string"),
        )
    },
    needsApproval = { needsApproval("workspace_edit") || it.pathOutsideWritableRoots("file_path") },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("file_path")
        val oldString = params.string("old_string") ?: error("old_string is required")
        val newString = params.string("new_string") ?: error("new_string is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        require(oldString.isNotEmpty()) { "old_string must not be empty" }

        val original = workspaceRepository.readTextInRootfs(workspaceId, path)
        val result = try {
            replaceText(original, oldString, newString, replaceAll)
        } catch (e: IllegalArgumentException) {
            error("${e.message} (path: $path)")
        }
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, result.updated, overwrite = true)
        val diff = generateUnifiedDiff(original, result.updated, entry.path)
        listOf(
            UIMessagePart.Text(
                text = buildJsonObject {
                    put("path", entry.path)
                    put("replacements", result.replacements)
                    if (result.strategy != ExactReplacer.name) put("matchStrategy", result.strategy)
                    put("sizeBytes", entry.sizeBytes)
                    put("updatedAt", entry.updatedAt)
                }.toString(),
                metadata = diff?.let { d -> DiffMetadata(diff = d).toMetadata() },
            )
        )
    },
)

private fun createShellTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
    state: WorkspaceToolState,
) = Tool(
    name = "workspace_bash",
    description = "Executes a given bash command and returns its output\n\nThe working directory persists between commands, but shell state does not. The shell environment is initialized from the user's profile (bash or zsh)\n\nIMPORTANT: Avoid using this tool to run `cat`, `head`, `tail`, `sed`, `awk`, or `echo` commands, unless explicitly instructed or after you have verified that a dedicated tool cannot accomplish your task. Instead, use the appropriate dedicated tool as this will provide a much better experience for the user:\n - Read files: Use Read (NOT cat/head/tail)\n - Edit files: Use Edit (NOT sed/awk)\n - Write files: Use Write (NOT echo >/cat <<EOF)\n - Communication: Output text directly (NOT echo/printf)\nWhile the Bash can do similar things, it's better to use the built-in tools as they provide a better user experience and make it easier to review tool calls and give permission.\n\n# Instructions\n- If your command will create new directories or files, first use this tool to run `ls` to verify the parent directory exists and is the correct location\n- Always quote file paths that contain spaces with double quotes in your command (e.g., cd \"path with spaces/file.txt\")\n- Try to maintain your current working directory throughout the session by using absolute paths and avoiding usage of `cd`. You may use `cd` if the User explicitly requests it. In particular, never prepend `cd <current-directory>` to a `git` command `git` already operates on the current working tree, and the compound triggers a permission prompt\n- You may specify an optional timeout in milliseconds (up to 600000ms / 10 minutes). By default, your command will timeout after 120000ms (2 minutes)\n- When issuing multiple commands:\n  - If the commands are independent and can run in parallel, make multiple Bash calls in a single message. Example: if you need to run \"git status\" and \"git diff\", send a single message with two Bash calls in parallel\n  - If the commands depend on each other and must run sequentially, use a single Bash call with '&&' to chain them together\n  - Use ';' only when you need to run commands sequentially but don't care if earlier commands fail\n  - DO NOT use newlines to separate commands (newlines are ok in quoted strings)\n- For git commands:\n  - Prefer to create a new commit rather than amending an existing commit\n  - Before running destructive operations (e.g., git reset --hard, git push --force, git checkout --), consider whether there is a safer alternative that achieves the same goal. Only use destructive operations when they are truly the best approach\n  - Never skip hooks (--no-verify) or bypass signing (--no-gpg-sign, -c commit.gpgsign=false) unless the user has explicitly asked for it. If a hook fails, investigate and fix the underlying issue\n- Avoid unnecessary `sleep` commands:\n  - Do not sleep between commands that can run immediately, just run them\n  - Do not retry failing commands in a sleep loop diagnose the root cause.\n- When running `find`, search from `.` (or a specific path), not `/` scanning the full filesystem can exhaust system resources on large trees.\n- When using `find -regex` with alternation, put the longest alternative first. Example: use `'.*\\.\\(tsx\\|ts\\)'` not `'.*\\.\\(ts\\|tsx\\)'` the second form silently skips `.tsx` files\n\n# Committing changes with git\nOnly create commits when requested by the user. If unclear, ask first. When the user asks you to create a new git commit, follow these steps carefully:\n\nYou can call multiple tools in a single response. When multiple independent pieces of information are requested and all commands are likely to succeed, run multiple tool calls in parallel for optimal performance. The numbered steps below indicate which commands should be batched in parallel\n\nGit Safety Protocol:\n- NEVER update the git config\n- NEVER run destructive git commands (push --force, reset --hard, checkout ., restore ., clean -f, branch -D) unless the user explicitly requests these actions. Taking unauthorized destructive actions is unhelpful and can result in lost work, so it's best to ONLY run these commands when given direct instructions \n- NEVER skip hooks (--no-verify, --no-gpg-sign, etc) unless the user explicitly requests it\n- NEVER run force push to main/master, warn the user if they request it\n- CRITICAL: Always create NEW commits rather than amending, unless the user explicitly requests a git amend. When a pre-commit hook fails, the commit did NOT happen so --amend would modify the PREVIOUS commit, which may result in destroying work or losing previous changes. Instead, after hook failure, fix the issue, re-stage, and create a NEW commit\n- When staging files, prefer adding specific files by name rather than using \"git add -A\" or \"git add .\", which can accidentally include sensitive files (.env, credentials) or large binaries\n- NEVER commit changes unless the user explicitly asks you to. It is VERY IMPORTANT to only commit when explicitly asked, otherwise the user will feel that you are being too proactive\n\n1. Run the following bash commands in parallel, each using the Bash tool:\n  - Run a git status command to see all untracked files. IMPORTANT: Never use the -uall flag as it can cause memory issues on large repos\n  - Run a git diff command to see both staged and unstaged changes that will be committed\n  - Run a git log command to see recent commit messages, so that you can follow this repository's commit message style\n2. Analyze all staged changes (both previously staged and newly added) and draft a commit message:\n  - Summarize the nature of the changes (eg. new feature, enhancement to an existing feature, bug fix, refactoring, test, docs, etc.). Ensure the message accurately reflects the changes and their purpose (i.e. \"add\" means a wholly new feature, \"update\" means an enhancement to an existing feature, \"fix\" means a bug fix, etc.)\n  - Do not commit files that likely contain secrets (.env, credentials.json, etc). Warn the user if they specifically request to commit those files\n  - Draft a concise (1-2 sentences) commit message that focuses on the \"why\" rather than the \"what\"\n  - Ensure it accurately reflects the changes and their purpose\n3. Run the following commands in parallel:\n   - Add relevant untracked files to the staging area\n   - Create the commit with a message\n   - Run git status after the commit completes to verify success\n   Note: git status depends on the commit completing, so run it sequentially after the commit\n4. If the commit fails due to pre-commit hook: fix the issue and create a NEW commit\n\nImportant notes:\n- NEVER run additional commands to read or explore code, besides git bash commands\n- DO NOT push to the remote repository unless the user explicitly asks you to do so\n- IMPORTANT: Never use git commands with the -i flag (like git rebase -i or git add -i) since they require interactive input which is not supported\n- IMPORTANT: Do not use --no-edit with git rebase commands, as the --no-edit flag is not a valid option for git rebase.\n- If there are no changes to commit (i.e., no untracked files and no modifications), do not create an empty commit\n- In order to ensure good formatting, ALWAYS pass the commit message via a HEREDOC, a la this example:\n<example>\ngit commit -m \"$(cat <<'EOF'\n   Commit message here.\n   EOF\n   )\"\n</example>\n\n# Creating pull requests\nUse the gh command via the Bash for ALL GitHub-related tasks including working with issues, pull requests, checks, and releases. If given a Github URL use the gh command to get the information needed\n\nIMPORTANT: When the user asks you to create a pull request, follow these steps carefully:\n1. Run the following bash commands in parallel using the Bash tool, in order to understand the current state of the branch since it diverged from the main branch:\n   - Run a git status command to see all untracked files (never use -uall flag)\n   - Run a git diff command to see both staged and unstaged changes that will be committed\n   - Check if the current branch tracks a remote branch and is up to date with the remote, so you know if you need to push to the remote\n   - Run a git log command and `git diff [base-branch]...HEAD` to understand the full commit history for the current branch (from the time it diverged from the base branch)\n2. Analyze all changes that will be included in the pull request, making sure to look at all relevant commits (NOT just the latest commit, but ALL commits that will be included in the pull request!!!), and draft a pull request title and summary:\n   - Keep the PR title short (under 70 characters)\n   - Use the description/body for details, not the title\n3. Run the following commands in parallel:\n   - Create new branch if needed\n   - Push to remote with -u flag if needed\n   - Create PR using gh pr create with the format below. Use a HEREDOC to pass the body to ensure correct formatting\n<example>\ngh pr create --title \"the pr title\" --body \"$(cat <<'EOF'\n## Summary\n<1-3 bullet points>\n\n## Test plan\n[Bulleted markdown checklist of TODOs for testing the pull request...]\nEOF\n)\"\n</example>\n",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "The command to execute")
                })
                put("timeout", buildJsonObject {
                    put("type", "number")
                    put("description", "Optional timeout in milliseconds (max 600000)")
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { needsApproval("workspace_bash") },
    execute = {
        val params = it.jsonObject
        val command = params.string("command") ?: error("command is required")
        val timeoutMs = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_MS)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS

        val wrappedCommand = buildString {
            if (state.cwd.isNotBlank()) {
                append("cd ").append(state.cwd.shellQuote()).append(" 2>/dev/null; ")
            }
            append(command)
            append("; __exit=$?; echo \"___PWD___$(pwd)___ENDPWD___\"; exit $__exit")
        }

        val result = workspaceRepository.executeCommand(workspaceId, wrappedCommand, "", timeoutMs)

        val cleanStdout = parseAndUpdateCwd(result.stdout, state)

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exitCode", result.exitCode)
                    put("stdout", cleanStdout)
                    put("stderr", result.stderr)
                    put("timedOut", result.timedOut)
                    if (result.truncated) put("truncated", true)
                }.toString()
            )
        )
    },
)

private const val PWD_MARKER_START = "___PWD___"
private const val PWD_MARKER_END = "___ENDPWD___"

private fun parseAndUpdateCwd(stdout: String, state: WorkspaceToolState): String {
    val startIdx = stdout.indexOf(PWD_MARKER_START)
    if (startIdx < 0) return stdout
    val endIdx = stdout.indexOf(PWD_MARKER_END, startIdx)
    if (endIdx < 0) return stdout
    val newCwd = stdout.substring(startIdx + PWD_MARKER_START.length, endIdx).trim()
    if (newCwd.isNotBlank()) state.cwd = newCwd
    return (stdout.substring(0, startIdx).trimEnd() + stdout.substring(endIdx + PWD_MARKER_END.length)).trimEnd()
}

private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String {
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    val size = fileSize(workspaceId, area, relativePath)
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read: $path (${size / 1024 / 1024}MB, max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). Use shell commands like head, tail, or grep to read parts of it."
    }
    val buffer = ByteArrayOutputStream(size.toInt())
    exportFile(workspaceId, area, relativePath, buffer)
    return buffer.toString(Charsets.UTF_8.name())
}

private suspend fun WorkspaceRepository.fileExistsInRootfs(
    workspaceId: String,
    path: String,
): Boolean = try {
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    fileSize(workspaceId, area, relativePath) >= 0
} catch (e: Exception) {
    false
}

private fun rootfsPathToAreaAndRelative(path: String): Pair<WorkspaceStorageArea, String> {
    val trimmed = path.trimEnd('/')
    return if (trimmed == "/workspace" || trimmed.startsWith("/workspace/")) {
        WorkspaceStorageArea.FILES to trimmed.removePrefix("/workspace").trimStart('/')
    } else {
        WorkspaceStorageArea.LINUX to trimmed.trimStart('/')
    }
}

private suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
): List<UIMessagePart> {
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    val buffer = ByteArrayOutputStream()
    exportFile(workspaceId, area, relativePath, buffer)
    val bytes = buffer.toByteArray()

    val filesManager = getKoin().get<FilesManager>()
    val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
    return listOf(
        UIMessagePart.Image(url = uris.first().toString()),
        UIMessagePart.Text(
            buildJsonObject {
                put("path", path)
                put("description", "Image file read successfully")
            }.toString()
        ),
    )
}

private suspend fun WorkspaceRepository.writeTextInRootfs(
    workspaceId: String,
    path: String,
    text: String,
    overwrite: Boolean,
): WorkspaceFileEntry {
    val pathArg = path.shellQuote()
    val result = runRootfsCommand(
        workspaceId = workspaceId,
        action = "Write file",
        command = """
            if [ -e $pathArg ] && [ ${(!overwrite).shellFlag()} = 1 ]; then
              printf '%s\n' ${"File already exists: $path".shellQuote()} >&2
              exit 1
            fi
            if [ -e $pathArg ] && [ ! -f $pathArg ]; then
              printf '%s\n' ${"Path is not a file: $path".shellQuote()} >&2
              exit 1
            fi
            parent=${'$'}(dirname -- $pathArg) || exit 1
            mkdir -p -- "${'$'}parent" || exit 1
            cat > $pathArg || exit 1
            ${statEntryCommand(path)}
        """.trimIndent(),
        stdin = text.toByteArray(Charsets.UTF_8),
    )
    return result.stdout.parseRootfsEntry()
}

private suspend fun WorkspaceRepository.runRootfsCommand(
    workspaceId: String,
    action: String,
    command: String,
    stdin: ByteArray? = null,
): WorkspaceCommandResult {
    val result = executeCommand(
        id = workspaceId,
        command = command,
        timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin = stdin,
    )
    if (result.timedOut) {
        error("$action timed out")
    }
    if (result.exitCode != 0) {
        val message = result.stderr.ifBlank { result.stdout }.trim()
        error(if (message.isBlank()) "$action failed with exit code ${result.exitCode}" else message)
    }
    if (result.truncated) {
        error("$action output is too large")
    }
    return result
}

private fun statEntryCommand(path: String): String {
    val pathArg = path.shellQuote()
    return """
        if [ -d $pathArg ]; then entry_type=d; else entry_type=f; fi
        entry_size=${'$'}(stat -c '%s' -- $pathArg) || exit 1
        entry_mtime=${'$'}(stat -c '%Y' -- $pathArg) || exit 1
        printf '%s\0%s\0%s\0%s\0' "${'$'}entry_type" "${'$'}entry_size" "${'$'}entry_mtime" $pathArg
    """.trimIndent()
}

private fun String.parseRootfsEntry(): WorkspaceFileEntry =
    parseRootfsEntries().singleOrNull() ?: error("Invalid file metadata output")

private fun String.parseRootfsEntries(): List<WorkspaceFileEntry> {
    val fields = split('\u0000').dropLastWhile { it.isEmpty() }
    require(fields.size % 4 == 0) { "Invalid file metadata output" }
    return fields.chunked(4).map { chunk ->
        val type = chunk[0]
        val size = chunk[1].toLongOrNull() ?: error("Invalid file size: ${chunk[1]}")
        val updatedAt = (chunk[2].toLongOrNull() ?: error("Invalid file mtime: ${chunk[2]}")) * 1_000L
        val path = chunk[3]
        WorkspaceFileEntry(
            path = path,
            name = path.rootfsName(),
            isDirectory = type == "d",
            sizeBytes = size,
            updatedAt = updatedAt,
        )
    }
}

private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/')?.trim() ?: error("$name is required")
    require(path.isNotBlank()) { "$name is required" }
    require(path.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "$name contains invalid character" }
    return path
}

private val WRITABLE_ROOT_PREFIXES = listOf("/workspace", "/tmp")

private fun kotlinx.serialization.json.JsonElement.pathOutsideWritableRoots(name: String): Boolean =
    runCatching {
        jsonObject.absolutePath(name).isOutsideWritableRoots()
    }.getOrDefault(true)

private fun String.isOutsideWritableRoots(): Boolean {
    val normalized = trimEnd('/').ifBlank { "/" }
    return WRITABLE_ROOT_PREFIXES.none { prefix ->
        normalized == prefix || normalized.startsWith("$prefix/")
    }
}

private fun String.rootfsName(): String =
    trimEnd('/').substringAfterLast('/').ifBlank { "/" }

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private fun Boolean.shellFlag(): Int = if (this) 1 else 0

private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}
