package me.rerere.rikkahub.data.event

import kotlin.uuid.Uuid

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data object OpenUsageAccessSettings : AppEvent()

    /** MCP OAuth 授权完成后经 deep link 回传的结果。 */
    data class McpOAuthCallback(
        val state: String?,
        val code: String?,
        val error: String?,
    ) : AppEvent()

    /** Background command completion pushed by an MCP server (notifications/agent-mcp/background). */
    data class McpBackgroundCommand(
        val conversationId: Uuid?,
        val serverName: String,
        val command: String,
        val exitCode: Int,
        val durationMs: Long,
        val timedOut: Boolean,
        val logPath: String,
        val tail: String,
    ) : AppEvent()
}
