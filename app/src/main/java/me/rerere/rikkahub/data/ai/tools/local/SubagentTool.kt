package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.Settings

internal fun buildSubagentTool(settings: Settings): Tool = Tool(
    name = "Subagent",
    description = SUBAGENT_DESCRIPTION_INTRO +
        settings.subagentPrompts.joinToString("\n") { "- ${it.name}: ${it.description}" } +
        SUBAGENT_DESCRIPTION_REST,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("prompt", buildJsonObject {
                    put("description", "The task for the subagent to perform")
                    put("type", "string")
                })
                put("subagent_type", buildJsonObject {
                    put("description", "The type of specialized subagent to use for this task")
                    put("type", "string")
                })
            },
            required = listOf("prompt")
        )
    },
    execute = { error("Subagent tool execute is handled in ChatService") }
)

private const val SUBAGENT_DESCRIPTION_INTRO =
    "Launch a new subagent to handle complex, multi-step tasks. " +
    "Each subagent type has specific capabilities and tools available to it\n\n" +
    "Available subagent types and the tools they have access to:\n"

private val SUBAGENT_DESCRIPTION_REST = """

When using the Subagent, specify a subagent_type parameter to select which subagent type to use. If omitted, the general-purpose subagent is used

## When not to use

If the target is already known, use the direct tool: Read for a known path, `grep` via the Bash for a specific symbol or string. Reserve this tool for open-ended questions that span the codebase, or tasks that match an available subagent type

## Usage notes
- Always include a short description summarizing what the subagent will do
- When you launch multiple subagents for independent work, send them in a single message with multiple tool uses so they run concurrently
- When the subagent is done, it will return a single message back to you. The result returned by the subagent is not visible to the user. To show the user the result, you should send a text message back to the user with a concise summary of the result
- Trust but verify: an subagent's summary describes what it intended to do, not necessarily what it did. When an subagent writes or edits code, check the actual changes before reporting the work as done
- Clearly tell the subagent whether you expect it to write code or just to do research (search, reads, web fetches, etc.), since it is not aware of the user's intent
- If the subagent description mentions that it should be used proactively, then you should try your best to use it without the user having to ask for it first
- If the user specifies that they want you to run subagents "in parallel", you MUST send a single message with multiple Subagent use content blocks. For example, if you need to launch both a build-validator subagent and a test-runner subagent in parallel, send a single message with both tool calls

## Writing the prompt
Brief the subagent like a smart colleague who just walked into the room it hasn't seen this conversation, doesn't know what you've tried, doesn't understand why this task matters
- Explain what you're trying to accomplish and why
- Describe what you've already learned or ruled out
- Give enough context about the surrounding problem that the subagent can make judgment calls rather than just following a narrow instruction
- If you need a short response, say so ("report in under 200 words")
- Lookups: hand over the exact command. Investigations: hand over the question prescribed steps become dead weight when the premise is wrong

Terse command-style prompts produce shallow, generic work

Never delegate understanding. Don't write "based on your findings, fix the bug" or "based on the research, implement it." Those phrases push synthesis onto the subagent instead of doing it yourself. Write prompts that prove you understood: include file paths, line numbers, what specifically to change

Example usage:

<example>
user: "What's left on this branch before we can ship?"
assistant: <thinking>A survey question across git state, tests, and config. I'll delegate it and ask for a short report so the raw command output stays out of my context.</thinking>
Subagent({
  description: "Branch ship-readiness audit",
  prompt: "Audit what's left before this branch can ship. Check: uncommitted changes, commits ahead of main, whether tests exist, whether the GrowthBook gate is wired up, whether CI-relevant files changed. Report a punch list done vs. missing. Under 200 words."
})
<commentary>
The prompt is self-contained: it states the goal, lists what to check, and caps the response length. The subagent's report comes back as the tool result relay the findings to the user.
</commentary>
</example>

<example>
user: "Can you get a second opinion on whether this migration is safe?"
assistant: <thinking>I'll ask the code-reviewer agent it won't see my analysis, so it can give an independent read.</thinking>
Subagent({
  description: "Independent migration review",
  subagent_type: "code-reviewer",
  prompt: "Review migration 0042_user_schema.sql for safety. Context: we're adding a NOT NULL column to a 50M-row table. Existing rows get a backfill default. I want a second opinion on whether the backfill approach is safe under concurrent writes I've checked locking behavior but want independent verification. Report: is this safe, and if not, what specifically breaks?"
})
<commentary>
The subagent starts with no context from this conversation, so the prompt briefs it: what to assess, the relevant background, and what form the answer should take
</commentary>
</example>
""".trimIndent()
