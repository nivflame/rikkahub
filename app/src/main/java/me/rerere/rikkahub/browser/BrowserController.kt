package me.rerere.rikkahub.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class BrowserController(
    val session: GeckoSession,
    private val context: Context,
    private val onUrlChanged: ((String) -> Unit)? = null,
) {
    var perToolTimeoutMs: Long = DEFAULT_PER_TOOL_TIMEOUT_MS

    var geckoView: GeckoView? = null
        set(value) {
            field = value
            value?.setSession(session)
        }

    private var loadDeferred: CompletableDeferred<Unit>? = null

    @Volatile
    private var readabilityInjected = false
    private var readabilityScript: String? = null
    @Volatile
    private var turndownInjected = false
    private var turndownScript: String? = null
    private val consoleLogs = ArrayDeque<String>()
    private val networkLogs = ArrayDeque<String>()

    @Volatile
    private var currentUrlValue: String = ""
    @Volatile
    private var canGoBackValue: Boolean = false
    @Volatile
    private var canGoForwardValue: Boolean = false

    private var textZoomPercent: Int = 100
    @Volatile
    private var evalPort: WebExtension.Port? = null
    private var evalDeferred: CompletableDeferred<String?>? = null
    private var evalExtension: WebExtension? = null

    init {
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFirstComposite(session: GeckoSession) {
                loadDeferred?.complete(Unit)
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackValue = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                canGoForwardValue = canGoForward
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                networkLogs.addLast("GET ${request.uri.take(500)}")
                if (networkLogs.size > MAX_LOG_LINES) networkLogs.removeFirst()
                return null
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                loadDeferred?.complete(Unit)
                kotlinx.coroutines.MainScope().launch {
                    evaluateJavascriptAsync(
                        "(function(){var s=document.createElement('style');" +
                            "s.textContent='a[href^=\"#main\"],[class*=\"skip\" i],[aria-label*=\"Skip to\" i]{display:none!important;}';" +
                            "document.head.appendChild(s);})();",
                    )
                    applyTextZoom()
                }
                onUrlChanged?.invoke(currentUrlValue)
            }
        }

        // Register eval bridge WebExtension
        val runtime = GeckoRuntimeSingleton.getRuntime(context)
        runtime.webExtensionController.ensureBuiltIn(
            "resource://android/assets/eval-extension/",
            "eval@rikkahub",
        ).then({ ext ->
            evalExtension = ext
            ext?.let { extension ->
                session.getWebExtensionController().setMessageDelegate(
                    extension,
                    object : WebExtension.MessageDelegate {
                        override fun onConnect(port: WebExtension.Port) {
                            evalPort = port
                            port.setDelegate(object : WebExtension.PortDelegate {
                                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                    val json = message as? JSONObject ?: return
                                    when (json.optString("type")) {
                                        "ready" -> {}
                                        "result" -> {
                                            evalDeferred?.complete(json.optString("result"))
                                        }
                                        "error" -> {
                                            evalDeferred?.complete(null)
                                        }
                                    }
                                }
                                override fun onDisconnect(port: WebExtension.Port) {
                                    if (evalPort == port) evalPort = null
                                }
                            })
                        }
                    },
                    "browser",
                )
            }
            GeckoResult.fromValue(null)
        }, { _ -> GeckoResult.fromValue(null) })
    }

    fun loadUrl(url: String) {
        session.loadUri(url)
    }

    fun goBack() {
        session.goBack()
    }

    fun goForward() {
        session.goForward()
    }

    fun reload() {
        session.reload()
    }

    fun canGoBack(): Boolean = canGoBackValue

    fun canGoForward(): Boolean = canGoForwardValue

    fun setUserAgent(ua: String) {
        session.settings.userAgentOverride = ua
    }

    fun getUserAgent(): String {
        return session.settings.userAgentOverride ?: ""
    }

    fun setTextZoom(percent: Int) {
        textZoomPercent = percent
        applyTextZoom()
    }

    private fun applyTextZoom() {
        kotlinx.coroutines.MainScope().launch {
            evaluateJavascriptAsync(
                "document.documentElement.style.fontSize='${textZoomPercent}%';",
            )
        }
    }

    suspend fun navigate(
        url: String,
        type: String = "url",
    ): String = withTimeoutOrNull(perToolTimeoutMs) {
        when (type) {
            "back" -> {
                if (!canGoBackValue) return@withTimeoutOrNull "no history to go back to"
                loadDeferred = CompletableDeferred()
                session.goBack()
                loadDeferred?.await()
            }

            "forward" -> {
                if (!canGoForwardValue) return@withTimeoutOrNull "no history to go forward to"
                loadDeferred = CompletableDeferred()
                session.goForward()
                loadDeferred?.await()
            }

            "reload" -> {
                loadDeferred = CompletableDeferred()
                session.reload()
                loadDeferred?.await()
            }

            else -> {
                loadDeferred = CompletableDeferred()
                session.loadUri(url.ifBlank { "about:blank" })
                loadDeferred?.await()
            }
        }
        currentUrlValue
    } ?: "timeout navigating"

    suspend fun currentUrl(): String = currentUrlValue

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
                " if(md && md.replace(/\\s/g,'').length < 200 && doc.body) return doc.body.textContent;" +
                " return md;" +
                " } catch(e) { return document.body ? document.body.innerText : ''; } })();"
            val raw = evaluateJavascriptAsync(js)
            raw?.let { unquoteJsString(it) } ?: ""
        }
        return paginateMarkdown(markdown, startIndex, maxChars)
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
            slice + "\n\n[Content truncated. Total lines: $total. Showing lines $startIndex to ${end - 1}. Call browser_get_content again with start_index=$end to continue. Read all chunks before responding.]"
        } else {
            slice
        }
    }

    private suspend fun ensureReadability() {
        if (readabilityInjected) return
        val script = readabilityScript ?: withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("browser/readability.js").bufferedReader().use { it.readText() }
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
                context.assets.open("browser/turndown.js").bufferedReader().use { it.readText() }
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
        doubleClick: Boolean = false,
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
        val js = "(function(){var sel=$sel,out=[],refCount=0,skip=['script','style','noscript','svg','path','head','meta','link','br','wbr','hr','iframe','canvas'];" +
            "function roleOf(el){var r=el.getAttribute('role');if(r==='none'||r==='presentation')return null;if(r)return r;var tag=el.tagName.toLowerCase();" +
            "if(tag==='a')return el.getAttribute('href')?'link':null;" +
            "if(tag==='button')return 'button';" +
            "if(tag==='input'){var ty=(el.getAttribute('type')||'text').toLowerCase();if(ty==='checkbox')return 'checkbox';if(ty==='radio')return 'radio';if(ty==='submit'||ty==='button'||ty==='reset')return 'button';if(ty==='search')return 'searchbox';return 'textbox';}" +
            "if(tag==='textarea')return 'textbox';if(tag==='select')return 'combobox';if(tag==='img')return 'img';" +
            "if(tag==='h1'||tag==='h2'||tag==='h3'||tag==='h4'||tag==='h5'||tag==='h6')return 'heading';" +
            "if(tag==='nav')return 'navigation';if(tag==='main')return 'main';if(tag==='article')return 'article';" +
            "if(tag==='section')return 'region';if(tag==='aside')return 'complementary';if(tag==='header')return 'banner';" +
            "if(tag==='footer')return 'contentinfo';if(tag==='form')return 'form';if(tag==='ul'||tag==='ol')return 'list';" +
            "if(tag==='li')return 'listitem';if(tag==='table')return 'table';return null;}" +
            "function nameOf(el){return (el.getAttribute('aria-label')||el.getAttribute('alt')||el.getAttribute('title')||el.getAttribute('placeholder')||(el.innerText||'').trim()||'').slice(0,120);}" +
            "function directText(el){var s='';for(var i=0;i<el.childNodes.length;i++){var n=el.childNodes[i];if(n.nodeType===3)s+=n.textContent;}return s.trim();}" +
            "function ind(d){return Array(d+1).join('  ');}" +
            "var leafRoles=['link','button','img','heading','listitem'];" +
            "function walk(el,depth){if(out.length>$maxNodes)return;" +
            "if(el.nodeType===3){var t=(el.textContent||'').trim();if(t)out.push(ind(depth)+'text: '+t.slice(0,120));return;}" +
            "if(el.nodeType!==1)return;var tag=el.tagName.toLowerCase();if(skip.indexOf(tag)>=0)return;" +
            "var role=roleOf(el);if(role==='alert')return;" +
            "if(!role){var dt=directText(el);if(dt)out.push(ind(depth)+'text: '+dt.slice(0,120));for(var i=0;i<el.children.length;i++)walk(el.children[i],depth);return;}" +
            "var name=nameOf(el);var line=ind(depth)+role;if(name)line+=' \"'+name+'\"';" +
            "var href=el.getAttribute('href');if(href){try{href=new URL(href,location.href).href;}catch(e){}line+=' [href='+href.slice(0,100)+']';}" +
            "if(role==='heading'){var lvl=el.getAttribute('aria-level');if(!lvl&&tag.length===2&&tag.charAt(0)==='h')lvl=tag.charAt(1);if(lvl)line+=' [level='+lvl+']';}" +
            "if((tag==='input'||tag==='textarea')&&el.value)line+=' [value='+String(el.value).slice(0,80)+']';" +
            "if(['link','button','textbox','combobox','checkbox','radio','searchbox','img','listitem','heading'].indexOf(role)>=0){refCount++;var ref='e'+refCount;el.setAttribute('data-rkref',ref);line+=' [ref='+ref+']';}" +
            "out.push(line);" +
            "if(!name||leafRoles.indexOf(role)<0){for(var i=0;i<el.children.length;i++)walk(el.children[i],depth+1);}}" +
            "var root=sel?document.querySelector(sel):document.body;if(!root)return 'element not found';walk(root,0);return out.join('\\n');})();"
        val raw = evaluateJavascriptAsync(js)
        raw?.let { unquoteJsString(it) } ?: "no snapshot"
    }

    suspend fun executeScript(expression: String): String = withContext(Dispatchers.Main) {
        val raw = evaluateJavascriptAsync(expression)
        raw?.let { unquoteJsString(it) } ?: "null"
    }

    suspend fun logs(type: String): String = withContext(Dispatchers.Main) {
        val src = if (type == "network") networkLogs else consoleLogs
        src.joinToString("\n").take(MAX_LOG_CHARS)
    }

    fun close() {
        consoleLogs.clear()
        networkLogs.clear()
        session.close()
    }

    suspend fun screenshot(
        maxHeightPx: Int,
        context: Context,
        selector: String? = null,
        fullPage: Boolean = false,
    ): String? = withTimeoutOrNull(perToolTimeoutMs) {
        val view = geckoView ?: return@withTimeoutOrNull null
        val bitmap = withContext(Dispatchers.Main) {
            val width = view.width.coerceAtLeast(1)
            val full = awaitGeckoResult(view.capturePixels()) ?: return@withContext null
            if (selector != null) {
                val sel = Json.encodeToString(selector)
                val rectRaw = evaluateJavascriptAsync(
                    "(function(){var e=document.querySelector($sel);if(!e)return null;" +
                        "var r=e.getBoundingClientRect();return JSON.stringify({x:r.x,y:r.y,w:r.width,h:r.height});})();",
                )
                val rect = rectRaw?.let { parseRect(it) }
                if (rect != null) {
                    val cx = rect.left.toInt().coerceIn(0, (width - 1).coerceAtLeast(0))
                    val cy = rect.top.toInt().coerceIn(0, (full.height - 1).coerceAtLeast(0))
                    val cw = rect.width().toInt().coerceIn(1, width - cx)
                    val ch = rect.height().toInt().coerceIn(1, full.height - cy)
                    Bitmap.createBitmap(full, cx, cy, cw, ch).also { full.recycle() }
                } else {
                    full
                }
            } else {
                full
            }
        } ?: return@withTimeoutOrNull null
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

    private suspend fun evaluateJavascriptAsync(script: String): String? {
        val port = evalPort ?: return null
        val deferred = CompletableDeferred<String?>()
        evalDeferred = deferred
        val message = JSONObject()
        message.put("type", "eval")
        message.put("code", script)
        port.postMessage(message)
        return withTimeoutOrNull(perToolTimeoutMs) { deferred.await() }
    }

    private suspend fun <T> awaitGeckoResult(result: GeckoResult<T>): T? {
        val deferred = CompletableDeferred<T?>()
        result.then { value ->
            deferred.complete(value)
            GeckoResult.fromValue(null)
        }
        result.exceptionally {
            deferred.complete(null)
            GeckoResult.fromValue(null)
        }
        return deferred.await()
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
        const val MAX_LOG_CHARS = 64 * 1024
        const val MAX_LOG_LINES = 500
        const val MAX_SCREENSHOT_HEIGHT_PX = 8192
    }
}
