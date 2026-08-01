package me.rerere.rikkahub.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.uuid.Uuid

/**
 * Wraps a single [WebView] and exposes the suspend operations backing the browser tools.
 * Every WebView call runs on the main thread (WebView is not thread-safe). Page loads are
 * awaited via [WebViewClient.onPageFinished] with a hard per-tool timeout so a hung page
 * cannot wedge the agent loop.
 */
class BrowserController(val webView: WebView, private val onUrlChanged: ((String) -> Unit)? = null) {
    var perToolTimeoutMs: Long = DEFAULT_PER_TOOL_TIMEOUT_MS

    private var loadDeferred: CompletableDeferred<Unit>? = null

    @Volatile
    private var readabilityInjected = false
    private var readabilityScript: String? = null
    @Volatile
    private var turndownInjected = false
    private var turndownScript: String? = null

    @Volatile
    private var lastRequestAt = 0L

    private val displayW: Int = webView.context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    private val displayH: Int = webView.context.resources.displayMetrics.heightPixels.coerceAtLeast(1)

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.blockNetworkImage = true
        webView.settings.loadsImagesAutomatically = false
        webView.settings.userAgentString = webView.settings.userAgentString + " RikkaHubBrowser/1.0"
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                readabilityInjected = false
                turndownInjected = false
                onUrlChanged?.invoke(url ?: "")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadDeferred?.complete(Unit)
                layoutForCapture(displayW, displayH)
                onUrlChanged?.invoke(url ?: "")
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.url?.let { url ->
                    lastRequestAt = System.currentTimeMillis()
                    val host = url.host ?: return@let
                    val path = url.path ?: ""
                    val lowerPath = path.lowercase()

                    // Block analytics and ad domains
                    if (host in BLOCKED_DOMAINS) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    // Block fonts
                    if (lowerPath.endsWith(".woff") || lowerPath.endsWith(".woff2") ||
                        lowerPath.endsWith(".ttf") || lowerPath.endsWith(".otf") || lowerPath.endsWith(".eot")
                    ) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }

                    // Block favicons
                    if (lowerPath.endsWith(".ico") || lowerPath.contains("favicon")) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }
                return null
            }
        }
    }

    suspend fun navigate(
        url: String,
        type: String = "url",
    ): String = withTimeoutOrNull(perToolTimeoutMs) {
        withContext(Dispatchers.Main) {
            lastRequestAt = System.currentTimeMillis()
            when (type) {
                "back" -> {
                    if (!webView.canGoBack()) return@withContext "no history to go back to"
                    loadDeferred = CompletableDeferred()
                    webView.goBack()
                    loadDeferred?.await()
                }

                "forward" -> {
                    if (!webView.canGoForward()) return@withContext "no history to go forward to"
                    loadDeferred = CompletableDeferred()
                    webView.goForward()
                    loadDeferred?.await()
                }

                "reload" -> {
                    loadDeferred = CompletableDeferred()
                    webView.reload()
                    loadDeferred?.await()
                }

                else -> {
                    loadDeferred = CompletableDeferred()
                    webView.loadUrl(url.ifBlank { "about:blank" })
                    loadDeferred?.await()
                }
            }
            awaitNetworkIdle()
            webView.url ?: ""
        }
    } ?: "timeout navigating"

    suspend fun currentUrl(): String = withContext(Dispatchers.Main) {
        webView.url ?: ""
    }

    suspend fun search(query: String, news: Boolean, resultCount: Int = 20): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val baseUrl = if (news) {
            "https://www.google.com/search?q=$encodedQuery&tbm=nws"
        } else {
            "https://www.google.com/search?q=$encodedQuery"
        }

        val allResults = mutableListOf<String>()
        val seenUrls = mutableSetOf<String>()
        var start = 0
        var pagesRemaining = (resultCount + 9) / 10

        while (allResults.size < resultCount && pagesRemaining > 0) {
            val currentUrl = if (start > 0) "$baseUrl&start=$start" else baseUrl
            navigate(currentUrl)

            val extractJs = """
(function(){
var containers=document.querySelectorAll('[class*="Ww4FFb"]');
var results=[];
var seen=new Set();
var siteNames=['YouTube','Reddit','Medium','Facebook','Twitter','X','Instagram','TikTok','LinkedIn','Pinterest','Quora'];
for(var i=0;i<containers.length;i++){
var links=containers[i].querySelectorAll('a[href]');
for(var j=0;j<links.length;j++){
var link=links[j];
var href=link.href;
if(!href||href.indexOf('google.')>=0) continue;
if(seen.has(href)) continue;
var text=(link.innerText||'').trim();
if(!text||text.length<5) continue;
var lines=text.split('\n').filter(function(s){return s.trim();});
if(lines.length<2) continue;
var title='';
var snippet='';
var line1=lines[1].trim();
if(line1.indexOf('http')===0||line1.indexOf('www.')===0){
title=lines.length>=3?lines[2].trim():'';
if(lines.length>=4){snippet=lines.slice(3).join(' ').trim().slice(0,200);}
}else{
var longest='';
for(var k=0;k<lines.length;k++){
var l=lines[k].trim();
if(l.indexOf('http')===0||l.indexOf('www.')===0) continue;
if(l.match(/^\d+\s*(hour|day|week|month|year|ago|min)/i)) continue;
if(l.match(/^\d{4}$/)) continue;
if(siteNames.indexOf(l)>=0) continue;
if(l.indexOf('\u00b7')>=0) continue;
if(l.length>longest.length) longest=l;
}
title=longest;
}
if(!title||title.length<3) continue;
if(!snippet){
var descEl=containers[i].querySelector('.VwiC3b, .GI74Re');
if(descEl) snippet=descEl.innerText.trim().slice(0,200);
}
seen.add(href);
results.push(JSON.stringify({title:title.slice(0,150),snippet:snippet,url:href}));
}
}
return '['+results.join(',')+']';
})();
            """.trimIndent()

            repeat(5) { attempt ->
                if (attempt > 0) delay(500)
                val extractRaw = withContext(Dispatchers.Main) { evaluateJavascriptAsync(extractJs) }
                val extractText = extractRaw?.let { unquoteJsString(it) } ?: ""

                if (extractText.isNotBlank()) {
                    val jsonArray = Json.parseToJsonElement(extractText).jsonArray
                    for (item in jsonArray) {
                        val url = item.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (url.isNotEmpty() && url !in seenUrls) {
                            seenUrls.add(url)
                            allResults.add(item.toString())
                        }
                    }
                }

                if (allResults.size > 0) return@repeat
            }

            if (allResults.size >= resultCount) break
            start += 10
            pagesRemaining--
        }

        if (allResults.isEmpty()) {
            val noResultJs = "(function(){return document.body.innerText.indexOf('did not match any documents')>=0;})();"
            val noResultRaw = withContext(Dispatchers.Main) { evaluateJavascriptAsync(noResultJs) }
            val isNoResults = noResultRaw?.let { unquoteJsString(it) } == "true"
            if (isNoResults) return "{\"error\":\"Your search did not have any results. You MUST use different query.\"}"
            return "{\"error\":\"RATE LIMITED: You MUST wait 60s before retrying. Run 'sleep 60' via Bash, then call WebSearch again with the same query\"}"
        }

        val jsonResults = allResults.take(resultCount).map { raw ->
            val obj = Json.parseToJsonElement(raw).jsonObject.toMutableMap()
            obj["id"] = JsonPrimitive(Uuid.random().toString().take(6))
            JsonObject(obj)
        }
        return JsonArray(jsonResults).toString()
    }

    suspend fun searchBrave(query: String, news: Boolean, resultCount: Int = 10): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val basePath = if (news) "search.brave.com/news" else "search.brave.com/search"

        val allResults = mutableListOf<String>()
        val seenUrls = mutableSetOf<String>()
        var offset = 0
        var pagesRemaining = (resultCount + 9) / 10

        while (allResults.size < resultCount && pagesRemaining > 0) {
            val url = "https://$basePath?q=$encodedQuery" +
                if (offset > 0) "&offset=$offset&spellcheck=0" else ""
            navigate(url)

            val extractJs = """
(function(){
var selectors='#results .snippet[data-type="web"], main div.results .snippet[data-type="news"], #results .snippet[data-type="news"]';
var snippets=document.querySelectorAll(selectors);
var results=[];
var seen=window.__seenUrls||new Set();
window.__seenUrls=seen;
for(var i=0;i<snippets.length;i++){
var s=snippets[i];
var dataType=s.getAttribute('data-type');
if(dataType!=='web'&&dataType!=='news') continue;
var titleEl=s.querySelector('.title, .search-snippet-title');
var title=titleEl?titleEl.innerText.trim():'';
var link=s.querySelector('a[href]');
var url=link?link.href:'';
if(!url||url.indexOf('search.brave.com')>=0) continue;
if(seen.has(url)) continue;
if(!title) continue;
var descEls=s.querySelectorAll('.content, .description, .snippet-description');
var descParts=[];
for(var j=0;j<descEls.length;j++){
var t=(descEls[j].innerText||'').trim();
if(t&&t.length>5) descParts.push(t);
}
var desc=descParts.join(' ').slice(0,200);
seen.add(url);
results.push(JSON.stringify({title:title.slice(0,150),snippet:desc,url:url}));
}
return '['+results.join(',')+']';
})();
            """.trimIndent()

            repeat(5) { attempt ->
                if (attempt > 0) delay(500)
                val extractRaw = withContext(Dispatchers.Main) { evaluateJavascriptAsync(extractJs) }
                val extractText = extractRaw?.let { unquoteJsString(it) } ?: ""

                if (extractText.isNotBlank()) {
                    val jsonArray = Json.parseToJsonElement(extractText).jsonArray
                    for (item in jsonArray) {
                        val url = item.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (url.isNotEmpty() && url !in seenUrls) {
                            seenUrls.add(url)
                            allResults.add(item.toString())
                        }
                    }
                }

                if (allResults.size > 0) return@repeat
            }

            if (allResults.size >= resultCount) break
            offset += 20
            pagesRemaining--
        }

        if (allResults.isEmpty()) return "{\"error\":\"RATE LIMITED: You MUST wait 60s before retrying. Run 'sleep 60' via Bash, then call WebSearch again with the same query\"}"

        val jsonResults = allResults.take(resultCount).map { raw ->
            val obj = Json.parseToJsonElement(raw).jsonObject.toMutableMap()
            obj["id"] = JsonPrimitive(Uuid.random().toString().take(6))
            JsonObject(obj)
        }
        return JsonArray(jsonResults).toString()
    }

    private suspend fun awaitNetworkIdle(timeoutMs: Long = 3000, quietMs: Long = 300) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            if (lastRequestAt > 0 && now - lastRequestAt > quietMs) return
            kotlinx.coroutines.delay(100)
        }
    }

    suspend fun getContent(maxChars: Int, startIndex: Int): String {
        ensureReadability()
        ensureTurndown()
        val markdown = withContext(Dispatchers.Main) {
            val js = "(function(){ try { var doc = document.cloneNode(true);" +
                " var aoEls = doc.querySelectorAll('[data-attrid*=\"overview\"], [aria-label*=\"AI Overview\" i], .Kevs9'); aoEls.forEach(function(el){el.remove();});" +
                " var heads = doc.querySelectorAll('h1, h2, div.Fzsovc, div.YzCcne'); heads.forEach(function(h){if(h.textContent.trim()==='AI Overview'){var p=h.parentElement; if(p)p.remove();}});" +
                " var article = new Readability(doc).parse();" +
                " var html = article ? (article.content || '') : (doc.body ? doc.body.outerHTML : '');" +
                " if(!html) return '';" +
                " var td = new TurndownService({headingStyle:'atx', bulletListMarker:'-', codeBlockStyle:'fenced'});" +
                " td.addRule('absoluteLinks', {filter:function(n){return n.nodeName==='A' && n.getAttribute('href');}, replacement:function(c, n){var h=n.getAttribute('href'); try{h=new URL(h, location.href).href;}catch(e){} return '['+(c||n.textContent||'')+']('+h+')';}});" +
                " var md = td.turndown(html);" +
                " if(md && md.replace(/\\s/g,'').length < 200) {" +
                "   var mainEl = document.querySelector('article, main, [role=\"main\"]');" +
                "   if(mainEl) { var mainMd = td.turndown(mainEl.outerHTML); if(mainMd && mainMd.replace(/\\s/g,'').length > 200) return mainMd; }" +
                "   if(doc.body) return doc.body.textContent;" +
                "   return '';" +
                " }" +
                " return md;" +
                " } catch(e) { return document.body ? document.body.innerText : ''; } })();"
            val raw = evaluateJavascriptAsync(js)
            raw?.let { unquoteJsString(it) } ?: ""
        }
        return paginateMarkdown(markdown, startIndex, maxChars)
    }

    suspend fun fetch(url: String, maxChars: Int, startIndex: Int): String {
        navigate(url)
        return getContent(maxChars, startIndex)
    }

    suspend fun waitFor(selector: String, timeoutMs: Long): String {
        val cssChars = setOf('#', '.', '>', '[', ':', '*')
        val isCss = selector.any { it in cssChars } || selector.startsWith("//")
        val checkJs = if (isCss) {
            val sel = Json.encodeToString(selector)
            "(function(){return document.querySelector($sel)?'true':'false';})()"
        } else {
            val text = Json.encodeToString(selector)
            "(function(){var t=$text;var els=document.querySelectorAll('*');" +
                "for(var i=0;i<els.length;i++){" +
                "var c=els[i].textContent||'';" +
                "if(c.indexOf(t)>=0&&els[i].children.length===0)return'true';" +
                "}return'false';})()"
        }
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val raw = withContext(Dispatchers.Main) { evaluateJavascriptAsync(checkJs) }
            val result = raw?.let { unquoteJsString(it) }
            if (result == "true") return "found"
            kotlinx.coroutines.delay(500)
        }
        return "not found within ${timeoutMs}ms"
    }

    private fun paginateMarkdown(markdown: String, startIndex: Int, maxChars: Int): String {
        if (markdown.isBlank()) return "no content on the current page"
        val lines = markdown.split("\n")
        val total = lines.size
        if (startIndex >= total) return "[No more content. Total lines: $total.]"
        var chars = 0
        var end = startIndex
        for (i in startIndex until total) {
            val len = lines[i].length + 1
            if (chars + len > maxChars && i > startIndex) break
            chars += len
            end = i + 1
        }
        val slice = lines.subList(startIndex, end).joinToString("\n")
        return if (end < total) {
            slice + "\n\n[Content truncated. Total lines: $total. Showing lines $startIndex to ${end - 1}. Call again with start_index=$end to continue. Read all chunks before responding.]"
        } else {
            slice
        }
    }

    private suspend fun ensureReadability() {
        if (readabilityInjected) return
        val script = readabilityScript ?: withContext(Dispatchers.IO) {
            runCatching {
                webView.context.assets.open("browser/readability.js").bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: ""
        readabilityScript = script
        if (script.isNotBlank()) {
            withContext(Dispatchers.Main) { evaluateJavascriptAsync(script) }
        }
        readabilityInjected = true
    }

    private suspend fun ensureTurndown() {
        if (turndownInjected) return
        val script = turndownScript ?: withContext(Dispatchers.IO) {
            runCatching {
                webView.context.assets.open("browser/turndown.js").bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: ""
        turndownScript = script
        if (script.isNotBlank()) {
            withContext(Dispatchers.Main) { evaluateJavascriptAsync(script) }
        }
        turndownInjected = true
    }

    suspend fun interact(
        action: String,
        selector: String? = null,
        value: String? = null,
        key: String? = null,
        text: String? = null,
        doubleClick: Boolean = false
    ): String = withContext(Dispatchers.Main) {
        val params = buildJsonObject {
            put("action", action)
            put("selector", selector ?: "")
            put("value", value ?: "")
            put("key", key ?: "")
            put("text", text ?: "")
            put("doubleClick", doubleClick)
        }
        val js = "(function(){var p=$params;var el=p.selector?document.querySelector(p.selector):null;" +
            "try{var a=p.action;" +
            "if(a==='click'){if(!el)return 'element not found';el.dispatchEvent(new MouseEvent('click',{bubbles:true}));" +
            "if(p.doubleClick)el.dispatchEvent(new MouseEvent('click',{bubbles:true}));return 'clicked';}" +
            "if(a==='fill'){if(!el)return 'element not found';el.focus();el.value=p.value;" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));return 'filled';}" +
            "if(a==='scroll'){var v=parseInt(p.value||'0');if(el){var node=el;while(node&&node!==document.body){var oy=getComputedStyle(node).overflowY;if(oy==='auto'||oy==='scroll'){node.scrollTop+=v;return 'scrolled';}node=node.parentElement;}}window.scrollBy(0,v);return 'scrolled';}" +
            "if(a==='hover'){if(!el)return 'element not found';el.dispatchEvent(new MouseEvent('mouseover',{bubbles:true}));return 'hovered';}" +
            "if(a==='press_key'){document.dispatchEvent(new KeyboardEvent('keydown',{key:p.key,bubbles:true}));" +
            "document.dispatchEvent(new KeyboardEvent('keyup',{key:p.key,bubbles:true}));return 'pressed '+p.key;}" +
            "if(a==='type_text'){if(!el)return 'element not found';el.focus();el.value=el.value+p.text;" +
            "el.dispatchEvent(new Event('input',{bubbles:true}));return 'typed';}" +
            "return 'unknown action';}catch(e){return 'error: '+e.message;}})();"
        val raw = evaluateJavascriptAsync(js)
        raw?.let { unquoteJsString(it) } ?: "ok"
    }

    suspend fun domSnapshot(selector: String?, maxNodes: Int): String = withContext(Dispatchers.Main) {
        val sel = Json.encodeToString(selector ?: "")
        val js = """
(function(){var sel=$sel,out=[],refCount=0,skip=['script','style','noscript','svg','path','head','meta','link','br','wbr','hr','iframe','canvas'];
function roleOf(el){var r=el.getAttribute('role');if(r==='none'||r==='presentation')return null;if(r)return r;var tag=el.tagName.toLowerCase();
if(tag==='a')return el.getAttribute('href')?'link':null;
if(tag==='button')return 'button';
if(tag==='input'){var ty=(el.getAttribute('type')||'text').toLowerCase();if(ty==='checkbox')return 'checkbox';if(ty==='radio')return 'radio';if(ty==='submit'||ty==='button'||ty==='reset')return 'button';if(ty==='search')return 'searchbox';return 'textbox';}
if(tag==='textarea')return 'textbox';if(tag==='select')return 'combobox';if(tag==='img')return 'img';
if(tag==='h1'||tag==='h2'||tag==='h3'||tag==='h4'||tag==='h5'||tag==='h6')return 'heading';
if(tag==='nav')return 'navigation';if(tag==='main')return 'main';if(tag==='article')return 'article';
if(tag==='section')return 'region';if(tag==='aside')return 'complementary';if(tag==='header')return 'banner';
if(tag==='footer')return 'contentinfo';if(tag==='form')return 'form';if(tag==='ul'||tag==='ol')return 'list';
if(tag==='li')return 'listitem';if(tag==='table')return 'table';return null;}
function nameOf(el){return (el.getAttribute('aria-label')||el.getAttribute('alt')||el.getAttribute('title')||el.getAttribute('placeholder')||(el.innerText||'').trim()||'').slice(0,120);}
function directText(el){var s='';for(var i=0;i<el.childNodes.length;i++){var n=el.childNodes[i];if(n.nodeType===3)s+=n.textContent;}return s.trim();}
function ind(d){return Array(d+1).join('  ');}
var leafRoles=['link','button','img','heading','listitem'];
function walk(el,depth){if(out.length>$maxNodes)return;
if(el.nodeType===3){var t=(el.textContent||'').trim();if(t)out.push(ind(depth)+'text: '+t.slice(0,120));return;}
if(el.nodeType!==1)return;var tag=el.tagName.toLowerCase();if(skip.indexOf(tag)>=0)return;
var role=roleOf(el);if(role==='alert')return;
if(!role){var dt=directText(el);if(dt)out.push(ind(depth)+'text: '+dt.slice(0,120));for(var i=0;i<el.children.length;i++)walk(el.children[i],depth);return;}
var name=nameOf(el);var line=ind(depth)+role;if(name)line+=' "'+name+'"';
var href=el.getAttribute('href');if(href){try{href=new URL(href,location.href).href;}catch(e){}line+=' [href='+href.slice(0,100)+']';}
if(role==='heading'){var lvl=el.getAttribute('aria-level');if(!lvl&&tag.length===2&&tag.charAt(0)==='h')lvl=tag.charAt(1);if(lvl)line+=' [level='+lvl+']';}
if((tag==='input'||tag==='textarea')&&el.value)line+=' [value='+String(el.value).slice(0,80)+']';
if(['link','button','textbox','combobox','checkbox','radio','searchbox','img','listitem','heading'].indexOf(role)>=0){refCount++;var ref='e'+refCount;el.setAttribute('data-rkref',ref);line+=' [ref='+ref+']';}
out.push(line);
if(!name||leafRoles.indexOf(role)<0){for(var i=0;i<el.children.length;i++)walk(el.children[i],depth+1);}}
var root=sel?document.querySelector(sel):document.body;if(!root)return 'element not found';walk(root,0);return out.join('\n');})();
        """.trimIndent()
        val raw = evaluateJavascriptAsync(js)
        raw?.let { unquoteJsString(it) } ?: "no snapshot"
    }

    suspend fun executeScript(expression: String): String = withContext(Dispatchers.Main) {
        if (webView.measuredWidth <= 0) layoutForCapture(displayW, displayH)
        val raw = evaluateJavascriptAsync(expression)
        raw?.let { unquoteJsString(it) } ?: "null"
    }

    fun close() {
    }

    suspend fun screenshot(
        maxHeightPx: Int,
        context: Context,
        selector: String? = null,
        fullPage: Boolean = false
    ): String? = withTimeoutOrNull(perToolTimeoutMs) {
        val bitmap = withContext(Dispatchers.Main) {
            val metrics = context.resources.displayMetrics
            val displayW = metrics.widthPixels.coerceAtLeast(1)
            val displayH = metrics.heightPixels.coerceAtLeast(1)
            // Use the WebView's current on-screen size when it is already laid out (what the user
            // sees), otherwise fall back to the device display size for the headless WebView.
            if (fullPage) {
                val sh = evaluateJavascriptAsync("document.documentElement.scrollHeight")
                    ?.let { unquoteJsString(it) }?.toIntOrNull() ?: displayH
                val fw = webView.measuredWidth.takeIf { it > 0 } ?: displayW
                layoutForCapture(fw, sh.coerceIn(1, maxHeightPx))
            } else if (webView.measuredWidth <= 0 || webView.measuredHeight <= 0) {
                layoutForCapture(displayW, displayH)
            }
            val w = webView.measuredWidth.coerceAtLeast(1)
            val h = webView.measuredHeight.coerceAtLeast(1)
            val full = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { webView.draw(Canvas(it)) }
            if (selector != null) {
                val sel = Json.encodeToString(selector)
                val rectRaw = evaluateJavascriptAsync(
                    "(function(){var e=document.querySelector($sel);if(!e)return null;" +
                        "var r=e.getBoundingClientRect();return JSON.stringify({x:r.x,y:r.y,w:r.width,h:r.height});})();"
                )
                val rect = rectRaw?.let { parseRect(it) }
                if (rect != null) {
                    val cx = rect.left.toInt().coerceIn(0, (w - 1).coerceAtLeast(0))
                    val cy = rect.top.toInt().coerceIn(0, (h - 1).coerceAtLeast(0))
                    val cw = rect.width().toInt().coerceIn(1, w - cx)
                    val ch = rect.height().toInt().coerceIn(1, h - cy)
                    Bitmap.createBitmap(full, cx, cy, cw, ch).also { full.recycle() }
                } else {
                    full
                }
            } else {
                full
            }
        }
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "browser-shots").apply { mkdirs() }
            val file = dir.resolve("shot-${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 60, it) }
            bitmap.recycle()
            file.absolutePath
        }
    }

    private fun parseRect(raw: String): RectF? {
        val json = unquoteJsString(raw).ifBlank { return null }
        return runCatching {
            val obj = Json.parseToJsonElement(json).jsonObject
            val x = obj["x"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val y = obj["y"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val w = obj["w"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            val h = obj["h"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f
            RectF(x, y, x + w, y + h)
        }.getOrNull()
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
        const val MAX_CONTENT_CHARS = 50 * 1024
        const val MAX_LINKS = 200
        const val MAX_DOM_NODES = 200
        const val MAX_SCREENSHOT_HEIGHT_PX = 8192

        private val BLOCKED_DOMAINS = setOf(
            "cdn.optimizely.com",
            "cdn.tinypass.com",
            "static.chartbeat.com",
            "www.googletagmanager.com",
            "www.googleadservices.com",
            "www.googletagservices.com",
            "www.google-analytics.com",
            "ssl.google-analytics.com",
            "doubleclick.net",
            "ad.doubleclick.net",
            "stats.g.doubleclick.net",
            "connect.facebook.net",
            "static.ads-twitter.com",
            "analytics.twitter.com",
            "bat.bing.com",
            "pixel.adsafeprotected.com",
            "sb.scorecardresearch.com",
            "rum.staticOpera.com",
            "cdn.branch.io",
            "app.link",
        )
    }
}
