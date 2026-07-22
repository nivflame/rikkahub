package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object AgentsMdTransformer : InputMessageTransformer, KoinComponent {

    private const val AGENTS_MD_FILENAME = "AGENTS.md"
    private const val WORKSPACE_PREFIX = "/workspace"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (!ctx.assistant.enableAgentsMdInjection) return messages
        val workspaceId = ctx.assistant.workspaceId?.toString() ?: return messages

        val absoluteCwd = ctx.workspaceCwd?.ifBlank { null } ?: WORKSPACE_PREFIX
        val relativeCwd = absoluteCwd
            .removePrefix(WORKSPACE_PREFIX)
            .removePrefix("/")
            .trimEnd('/')

        val relativePath = if (relativeCwd.isBlank()) AGENTS_MD_FILENAME else "$relativeCwd/$AGENTS_MD_FILENAME"
        val agentsMdContent = runCatching {
            get<WorkspaceRepository>().readText(workspaceId, relativePath)
        }.getOrNull()
        if (agentsMdContent.isNullOrBlank()) return messages

        val injection = buildString {
            appendLine("# AGENTS.md instructions for `$absoluteCwd`")
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
