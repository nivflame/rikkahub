package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata

internal val DEFAULT_SKILL_TOOL_DESCRIPTION = """
Execute a skill within the main conversation

When users ask you to perform tasks, check if any of the available skills match. Skills provide specialized capabilities and domain knowledge

When users reference a "slash command" or "/<something>", they are referring to a skill. Use this tool to invoke it

Important:
- Available skills are listed in the system prompt in the conversation
- Only invoke a skill that appears in that list, or one the user explicitly typed as `/<name>` in their message. Never guess or invent a skill name from training data otherwise do not call this tool
- When a skill matches the user's request, you MUST invoke the relevant Skill BEFORE generating any other response about the task
- NEVER mention a skill without actually calling this tool
- Do not invoke a skill that is already running
""".trimIndent()

internal fun buildSkillTool(
    available: List<SkillMetadata>,
    execute: suspend (JsonElement) -> List<UIMessagePart>,
    description: String = DEFAULT_SKILL_TOOL_DESCRIPTION,
): Tool = Tool(
    name = "Skill",
    description = description,
    systemPrompt = { _, _ ->
        buildString {
            appendLine("**Skills**")
            appendLine("You have access to the following skills. Use the `Skill` tool to load a skill's instructions when the user's request matches.")
            appendLine("<available_skills>")
            available.filter { !it.disableModelInvocation }.forEach { skill ->
                appendLine("  <skill>")
                appendLine("    <name>${skill.name}</name>")
                appendLine("    <description>${skill.description}</description>")
                appendLine("  </skill>")
            }
            append("</available_skills>")
            appendLine()
        }
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("skill", buildJsonObject {
                    put("type", "string")
                    put("description", "The name of a skill from the available-skills list. Do not guess names")
                })
                put("path", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional relative path to a file inside the skill directory. Omit to read the default SKILL.md instructions. Only use paths extracted from Markdown links in the SKILL.md content. Do NOT guess or infer paths.")
                })
            },
            required = listOf("skill")
        )
    },
    execute = execute
)

fun createSkillTools(
    enabledSkills: Set<String>,
    allSkills: List<SkillMetadata>,
    skillManager: SkillManager,
): List<Tool> {
    val available = allSkills.filter { it.name in enabledSkills }
    if (available.isEmpty()) return emptyList()
    return listOf(
        buildSkillTool(available) { json ->
            val name = json.jsonObject["skill"]?.jsonPrimitive?.content
                ?: error("skill is required")
            if (name !in enabledSkills) {
                error("Skill '$name' is not available. Available skills: ${enabledSkills.joinToString()}")
            }
            val skill = available.firstOrNull { it.name == name }
            if (skill?.disableModelInvocation == true) {
                error("Skill '$name' cannot be invoked by the model. Ask the user to invoke it with /$name.")
            }
            val path = json.jsonObject["path"]?.jsonPrimitive?.content
            val content = if (path.isNullOrBlank()) {
                skillManager.readSkillBody(name)
                    ?: error("Skill '$name' not found")
            } else {
                val target = skillManager.resolveSkillFile(name, path)
                    ?: error("Path '$path' is outside the skill directory")
                require(target.exists()) { "File '$path' not found in skill '$name'" }
                target.readText()
            }
            listOf(UIMessagePart.Text(content))
        }
    )
}

fun buildSkillToolForDisplay(): Tool = buildSkillTool(available = emptyList()) { error("display only") }
