package me.rerere.rikkahub.ui.pages.extensions.skills

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.files.FileUtils
import me.rerere.rikkahub.data.files.SafEntry
import me.rerere.rikkahub.data.files.SkillFrontmatterParser
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata
import org.json.JSONArray
import kotlin.collections.iterator

private const val SAF_IMPORT_PARALLELISM = 8

class SkillsVM(
    private val skillManager: SkillManager,
) : ViewModel() {
    private val _skills = MutableStateFlow<List<SkillMetadata>>(emptyList())
    val skills = _skills.asStateFlow()
    private val _selectedSkills = MutableStateFlow<Set<String>>(emptySet())
    val selectedSkills = _selectedSkills.asStateFlow()

    fun toggleSkillSelection(name: String) {
        val current = _selectedSkills.value
        _selectedSkills.value = if (name in current) current - name else current + name
    }

    fun clearSelection() {
        _selectedSkills.value = emptySet()
    }

    fun deleteSelectedSkills() {
        val names = _selectedSkills.value
        _selectedSkills.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) {
            names.forEach { skillManager.deleteSkill(it) }
            _skills.value = skillManager.listSkills()
        }
    }

    init {
        loadSkills()
    }

    private fun loadSkills() {
        viewModelScope.launch(Dispatchers.IO) {
            _skills.value = skillManager.listSkills()
        }
    }

    fun saveSkill(name: String, content: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = skillManager.saveSkill(name, content)
            _skills.value = skillManager.listSkills()
            withContext(Dispatchers.Main) {
                onResult(result != null)
            }
        }
    }

    fun deleteSkill(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            skillManager.deleteSkill(name)
            val existing = skillManager.listSkills()
            _skills.value = existing
            _selectedSkills.value = _selectedSkills.value intersect existing.mapTo(HashSet()) { it.name }
        }
    }

    fun getSkillsDir() = skillManager.getSkillsDir()

    fun importSkillFromFile(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = FileUtils.getFileNameFromUri(appContext, uri).orEmpty()
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: run {
                        withContext(Dispatchers.Main) { onResult(false, "无法读取文件") }
                        return@launch
                    }

                val importedNames = if (isZipFile(fileName, bytes)) {
                    importSkillsFromZip(bytes)
                } else {
                    importSkillMarkdown(bytes)
                }

                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) {
                    onResult(true, importedNames.joinToString())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "未知错误") }
            }
        }
    }

    fun importSkillFromFolder(context: Context, treeUri: Uri, onResult: (Boolean, String) -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tree = DocumentFile.fromTreeUri(appContext, treeUri)
                if (tree == null || !tree.isDirectory) {
                    withContext(Dispatchers.Main) { onResult(false, "Invalid folder") }
                    return@launch
                }
                val skillDirs = findSkillDirs(appContext, tree)
                if (skillDirs.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult(false, "No SKILL.md found in folder") }
                    return@launch
                }
                val importedNames = coroutineScope {
                    val importDispatcher = Dispatchers.IO.limitedParallelism(SAF_IMPORT_PARALLELISM)
                    val claimedNames = ConcurrentHashMap.newKeySet<String>()
                    skillDirs.map { skillDir ->
                        async(importDispatcher) {
                            runCatching {
                                val files = FileUtils.collectSafFiles(appContext, skillDir, dispatcher = importDispatcher)
                                val skillMdBytes = files["SKILL.md"]
                                    ?: files.entries.firstOrNull { it.key.endsWith("/SKILL.md") }?.value
                                    ?: return@runCatching null
                                val content = skillMdBytes.toString(Charsets.UTF_8)
                                val frontmatter = SkillFrontmatterParser.parse(content)
                                val name = frontmatter["name"]?.trim()
                                if (name.isNullOrBlank() || frontmatter["description"].isNullOrBlank()) return@runCatching null
                                if (!claimedNames.add(name)) return@runCatching null
                                val normalizedFiles = LinkedHashMap<String, ByteArray>()
                                for ((path, bytes) in files) {
                                    val normalizedPath = if (path.equals("SKILL.md", ignoreCase = true)) {
                                        "SKILL.md"
                                    } else {
                                        path
                                    }
                                    normalizedFiles[normalizedPath] = bytes
                                }
                                val saved = skillManager.saveSkillFileBytesAtomically(name, normalizedFiles)
                                if (saved) name else null
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }
                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) {
                    if (importedNames.isEmpty()) {
                        onResult(false, "No valid skills found")
                    } else {
                        onResult(true, importedNames.joinToString())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("SkillsVM", "importSkillFromFolder failed", e)
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Import failed") }
            }
        }
    }

    private fun findSkillDirs(context: Context, root: DocumentFile): List<DocumentFile> {
        val result = mutableListOf<DocumentFile>()
        val rootChildren = FileUtils.listSafChildren(context, root)
        fun hasSkillMd(entries: List<SafEntry>) =
            entries.any { !it.isDir && it.name.equals("SKILL.md", ignoreCase = true) }
        if (hasSkillMd(rootChildren)) {
            result.add(root)
        }
        for (entry in rootChildren) {
            if (!entry.isDir) continue
            val childDir = DocumentFile.fromTreeUri(context, entry.uri) ?: continue
            if (hasSkillMd(FileUtils.listSafChildren(context, childDir))) {
                result.add(childDir)
            }
        }
        return result
    }

    fun importSkillFromGitHub(repoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = parseGitHubUrl(repoUrl) ?: run {
                    withContext(Dispatchers.Main) { onResult(false, "无效的 GitHub 仓库链接") }
                    return@launch
                }

                // Collect all files recursively via GitHub Contents API
                val files = mutableListOf<Pair<String, String>>() // relativePath -> downloadUrl
                val listed = listFilesRecursively(info.owner, info.repo, info.branch, info.path, info.path, files)
                if (!listed) {
                    withContext(Dispatchers.Main) { onResult(false, "读取 GitHub 目录失败") }
                    return@launch
                }

                val skillMdEntry = files.find { it.first == "SKILL.md" } ?: run {
                    withContext(Dispatchers.Main) { onResult(false, "目录中未找到 SKILL.md") }
                    return@launch
                }

                val skillMdContent = downloadText(skillMdEntry.second) ?: run {
                    withContext(Dispatchers.Main) { onResult(false, "下载 SKILL.md 失败，请检查链接或网络") }
                    return@launch
                }

                val frontmatter = SkillFrontmatterParser.parse(skillMdContent)
                val name = frontmatter["name"]
                if (name.isNullOrBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "SKILL.md 格式错误：缺少 name 字段") }
                    return@launch
                }

                val fileContents = LinkedHashMap<String, String>()
                for ((relativePath, downloadUrl) in files) {
                    val content = downloadText(downloadUrl)
                    if (content == null) {
                        withContext(Dispatchers.Main) { onResult(false, "下载文件失败：$relativePath") }
                        return@launch
                    }
                    fileContents[relativePath] = content
                }

                val saved = skillManager.saveSkillFilesAtomically(name, fileContents)
                if (!saved) {
                    withContext(Dispatchers.Main) { onResult(false, "保存失败") }
                    return@launch
                }

                _skills.value = skillManager.listSkills()
                withContext(Dispatchers.Main) { onResult(true, name) }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "未知错误") }
            }
        }
    }

    private fun importSkillMarkdown(bytes: ByteArray): List<String> {
        val content = bytes.toString(Charsets.UTF_8)
        val frontmatter = SkillFrontmatterParser.parse(content)
        val name = frontmatter["name"]?.trim()
        if (name.isNullOrBlank()) {
            error("SKILL.md 格式错误：缺少 name 字段")
        }
        if (frontmatter["description"].isNullOrBlank()) {
            error("SKILL.md 格式错误：缺少 description 字段")
        }
        val saved = skillManager.saveSkill(name, content) ?: error("保存失败，请检查技能格式")
        return listOf(saved.name)
    }

    private fun importSkillsFromZip(bytes: ByteArray): List<String> {
        val files = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                try {
                    if (!entry.isDirectory) {
                        val path = normalizeZipEntryPath(entry.name)
                        if (path != null) {
                            files[path] = zipInput.readBytes()
                        }
                    }
                } finally {
                    zipInput.closeEntry()
                }
            }
        }

        val skillMdPaths = files.keys
            .filter { it.substringAfterLast('/').equals("SKILL.md", ignoreCase = true) }
            .sorted()
        if (skillMdPaths.isEmpty()) {
            error("压缩包中未找到 SKILL.md")
        }
        val skillBasePaths = skillMdPaths.map {
            it.substringBeforeLast('/', missingDelimiterValue = "")
        }

        val importedNames = mutableListOf<String>()
        for (skillMdPath in skillMdPaths) {
            val skillContent = files[skillMdPath]?.toString(Charsets.UTF_8)
                ?: error("读取失败：$skillMdPath")
            val frontmatter = SkillFrontmatterParser.parse(skillContent)
            val name = frontmatter["name"]?.trim()
            if (name.isNullOrBlank()) {
                error("$skillMdPath 格式错误：缺少 name 字段")
            }
            if (frontmatter["description"].isNullOrBlank()) {
                error("$skillMdPath 格式错误：缺少 description 字段")
            }

            val basePath = skillMdPath.substringBeforeLast('/', missingDelimiterValue = "")
            val skillFiles = LinkedHashMap<String, ByteArray>()
            for ((path, content) in files) {
                if (isInsideNestedSkill(path, basePath, skillBasePaths)) continue
                val relativePath = relativeToSkillBase(path, basePath) ?: continue
                val targetPath = if (relativePath.equals("SKILL.md", ignoreCase = true)) {
                    "SKILL.md"
                } else {
                    relativePath
                }
                skillFiles[targetPath] = content
            }

            val saved = skillManager.saveSkillFileBytesAtomically(name, skillFiles)
            if (!saved) {
                error("保存失败：$name")
            }
            importedNames += name
        }
        return importedNames.distinct()
    }

    private fun isInsideNestedSkill(path: String, basePath: String, skillBasePaths: List<String>): Boolean {
        return skillBasePaths.any { otherBasePath ->
            otherBasePath != basePath &&
                isPathInsideBase(path, otherBasePath) &&
                (basePath.isBlank() || isPathInsideBase(otherBasePath, basePath))
        }
    }

    private fun isPathInsideBase(path: String, basePath: String): Boolean {
        return basePath.isBlank() || path == basePath || path.startsWith("$basePath/")
    }

    private fun relativeToSkillBase(path: String, basePath: String): String? {
        if (basePath.isBlank()) return path
        if (path == basePath) return null
        return path.removePrefix("$basePath/").takeIf { it != path }
    }

    private fun normalizeZipEntryPath(path: String): String? {
        val parts = path.replace('\\', '/')
            .trimStart('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun isZipFile(fileName: String, bytes: ByteArray): Boolean {
        return fileName.endsWith(".zip", ignoreCase = true) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x03, 0x04) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x05, 0x06) ||
            bytes.startsWithBytes(0x50, 0x4B, 0x07, 0x08)
    }

    private fun ByteArray.startsWithBytes(vararg values: Int): Boolean {
        if (size < values.size) return false
        return values.indices.all { index -> (this[index].toInt() and 0xFF) == values[index] }
    }

    private fun listFilesRecursively(
        owner: String,
        repo: String,
        branch: String,
        dirPath: String,
        basePath: String,
        result: MutableList<Pair<String, String>>,
    ): Boolean {
        val apiUrl = "https://api.github.com/repos/$owner/$repo/contents/$dirPath?ref=$branch"
        val json = downloadText(apiUrl) ?: return false
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val type = item.getString("type")
            val itemPath = item.getString("path")
            val relativePath = itemPath.removePrefix("$basePath/").removePrefix(basePath)
            when (type) {
                "file" -> {
                    val downloadUrl = item.optString("download_url").takeIf { it.isNotBlank() }
                        ?: return false
                    result.add(relativePath to downloadUrl)
                }

                "dir" -> {
                    val ok = listFilesRecursively(owner, repo, branch, itemPath, basePath, result)
                    if (!ok) return false
                }
            }
        }
        return true
    }

    private data class GitHubRepoInfo(
        val owner: String,
        val repo: String,
        val branch: String,
        val path: String,
    )

    private fun parseGitHubUrl(url: String): GitHubRepoInfo? {
        val trimmed = url.trim().trimEnd('/')
        // https://github.com/owner/repo
        // https://github.com/owner/repo/tree/branch
        // https://github.com/owner/repo/tree/branch/sub/path
        val regex = Regex("""https://github\.com/([^/]+)/([^/]+)(?:/tree/([^/]+)(/.*)?)?""")
        val match = regex.matchEntire(trimmed) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val branch = match.groupValues[3].ifBlank { "HEAD" }
        val subPath = match.groupValues[4].trimStart('/')
        return GitHubRepoInfo(owner, repo, branch, subPath)
    }

    private fun downloadText(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        return try {
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().readText()
            else null
        } finally {
            connection.disconnect()
        }
    }
}
