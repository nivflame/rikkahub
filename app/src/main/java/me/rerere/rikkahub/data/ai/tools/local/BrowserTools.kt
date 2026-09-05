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

internal val ALL_BROWSER_TOOL_NAMES: List<String> = listOf(
    "browser_navigate",
    "browser_get_content",
    "browser_screenshot",
    "browser_interact",
    "browser_dom_snapshot",
    "browser_execute_script",
    "browser_waitfor",
    "browser_logs",
)

val DEFAULT_ENABLED_BROWSER_TOOLS: Set<String> = ALL_BROWSER_TOOL_NAMES.toSet()

internal fun buildBrowserTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "browser_navigate",
        description = "Navigate the in-app browser to a URL, or go back, forward, or reload.\n\nUsage notes:\n- The page is fully loaded and ready when this tool returns\n- url may also be a workspace-relative file path (e.g. \"index.html\") to open an HTML file from the workspace\n- Set type to \"back\", \"forward\", or \"reload\" to navigate history instead of opening a URL",
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
        description = "Read the current page as markdown (main article content with links resolved to absolute URLs), paginated.\n\nUsage notes:\n- Use this to read content from a page you have already navigated to and possibly interacted with\n- For reading a new URL, use WebFetch instead (it combines navigation and content extraction in one call)\n- For search results or structured list pages, use browser_dom_snapshot instead\n- If the result ends with a truncation notice, call this tool again with the start_index from that notice until the whole page is read",
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
        description = "Capture the current page as a JPEG image.\n\nUsage notes:\n- Use this to see the visual layout, not for reading text (use browser_get_content for text)\n- Omit selector to capture the viewport, or set fullPage to capture the entire scrollable page",
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
                listOf(UIMessagePart.Image("file://$path"))
            } else {
                listOf(UIMessagePart.Text("failed to capture screenshot"))
            }
        }
    ),
    Tool(
        name = "browser_interact",
        description = "Interact with a DOM element on the current page.\n\nUsage notes:\n- Actions: click, fill, scroll, hover, press_key, type_text\n- A selector is required for all actions except press_key. Use a [data-rkref=\"eN\"] selector from browser_dom_snapshot for reliable targeting\n- Use value for fill (text to type) and scroll (pixels to scroll by)",
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
        description = "Return an accessibility tree of the current page: semantic roles, names, links, and interactive elements, capped to ${BrowserController.MAX_DOM_NODES} nodes.\n\nUsage notes:\n- Use this to inspect page structure and find elements to interact with\n- Interactive elements are tagged with a ref, e.g. [ref=e1]. Pass that as the selector [data-rkref=\"e1\"] to browser_interact\n- For reading article text, prefer browser_get_content. For search results or structured list pages, prefer this tool\n- Scope the snapshot to a subtree by providing a selector",
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
        description = "Execute a JavaScript expression in the current page and return the result.\n\nUsage notes:\n- Requires an active page. Call browser_navigate first\n- Use for extracting data, triggering events, inspecting page state or actions not covered by the other browser tools\n- This tool is read-only and does not modify any files",
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
        name = "browser_waitfor",
        description = "Wait for an element to appear on the current page. Supports both CSS selectors and text search.\n\n- If the selector contains CSS metacharacters (#.>[:*), it is treated as a CSS selector\n- Otherwise it is treated as a text search (e.g., \"Login\" finds a button with that text)\n- Returns whether the element was found within the timeout\n- Requires an active page (call browser_navigate or WebFetch first)\n- Useful for waiting for Cloudflare challenges to complete, dynamic content to load, or popups to appear",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector or text to search for")
                    })
                    put("timeout", buildJsonObject {
                        put("type", "number")
                        put("description", "Maximum wait time in milliseconds (default 10000)")
                    })
                },
                required = listOf("selector")
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: ""
            val timeout = it.jsonObject["timeout"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 10000L
            val result = HeadlessBrowserSession.withController(context) {
                it.waitFor(selector, timeout)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_logs",
        description = """Retrieve captured browser logs from the current browser session, similar to Chrome DevTools console/network panels.

Console logs (type="console"):
- Each entry: type (log/warning/error/info/debug), args, url, lineNumber, timestamp
- Filter with "types" (e.g., ["error"] for errors only)

Network logs (type="network"):
- One entry per request the page makes (documents, scripts, images, XHR/fetch API calls)
- url, method, status, statusText, resourceType, mimeType, queryString
- requestHeaders on every entry; responseHeaders on fetch/XHR entries; use includeRequestBody/includeResponseBody for bodies (truncated at 4KB)
- resourceType is inferred from context (Document, Script, Stylesheet, Image, Font, Media, Fetch, XHR)
- Use "resourceTypes" to filter (e.g., ["XHR","Fetch"] for API calls only)
- Use "urlPattern" to find specific endpoints (e.g., "/api/")
- Use "requestId" to get full detail of a single request

Pagination: pageIdx + pageSize (default 50), most recent first. Logs persist across navigations for the whole session.""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("type", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("console"); add("network")
                        })
                        put("description", "Log type to retrieve")
                    })
                    put("pageIdx", buildJsonObject {
                        put("type", "number")
                        put("description", "Pagination offset (0 = most recent page, default 0)")
                    })
                    put("pageSize", buildJsonObject {
                        put("type", "number")
                        put("description", "Entries per page (default 50)")
                    })
                    put("resourceTypes", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "Network only: filter by resource type (e.g., [\"XHR\",\"Fetch\",\"Document\",\"Image\"])")
                    })
                    put("urlPattern", buildJsonObject {
                        put("type", "string")
                        put("description", "Network only: substring filter on URL (e.g., \"/api/\")")
                    })
                    put("requestId", buildJsonObject {
                        put("type", "string")
                        put("description", "Network only: return full detail of a single request by its requestId")
                    })
                    put("includeRequestBody", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Network only: include request body when captured (default false)")
                    })
                    put("includeResponseBody", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Network only: include response body when captured, truncated at 4KB (default false)")
                    })
                    put("types", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "Console only: filter by log level (e.g., [\"error\"])")
                    })
                },
                required = listOf("type")
            )
        },
        execute = {
            val args = it.jsonObject
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.getLogs(args)
            }
            listOf(UIMessagePart.Text(result))
        }
    )
)
