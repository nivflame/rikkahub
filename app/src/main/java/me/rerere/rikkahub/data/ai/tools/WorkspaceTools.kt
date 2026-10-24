package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    "Read" to false,
    "Write" to false,
    "Edit" to false,
    "Bash" to true,
)

fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false

class WorkspaceToolState {
    val readFiles = mutableSetOf<String>()
    var cwd: String = ""
}

suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
    state: WorkspaceToolState = WorkspaceToolState(),
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val approvalOverrides = workspaceRepository.getById(workspaceId)?.toolApprovalOverrides().orEmpty()
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, approvalOverrides)

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
    name = "Read",
    description = """
        Reads a file from the local filesystem. By default, it reads up to 2000 lines starting from the beginning of the file. When you already know which part of the file you need, only read that part. This can be important for larger files. Results are returned using cat -n format, with line numbers starting at 1. This tool allows you to read images. Do not re-read a file you just edited to verify. Edit/Write would have errored if the change failed.
    """.trimIndent(),
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
    needsApproval = { needsApproval("Read") },
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
    name = "Write",
    description = """
        Writes a file to the local filesystem, overwriting if one exists. You must Read the existing file before overwriting it so you keep the content you meant to keep. If you only need to modify part of the file, use Edit instead.
    """.trimIndent(),
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
    needsApproval = { needsApproval("Write") || it.pathOutsideWritableRoots("file_path") },
    preExecute = preExecute@{ input ->
        val path = input.jsonObject.absolutePath("file_path")
        val content = input.jsonObject.string("content") ?: return@preExecute null
        val oldContent = runCatching {
            workspaceRepository.readTextInRootfs(workspaceId, path)
        }.getOrNull()
        val diff = generateUnifiedDiff(oldContent ?: "", content, path) ?: return@preExecute null
        mapOf("diff" to JsonPrimitive(diff))
    },
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("file_path")

        if (workspaceRepository.fileExistsInRootfs(workspaceId, path) && path !in state.readFiles) {
            error("You must read the file at $path with the Read tool before writing to it.")
        }

        val content = params.string("content") ?: error("content is required")
        val oldContent = runCatching {
            workspaceRepository.readTextInRootfs(workspaceId, path)
        }.getOrNull()
        state.readFiles.add(path)
        workspaceRepository.writeTextInRootfs(workspaceId, path, content, overwrite = true)
        val diff = generateUnifiedDiff(oldContent ?: "", content, path)
        listOf(
            UIMessagePart.Text(
                text = "Successfully wrote to $path",
                metadata = diff?.let { d -> DiffMetadata(diff = d).toMetadata() },
            )
        )
    },
)

private fun createEditFileTool(
    workspaceId: String,
    needsApproval: (String) -> Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "Edit",
    description = """
        Performs exact string replacements in files. You must Read the file before editing so your old_string matches the current content. The old_string must match the file content as shown by Read, including indentation and must be unique. Strip the Read line prefix (line number + tab) before matching. Use replace_all for replacing and renaming strings across the file.
    """.trimIndent(),
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
                    put("description", "Replace all occurrences of old_string (default: false)")
                })
            },
            required = listOf("file_path", "old_string", "new_string"),
        )
    },
    needsApproval = { needsApproval("Edit") || it.pathOutsideWritableRoots("file_path") },
    preExecute = preExecuteEdit@{ input ->
        val path = input.jsonObject.absolutePath("file_path")
        val oldString = input.jsonObject.string("old_string") ?: return@preExecuteEdit null
        val newString = input.jsonObject.string("new_string") ?: return@preExecuteEdit null
        val replaceAll = input.jsonObject["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val original = runCatching {
            workspaceRepository.readTextInRootfs(workspaceId, path)
        }.getOrNull() ?: return@preExecuteEdit null
        val result = runCatching {
            replaceText(original, oldString, newString, replaceAll)
        }.getOrNull() ?: return@preExecuteEdit null
        val diff = generateUnifiedDiff(original, result.updated, path) ?: return@preExecuteEdit null
        mapOf("diff" to JsonPrimitive(diff))
    },
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
    name = "Bash",
    description = """
        Executes a bash command and returns its output. Prefer using dedicated tools over Bash unless explicitly instructed or after you have verified that a dedicated tool cannot accomplish your task. By default, your command will time out after 120000ms. When you search for text or files, use `rg` and `fd`. Use `gh` CLI for GitHub operations. Never use git commands with the `-i` flag, because they require interactive input, which this environment doesn't support. Do not commit or push unless the user explicitly asks you to do so. When staging files, prefer adding specific files by name rather than using `git add -A` or `git add .`.

        # Environment
        - Current working directory: /workspace/${state.cwd}
    """.trimIndent(),
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
    needsApproval = { needsApproval("Bash") },
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
        }

        val result = workspaceRepository.executeCommand(workspaceId, wrappedCommand, "", timeoutMs)

        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exitCode", result.exitCode)
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    put("timedOut", result.timedOut)
                    if (result.truncated) put("truncated", true)
                }.toString()
            )
        )
    },
)

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
        entry_size=$(stat -c '%s' $pathArg) || exit 1
        entry_mtime=$(stat -c '%Y' $pathArg) || exit 1
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
