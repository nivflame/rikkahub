package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SubagentPrompt(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val enabledTools: List<String> = emptyList(),
)

internal val SUBAGENT_LOCAL_TOOL_NAMES: List<String> = listOf(
    "javascript_engine",
    "time_info",
    "clipboard",
    "tts",
    "ask_user",
    "screen_time",
)

private val SUBAGENT_READ_ONLY_DISABLE: List<String> = listOf(
    "javascript_engine",
    "browser_interact",
    "browser_execute_script",
)

private val ALL_SUBAGENT_TOOLS: List<String> = SUBAGENT_LOCAL_TOOL_NAMES + ALL_BROWSER_TOOL_NAMES

fun defaultSubagentPrompts(): List<SubagentPrompt> = listOf(
    SubagentPrompt(
        name = "general-purpose",
        description = "General-purpose subagent for multi-step research and tasks with browser, search, and MCP tools. Use when a task needs several tool calls or exploration across multiple pages.",
        systemPrompt = "You are a subagent. Given the task, use the tools available to you (browser, search, MCP, and other configured tools) to complete it fully. When done, respond with a concise report covering what you did and any key findings, the caller will relay this to the user so only include the essentials. Prefer browser_get_content for reading pages and call it again with the start_index from the truncation notice until you have read the whole page. Use browser_interact or browser_execute_script only when you need to act on the page. Do not use the Subagent tool.",
        enabledTools = ALL_SUBAGENT_TOOLS,
    ),
    SubagentPrompt(
        name = "explore",
        description = "Read-only research subagent for broad, multi-source information gathering. It reads and navigates but does not modify or interact with pages.",
        systemPrompt = "You are a read-only research subagent. Use the browser, search, and MCP tools to gather information, but do not modify anything. Do not use browser_interact, browser_execute_script, or javascript_engine. Prefer browser_get_content for reading pages (call again with start_index from the truncation notice until done) and browser_dom_snapshot to inspect page structure. Report your findings concisely as your final message. Do not use the Subagent tool.",
        enabledTools = ALL_SUBAGENT_TOOLS - SUBAGENT_READ_ONLY_DISABLE.toSet(),
    ),
    SubagentPrompt(
        name = "plan",
        description = "Read-only planning subagent. Researches with browser, search, and MCP tools, then produces a clear step-by-step plan.",
        systemPrompt = "You are a read-only planning subagent. Research the task using browser, search, and MCP tools (read-only: no browser_interact, browser_execute_script, or javascript_engine), then produce a clear, step-by-step plan. Report the plan concisely as your final message. Do not use the Subagent tool.",
        enabledTools = ALL_SUBAGENT_TOOLS - SUBAGENT_READ_ONLY_DISABLE.toSet(),
    ),
)
