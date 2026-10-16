package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.Settings

internal fun buildSubagentTool(settings: Settings): Tool {
    val subagents = settings.subagentPrompts.filter { it.enabled }.joinToString("\n") { "- ${it.name}: ${it.description}" }
    return Tool(
        name = "Subagent",
        description = """
            Launch a new subagent to handle complex, multi-step tasks. Each subagent type has specific capabilities and tools available to it.

            Available subagent types and the tools they have access to:
            {{subagents}}

            When using the Subagent, specify a subagent_type parameter to select which subagent type to use. If omitted, the general subagent is used.

            ## Usage notes
            - When you launch multiple subagents for independent work, send them in a single message with multiple tool uses so they run concurrently (up to {{concurrency}} at a time)
            - Trust but verify: an subagent's summary describes what it intended to do, not necessarily what it did. When an subagent writes or edits code, check the actual changes before reporting the work as done.
            - Clearly tell the subagent whether you expect it to write code or just to do research (search, reads, web fetches, etc.), since it is not aware of the user's intent.

            ## Writing the prompt
            Brief the subagent like a smart colleague who just walked into the room it hasn't seen this conversation, doesn't know what you've tried, doesn't understand why this task matters:
            - Explain what you're trying to accomplish and why.
            - Describe what you've already learned or ruled out.
            - Give enough context about the surrounding problem that the subagent can make judgment calls rather than just following a narrow instruction.
            - Lookups: hand over the exact command. Investigations: hand over the question prescribed steps become dead weight when the premise is wrong.
            - Terse command-style prompts produce shallow, generic work.
            - Never delegate understanding. Don't write "based on your findings, fix the bug" or "based on the research, implement it." Those phrases push synthesis onto the subagent instead of doing it yourself. Write prompts that prove you understood: include file paths, line numbers, what specifically to change.
        """.trimIndent()
        .replace("{{subagents}}", subagents)
        .replace("{{concurrency}}", settings.subagentConcurrency.toString()),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("description", buildJsonObject {
                        put("description", "A short (3-5 word) description of the task")
                        put("type", "string")
                    })
                    put("prompt", buildJsonObject {
                        put("description", "The task for the subagent to perform")
                        put("type", "string")
                    })
                    put("subagent_type", buildJsonObject {
                        put("description", "The type of specialized subagent to use for this task")
                        put("type", "string")
                    })
                },
                required = listOf("description", "prompt")
            )
        },
        execute = { error("Subagent tool execute is handled in ChatService") }
    )
}

