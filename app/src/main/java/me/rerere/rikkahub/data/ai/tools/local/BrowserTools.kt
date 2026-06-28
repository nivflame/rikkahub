package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.browser.HeadlessBrowserSession

private val BROWSER_SYSTEM_PROMPT = """
You have browser tools to navigate and automate the in-app WebView. Use browser_navigate to
open a URL or go back, forward, and reload. Use browser_get_content to read the current page
as markdown (main content with links resolved to absolute URLs), browser_dom_snapshot to
inspect the DOM tree, and browser_interact to click, fill, scroll, hover, or type on elements.
Use browser_execute_script to run JavaScript, browser_logs to read console or network logs,
and browser_screenshot to capture the page. Call browser_close when the browsing task is
complete. Prefer browser_get_content for reading. If browser_get_content returns a truncation
notice, call it again with the start_index shown in that notice until you have read the whole
page before responding.
""".trimIndent().replace("\n", " ")

internal val ALL_BROWSER_TOOL_NAMES: List<String> = listOf(
    "browser_navigate",
    "browser_get_content",
    "browser_screenshot",
    "browser_interact",
    "browser_dom_snapshot",
    "browser_execute_script",
    "browser_logs",
    "browser_close",
)

val DEFAULT_ENABLED_BROWSER_TOOLS: Set<String> = ALL_BROWSER_TOOL_NAMES.toSet()

internal fun buildBrowserTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "browser_navigate",
        description = "Navigate the browser. Opens a URL by default, or goes back, forward, " +
            "or reloads. The page is loaded and ready when this returns. Optionally set a " +
            "viewport (e.g. \"375x667,mobile\") or userAgent.",
        systemPrompt = { _, _ -> BROWSER_SYSTEM_PROMPT },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "The URL to navigate to (required for type \"url\")")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("url"); add("back"); add("forward"); add("reload")
                        })
                        put("description", "Navigation type, default \"url\"")
                    })
                    put("viewport", buildJsonObject {
                        put("type", "string")
                        put("description", "Viewport override, e.g. \"1920x1080\" or \"375x667,mobile\"")
                    })
                    put("userAgent", buildJsonObject {
                        put("type", "string")
                        put("description", "Override the User-Agent header")
                    })
                }
            )
        },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = it.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "url"
            val viewport = it.jsonObject["viewport"]?.jsonPrimitive?.contentOrNull
            val userAgent = it.jsonObject["userAgent"]?.jsonPrimitive?.contentOrNull
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.navigate(url, type, viewport, userAgent)
            }
            listOf(UIMessagePart.Text("navigated to: $result"))
        }
    ),
    Tool(
        name = "browser_get_content",
        description = "Return the current page as markdown (main content with links resolved " +
            "to absolute URLs), paginated. If the result ends with a truncation notice, call " +
            "this tool again with the start_index shown in that notice, and keep reading until " +
            "there is no truncation. Use this to read what is on the page and find where to go next.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("start_index", buildJsonObject {
                        put("type", "number")
                        put("description", "Line number to start reading from. Default 0. Use the start_index from a truncation notice to continue.")
                    })
                }
            )
        },
        execute = {
            val startIndex = it.jsonObject["start_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val content = HeadlessBrowserSession.withController(context) {
                it.getContent(BrowserController.MAX_CONTENT_CHARS, startIndex)
            }
            listOf(UIMessagePart.Text(content))
        }
    ),
    Tool(
        name = "browser_screenshot",
        description = "Capture the current page as a JPEG image and return it. Optionally " +
            "capture a single element by selector or the full scrollable page. Use this only " +
            "when you need to see the visual layout, not for reading text.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the element to capture. Omit for the viewport.")
                    })
                    put("fullPage", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Capture the entire scrollable page (default false)")
                    })
                }
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
            val fullPage = it.jsonObject["fullPage"]?.jsonPrimitive?.contentOrNull == "true"
            val path = HeadlessBrowserSession.withController(context) {
                it.screenshot(BrowserController.MAX_SCREENSHOT_HEIGHT_PX, context, selector, fullPage)
            }
            if (path != null) {
                listOf(UIMessagePart.Image(path))
            } else {
                listOf(UIMessagePart.Text("failed to capture screenshot"))
            }
        }
    ),
    Tool(
        name = "browser_interact",
        description = "Interact with a DOM element on the current page: click, fill, scroll, " +
            "hover, press_key, or type_text.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("click"); add("fill"); add("scroll"); add("hover"); add("press_key"); add("type_text")
                        })
                        put("description", "The interaction action")
                    })
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the target element (required except for press_key)")
                    })
                    put("value", buildJsonObject {
                        put("type", "string")
                        put("description", "For fill: text to type. For scroll: pixels to scroll by.")
                    })
                    put("key", buildJsonObject {
                        put("type", "string")
                        put("description", "For press_key: the key to press (e.g. Enter, Tab, Escape)")
                    })
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "For type_text: text to append to the element")
                    })
                    put("doubleClick", buildJsonObject {
                        put("type", "boolean")
                        put("description", "For click: double-click (default false)")
                    })
                },
                required = listOf("action")
            )
        },
        execute = {
            val action = it.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: ""
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
            val value = it.jsonObject["value"]?.jsonPrimitive?.contentOrNull
            val key = it.jsonObject["key"]?.jsonPrimitive?.contentOrNull
            val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
            val doubleClick = it.jsonObject["doubleClick"]?.jsonPrimitive?.contentOrNull == "true"
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.interact(action, selector, value, key, text, doubleClick)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_dom_snapshot",
        description = "Return a text outline of the DOM tree of the current page (or a scoped " +
            "element), capped to ${BrowserController.MAX_DOM_NODES} nodes. Use this to inspect " +
            "page structure and find elements to interact with.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector to scope the snapshot. Omit for the whole page.")
                    })
                }
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
            val snapshot = HeadlessBrowserSession.withController(context) {
                it.domSnapshot(selector, BrowserController.MAX_DOM_NODES)
            }
            listOf(UIMessagePart.Text(snapshot))
        }
    ),
    Tool(
        name = "browser_execute_script",
        description = "Execute a JavaScript expression in the current page and return the " +
            "result as a string. Use this for custom extraction or actions not covered by " +
            "other browser tools.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("expression", buildJsonObject {
                        put("type", "string")
                        put("description", "JavaScript expression to evaluate")
                    })
                },
                required = listOf("expression")
            )
        },
        execute = {
            val expression = it.jsonObject["expression"]?.jsonPrimitive?.contentOrNull ?: ""
            val result = HeadlessBrowserSession.withController(context) {
                it.executeScript(expression)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_logs",
        description = "Return captured browser logs. Use type \"console\" for console output " +
            "or \"network\" for network request URLs (no response bodies).",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("console"); add("network")
                        })
                        put("description", "Log type to retrieve (default \"console\")")
                    })
                }
            )
        },
        execute = {
            val type = it.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "console"
            val logs = HeadlessBrowserSession.withController(context) {
                it.logs(type)
            }
            listOf(UIMessagePart.Text(logs.ifBlank { "no logs" }))
        }
    ),
    Tool(
        name = "browser_close",
        description = "Signal that the browsing task is complete and clear captured logs. " +
            "Call this when you have finished navigating and reading.",
        execute = {
            HeadlessBrowserSession.withController(context) { it.close() }
            listOf(UIMessagePart.Text("browser task complete"))
        }
    )
)
