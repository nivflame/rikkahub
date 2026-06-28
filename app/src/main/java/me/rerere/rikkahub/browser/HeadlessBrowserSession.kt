package me.rerere.rikkahub.browser

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-global browser session manager. Owns one off-screen headless [WebView] the agent drives in
 * chat, and also tracks an optional "active" controller owned by the visible [BrowserActivity].
 * When the visible browser is open it registers its controller as active so the browser tools
 * operate on the page the user is looking at, otherwise the tools fall back to the headless
 * WebView. Tool dispatch is serialized so concurrent calls do not race the same WebView.
 */
object HeadlessBrowserSession {
    private val mutex = Mutex()

    @Volatile
    private var active: BrowserController? = null

    private var headless: BrowserController? = null

    private val _url = MutableStateFlow("")
    val urlFlow: StateFlow<String> = _url.asStateFlow()

    fun setActive(controller: BrowserController?) {
        active = controller
    }

    private suspend fun getOrCreateHeadless(context: Context): BrowserController {
        headless?.let { return it }
        return withContext(Dispatchers.Main) {
            headless ?: BrowserController(
                WebView(context.applicationContext),
                onUrlChanged = { _url.value = it }
            ).also { headless = it }
        }
    }

    suspend fun <T> withController(context: Context, block: suspend (BrowserController) -> T): T =
        mutex.withLock {
            val controller = active ?: getOrCreateHeadless(context)
            block(controller)
        }

    suspend fun currentUrl(context: Context): String =
        withController(context) { it.currentUrl() }
}
