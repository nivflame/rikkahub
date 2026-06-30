package me.rerere.search

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.search.SearchResult.SearchResultItem
import java.net.URLEncoder
import kotlin.coroutines.resume

object OoggleSearchService : SearchService<SearchServiceOptions.OoggleOptions> {
    override val name: String = "Ooggle"

    @Composable
    override fun Description() {
        Text(stringResource(R.string.ooggle_desc))
    }

    override fun parameters(options: SearchServiceOptions.OoggleOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.OoggleOptions): InputSchema? = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.OoggleOptions
    ): Result<SearchResult> = withContext(Dispatchers.Main) {
        runCatching {
            val context = SearchService.appContext
                ?: error("SearchService is not initialized with a Context")
            val query = params["query"]?.jsonPrimitive?.content ?: error("query is required")
            val news = params["news"]?.jsonPrimitive?.content?.equals("true", ignoreCase = true) == true
            val searchUrl = "https://www.google.com/search?q=" +
                URLEncoder.encode(query, "UTF-8") + "&uccb=1" + (if (news) "&tbm=nws" else "")
            val resultSize = commonOptions.resultSize.coerceIn(1, 10)
            val timeoutMs = serviceOptions.timeoutSeconds.coerceAtLeast(5).toLong() * 1000

            val webView = WebView(context)
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.setSupportZoom(false)
                webView.isVerticalScrollBarEnabled = false
                webView.isHorizontalScrollBarEnabled = false

                val pageLoaded = CompletableDeferred<Unit>()
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        pageLoaded.complete(Unit)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError
                    ) {
                        if (request.isForMainFrame && pageLoaded.isActive) {
                            pageLoaded.completeExceptionally(
                                Exception("Failed to load search page: ${error.description}")
                            )
                        }
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                webView.loadUrl(searchUrl)
                withTimeoutOrNull(deadline - System.currentTimeMillis()) { pageLoaded.await() }
                    ?: error("Search timed out waiting for the page to load")

                var results = emptyList<SearchResultItem>()
                var lastCount = 0
                var stableRounds = 0
                while (System.currentTimeMillis() < deadline) {
                    delay(700)
                    val extractJs = if (news) EXTRACT_NEWS_JS else EXTRACT_JS
                    val items = parseItems(webView.evaluateJs(extractJs))
                    if (items.size > lastCount) {
                        lastCount = items.size
                        stableRounds = 0
                    } else {
                        stableRounds++
                    }
                    if (items.isNotEmpty()) results = items
                    if (results.size >= resultSize || stableRounds >= 8) break
                }

                require(results.isNotEmpty()) {
                        "Search failed: no organic results found. " +
                        "The search engine may have shown a consent or captcha page."
                }

                SearchResult(items = results.take(resultSize))
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.OoggleOptions
    ): Result<ScrapedResult> {
        return Result.failure(Exception("Scraping is not supported for Ooggle"))
    }

    private fun parseItems(raw: String?): List<SearchResultItem> {
        if (raw == null || raw == "null") return emptyList()
        return runCatching {
            SearchService.json.decodeFromString<List<JsResultItem>>(raw)
                .map { SearchResultItem(title = it.title, url = it.url, text = it.text) }
        }.getOrDefault(emptyList())
    }

    @Serializable
    private data class JsResultItem(
        val title: String = "",
        val url: String = "",
        val text: String = "",
    )

    private suspend fun WebView.evaluateJs(script: String): String? =
        suspendCancellableCoroutine { cont ->
            evaluateJavascript(script) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }
}

private val EXTRACT_JS = """
    (function() {
      window.scrollTo(0, document.body.scrollHeight);
      var seen = {};
      var results = [];
      document.querySelectorAll('a.WlydOe.eR6uYd').forEach(function(a) {
        var href = a.href;
        if (!href || href.indexOf('http') !== 0 || href.indexOf('google.com/search') !== -1) return;
        if (href.indexOf('youtube.com/watch') > -1) return;
        if (seen[href]) return;
        seen[href] = true;
        var titleEl = a.querySelector('div.eAaXgc') || a.querySelector('div.pontCc');
        var title = titleEl ? titleEl.textContent.trim() : '';
        if (!title) return;
        var timeEl = a.querySelector('div.OSrXXb');
        var sourceEl = a.querySelector('div.xLCVmd') || a.querySelector('div.iDBaYb');
        var time = timeEl ? timeEl.textContent.trim() : '';
        var source = sourceEl ? sourceEl.textContent.trim() : '';
        var snippet = '';
        var container = a.closest('div.Ww4FFb') || a.parentElement.parentElement;
        if (container) {
          var snEl = container.querySelector('div.VwiC3b');
          if (snEl) snippet = snEl.textContent.trim();
        }
        var text = snippet || [source, time].filter(function(s) { return s; }).join(', ');
        results.push({ title: title, url: href, text: text });
      });
      return results;
    })()
""".trimIndent()

private val EXTRACT_NEWS_JS = """
    (function() {
      window.scrollTo(0, document.body.scrollHeight);
      var seen = {};
      var results = [];
      document.querySelectorAll('a.WlydOe').forEach(function(a) {
        var href = a.href;
        if (!href || href.indexOf('http') !== 0 || href.indexOf('google.com/search') !== -1) return;
        if (seen[href]) return;
        seen[href] = true;
        var titleEl = a.querySelector('div.n0jPhd');
        var title = titleEl ? titleEl.textContent.trim() : '';
        if (!title) return;
        var sourceEl = a.querySelector('div.SoAPf > div:first-child');
        var timeEl = a.querySelector('div.M8eS9e');
        var source = sourceEl ? sourceEl.textContent.trim() : '';
        var date = timeEl ? timeEl.textContent.trim() : '';
        var text = [source, date].filter(function(s) { return s; }).join(', ');
        results.push({ title: title, url: href, text: text });
      });
      return results;
    })()
""".trimIndent()
