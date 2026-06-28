package me.rerere.rikkahub.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps a single [WebView] and exposes the suspend operations backing the browser tools.
 * Every WebView call runs on the main thread (WebView is not thread-safe). Page loads are
 * awaited via [WebViewClient.onPageFinished] with a hard per-tool timeout so a hung page
 * cannot wedge the agent loop.
 */
class BrowserController(val webView: WebView) {
    var perToolTimeoutMs: Long = DEFAULT_PER_TOOL_TIMEOUT_MS

    private var loadDeferred: CompletableDeferred<Unit>? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = webView.settings.userAgentString + " RikkaHubBrowser/1.0"
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                loadDeferred?.complete(Unit)
            }
        }
    }

    suspend fun open(url: String): String = withTimeoutOrNull(perToolTimeoutMs) {
        withContext(Dispatchers.Main) {
            loadDeferred = CompletableDeferred()
            webView.loadUrl(url)
            loadDeferred?.await()
            webView.url ?: url
        }
    } ?: "timeout loading $url"

    suspend fun currentUrl(): String = withContext(Dispatchers.Main) {
        webView.url ?: ""
    }

    suspend fun back(): String = withTimeoutOrNull(perToolTimeoutMs) {
        withContext(Dispatchers.Main) {
            if (!webView.canGoBack()) return@withContext "no history to go back to"
            loadDeferred = CompletableDeferred()
            webView.goBack()
            loadDeferred?.await()
            webView.url ?: ""
        }
    } ?: "timeout going back"

    suspend fun getText(maxChars: Int): String = withContext(Dispatchers.Main) {
        val js = "(function(){ var b=document.body; return b ? b.innerText : ''; })();"
        val raw = evaluateJavascriptAsync(js)
        raw?.let { unquoteJsString(it) }?.take(maxChars) ?: ""
    }

    suspend fun getLinks(maxCount: Int): String = withContext(Dispatchers.Main) {
        val js = "(function(){ var out=[]; var as=document.querySelectorAll('a[href]');" +
            " for (var i=0;i<as.length && i<${maxCount};i++){" +
            " out.push({href:as[i].href,text:(as[i].innerText||'').trim()}); }" +
            " return JSON.stringify(out); })();"
        val raw = evaluateJavascriptAsync(js)
        raw?.let { unquoteJsString(it) } ?: "[]"
    }

    suspend fun screenshot(maxHeightPx: Int, context: Context): String? =
        withTimeoutOrNull(perToolTimeoutMs) {
            withContext(Dispatchers.Main) {
                layoutForCapture()
                val w = webView.measuredWidth.coerceAtLeast(1)
                val h = webView.measuredHeight.coerceAtLeast(1).coerceAtMost(maxHeightPx)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                webView.draw(Canvas(bitmap))
                val dir = File(context.cacheDir, "browser-shots").apply { mkdirs() }
                val file = dir.resolve("shot-${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 60, it) }
                bitmap.recycle()
                file.absolutePath
            }
        }

    private fun layoutForCapture(width: Int = 1080, height: Int = 1920) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        webView.measure(widthSpec, heightSpec)
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
    }

    private suspend fun evaluateJavascriptAsync(script: String): String? {
        val deferred = CompletableDeferred<String?>()
        webView.evaluateJavascript(script) { result -> deferred.complete(result) }
        return withTimeoutOrNull(perToolTimeoutMs) { deferred.await() }
    }

    private fun unquoteJsString(raw: String): String {
        if (raw == "null") return ""
        return runCatching { Json.decodeFromString<String>(raw) }.getOrDefault(raw.trim('"'))
    }

    companion object {
        const val DEFAULT_PER_TOOL_TIMEOUT_MS = 30_000L
        const val MAX_TEXT_CHARS = 64 * 1024
        const val MAX_LINKS = 200
        const val MAX_SCREENSHOT_HEIGHT_PX = 8192
    }
}
