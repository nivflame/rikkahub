package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.event.AppEventBus

class LocalTools(private val context: Context, private val eventBus: AppEventBus) {
    val browserTools by lazy { buildBrowserTools(context) }

    fun getTools(
        options: List<LocalToolOption>,
        enabledBrowserTools: Set<String> = emptySet(),
        browserToolDescriptions: Map<String, String> = emptyMap(),
        askQuestionDescription: String = ""
    ): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.AskQuestion)) {
            tools.add(buildAskQuestionTool(askQuestionDescription))
        }
        if (options.contains(LocalToolOption.Browser)) {
            tools.addAll(
                browserTools
                    .filter { it.name in enabledBrowserTools }
                    .map { tool -> browserToolDescriptions[tool.name]?.let { tool.copy(description = it) } ?: tool }
            )
        }
        return tools
    }
}
