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
    val enabled: Boolean = true,
    val modelId: Uuid? = null,
    val isBuiltIn: Boolean = false,
    val modeInjectionIds: Set<Uuid> = emptySet(),
)

internal val SUBAGENT_LOCAL_TOOL_NAMES: List<String> = listOf(
    "Subagent",
    "Bash",
    "Read",
    "Write",
    "Edit",
    "AskQuestion",
    "Skill",
    "WebSearch",
    "WebFetch",
    "ToolSearch",
)

private val GENERAL_PURPOSE_EXCLUDE: Set<String> = setOf(
    "Subagent",
)

private val READ_ONLY_EXCLUDE: Set<String> = setOf(
    "Subagent",
    "Edit",
    "Write",
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
        val exclude = if (name == "general-purpose") GENERAL_PURPOSE_EXCLUDE else READ_ONLY_EXCLUDE
        val enabledTools = ALL_SUBAGENT_TOOLS - exclude
        SubagentPrompt(
            name = name,
            description = description,
            systemPrompt = body.trim(),
            enabledTools = enabledTools,
            isBuiltIn = true,
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

fun mergeSubagentPrompts(
    backup: List<SubagentPrompt>,
    current: List<SubagentPrompt>,
    defaults: List<SubagentPrompt>,
): List<SubagentPrompt> {
    val defaultByName = defaults.associateBy { it.name }
    val currentEditedBuiltIns = current.filter { prompt ->
        prompt.isBuiltIn && defaultByName[prompt.name]?.let { default ->
            prompt.copy(id = default.id) != default
        } ?: false
    }.associateBy { it.name }

    val result = mutableListOf<SubagentPrompt>()
    val processedNames = mutableSetOf<String>()
    val processedIds = mutableSetOf<Uuid>()

    for (prompt in backup) {
        if (prompt.isBuiltIn && prompt.name in currentEditedBuiltIns) {
            result.add(currentEditedBuiltIns[prompt.name]!!)
        } else {
            result.add(prompt)
        }
        processedNames.add(prompt.name)
        if (!prompt.isBuiltIn) {
            processedIds.add(prompt.id)
        }
    }

    for (default in defaults) {
        if (default.name !in processedNames) {
            result.add(default)
            processedNames.add(default.name)
        }
    }

    for (prompt in current) {
        if (!prompt.isBuiltIn && prompt.id !in processedIds) {
            result.add(prompt)
            processedIds.add(prompt.id)
        }
    }

    return result
}
