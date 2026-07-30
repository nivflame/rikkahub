package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import me.rerere.ai.ui.UIMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

@Serializable
data class SubagentProgress(
    val currentTool: String? = null,
    val latestText: String = "",
    val step: Int = 0,
    val finished: Boolean = false,
)

class SubagentProgressStore {
    private val _active = MutableStateFlow<Map<String, SubagentProgress>>(emptyMap())
    val active: StateFlow<Map<String, SubagentProgress>> = _active.asStateFlow()

    private val sessions = ConcurrentHashMap<Uuid, ConcurrentHashMap<Int, List<UIMessage>>>()
    private val sessionIdCounter = AtomicInteger(0)

    fun update(toolCallId: String, progress: SubagentProgress) {
        _active.value = _active.value + (toolCallId to progress)
    }

    fun markFinished(toolCallId: String) {
        val current = _active.value[toolCallId] ?: return
        _active.value = _active.value + (toolCallId to current.copy(finished = true, currentTool = null))
    }

    fun remove(toolCallId: String) {
        _active.value = _active.value - toolCallId
    }

    fun clearAll() {
        _active.value = emptyMap()
    }

    fun get(toolCallId: String): SubagentProgress? = _active.value[toolCallId]

    fun saveSession(conversationId: Uuid, messages: List<UIMessage>): Int {
        val id = sessionIdCounter.incrementAndGet()
        sessions.computeIfAbsent(conversationId) { ConcurrentHashMap() }[id] = messages
        return id
    }

    fun updateSession(conversationId: Uuid, id: Int, messages: List<UIMessage>) {
        sessions[conversationId]?.let { it[id] = messages }
    }

    fun getSession(conversationId: Uuid, id: Int): List<UIMessage>? = sessions[conversationId]?.get(id)

    fun clearSession(conversationId: Uuid, id: Int) {
        sessions[conversationId]?.remove(id)
    }

    fun clearConversationSessions(conversationId: Uuid) {
        sessions.remove(conversationId)
    }
}
