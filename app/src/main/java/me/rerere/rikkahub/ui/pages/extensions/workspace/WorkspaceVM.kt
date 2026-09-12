package me.rerere.rikkahub.ui.pages.extensions.workspace

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import java.io.InputStream
import java.io.OutputStream

class WorkspaceVM(
    private val application: Application,
    private val repository: WorkspaceRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val _workspaces = MutableStateFlow<List<WorkspaceEntity>>(emptyList())

    init {
        viewModelScope.launch {
            combine(repository.listFlow(), settingsStore.workspaceOrderFlow) { list, order ->
                if (order.isEmpty()) return@combine list
                val orderMap = order.withIndex().associate { it.value to it.index }
                list.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
            }.collect { ordered ->
                _workspaces.value = ordered
                refreshSizes()
            }
        }
    }

    val workspaces = _workspaces.asStateFlow()

    private val _workspaceSizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val workspaceSizes = _workspaceSizes.asStateFlow()

    fun refreshSizes() {
        viewModelScope.launch {
            val sizes = mutableMapOf<String, Long>()
            for (workspace in _workspaces.value) {
                sizes[workspace.id] = repository.getWorkspaceSize(workspace.id)
            }
            _workspaceSizes.value = sizes
        }
    }

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { repository.create(name) }
        }
    }

    fun exportRootfs(id: String, outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching { repository.exportRootfs(id, outputStream) }
        }
    }

    fun importWorkspace(uri: Uri, inputStream: InputStream) {
        viewModelScope.launch {
            runCatching {
                val name = application.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }.toWorkspaceName()
                val workspace = repository.create(name)
                repository.importRootfs(workspace.id, inputStream)
            }
        }
    }

    private fun String?.toWorkspaceName(): String {
        if (isNullOrBlank()) return "Imported Workspace"
        val inner = EXPORT_NAME_PATTERN.matchEntire(this)?.groupValues?.getOrNull(1)?.trim()
        if (!inner.isNullOrBlank()) return inner
        val stripped = EXPORT_ARCHIVE_SUFFIXES.fold(this as String) { acc, suffix ->
            if (acc.endsWith(suffix, ignoreCase = true)) acc.dropLast(suffix.length) else acc
        }.trim()
        return stripped.ifBlank { "Imported Workspace" }
    }

    private companion object {
        val EXPORT_NAME_PATTERN = Regex("^rikkahub_workspace\\((.+)\\)_.+$")
        val EXPORT_ARCHIVE_SUFFIXES = listOf(".tar.zst", ".tar.gz", ".tgz", ".zip")
    }

    fun rename(workspace: WorkspaceEntity, name: String) {
        viewModelScope.launch {
            runCatching { repository.rename(workspace.id, name) }
        }
    }

    fun delete(workspace: WorkspaceEntity) {
        viewModelScope.launch {
            repository.delete(workspace.id)
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val current = _workspaces.value
        val reordered = current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        _workspaces.value = reordered
        viewModelScope.launch {
            settingsStore.setWorkspaceOrder(reordered.map { it.id })
        }
    }
}
