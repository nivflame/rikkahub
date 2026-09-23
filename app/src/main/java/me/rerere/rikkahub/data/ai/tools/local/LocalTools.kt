package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.event.AppEventBus

class LocalTools(private val context: Context, private val eventBus: AppEventBus) {
    val browserTools by lazy { buildBrowserTools(context) }
    val deviceTools by lazy { buildDeviceTools(context) }

    fun getTools(
        options: List<LocalToolOption>,
        enabledBrowserTools: Set<String> = emptySet(),
        browserToolDescriptions: Map<String, String> = emptyMap(),
        askQuestionDescription: String = "",
        webSearchEngine: String = "google",
        webSearchResultCount: Int = 10,
        webSearchDelayMs: Long = 3000L,
        enabledDeviceTools: Set<String> = emptySet(),
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
        if (options.contains(LocalToolOption.Device)) {
            tools.addAll(deviceTools.filter { it.name in enabledDeviceTools })
        }
        if (options.contains(LocalToolOption.WebSearch)) {
            tools.add(buildWebTools(context, webSearchEngine, webSearchResultCount, webSearchDelayMs).first { it.name == "WebSearch" })
        }
        if (options.contains(LocalToolOption.WebFetch)) {
            tools.add(buildWebTools(context, webSearchEngine, webSearchResultCount, webSearchDelayMs).first { it.name == "WebFetch" })
        }
        return tools
    }
}
