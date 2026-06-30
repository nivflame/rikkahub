package me.rerere.rikkahub.data.ai.tools.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart

internal fun buildToolSearchTool(
    deferredTools: List<Tool>,
    activeTools: MutableList<Tool>,
): Tool = Tool(
    name = "ToolSearch",
    description = TOOL_SEARCH_DESCRIPTION,
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("description", TOOL_SEARCH_QUERY_DESC)
                    put("type", "string")
                })
                put("max_results", buildJsonObject {
                    put("description", "Maximum number of results to return (default: 5)")
                    put("type", "number")
                })
            },
            required = listOf("query", "max_results")
        )
    },
    systemPrompt = { _, _ ->
        if (deferredTools.isEmpty()) return@Tool ""
        "Deferred tools (use ToolSearch to fetch their schemas): " +
            deferredTools.joinToString(", ") { it.name }
    },
    execute = { input ->
        val query = input.jsonObject["query"]?.jsonPrimitive?.contentOrNull ?: ""
        val maxResults = input.jsonObject["max_results"]?.jsonPrimitive?.intOrNull ?: 5

        val matched = searchDeferredTools(deferredTools, query, maxResults)

        matched.forEach { tool ->
            if (activeTools.none { it.name == tool.name }) {
                activeTools.add(tool)
            }
        }

        val functionsXml = matched.joinToString("\n") { tool ->
            val schema = tool.parameters()
            val paramsJson = if (schema is InputSchema.Obj) {
                buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", schema.properties)
                    schema.required?.let { req ->
                        put("required", JsonArray(req.map { JsonPrimitive(it) }))
                    }
                }.toString()
            } else {
                "{}"
            }
            val descEscaped = Json.encodeToString(serializer(), tool.description)
            "<function>{\"description\": $descEscaped, \"name\": \"${tool.name}\", \"parameters\": $paramsJson}</function>"
        }

        val result = if (functionsXml.isBlank()) {
            "<functions>\n(no matches found)\n</functions>"
        } else {
            "<functions>\n$functionsXml\n</functions>"
        }

        listOf(UIMessagePart.Text(result))
    }
)

private fun searchDeferredTools(
    deferredTools: List<Tool>,
    query: String,
    maxResults: Int,
): List<Tool> {
    if (query.isBlank()) return emptyList()

    return when {
        query.startsWith("select:") -> {
            val names = query.removePrefix("select:").split(",")
                .map { it.trim() }.filter { it.isNotBlank() }
            deferredTools.filter { it.name in names }
        }

        query.startsWith("+") -> {
            val parts = query.removePrefix("+").trim().split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) return emptyList()
            val required = parts.first().lowercase()
            val keywords = parts.drop(1).map { it.lowercase() }
            deferredTools
                .filter { tool -> required in tool.name.lowercase() }
                .sortedByDescending { tool ->
                    val searchText = (tool.name + " " + tool.description).lowercase()
                    keywords.count { it in searchText }
                }
                .take(maxResults)
        }

        else -> {
            val keywords = query.trim().split(" ").filter { it.isNotBlank() }.map { it.lowercase() }
            deferredTools
                .sortedByDescending { tool ->
                    val searchText = (tool.name + " " + tool.description).lowercase()
                    keywords.count { it in searchText }
                }
                .take(maxResults)
        }
    }
}

private const val TOOL_SEARCH_QUERY_DESC =
    "Query to find deferred tools. Use \"select:<tool_name>\" for direct selection, or keywords to search"

private val TOOL_SEARCH_DESCRIPTION = """
Fetches full schema definitions for deferred tools so they can be called

Deferred tools appear by name in <system-reminder> messages. Until fetched, only the name is known. There is no parameter schema, so the tool cannot be invoked. This tool takes a query, matches it against the deferred tool list, and returns the matched tools complete JSONSchema definitions inside a <functions> block. Once a tools schema appears in that result, it is callable exactly like any tool defined at the top of the prompt

Result format: each matched tool appears as one <function>{"description": "...", "name": "...", "parameters": {...}}</function> line inside the <functions> block. The same encoding as the tool list at the top of this prompt

Query forms:
- "select:Read,Edit,Grep": Fetch these exact tools by name
- "notebook jupyter": Keyword search, up to max_results best matches
- "+slack send": Require "slack" in the name, rank by remaining terms
""".trimIndent()
