package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceStorageArea

class WorkspaceDetailVM(
    private val id: String,
    private val repository: WorkspaceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceDetailState())
    val state = _state.asStateFlow()

    private val _terminalState = MutableStateFlow(WorkspaceTerminalState())
    val terminalState = _terminalState.asStateFlow()

    val installProgress = repository.installProgress
    val installError = repository.installError

    init {
        repository.getByIdFlow(id)
            .onEach { workspace -> _state.update { it.copy(workspace = workspace) } }
            .launchIn(viewModelScope)
        refresh()
    }

    private var refreshJob: Job? = null

    fun selectArea(area: WorkspaceStorageArea) {
        _state.update {
            it.copy(
                area = area,
                path = "",
                entries = emptyList(),
                error = null,
            )
        }
        refresh()
    }

    fun open(entry: WorkspaceFileEntry) {
        if (!entry.isDirectory) return
        _state.update { it.copy(path = entry.path, entries = emptyList(), error = null) }
        refresh()
    }

    fun goUp() {
        val path = state.value.path
        if (path.isBlank()) return
        _state.update {
            it.copy(
                path = path.substringBeforeLast('/', missingDelimiterValue = ""),
                entries = emptyList(),
                error = null,
            )
        }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val area = state.value.area
            val path = state.value.path
            val sortBy = state.value.sortBy
            val ascending = state.value.ascending
            val query = state.value.query.trim()
            val recursive = state.value.recursive
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                if (query.isNotBlank()) {
                    repository.searchFileNames(id = id, area = area, path = path, query = query, recursive = recursive)
                } else {
                    repository.listFiles(id = id, area = area, path = path)
                }
            }.onSuccess { entries ->
                val keyComparator: Comparator<WorkspaceFileEntry> = when (sortBy) {
                    WorkspaceSortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    WorkspaceSortBy.DATE -> compareBy { it.updatedAt }
                    WorkspaceSortBy.SIZE -> compareBy { it.sizeBytes }
                }
                val comparator = compareBy<WorkspaceFileEntry> { !it.isDirectory }
                    .then(if (ascending) keyComparator else keyComparator.reversed())
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                _state.update { it.copy(entries = entries.sortedWith(comparator), loading = false) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        entries = emptyList(),
                        loading = false,
                        error = error.message ?: "加载工作区文件失败",
                    )
                }
            }
        }
    }

    fun setSortBy(sortBy: WorkspaceSortBy) {
        _state.update { it.copy(sortBy = sortBy) }
        refresh()
    }

    fun toggleOrder() {
        _state.update { it.copy(ascending = !it.ascending) }
        refresh()
    }

    fun setSearch(query: String, recursive: Boolean) {
        _state.update { it.copy(query = query, recursive = recursive) }
        refresh()
    }

    fun clearSearch() {
        _state.update { it.copy(query = "", recursive = true) }
        refresh()
    }

    suspend fun permissionOf(entry: WorkspaceFileEntry): String {
        return runCatching {
            repository.filePermission(id = id, area = state.value.area, path = entry.path)
        }.getOrDefault("")
    }

    fun rename(entry: WorkspaceFileEntry, newName: String) {
        viewModelScope.launch {
            runCatching {
                val trimmed = newName.trim()
                require(trimmed.isNotBlank() && !trimmed.contains('/')) { "Invalid name" }
                val parent = entry.path.substringBeforeLast('/', "")
                val destinationPath = if (parent.isBlank()) trimmed else "$parent/$trimmed"
                repository.moveFile(
                    id = id,
                    source = entry.path,
                    target = destinationPath,
                    overwrite = false,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "重命名失败") }
            }
        }
    }

    fun delete(entry: WorkspaceFileEntry) {
        viewModelScope.launch {
            runCatching {
                repository.deleteFile(
                    id = id,
                    area = state.value.area,
                    path = entry.path,
                    recursive = entry.isDirectory,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "删除失败") }
            }
        }
    }

    fun importFile(inputStream: InputStream, fileName: String) {
        viewModelScope.launch {
            runCatching {
                repository.importFile(
                    id = id,
                    area = state.value.area,
                    destinationPath = state.value.path,
                    fileName = fileName,
                    inputStream = inputStream,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "导入文件失败") }
            }
        }
    }

    fun createDirectory(name: String) {
        viewModelScope.launch {
            runCatching {
                val destinationPath = if (state.value.path.isBlank()) name else "${state.value.path}/$name"
                repository.createDirectory(
                    id = id,
                    area = state.value.area,
                    path = destinationPath,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "创建文件夹失败") }
            }
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            runCatching {
                val destinationPath = if (state.value.path.isBlank()) name else "${state.value.path}/$name"
                repository.createFile(
                    id = id,
                    area = state.value.area,
                    path = destinationPath,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "创建文件失败") }
            }
        }
    }

    fun importFolder(treeUri: Uri, context: Context) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
                    val folderName = tree.name?.trim().takeUnless { it.isNullOrBlank() } ?: "folder"
                    val basePath = if (state.value.path.isBlank()) folderName else "${state.value.path}/$folderName"
                    try {
                        repository.createDirectory(id = id, area = state.value.area, path = basePath)
                    } catch (_: Throwable) {
                    }
                    importDocumentTree(tree, basePath, context)
                }
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "导入文件夹失败") }
            }
        }
    }

    private suspend fun importDocumentTree(dir: DocumentFile, currentPath: String, context: Context) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                val dirPath = if (currentPath.isBlank()) child.name ?: "folder" else "$currentPath/${child.name}"
                try {
                    repository.createDirectory(id = id, area = state.value.area, path = dirPath)
                } catch (_: Throwable) {
                    // Directory may already exist, continue
                }
                importDocumentTree(child, dirPath, context)
            } else if (child.isFile) {
                val fileName = child.name ?: "file"
                val inputStream = context.contentResolver.openInputStream(child.uri) ?: continue
                inputStream.use { stream ->
                    repository.importFile(
                        id = id,
                        area = state.value.area,
                        destinationPath = currentPath,
                        fileName = fileName,
                        inputStream = stream,
                    )
                }
            }
        }
    }

    fun exportFile(entry: WorkspaceFileEntry, outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching {
                if (entry.isDirectory) {
                    repository.exportDirectory(
                        id = id,
                        area = state.value.area,
                        path = entry.path,
                        outputStream = outputStream,
                    )
                } else {
                    repository.exportFile(
                        id = id,
                        area = state.value.area,
                        path = entry.path,
                        outputStream = outputStream,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "导出文件失败") }
            }
        }
    }

    fun dismissInstallError() {
        repository.dismissInstallError()
    }

    fun exportRootfs(outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching {
                repository.exportRootfs(id, outputStream)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "导出 rootfs 失败") }
            }
        }
    }

    fun importRootfs(inputStream: InputStream) {
        repository.importRootfs(id, inputStream)
    }

    fun executeTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        // 原子地完成「检查 running」与「置 running=true」, 避免两次快速提交并发启动两条命令
        val previous = _terminalState.getAndUpdate { state ->
            if (state.running) {
                state
            } else {
                state.copy(
                    running = true,
                    input = "",
                    history = state.history + WorkspaceTerminalEntry.Command(trimmed),
                )
            }
        }
        if (previous.running) return
        viewModelScope.launch {
            runCatching {
                repository.executeCommand(id, trimmed)
            }.onSuccess { result ->
                _terminalState.update {
                    it.copy(
                        running = false,
                        history = it.history + WorkspaceTerminalEntry.Result(result),
                    )
                }
            }.onFailure { error ->
                _terminalState.update {
                    it.copy(
                        running = false,
                        history = it.history + WorkspaceTerminalEntry.Error(error.message ?: "命令执行失败"),
                    )
                }
            }
        }
    }

    fun updateTerminalInput(input: String) {
        _terminalState.update { it.copy(input = input) }
    }

    fun clearTerminal() {
        _terminalState.update { it.copy(history = emptyList()) }
    }
}

data class WorkspaceDetailState(
    val workspace: WorkspaceEntity? = null,
    val area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    val path: String = "",
    val entries: List<WorkspaceFileEntry> = emptyList(),
    val sortBy: WorkspaceSortBy = WorkspaceSortBy.NAME,
    val ascending: Boolean = true,
    val query: String = "",
    val recursive: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
)

enum class WorkspaceSortBy {
    NAME,
    DATE,
    SIZE,
}

data class WorkspaceTerminalState(
    val input: String = "",
    val running: Boolean = false,
    val history: List<WorkspaceTerminalEntry> = emptyList(),
)

sealed interface WorkspaceTerminalEntry {
    data class Command(val command: String) : WorkspaceTerminalEntry
    data class Result(val result: WorkspaceCommandResult) : WorkspaceTerminalEntry
    data class Error(val message: String) : WorkspaceTerminalEntry
}
