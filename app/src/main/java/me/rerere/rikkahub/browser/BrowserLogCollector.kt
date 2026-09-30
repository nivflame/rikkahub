package me.rerere.rikkahub.browser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Ring-buffer collector backing the browser_logs tool: capped buffers (500), most recent first,
 * bodies truncated at 4 KiB and always returned.
 *
 * Two entry origins:
 * - native: recorded from [android.webkit.WebViewClient.shouldInterceptRequest], carries
 *   request-side data only (url, method, request headers, inferred resource type)
 * - js: reported by the injected fetch/XHR hook, additionally carries status, response
 *   headers and bodies; merged into its matching native entry when one exists
 */
internal class BrowserLogCollector {
    private val consoleLog = ArrayDeque<JsonObject>()
    private val networkLog = ArrayDeque<JsonObject>()

    private var nativeSeq = 0
    private var jsSeq = 0

    fun addConsole(level: String, message: String, sourceId: String?, lineNumber: Int) {
        val entry = buildJsonObject {
            put("type", level)
            put("args", buildJsonArray { add(JsonPrimitive(message)) })
            sourceId?.takeIf { it.isNotBlank() }?.let { put("url", it) }
            if (lineNumber > 0) put("lineNumber", lineNumber)
            put("timestamp", System.currentTimeMillis() / 1000.0)
        }
        synchronized(this) {
            ringAdd(consoleLog, entry)
        }
    }

    fun onNativeRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        isMainFrame: Boolean,
    ) {
        val id = synchronized(this) { "nat-${++nativeSeq}" }
        val entry = buildJsonObject {
            put("requestId", id)
            put("url", url)
            put("method", method)
            put("resourceType", inferResourceType(url, isMainFrame))
            if (headers.isNotEmpty()) {
                put("requestHeaders", headersToJsonObject(headers))
            }
        }
        synchronized(this) {
            ringAdd(networkLog, entry)
        }
    }

    /**
     * Enrich or append an entry reported by the injected fetch/XHR hook.
     * Merges into the most recent unresolved native entry with the same url+method,
     * otherwise records it as a standalone js-originated entry.
     */
    fun mergeJsEntry(raw: JsonObject) {
        val url = raw.str("url") ?: return
        if (url.isBlank()) return
        synchronized(this) {
            for (i in networkLog.indices.reversed()) {
                val existing = networkLog[i]
                if (!existing.containsKey("status") &&
                    existing.str("url") == url &&
                    existing.str("method") == raw.str("method")
                ) {
                    networkLog[i] = mergeEntries(existing, raw)
                    return
                }
            }
            val entry = mergeEntries(buildJsonObject { put("requestId", "js-${++jsSeq}") }, raw)
            ringAdd(networkLog, entry)
        }
    }

    fun clear(type: String) {
        synchronized(this) {
            when (type) {
                "console" -> consoleLog.clear()
                "network" -> networkLog.clear()
            }
        }
    }

    fun getLogs(params: JsonObject): String {
        return when (params.str("type")) {
            "console" -> {
                val scanned = synchronized(this) { consoleLog.size }
                var entries = synchronized(this) { consoleLog.toList() }
                params.str("pattern")?.takeIf { it.isNotBlank() }?.let { raw ->
                    entries = entries.filter { matchesPattern(consoleHaystack(it), raw) }
                }
                val page = entries.takeLast(limit(params)).reversed()
                if (params.bool("clear")) clear("console")
                buildJsonObject {
                    put("logs", buildJsonArray { page.forEach { add(it) } })
                    put("total", entries.size)
                    put("scanned", scanned)
                }.toString()
            }

            "network" -> {
                var entries = synchronized(this) { networkLog.toList() }
                params.arr("resource_type")?.let { allowed ->
                    entries = entries.filter { it.str("resourceType") in allowed }
                }
                params.str("url_pattern")?.takeIf { it.isNotBlank() }?.let { pattern ->
                    entries = entries.filter { (it.str("url") ?: "").contains(pattern) }
                }
                val response = buildLogsResponse(entries, params)
                if (params.bool("clear")) clear("network")
                response
            }

            else -> """{"error":"unknown log type, expected \"console\" or \"network\""}"""
        }
    }

    private fun buildLogsResponse(entries: List<JsonObject>, params: JsonObject): String {
        val page = entries.takeLast(limit(params)).reversed()

        val logs = page.map { entry ->
            val out = buildJsonObject {
                put("requestId", entry.str("requestId"))
                put("url", entry.str("url"))
                put("method", entry.str("method"))
                entry["status"]?.let { put("status", it) }
                entry["statusText"]?.let { put("statusText", it) }
                put("resourceType", entry.str("resourceType"))
                entry["mimeType"]?.let { put("mimeType", it) }
                entry.str("url")?.substringAfter('?', "")?.takeIf { q -> q.isNotEmpty() }?.let {
                    put("queryString", it)
                }
                entry["requestHeaders"]?.let { put("requestHeaders", it) }
                entry["responseHeaders"]?.let { put("responseHeaders", it) }
            }
            val mutable = out.toMutableMap()
            entry["requestBody"]?.let { mutable["requestBody"] = it }
            entry["responseBody"]?.let { mutable["responseBody"] = it }
            JsonObject(mutable)
        }
        return buildJsonObject {
            put("logs", buildJsonArray { logs.forEach { add(it) } })
            put("total", entries.size)
        }.toString()
    }

    private fun limit(params: JsonObject): Int =
        params.int("limit")?.coerceIn(1, LOG_RING_CAP) ?: DEFAULT_LIMIT

    private fun consoleHaystack(entry: JsonObject): String = buildList {
        entry.str("type")?.let { add(it) }
        (entry["args"] as? JsonArray)?.mapNotNullTo(this) { it.jsonPrimitive.contentOrNull }
        entry.str("url")?.let { add(it) }
        entry["lineNumber"]?.jsonPrimitive?.contentOrNull?.let { add(it) }
    }.joinToString(" ")

    private fun matchesPattern(haystack: String, raw: String): Boolean {
        val pattern = raw.take(MAX_PATTERN_LEN)
        if (pattern.length >= 2 && pattern.startsWith("/") && pattern.endsWith("/")) {
            val body = pattern.substring(1, pattern.length - 1)
            runCatching { Regex(body, RegexOption.IGNORE_CASE).containsMatchIn(haystack) }
                .getOrNull()?.let { return it }
        }
        return haystack.contains(pattern, ignoreCase = true)
    }

    private fun mergeEntries(base: JsonObject, extra: JsonObject): JsonObject {
        val merged = base.toMutableMap()
        for ((key, value) in extra) {
            when (key) {
                "requestBody", "responseBody" -> {
                    value.jsonPrimitive.contentOrNull?.take(MAX_BODY_SIZE)?.let {
                        merged[key] = JsonPrimitive(it)
                    }
                }

                else -> merged.putIfAbsent(key, value)
            }
        }
        if (!merged.containsKey("mimeType")) {
            val responseHeaders = extra["responseHeaders"] as? JsonObject
            val contentType = responseHeaders?.str("content-type")
                ?: responseHeaders?.str("Content-Type")
            contentType?.let {
                merged["mimeType"] = JsonPrimitive(it.substringBefore(';').trim())
            }
        }
        return JsonObject(merged)
    }

    private fun inferResourceType(url: String, isMainFrame: Boolean): String {
        if (isMainFrame) return "Document"
        val path = url.substringBefore('?').substringAfterLast('/')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png", "jpg", "jpeg", "gif", "webp", "svg", "avif", "bmp" -> "Image"
            "js", "mjs" -> "Script"
            "css" -> "Stylesheet"
            "woff", "woff2", "ttf", "otf", "eot" -> "Font"
            "mp4", "webm", "m4v", "mp3", "wav", "ogg" -> "Media"
            else -> "Other"
        }
    }

    private fun headersToJsonObject(headers: Map<String, String>): JsonObject =
        buildJsonObject {
            headers.forEach { (key, value) ->
                put(key.take(MAX_HEADER_KEY_LEN), value.take(MAX_VALUE_SIZE))
            }
        }

    private fun ringAdd(buffer: ArrayDeque<JsonObject>, entry: JsonObject) {
        while (buffer.size >= LOG_RING_CAP) buffer.removeFirst()
        buffer.addLast(entry)
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.bool(key: String): Boolean {
        val raw = this[key]?.jsonPrimitive?.contentOrNull
        return raw == "true" || raw == "1"
    }

    private fun JsonObject.arr(key: String): Set<String>? {
        val element = this[key] as? JsonArray ?: return null
        return element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    }

    companion object {
        private const val LOG_RING_CAP = 500
        private const val MAX_BODY_SIZE = 4096
        private const val MAX_HEADER_KEY_LEN = 128
        private const val MAX_VALUE_SIZE = 8192
        private const val MAX_PATTERN_LEN = 300
        private const val DEFAULT_LIMIT = 100
    }
}
