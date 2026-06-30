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
            val searchUrl = "https://www.google.com/search?q=" +
                URLEncoder.encode(query, "UTF-8") + "&uccb=1"
            val resultSize = commonOptions.resultSize.coerceIn(1, 10)
            val timeoutMs = serviceOptions.timeoutSeconds.coerceAtLeast(5).toLong() * 1000

            val webView = WebView(context)
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = DESKTOP_UA
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
                                Exception("Failed to load Google: ${error.description}")
                            )
                        }
                    }
                }

                val deadline = System.currentTimeMillis() + timeoutMs
                webView.loadUrl(searchUrl)
                withTimeoutOrNull(deadline - System.currentTimeMillis()) { pageLoaded.await() }
                    ?: error("Search timed out waiting for Google to load")

                var results = emptyList<SearchResultItem>()
                var lastCount = 0
                var stableRounds = 0
                while (System.currentTimeMillis() < deadline) {
                    delay(700)
                    val items = parseItems(webView.evaluateJs(EXTRACT_JS))
                    if (items.size > lastCount) {
                        lastCount = items.size
                        stableRounds = 0
                    } else {
                        stableRounds++
                    }
                    if (items.isNotEmpty()) results = items
                    if (results.size >= resultSize || stableRounds >= 5) break
                }

                require(results.isNotEmpty()) {
                    "Search failed: no organic results found. " +
                        "Google may have shown a consent or captcha page."
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

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private val EXTRACT_JS = """
    (function() {
      window.scrollBy(0, window.innerHeight * 3);
      var seen = {};
      var results = [];
      document.querySelectorAll('div.MjjYud').forEach(function(div) {
        if (div.querySelector('.related-question-pair')) return;
        var h3 = div.querySelector('h3');
        if (!h3) return;
        var link = div.querySelector('a.sXtWJb')
          || (h3.closest ? h3.closest('a') : null)
          || div.querySelector('h3 a');
        if (!link) return;
        var href = link.href;
        if (!href || href.indexOf('http') !== 0 || href.indexOf('google.com/search') !== -1) return;
        if (seen[href]) return;
        seen[href] = true;
        var title = h3.textContent.trim();
        if (!title) return;
        var sn = div.querySelector('div.VwiC3b')
          || div.querySelector('div.lEBKkf')
          || div.querySelector('span.aCOpRe')
          || div.querySelector('[data-sncf]');
        results.push({ title: title, url: href, text: sn ? sn.textContent.trim() : '' });
      });
      return results;
    })()
""".trimIndent()
