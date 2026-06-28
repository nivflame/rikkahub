package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.Settings

internal fun buildSubagentTool(settings: Settings): Tool = Tool(
    name = "Subagent",
    description = "Launch a subagent to handle a multi-step task in the background. " +
        "Each subagent type has specific tools. Available types: " +
        settings.subagentPrompts.joinToString("; ") { "${it.name}: ${it.description}" } +
        ". The subagent runs independently and its final result is returned to you " +
        "when it completes. Do not also do the same work yourself, wait for the result.",
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("subagent_type", buildJsonObject {
                    put("type", "string")
                    put("description", "The subagent type. Available: " +
                        settings.subagentPrompts.joinToString(", ") { it.name })
                })
                put("prompt", buildJsonObject {
                    put("type", "string")
                    put("description", "The task for the subagent")
                })
            },
            required = listOf("prompt")
        )
    },
    execute = { error("Subagent tool execute is handled in ChatService") }
)
