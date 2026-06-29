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
    "AskQuestion",
    "screen_time",
)

private val SUBAGENT_READ_ONLY_DISABLE: List<String> = listOf(
    "javascript_engine",
    "browser_interact",
    "browser_execute_script",
)

private val ALL_SUBAGENT_TOOLS: List<String> = SUBAGENT_LOCAL_TOOL_NAMES + ALL_BROWSER_TOOL_NAMES

fun loadDefaultSubagentPrompts(assets: android.content.res.AssetManager): List<SubagentPrompt> {
    val fileNames = listOf("subagent/general-purpose.md", "subagent/explore.md", "subagent/plan.md")
    return fileNames.map { fileName ->
        val content = runCatching {
            assets.open(fileName).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val (frontmatter, body) = parseSubagentFrontmatter(content)
        val name = frontmatter["name"] ?: ""
        val description = frontmatter["description"] ?: ""
        val enabledTools = if (name == "general-purpose") ALL_SUBAGENT_TOOLS else ALL_SUBAGENT_TOOLS - SUBAGENT_READ_ONLY_DISABLE.toSet()
        SubagentPrompt(
            name = name,
            description = description,
            systemPrompt = body.trim(),
            enabledTools = enabledTools,
        )
    }
}

private fun parseSubagentFrontmatter(content: String): Pair<Map<String, String>, String> {
    val lines = content.lines()
    if (lines.isEmpty() || lines[0].trim() != "---") return emptyMap<String, String>() to content
    var endIdx = -1
    for (i in 1 until lines.size) {
        if (lines[i].trim() == "---") {
            endIdx = i
            break
        }
    }
    if (endIdx < 0) return emptyMap<String, String>() to content
    val frontmatterLines = lines.subList(1, endIdx)
    val body = lines.subList(endIdx + 1, lines.size).joinToString("\n")
    val map = mutableMapOf<String, String>()
    frontmatterLines.forEach { line ->
        val idx = line.indexOf(':')
        if (idx > 0) {
            map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
        }
    }
    return map to body
}
