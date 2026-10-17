package me.rerere.rikkahub.data.ai.tools.local

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

internal fun List<SubagentPrompt>.migrateLegacySubagentPrompts(): List<SubagentPrompt> = mapNotNull { prompt ->
    when {
        prompt.isBuiltIn && prompt.name == "general-purpose" -> prompt.copy(name = "general")
        prompt.isBuiltIn && prompt.name == "plan" -> null
        else -> prompt
    }
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
