package me.rerere.rikkahub.data.ai.tools.local

import com.whl.quickjs.wrapper.JSCallFunction
import android.os.SystemClock
import com.whl.quickjs.wrapper.QuickJSContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext

private const val CODEMODE_IDLE_TIMEOUT_MS = 60_000L
private const val CODEMODE_HARD_TIMEOUT_MS = 10 * 60_000L
private const val CODEMODE_MEMORY_LIMIT_BYTES = 128 * 1024 * 1024
private const val CODEMODE_MAX_TOOL_CALLS = 50
private const val CODEMODE_MAX_IMAGES = 6

private val CODEMODE_EXCLUDE_TOOLS = setOf(
    "Subagent",
    "Codemode",
    "AskQuestion",
)

internal fun buildCodemodeTool(
    callableTools: List<Tool>,
    progressStore: SubagentProgressStore,
): Tool = Tool(
    name = "Codemode",
    description = """Run JavaScript in an isolated sandbox to call tools and process their results with code

The only callable tools inside are those listed in the Code Mode catalog (system message), plus any deferred tool found via searchTools(). Tools declared outside the catalog are not callable inside. Do not guess tool names or parameters: call describeTool(name) before the first call to an unfamiliar tool.

Globals:
- tools.call(name, args): call a tool, returns its result as a parsed value. Synchronous.
- searchTools(query): rank tools by relevance, returns [{name, description}]. Synchronous.
- describeTool(name): full description and parameters of a tool. Synchronous.

Prefer an explicit return; its JSON-serialized value becomes the result. Top-level await is supported; every await resolves synchronously through the host, so avoid promises that never settle. console.log output is appended after the return value. No timers, network, imports, or filesystem; the only capabilities are tool calls and console.log.
""".trimIndent(),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("code", buildJsonObject {
                    put("description", "JavaScript source, the body of a function. Use return to produce the result.")
                    put("type", "string")
                })
            },
            required = listOf("code"),
        )
    },
    systemPrompt = { _, _ ->
        if (callableTools.isEmpty()) {
            "No Code Mode tools are currently available."
        } else {
            buildString {
                appendLine("# Code Mode catalog")
                appendLine()
                appendLine("Tools callable from Codemode scripts via tools.call(name, args). Use describeTool(name) for parameters:")
                callableTools.forEach { tool ->
                    append("- ")
                    appendLine(tool.name)
                }
            }
        }
    },
    execute = { input ->
        val code = input.jsonObject["code"]?.jsonPrimitive?.contentOrNull ?: error("code is required")
        val toolCallId = coroutineContext.toolCallId ?: ""
        progressStore.start(toolCallId)
        try {
            val runner = CodemodeRunner(
                callableTools = callableTools.filter { it.name !in CODEMODE_EXCLUDE_TOOLS },
                toolCallId = toolCallId,
            )
            val text = runner.run(code)
            val citations = runner.citations
            val textPart = if (citations.isEmpty()) {
                UIMessagePart.Text(text)
            } else {
                UIMessagePart.Text(
                    text = text,
                    metadata = buildJsonObject {
                        put("citations", buildJsonObject {
                            citations.forEach { (id, url) -> put(id, url) }
                        })
                    },
                )
            }
            listOf(textPart) + runner.images
        } finally {
            progressStore.markFinished(toolCallId)
        }
    },
)

internal class CodemodeRunner(
    private val callableTools: List<Tool>,
    private val toolCallId: String,
) {
    private val consoleLines = mutableListOf<String>()
    private val lastActivityMs = AtomicLong(SystemClock.elapsedRealtime())
    private val activeToolCalls = AtomicInteger(0)
    val citations: Map<String, String> get() = collectedCitations
    val images: List<UIMessagePart.Image> get() = collectedImages
    private val collectedCitations = linkedMapOf<String, String>()
    private val collectedImages = mutableListOf<UIMessagePart.Image>()
    private var toolCallCount = 0

    suspend fun run(code: String): String {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<String?>(null)
        val errorRef = AtomicReference<String?>(null)

        thread(isDaemon = true, name = "codemode-$toolCallId") {
            var context: QuickJSContext? = null
            try {
                context = QuickJSContext.create()
                context.setMemoryLimit(CODEMODE_MEMORY_LIMIT_BYTES)
                injectHostFunctions(context)
                context.evaluate(PRELUDE, "codemode-prelude.js")
                context.evaluate(WRAPPER_PREFIX + code + WRAPPER_SUFFIX, "codemode.js")
                val settled = context.evaluate(SETTLED_PROBE, "codemode-settled.js") as? Boolean ?: false
                if (!settled) {
                    errorRef.set("Script promise never settled (un awaited pending promise or missing return).")
                } else {
                    val errored = context.evaluate("globalThis.__cmError !== undefined", "codemode-errored.js") as? Boolean ?: false
                    if (errored) {
                        val message = context.evaluate(ERROR_PROBE, "codemode-error.js") as? String
                        errorRef.set(message ?: "Unknown script error")
                    } else {
                        val raw = context.evaluate(RESULT_PROBE, "codemode-result.js") as? String ?: "undefined"
                        resultRef.set(raw)
                    }
                }
            } catch (e: Throwable) {
                errorRef.set(e.message ?: e.toString())
            } finally {
                runCatching { context?.destroy() }
                latch.countDown()
            }
        }

        val startMs = SystemClock.elapsedRealtime()
        while (!latch.await(1, TimeUnit.SECONDS)) {
            currentCoroutineContext().ensureActive()
            if (activeToolCalls.get() == 0 && SystemClock.elapsedRealtime() - lastActivityMs.get() > CODEMODE_IDLE_TIMEOUT_MS) {
                return "Code Mode script made no progress for ${CODEMODE_IDLE_TIMEOUT_MS / 1000}s and was abandoned."
            }
            if (SystemClock.elapsedRealtime() - startMs > CODEMODE_HARD_TIMEOUT_MS) {
                return "Code Mode script exceeded ${CODEMODE_HARD_TIMEOUT_MS / 60_000} minutes and was abandoned."
            }
        }

        return buildString {
            if (errorRef.get() != null) {
                appendLine("Code Mode script failed.")
                appendLine(errorRef.get())
            } else {
                appendLine(resultRef.get() ?: "undefined")
            }
            if (consoleLines.isNotEmpty()) {
                appendLine()
                appendLine(CONSOLE_TAG_OPEN)
                consoleLines.take(100).forEach { appendLine(it) }
                appendLine(CONSOLE_TAG_CLOSE)
            }
        }
    }

    private fun callToolInternal(args: Array<Any?>): String {
        val name = args.getOrNull(0) as? String ?: error("tool name is the first argument")
        val argsJson = args.getOrNull(1) as? String ?: "{}"
        val tool = callableTools.firstOrNull { it.name == name }
            ?: error("Unknown tool: $name. Use searchTools(query) to find tools.")
        val parsed = runCatching { Json.decodeFromString(JsonElement.serializer(), argsJson) }
            .getOrElse { JsonObject(emptyMap()) }
        val result = runBlocking { tool.execute(parsed) }
        result.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> runCatching { Json.parseToJsonElement(part.text) }.getOrNull()?.let { collectCitations(it) }
                is UIMessagePart.Image -> if (collectedImages.size < CODEMODE_MAX_IMAGES) collectedImages.add(part)
                else -> Unit
            }
        }
        return resultToJsonString(result)
    }

    private fun collectCitations(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                val id = element["id"]?.jsonPrimitiveOrNull?.contentOrNull
                val url = element["url"]?.jsonPrimitiveOrNull?.contentOrNull
                if (id != null && url != null && id.isNotBlank() && url.startsWith("http")) {
                    collectedCitations[id] = url
                }
                element.values.forEach { collectCitations(it) }
            }
            is JsonArray -> element.forEach { collectCitations(it) }
            else -> Unit
        }
    }

    private fun injectHostFunctions(context: QuickJSContext) {
        val global = context.getGlobalObject()
        global.setProperty("__callTool", JSCallFunction { args ->
            check(++toolCallCount <= CODEMODE_MAX_TOOL_CALLS) { "Tool call limit reached ($CODEMODE_MAX_TOOL_CALLS)" }
            lastActivityMs.set(SystemClock.elapsedRealtime())
            activeToolCalls.incrementAndGet()
            try {
                callToolInternal(args)
            } finally {
                activeToolCalls.decrementAndGet()
                lastActivityMs.set(SystemClock.elapsedRealtime())
            }
        })
        global.setProperty("__searchTools", JSCallFunction { args ->
            val query = args.getOrNull(0) as? String ?: ""
            val limit = (args.getOrNull(1) as? Int) ?: 5
            buildJsonArray {
                rankTools(callableTools, query, limit).forEach { tool ->
                    add(buildJsonObject {
                        put("name", tool.name)
                        put("description", tool.description.lineSequence().firstOrNull() ?: "")
                    })
                }
            }.toString()
        })
        global.setProperty("__describeTool", JSCallFunction { args ->
            val name = args.getOrNull(0) as? String
            val tool = name?.let { callableTools.firstOrNull { t -> t.name == it } }
            if (tool == null) {
                "null"
            } else {
                buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    tool.parameters()?.let { schema ->
                        if (schema is InputSchema.Obj) {
                            put("parameters", buildJsonObject {
                                put("type", "object")
                                put("properties", schema.properties)
                                schema.required?.let { req ->
                                    put("required", buildJsonArray { req.forEach { r -> add(JsonPrimitive(r)) } })
                                }
                            })
                        }
                    }
                }.toString()
            }
        })
        global.setProperty("__console", JSCallFunction { args ->
            consoleLines += args.joinToString(" ") { it?.toString() ?: "null" }
            null
        })
    }

    private fun resultToJsonString(result: List<UIMessagePart>): String = buildJsonArray {
        result.forEach { part ->
            when (part) {
                is UIMessagePart.Text -> add(buildJsonObject {
                    put("type", "text")
                    put("text", part.text)
                })
                is UIMessagePart.Image -> add(buildJsonObject {
                    put("type", "image")
                })
                else -> add(buildJsonObject {
                    put("type", "other")
                })
            }
        }
    }.toString()

    companion object {
        private const val CONSOLE_TAG_OPEN = "<" + "console_output" + ">"
        private const val CONSOLE_TAG_CLOSE = "</" + "console_output" + ">"

        private val PRELUDE = """
globalThis.__unwrap = function(raw) {
    var parts = JSON.parse(raw);
    if (Array.isArray(parts) && parts.length === 1 && parts[0] && parts[0].type === "text") {
        var text = parts[0].text;
        try {
            return JSON.parse(text);
        } catch (e) {
            return text;
        }
    }
    return parts;
};
globalThis.tools = {
    call: function(name, args) {
        if (name === "describeTool") return describeTool(args && args.name);
        if (name === "searchTools") return searchTools(args && args.query, args && args.limit);
        return __unwrap(__callTool(name, JSON.stringify(args === undefined ? {} : args)));
    }
};
globalThis.searchTools = function(query, limit) {
    return JSON.parse(__searchTools(query === undefined ? "" : query, limit === undefined ? 5 : limit));
};
globalThis.describeTool = function(name) {
    return JSON.parse(__describeTool(name));
};
globalThis.console = {
    log: function() { __console(Array.prototype.join.call(arguments, " ")); },
    info: function() { __console(Array.prototype.join.call(arguments, " ")); },
    warn: function() { __console(Array.prototype.join.call(arguments, " ")); },
    error: function() { __console(Array.prototype.join.call(arguments, " ")); },
};
""".trimIndent()

        private const val WRAPPER_PREFIX = "globalThis.__cmPromise = (async function() {\n"
        private const val WRAPPER_SUFFIX = "\n})();\nglobalThis.__cmPromise.then((v) => { globalThis.__cmSettled = true; globalThis.__cmResult = v; }, (e) => { globalThis.__cmSettled = true; globalThis.__cmError = e; });"
        private const val RESULT_PROBE = "JSON.stringify(globalThis.__cmSettled ? (globalThis.__cmError !== undefined ? globalThis.__cmError : globalThis.__cmResult) : undefined)"
        private const val ERROR_PROBE = "globalThis.__cmSettled && globalThis.__cmError !== undefined ? (globalThis.__cmError && globalThis.__cmError.message ? globalThis.__cmError.message : String(globalThis.__cmError)) : null"
        private const val SETTLED_PROBE = "globalThis.__cmSettled === true"
    }
}
