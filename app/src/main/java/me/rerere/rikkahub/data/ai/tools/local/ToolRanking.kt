package me.rerere.rikkahub.data.ai.tools.local

import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import kotlin.math.ln

private val STOP_WORDS = setOf(
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "in", "is", "it",
    "of", "on", "or", "that", "the", "this", "to", "with",
)

private val LOWER_UPPER = Regex("([a-z0-9])([A-Z])")
private val ACRONYM_TAIL = Regex("([A-Z]+)([A-Z][a-z])")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val PLURAL_ES = Regex("(ches|shes|sses|xes|zes)$")

private fun stem(term: String): String = when {
    term.length > 4 && term.endsWith("ies") -> term.dropLast(3) + "y"
    term.length > 4 && PLURAL_ES.containsMatchIn(term) -> term.dropLast(2)
    term.length > 3 && term.endsWith("s") && !term.endsWith("ss") -> term.dropLast(1)
    else -> term
}

internal fun tokenize(text: String): List<String> = ACRONYM_TAIL
    .replace(LOWER_UPPER.replace(text) { m -> "${m.groupValues[1]} ${m.groupValues[2]}" }) { m ->
        "${m.groupValues[1]} ${m.groupValues[2]}"
    }
    .lowercase()
    .split(NON_ALPHANUMERIC)
    .filter { it.isNotEmpty() && it !in STOP_WORDS }
    .map(::stem)

private fun searchDocument(tool: Tool): String {
    val parts = mutableListOf(tool.name, tool.description)
    val schema = runCatching { tool.parameters() }.getOrNull() as? InputSchema.Obj
    schema?.properties?.forEach { (property, spec) ->
        parts.add(property)
        val description = (spec as? kotlinx.serialization.json.JsonObject)
            ?.get("description")?.jsonPrimitiveOrNull?.content
        if (!description.isNullOrBlank()) parts.add(description)
    }
    return parts.filter { it.isNotBlank() }.joinToString(" ")
}

internal fun rankTools(
    tools: List<Tool>,
    query: String,
    maxResults: Int,
): List<Tool> {
    val k1 = 1.2
    val b = 0.75
    val queryTerms = tokenize(query).distinct()
    if (queryTerms.isEmpty() || tools.isEmpty() || maxResults <= 0) return emptyList()

    val nameTerms = tools.map { tokenize(it.name) }
    val docTerms = tools.map { tokenize(searchDocument(it)) }
    val termCounts = tools.indices.map { index ->
        (nameTerms[index] + docTerms[index]).groupingBy { it }.eachCount()
    }
    val nameTermSets = nameTerms.map { it.toSet() }
    val lengths = termCounts.map { it.values.sum() }
    val averageLength = (lengths.sum().toDouble() / tools.size).takeIf { it > 0 } ?: 1.0
    val idf = queryTerms.associateWith { term ->
        val frequency = termCounts.count { term in it }
        ln(1.0 + (tools.size - frequency + 0.5) / (frequency + 0.5))
    }

    return tools.mapIndexed { index, tool ->
        val matchedTerms = queryTerms.count { it in termCounts[index] }
        val nameFullyCovered = nameTermSets[index].all { it in queryTerms }
        val eligible = matchedTerms >= 2 || (matchedTerms == 1 && nameFullyCovered)
        var score = 0.0
        if (eligible) {
            for (term in queryTerms) {
                val count = termCounts[index][term] ?: continue
                val norm = k1 * (1 - b + b * lengths[index] / averageLength)
                score += idf.getValue(term) * (count * (k1 + 1)) / (count + norm)
            }
        }
        tool to score
    }.filter { (_, score) -> score > 0 }
        .sortedByDescending { (_, score) -> score }
        .take(maxResults)
        .map { (tool, _) -> tool }
}
