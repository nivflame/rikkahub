package me.rerere.rikkahub.browser

import kotlin.uuid.Uuid

/**
 * In-process state shared across [BrowserActivity] instances so reopening the browser resumes
 * the previous browser conversation and reloads the last page instead of starting fresh.
 * Lives for the lifetime of the app process.
 */
object BrowserSessionState {
    var conversationId: Uuid? = null
    var lastUrl: String? = null

    fun reset() {
        conversationId = null
        lastUrl = null
    }
}
