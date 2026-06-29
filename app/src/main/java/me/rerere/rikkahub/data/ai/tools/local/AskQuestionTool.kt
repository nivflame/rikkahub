package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool

internal val DEFAULT_ASK_QUESTION_DESCRIPTION = """
Use this tool when you need to ask the user questions during execution. This allows you to:
1. Gather user preferences or requirements
2. Clarify ambiguous instructions
3. Get decisions on implementation choices as you work
4. Offer choices to the user about what direction to take.

Usage notes:
- Users will always be able to select "Other" to provide custom text input
- Use multiSelect: true to allow multiple answers to be selected for a question
- If you recommend a specific option, make that the first option in the list and add "(Recommended)" at the end of the label

Preview feature:
Use the optional `preview` field on options when presenting concrete artifacts that users need to visually compare:
- ASCII mockups of UI layouts or components
- Code snippets showing different implementations
- Diagram variations
- Configuration examples

Preview content is rendered as markdown in a monospace box. Multi-line text with newlines is supported. When any option has a preview, the UI switches to a side-by-side layout with a vertical option list on the left and preview on the right. Do not use previews for simple preference questions where labels and descriptions suffice. Note: previews are only supported for single-select questions (not multiSelect).
""".trimIndent()

internal fun buildAskQuestionTool(description: String): Tool = Tool(
    name = "AskQuestion",
    description = description.ifBlank { DEFAULT_ASK_QUESTION_DESCRIPTION },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("questions", buildJsonObject {
                    put("type", "array")
                    put("description", "Questions to ask the user (1-4 questions)")
                    put("minItems", 1)
                    put("maxItems", 4)
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("question", buildJsonObject {
                                put("type", "string")
                                put("description", """The complete question to ask the user. Should be clear, specific, and end with a question mark. Example: "Which library should we use for date formatting?" If multiSelect is true, phrase it accordingly, e.g. "Which features do you want to enable?"""")
                            })
                            put("header", buildJsonObject {
                                put("type", "string")
                                put("description", """Very short label displayed as a chip/tag (max 12 chars). Examples: "Auth method", "Library", "Approach".""")
                            })
                            put("options", buildJsonObject {
                                put("type", "array")
                                put("description", "The available choices for this question. Must have 2-4 options. Each option should be a distinct, mutually exclusive choice (unless multiSelect is enabled). There should be no 'Other' option, that will be provided automatically.")
                                put("minItems", 2)
                                put("maxItems", 4)
                                put("items", buildJsonObject {
                                    put("type", "object")
                                    put("properties", buildJsonObject {
                                        put("label", buildJsonObject {
                                            put("type", "string")
                                            put("description", "The display text for this option that the user will see and select. Should be concise (1-5 words) and clearly describe the choice.")
                                        })
                                        put("description", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Explanation of what this option means or what will happen if chosen. Useful for providing context about trade-offs or implications.")
                                        })
                                        put("preview", buildJsonObject {
                                            put("type", "string")
                                            put("description", "Optional preview content rendered when this option is focused. Use for mockups, code snippets, or visual comparisons that help users compare options. See the tool description for the expected content format.")
                                        })
                                    })
                                    put("required", buildJsonArray {
                                        add("label"); add("description")
                                    })
                                    put("additionalProperties", false)
                                })
                            })
                            put("multiSelect", buildJsonObject {
                                put("type", "boolean")
                                put("description", "Set to true to allow the user to select multiple options instead of just one. Use when choices are not mutually exclusive.")
                                put("default", false)
                            })
                        })
                        put("required", buildJsonArray {
                            add("question"); add("header"); add("options"); add("multiSelect")
                        })
                        put("additionalProperties", false)
                    })
                })
                put("answers", buildJsonObject {
                    put("type", "object")
                    put("description", "User answers collected by the permission component")
                    put("additionalProperties", buildJsonObject { put("type", "string") })
                })
                put("annotations", buildJsonObject {
                    put("type", "object")
                    put("description", "Optional per-question annotations from the user (e.g., notes on preview selections). Keyed by question text.")
                    put("additionalProperties", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("preview", buildJsonObject {
                                put("type", "string")
                                put("description", "The preview content of the selected option, if the question used previews.")
                            })
                            put("notes", buildJsonObject {
                                put("type", "string")
                                put("description", "Free-text notes the user added to their selection.")
                            })
                        })
                        put("additionalProperties", false)
                    })
                })
            },
            required = listOf("questions")
        )
    },
    needsApproval = { true },
    execute = {
        error("AskQuestion tool should be handled by HITL flow")
    }
)
