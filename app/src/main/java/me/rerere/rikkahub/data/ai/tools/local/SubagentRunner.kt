package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import java.util.concurrent.atomic.AtomicInteger

class SubagentRunner(
    private val generationHandler: GenerationHandler,
    private val settingsStore: SettingsStore,
) {
    private val running = AtomicInteger(0)

    suspend fun runSync(
        parentTools: List<Tool>,
        subagentType: String,
        prompt: String,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val def = settings.subagentPrompts.firstOrNull { it.name == subagentType }
            ?: return "Subagent type '$subagentType' not found. Available types: " +
                settings.subagentPrompts.joinToString(", ") { it.name } + "."
        if (running.get() >= settings.subagentConcurrency) {
            return "Subagent concurrent limit reached (${settings.subagentConcurrency}). " +
                "Wait for a running subagent to finish before launching another."
        }
        val model = settings.findModelById(settings.subagentModelId)
            ?: return "Subagent model is not configured. Set it in Settings, Subagent."

        running.incrementAndGet()
        try {
            return runSubagent(def, prompt, model, parentTools, settings)
        } catch (e: Exception) {
            return "error: ${e.message}"
        } finally {
            running.decrementAndGet()
        }
    }

    private suspend fun runSubagent(
        def: SubagentPrompt,
        prompt: String,
        model: Model,
        parentTools: List<Tool>,
        settings: Settings,
    ): String {
        val subTools = parentTools.filter { tool ->
            val toggleableNames = SUBAGENT_LOCAL_TOOL_NAMES.toSet() + ALL_BROWSER_TOOL_NAMES.toSet() +
                settings.mcpServers.flatMap { server ->
                    server.commonOptions.tools
                        .map { "mcp__${server.commonOptions.name}__${it.name}" }
                }.toSet()
            tool.name in def.enabledTools || tool.name !in toggleableNames
        }
        val subAssistant = Assistant(
            name = def.name,
            systemPrompt = def.systemPrompt,
            enableMemory = false,
        )
        val messages = listOf(
            UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(prompt)),
            )
        )
        var lastMessages: List<UIMessage> = emptyList()
        generationHandler.generateText(
            settings = settings,
            model = model,
            messages = messages,
            assistant = subAssistant,
            tools = subTools,
            maxSteps = 25,
        ).collect { chunk ->
            if (chunk is GenerationChunk.Messages) lastMessages = chunk.messages
        }
        return lastMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.parts?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }
            ?: "(subagent finished with no text output)"
    }
}
