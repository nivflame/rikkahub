package me.rerere.rikkahub.browser

import android.content.Context
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * App-global singleton owning one off-screen [WebView] the agent drives in headless chat mode.
 * Created lazily on first use, on the main thread, and kept alive until the process dies so the
 * viewer can still show the current page after the agent stops. Calls are serialized so two
 * concurrent tool dispatches do not race the same WebView.
 */
object HeadlessBrowserSession {
    private val mutex = Mutex()
    private var controller: BrowserController? = null

    private suspend fun getOrCreate(context: Context): BrowserController {
        controller?.let { return it }
        return withContext(Dispatchers.Main) {
            val webView = WebView(context.applicationContext)
            BrowserController(webView).also { controller = it }
        }
    }

    suspend fun <T> withController(context: Context, block: suspend (BrowserController) -> T): T =
        mutex.withLock {
            val c = getOrCreate(context)
            block(c)
        }

    /** Current URL the headless session is on, or empty if it has not navigated yet. */
    suspend fun currentUrl(context: Context): String =
        withController(context) { it.currentUrl() }
}
