package me.rerere.rikkahub.ui.pages.extensions.workspace

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

class WorkspaceVM(
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
