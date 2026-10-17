package me.rerere.rikkahub.data.ai.tools.local

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class SubagentProgress(
    val currentTool: String? = null,
    val latestThinking: String = "",
    val startedAtElapsedRealtime: Long = 0,
    val finishedAtElapsedRealtime: Long = 0,
) {
    val finished: Boolean get() = finishedAtElapsedRealtime > 0
}

class SubagentProgressStore {
    private val _active = MutableStateFlow<Map<String, SubagentProgress>>(emptyMap())
    val active: StateFlow<Map<String, SubagentProgress>> = _active.asStateFlow()

    fun start(toolCallId: String) {
        _active.update { it + (toolCallId to SubagentProgress(startedAtElapsedRealtime = SystemClock.elapsedRealtime())) }
    }

    fun update(toolCallId: String, progress: SubagentProgress) {
        _active.update { current ->
            val startedAt = current[toolCallId]?.startedAtElapsedRealtime ?: SystemClock.elapsedRealtime()
            current + (toolCallId to progress.copy(startedAtElapsedRealtime = startedAt))
        }
    }

    fun markFinished(toolCallId: String) {
        _active.update { current ->
            val existing = current[toolCallId] ?: return@update current
            current + (toolCallId to existing.copy(finishedAtElapsedRealtime = SystemClock.elapsedRealtime()))
        }
    }

    fun clearAll() {
        _active.update { emptyMap() }
    }
}
