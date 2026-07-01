package me.rerere.rikkahub.browser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoSession

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
            headless ?: run {
                val session = GeckoSession()
                session.open(GeckoRuntimeSingleton.getRuntime(context))
                BrowserController(
                    session,
                    context.applicationContext,
                    onUrlChanged = { _url.value = it },
                ).also { headless = it }
            }
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
