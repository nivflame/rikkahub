package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object AgentsMdTransformer : InputMessageTransformer, KoinComponent {

    private const val TAG = "AgentsMdTransformer"
    private const val AGENTS_MD_FILENAME = "AGENTS.md"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.enableAgentsMdInjection) return messages
        val workspaceId = ctx.assistant.workspaceId?.toString() ?: return messages

        val agentsMdContent = runCatching {
            get<WorkspaceRepository>().readText(workspaceId, AGENTS_MD_FILENAME)
        }.getOrElse {
            Log.d(TAG, "No AGENTS.md found in workspace: $it")
            return messages
        }
        if (agentsMdContent.isBlank()) return messages

        val cwd = ctx.workspaceCwd?.removePrefix("/")?.ifBlank { null } ?: ""
        val injection = buildString {
            appendLine("# AGENTS.md instructions for `$cwd`")
            appendLine()
            appendLine("<INSTRUCTIONS>")
            appendLine(agentsMdContent.trim())
            append("</INSTRUCTIONS>")
        }

        val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
        if (systemIndex < 0) return messages

        val systemMessage = messages[systemIndex]
        val originalText = systemMessage.parts
            .filterIsInstance<UIMessagePart.Text>()
            .joinToString("") { it.text }

        val newText = buildString {
            append(originalText)
            if (originalText.isNotEmpty()) appendLine()
            appendLine()
            append(injection)
        }

        return messages.toMutableList().apply {
            this[systemIndex] = systemMessage.copy(
                parts = listOf(UIMessagePart.Text(newText))
            )
        }
    }
}
