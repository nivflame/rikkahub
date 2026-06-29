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
and browser_screenshot to capture the page. Prefer browser_get_content for reading. If
browser_get_content returns a truncation notice, call it again with the start_index shown in
that notice until you have read the whole page before responding.
""".trimIndent().replace("\n", " ")

internal val ALL_BROWSER_TOOL_NAMES: List<String> = listOf(
    "browser_navigate",
    "browser_get_content",
    "browser_screenshot",
    "browser_interact",
    "browser_dom_snapshot",
    "browser_execute_script",
    "browser_logs",
)

val DEFAULT_ENABLED_BROWSER_TOOLS: Set<String> = ALL_BROWSER_TOOL_NAMES.toSet()

internal fun buildBrowserTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "browser_navigate",
        description = """
            Navigate the in-app browser to a URL, or go back, forward, or reload.

            Usage notes:
            - The page is fully loaded and ready when this tool returns
            - Set type to "back", "forward", or "reload" to navigate history instead of opening a URL
        """.trimIndent(),
        systemPrompt = { _, _ -> BROWSER_SYSTEM_PROMPT },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "The URL to navigate to. Required when type is \"url\"")
                    })
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("url"); add("back"); add("forward"); add("reload")
                        })
                        put("description", "Navigation type. Defaults to \"url\"")
                    })
                }
            )
        },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val type = it.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "url"
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.navigate(url, type)
            }
            listOf(UIMessagePart.Text("navigated to: $result"))
        }
    ),
    Tool(
        name = "browser_get_content",
        description = """
            Read the current page as markdown (main content with links resolved to absolute URLs), paginated.

            Usage notes:
            - This is the primary tool for reading page content
            - If the result ends with a truncation notice, call this tool again with the start_index from that notice until the whole page is read
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("start_index", buildJsonObject {
                        put("type", "number")
                        put("description", "Line number to start reading from. Defaults to 0. Use the start_index from a truncation notice to continue reading")
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
        description = """
            Capture the current page as a JPEG image.

            Usage notes:
            - Use this to see the visual layout, not for reading text (use browser_get_content for text)
            - Omit selector to capture the viewport, or set fullPage to capture the entire scrollable page
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the element to capture. Omit to capture the viewport")
                    })
                    put("fullPage", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Capture the entire scrollable page. Defaults to false")
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
        description = """
            Interact with a DOM element on the current page.

            Usage notes:
            - Actions: click, fill, scroll, hover, press_key, type_text
            - A selector is required for all actions except press_key
            - Use value for fill (text to type) and scroll (pixels to scroll by)
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("click"); add("fill"); add("scroll"); add("hover"); add("press_key"); add("type_text")
                        })
                        put("description", "The interaction action to perform")
                    })
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the target element. Required for all actions except press_key")
                    })
                    put("value", buildJsonObject {
                        put("type", "string")
                        put("description", "For fill: the text to type. For scroll: the number of pixels to scroll by")
                    })
                    put("key", buildJsonObject {
                        put("type", "string")
                        put("description", "For press_key: the key to press (e.g. Enter, Tab, Escape)")
                    })
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "For type_text: the text to append to the element")
                    })
                    put("doubleClick", buildJsonObject {
                        put("type", "boolean")
                        put("description", "For click: double-click the element. Defaults to false")
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
        description = """
            Return a text outline of the DOM tree of the current page, capped to ${BrowserController.MAX_DOM_NODES} nodes.

            Usage notes:
            - Use this to inspect page structure and find elements to interact with
            - Scope the snapshot to a subtree by providing a selector
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector to scope the snapshot. Omit for the whole page")
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
        description = """
            Execute a JavaScript expression in the current page and return the result as a string.

            Usage notes:
            - Use this for custom extraction or actions not covered by the other browser tools
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("expression", buildJsonObject {
                        put("type", "string")
                        put("description", "The JavaScript expression to evaluate")
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
        description = """
            Return captured browser logs.

            Usage notes:
            - Use type "console" for console output, or "network" for network request URLs (no response bodies)
        """.trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("console"); add("network")
                        })
                        put("description", "The log type to retrieve. Defaults to \"console\"")
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
    )
)
