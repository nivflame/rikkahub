package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.graphics.RectF
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.browser.BrowserController
import me.rerere.rikkahub.browser.HeadlessBrowserSession

internal val ALL_BROWSER_TOOL_NAMES: List<String> = listOf(
    "browser_navigate",
    "browser_resize_window",
    "browser_screenshot",
    "browser_dom_snapshot",
    "browser_execute_script",
    "browser_click",
    "browser_fill",
    "browser_type_text",
    "browser_press_key",
    "browser_scroll",
    "browser_upload_file",
    "browser_waitfor",
    "browser_logs",
)

val DEFAULT_ENABLED_BROWSER_TOOLS: Set<String> = ALL_BROWSER_TOOL_NAMES.toSet()

internal fun buildBrowserTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "browser_navigate",
        description = """Navigate the browser to a URL or go back/forward/reload in the browser history

        Usage notes:
        - You can open the workspace HTML file by providing a relative path in the URL""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "The URL to navigate to")
                    })
                    put("action", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("back"); add("forward"); add("reload")
                        })
                        put("description", "History action to perform")
                    })
                }
            )
        },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val action = it.jsonObject["action"]?.jsonPrimitive?.contentOrNull ?: "url"
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.navigate(url, action)
            }
            listOf(UIMessagePart.Text("navigated to: $result"))
        }
    ),
    Tool(
        name = "browser_resize_window",
        description = "Resize the current browser window to specified dimensions. Useful for testing responsive designs or setting up specific screen sizes",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("width", buildJsonObject {
                        put("type", "number")
                        put("description", "Target window width in pixels")
                    })
                    put("height", buildJsonObject {
                        put("type", "number")
                        put("description", "Target window height in pixels")
                    })
                },
                required = listOf("width", "height")
            )
        },
        execute = {
            val width = it.jsonObject["width"]?.jsonPrimitive?.intOrNull
            val height = it.jsonObject["height"]?.jsonPrimitive?.intOrNull
            if (width == null || height == null) {
                listOf(UIMessagePart.Text("invalid dimensions"))
            } else {
                val result = HeadlessBrowserSession.withController(context) { controller ->
                    controller.resizeWindow(width, height)
                }
                listOf(UIMessagePart.Text("resized to: $result"))
            }
        }
    ),
    Tool(
        name = "browser_screenshot",
        description = """Capture the current page as a screenshot

        Usage notes:
        - Use this tool to see the visual layout of the current page
        - For reading text use browser_dom_snapshot instead""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the element to capture")
                    })
                    put("full_page", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Capture the entire scrollable page (defaults: false)")
                    })
                    put("x", buildJsonObject {
                        put("type", "number")
                        put("description", "Left edge in CSS pixels. Requires y, w and h; cannot be combined with selector")
                    })
                    put("y", buildJsonObject {
                        put("type", "number")
                        put("description", "Top edge in CSS pixels. Requires x, w and h; cannot be combined with selector")
                    })
                    put("w", buildJsonObject {
                        put("type", "number")
                        put("description", "Region width in CSS pixels. Requires x, y and h; cannot be combined with selector")
                    })
                    put("h", buildJsonObject {
                        put("type", "number")
                        put("description", "Region height in CSS pixels. Requires x, y and w; cannot be combined with selector")
                    })
                }
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
            val fullPage = it.jsonObject["full_page"]?.jsonPrimitive?.contentOrNull == "true"
            val x = it.jsonObject["x"]?.jsonPrimitive?.floatOrNull
            val y = it.jsonObject["y"]?.jsonPrimitive?.floatOrNull
            val w = it.jsonObject["w"]?.jsonPrimitive?.floatOrNull
            val h = it.jsonObject["h"]?.jsonPrimitive?.floatOrNull
            val hasRegion = listOf(x, y, w, h).any { it != null }
            if (hasRegion && listOf(x, y, w, h).any { it == null }) {
                listOf(UIMessagePart.Text("x, y, w and h are all required together"))
            } else if (hasRegion && selector != null) {
                listOf(UIMessagePart.Text("pass region or selector, not both"))
            } else if (hasRegion && (w!! <= 0 || h!! <= 0)) {
                listOf(UIMessagePart.Text("w and h must be positive"))
            } else {
                val region = if (hasRegion) RectF(x!!, y!!, x + w!!, y + h!!) else null
                val path = HeadlessBrowserSession.withController(context) {
                    it.screenshot(BrowserController.MAX_SCREENSHOT_HEIGHT_PX, context, selector, fullPage, region)
                }
                if (path != null) {
                    listOf(UIMessagePart.Image("file://$path"))
                } else {
                    listOf(UIMessagePart.Text("failed to capture screenshot"))
                }
            }
        }
    ),
    Tool(
        name = "browser_dom_snapshot",
        description = """Get an accessibility tree representation of elements on the page. Output is capped at ${BrowserController.MAX_SNAPSHOT_CHARS} characters with a truncation notice when cut

        Usage notes:
        - Use this to inspect page structure and find elements to interact with
        - Interactive elements are tagged with a ref, e.g. [ref=e1]. Pass that as the selector [data-rkref="e1"] to browser_click, browser_fill or browser_upload_file
        - Scope the snapshot to a subtree by providing a selector""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector to scope the snapshot")
                    })
                    put("depth", buildJsonObject {
                        put("type", "number")
                        put("description", "Maximum depth of tree to traverse. Use smaller depth if output is too large (default: 15)")
                    })
                    put("filter", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("all"); add("interactive")
                        })
                        put("description", "Filter elements: \"interactive\" for buttons/links/inputs only, or \"all\" for all elements (default: all)")
                    })
                }
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
            val depth = it.jsonObject["depth"]?.jsonPrimitive?.intOrNull ?: 15
            val filter = it.jsonObject["filter"]?.jsonPrimitive?.contentOrNull ?: "all"
            val snapshot = HeadlessBrowserSession.withController(context) {
                it.domSnapshot(selector, depth, filter)
            }
            listOf(UIMessagePart.Text(snapshot))
        }
    ),
    Tool(
        name = "browser_execute_script",
        description = """Execute JavaScript code in the current page. The code runs in the page context and can interact with the DOM, window object, and page variables""",
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
        name = "browser_click",
        description = """Click a DOM element on the current page

        Usage notes:
        - Always use a [data-rkref="eN"] selector from browser_dom_snapshot for reliable targeting
        - If the element is not in the DOM snapshot (e.g. inside an iframe), pass x/y CSS viewport coordinates instead of a selector""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the element to click. Omit when using x/y")
                    })
                    put("x", buildJsonObject {
                        put("type", "number")
                        put("description", "X coordinate in CSS pixels from the viewport left edge. Requires y")
                    })
                    put("y", buildJsonObject {
                        put("type", "number")
                        put("description", "Y coordinate in CSS pixels from the viewport top edge. Requires x")
                    })
                    put("clicks", buildJsonObject {
                        put("type", "number")
                        put("description", "Number of clicks, e.g. 2 for double click (default: 1)")
                    })
                    put("button", buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray {
                            add("left"); add("middle"); add("right")
                        })
                        put("description", "Mouse button to click (default: left)")
                    })
                },
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull
            val x = it.jsonObject["x"]?.jsonPrimitive?.intOrNull
            val y = it.jsonObject["y"]?.jsonPrimitive?.intOrNull
            val button = it.jsonObject["button"]?.jsonPrimitive?.contentOrNull ?: "left"
            val clicks = it.jsonObject["clicks"]?.jsonPrimitive?.intOrNull ?: 1
            if (selector.isNullOrBlank() && (x == null || y == null)) {
                listOf(UIMessagePart.Text("selector or x/y required"))
            } else {
                val result = HeadlessBrowserSession.withController(context) { controller ->
                    controller.click(selector, button, x, y, clicks)
                }
                listOf(UIMessagePart.Text(result))
            }
        }
    ),
    Tool(
        name = "browser_fill",
        description = """Enter text into an input element, replacing its current content

        Usage notes:
        - Always use a [data-rkref="eN"] selector from browser_dom_snapshot for reliable targeting""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the input element")
                    })
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to enter, replacing the current content")
                    })
                },
                required = listOf("selector", "text")
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: ""
            val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.fill(selector, text)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_type_text",
        description = """Type text into the currently focused element, appending to its content

        Usage notes:
        - Click the input first with browser_click to focus it""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "The text to append to the current content")
                    })
                },
                required = listOf("text")
            )
        },
        execute = {
            val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: ""
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.typeText(text)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_press_key",
        description = "Press a keyboard key on the current page",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("key", buildJsonObject {
                        put("type", "string")
                        put("description", "The keyboard key to press")
                    })
                    put("modifiers", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("enum", buildJsonArray {
                            add("ctrl"); add("alt"); add("shift"); add("meta")
                        })
                        put("description", "Modifier keys held while pressing (e.g. ctrl, shift, etc)")
                    })
                },
                required = listOf("key")
            )
        },
        execute = {
            val key = it.jsonObject["key"]?.jsonPrimitive?.contentOrNull ?: ""
            val modifiers = it.jsonObject["modifiers"]?.jsonArray
                ?.mapNotNull { item -> item.jsonPrimitive.contentOrNull?.lowercase() }
                ?.toSet() ?: emptySet()
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.pressKey(key, modifiers)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_upload_file",
        description = """Upload a file to a file input element on the current page

        Usage notes:
        - file_path is a workspace-relative path (e.g. "docs/report.pdf") or an URL
        - Always use a [data-rkref="eN"] selector from browser_dom_snapshot for reliable targeting""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector of the file input element")
                    })
                    put("file_path", buildJsonObject {
                        put("type", "string")
                        put("description", "Workspace-relative file path or URL of the file to upload")
                    })
                },
                required = listOf("selector", "file_path")
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: ""
            val filePath = it.jsonObject["file_path"]?.jsonPrimitive?.contentOrNull ?: ""
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.uploadFile(selector, filePath)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_scroll",
        description = "Scroll the page vertically by a number of pixels",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("dy", buildJsonObject {
                        put("type", "number")
                        put("description", "The number of pixels to scroll by; positive scrolls down, negative scrolls up (e.g. 800)")
                    })
                },
                required = listOf("dy")
            )
        },
        execute = {
            val dy = it.jsonObject["dy"]?.jsonPrimitive?.intOrNull ?: 0
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.scroll(dy)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_waitfor",
        description = """Wait for a CSS selector to appear on the current page

        Usage notes:
        - For text, find the element with browser_dom_snapshot first, then wait on its selector
        - Returns whether the element was found within the timeout""",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("selector", buildJsonObject {
                        put("type", "string")
                        put("description", "CSS selector to wait for. Omit to wait for the page to go quiet instead")
                    })
                    put("timeout", buildJsonObject {
                        put("type", "number")
                        put("description", "Maximum wait time in milliseconds (default: 10000)")
                    })
                    put("idle_ms", buildJsonObject {
                        put("type", "number")
                        put("description", "Quiet period in milliseconds after a match before returning, so dynamic content settles (default: 500)")
                    })
                },
            )
        },
        execute = {
            val selector = it.jsonObject["selector"]?.jsonPrimitive?.contentOrNull ?: ""
            val timeout = it.jsonObject["timeout"]?.jsonPrimitive?.longOrNull ?: 10000L
            val idleMs = it.jsonObject["idle_ms"]?.jsonPrimitive?.longOrNull ?: 500L
            val result = HeadlessBrowserSession.withController(context) {
                it.waitFor(selector, timeout, idleMs)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_logs",
        description = """Inspect console messages or network requests in current page. Request and response bodies are always included, truncated at 4KB

        Usage notes:
        - Read browser console messages for debugging JavaScript errors, viewing application logs, or understanding what is happening in the browser console.
        - Read network requests for debugging API calls, monitoring network activity, or understanding what requests a page is making""",
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
                    put("limit", buildJsonObject {
                        put("type", "number")
                        put("description", "Maximum number of messages to return. Increase only if you need more results (default: 100)")
                    })
                    put("resource_type", buildJsonObject {
                        put("type", "array")
                        put("items", buildJsonObject { put("type", "string") })
                        put("description", "Filter by resource type (e.g. XHR, Fetch, Document, Image, etc)")
                    })
                    put("url_pattern", buildJsonObject {
                        put("type", "string")
                        put("description", "URL pattern to filter requests. Only requests whose URL contains this string will be returned (e.g. \"/api/\" to filter API calls, \"https://example.com\" to filter by domain)")
                    })
                    put("pattern", buildJsonObject {
                        put("type", "string")
                        put("description", "Filter console messages by text. Wrap in /slashes/ for regex (e.g. /error|warning/); plain text matches as substring. Always provide one to avoid noise")
                    })
                    put("clear", buildJsonObject {
                        put("type", "boolean")
                        put("description", "If true, clear the console messages or network requests after reading to avoid duplicates on subsequent calls (default: false)")
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
