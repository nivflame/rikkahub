package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import java.util.concurrent.atomic.AtomicInteger

class SubagentRunner(
    private val generationHandler: GenerationHandler,
    private val settingsStore: SettingsStore,
    private val progressStore: SubagentProgressStore,
) {
    private val running = AtomicInteger(0)

    suspend fun runSync(
        parentTools: List<Tool>,
        subagentType: String,
        prompt: String,
        toolCallId: String,
        sessionId: Int? = null,
    ): String {
        val settings = settingsStore.settingsFlow.value
        val def = settings.subagentPrompts.firstOrNull { it.name == subagentType }
            ?: return "Subagent type '$subagentType' not found. Available types: " +
                settings.subagentPrompts.joinToString(", ") { it.name } + "."
        if (running.get() >= settings.subagentConcurrency) {
            return "Subagent concurrent limit reached (${settings.subagentConcurrency}). " +
                "Wait for a running subagent to finish before launching another."
        }
        val model = settings.findModelById(def.modelId ?: settings.subagentModelId)
            ?: return "Subagent model is not configured. Set it in Settings, Subagent."

        running.incrementAndGet()
        try {
            return runSubagent(def, prompt, model, parentTools, settings, toolCallId, sessionId)
        } catch (e: Exception) {
            return "error: ${e.message}"
        } finally {
            running.decrementAndGet()
            progressStore.remove(toolCallId)
        }
    }

    private suspend fun runSubagent(
        def: SubagentPrompt,
        prompt: String,
        model: Model,
        parentTools: List<Tool>,
        settings: Settings,
        toolCallId: String,
        sessionId: Int?,
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
            modeInjectionIds = def.modeInjectionIds,
        )
        val previousMessages = sessionId?.let { progressStore.getSession(it) }
        val messages = if (previousMessages != null) {
            previousMessages + UIMessage(
                role = MessageRole.USER,
                parts = listOf(UIMessagePart.Text(prompt)),
            )
        } else {
            listOf(
                UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(prompt)),
                )
            )
        }
        var lastMessages: List<UIMessage> = emptyList()
        var step = 0
        val inputTransformers = if (def.modeInjectionIds.isNotEmpty()) {
            listOf<InputMessageTransformer>(PromptInjectionTransformer)
        } else {
            emptyList()
        }
        generationHandler.generateText(
            settings = settings,
            model = model,
            messages = messages,
            inputTransformers = inputTransformers,
            assistant = subAssistant,
            tools = subTools,
            maxSteps = 25,
        ).collect { chunk ->
            if (chunk is GenerationChunk.Messages) {
                lastMessages = chunk.messages
                step++
                val lastAssistant = chunk.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                val latestText = lastAssistant
                    ?.parts?.filterIsInstance<UIMessagePart.Text>()
                    ?.joinToString("") { it.text }
                    ?.takeIf { it.isNotBlank() }
                    ?: ""
                val currentTool = lastAssistant
                    ?.getTools()
                    ?.lastOrNull { !it.isExecuted }
                    ?.toolName
                progressStore.update(
                    toolCallId,
                    SubagentProgress(
                        currentTool = currentTool,
                        latestText = latestText,
                        step = step,
                    ),
                )
            }
        }
        val resultText = lastMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
            ?.parts?.filterIsInstance<UIMessagePart.Text>()
            ?.joinToString("") { it.text }
            ?.takeIf { it.isNotBlank() }
            ?: "(subagent finished with no text output)"
        return if (sessionId != null) {
            progressStore.updateSession(sessionId, lastMessages)
            JsonInstant.encodeToString(
                buildJsonObject {
                    put("result", JsonPrimitive(resultText))
                }
            )
        } else {
            val newSessionId = progressStore.saveSession(lastMessages)
            JsonInstant.encodeToString(
                buildJsonObject {
                    put("result", JsonPrimitive(resultText))
                    put("session_id", JsonPrimitive(newSessionId))
                }
            )
        }
    }
}
