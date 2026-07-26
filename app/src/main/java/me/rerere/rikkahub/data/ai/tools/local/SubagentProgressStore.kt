package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class SubagentProgress(
    val currentTool: String? = null,
    val latestText: String = "",
    val step: Int = 0,
)

class SubagentProgressStore {
    private val _active = MutableStateFlow<Map<String, SubagentProgress>>(emptyMap())
    val active: StateFlow<Map<String, SubagentProgress>> = _active.asStateFlow()

    private val sessions = ConcurrentHashMap<Int, List<UIMessage>>()
    private val sessionIdCounter = AtomicInteger(0)

    fun update(toolCallId: String, progress: SubagentProgress) {
        _active.value = _active.value + (toolCallId to progress)
    }

    fun remove(toolCallId: String) {
        _active.value = _active.value - toolCallId
    }

    fun get(toolCallId: String): SubagentProgress? = _active.value[toolCallId]

    fun saveSession(messages: List<UIMessage>): Int {
        val id = sessionIdCounter.incrementAndGet()
        sessions[id] = messages
        return id
    }

    fun getSession(id: Int): List<UIMessage>? = sessions[id]

    fun clearSession(id: Int) {
        sessions.remove(id)
    }
}
