package me.rerere.rikkahub.data.ai.tools.local

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class ToolCallIdContextElement(
    val toolCallId: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ToolCallIdContextElement>
}

val CoroutineContext.toolCallId: String?
    get() = this[ToolCallIdContextElement]?.toolCallId
