package me.rerere.rikkahub.data.ai

import me.rerere.ai.core.Tool
import me.rerere.ai.ui.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

data class ContextWindowBreakdown(
    val modelName: String,
    val total: Int,
    val systemPrompt: Int,
    val systemTools: Int,
    val messages: Int,
    val toolCalls: Int,
    val toolResults: Int,
    val assistant: Int,
    val user: Int,
    val anchored: Boolean,
)

private fun isCjk(ch: Char): Boolean {
    if (ch.code !in 0x2E80..0x9FFF) return false
    return ch.code !in 0x303F..0x3040
}

internal fun estimateTokensFor(text: String): Int {
    var cjk = 0
    var other = 0
    for (ch in text) {
        if (isCjk(ch)) cjk++ else other++
    }
    return cjk + (other + 3) / 4
}

private const val IMAGE_TOKENS = 800

internal fun estimateSystemToolsTokens(tools: List<Tool>): Int {
    var total = 0
    for (tool in tools) {
        total += estimateTokensFor(tool.name)
        total += estimateTokensFor(tool.description)
        val schema = runCatching { tool.parameters() }.getOrNull()
        if (schema != null) {
            total += estimateTokensFor(schema.toString())
        }
    }
    return total
}

private class Buckets {
    var toolCalls = 0
    var toolResults = 0
    var assistant = 0
    var user = 0
    val sum get() = toolCalls + toolResults + assistant + user
}

private fun accumulate(message: UIMessage, buckets: Buckets) {
    when (message.role) {
        MessageRole.USER -> {
            var tokens = 0
            for (part in message.parts) {
                when (part) {
                    is UIMessagePart.Text -> tokens += estimateTokensFor(part.text)
                    is UIMessagePart.Image -> tokens += IMAGE_TOKENS
                    else -> {}
                }
            }
            buckets.user += tokens
        }
        MessageRole.ASSISTANT -> {
            for (part in message.parts) {
                when (part) {
                    is UIMessagePart.Text -> buckets.assistant += estimateTokensFor(part.text)
                    is UIMessagePart.Reasoning -> buckets.assistant += estimateTokensFor(part.reasoning)
                    is UIMessagePart.Tool -> {
                        buckets.toolCalls += estimateTokensFor(part.toolName) + estimateTokensFor(part.input)
                        if (part.isExecuted) {
                            for (output in part.output) {
                                when (output) {
                                    is UIMessagePart.Text -> buckets.toolResults += estimateTokensFor(output.text)
                                    is UIMessagePart.Image -> buckets.toolResults += IMAGE_TOKENS
                                    else -> {}
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        else -> {}
    }
}

internal fun estimateContextWindow(
    messages: List<UIMessage>,
    systemPromptTokens: Int,
    systemToolsTokens: Int,
    modelName: String,
): ContextWindowBreakdown {
    var anchorIndex = -1
    var anchorTotal = 0
    for ((index, message) in messages.withIndex()) {
        val usage = message.usage
        if (message.role == MessageRole.ASSISTANT && usage != null && usage.promptTokens > 0) {
            anchorTotal = usage.promptTokens + usage.completionTokens
            anchorIndex = index
        }
    }

    val all = Buckets()
    val trailing = Buckets()
    for ((index, message) in messages.withIndex()) {
        accumulate(message, all)
        if (anchorIndex >= 0 && index > anchorIndex) {
            accumulate(message, trailing)
        }
    }

    val total = if (anchorTotal > 0) anchorTotal + trailing.sum else systemPromptTokens + systemToolsTokens + all.sum
    val heuristicSum = systemPromptTokens + systemToolsTokens + all.sum
    val scale = if (heuristicSum > 0 && anchorTotal > 0) total.toDouble() / heuristicSum else 1.0

    fun scaled(v: Int): Int = (v * scale).toInt()
    return ContextWindowBreakdown(
        modelName = modelName,
        total = total,
        systemPrompt = scaled(systemPromptTokens),
        systemTools = scaled(systemToolsTokens),
        messages = scaled(all.sum),
        toolCalls = scaled(all.toolCalls),
        toolResults = scaled(all.toolResults),
        assistant = scaled(all.assistant),
        user = scaled(all.user),
        anchored = anchorTotal > 0,
    )
}
