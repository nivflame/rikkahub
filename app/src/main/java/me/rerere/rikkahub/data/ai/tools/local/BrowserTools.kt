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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val ALL_BROWSER_TOOL_NAMES: List<String> = listOf(
    "browser_search",
    "browser_fetch",
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
        name = "browser_search",
        description = "Allows you to search the web using Google Search\n\nUsage notes:\n- Set news to true for News search, false for regular web search\n- Keep queries short and specific (1-6 words). Start broad, then narrow\n- Make each query distinct. Repeating phrases yields the same results\n- Use the correct year in search queries:\n  - The current month is ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))}. You MUST use this year when searching for recent information, documentation, or current events\n  - Example: If the user asks for \"latest React docs\", search for \"React documentation\" with the current year ${LocalDate.now().year}, NOT last year\n- Use browser_fetch to read full articles. browser_search snippets are too brief to cite\n- Prioritize browser_fetch on primary sources (company blogs, official announcements, peer-reviewed papers, first-hand reports) over aggregator roundups. When search results contain both official source URLs and aggregator URLs covering the same topic, ALWAYS fetch the official source first\n- If a source is not found, inform the user\n- Use the user's provided location for location-dependent queries\n- NEVER mention your knowledge cutoff or justify using search tools. Just search\n- Provide a substantive answer first. Do not reply with only a search offer or disclaimer\n- Trust search results even if surprising. Be skeptical of SEO-heavy results and conspiracy-prone topics\n- If results conflict or are incomplete, run more searches to clarify\n- This is more efficient than navigating to Google and using browser_dom_snapshot",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "The search query")
                    })
                    put("news", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Search Google News instead of regular web search. Defaults to false")
                    })
                },
                required = listOf("query")
            )
        },
        execute = {
            val query = it.jsonObject["query"]?.jsonPrimitive?.contentOrNull ?: ""
            val news = it.jsonObject["news"]?.jsonPrimitive?.contentOrNull == "true"
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.search(query, news)
            }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_fetch",
        description = "Fetches content from a specified URL\n\nUsage notes:\n- When a URL redirects to a different host, the tool will inform you and provide the redirect URL in a special format. You should then make a new WebFetch request with the redirect URL to fetch the content\n- For GitHub URLs, prefer using the gh CLI via Bash instead (e.g., gh pr view, gh issue view, gh api)\n- For search results or structured list pages, use browser_dom_snapshot instead\n- If the result ends with a truncation notice, call this tool again with the start_index from that notice until you read full content\n- If it returns \"no content\", use browser_dom_snapshot with a selector targeting the main content area",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "The URL to fetch content")
                    })
                    put("start_index", buildJsonObject {
                        put("type", "number")
                        put("description", "Line number to start reading from. Defaults to 0. Use the start_index from a truncation notice to continue reading")
                    })
                },
                required = listOf("url")
            )
        },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val startIndex = it.jsonObject["start_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
            val content = HeadlessBrowserSession.withController(context) {
                it.fetch(url, BrowserController.MAX_CONTENT_CHARS, startIndex)
            }
            listOf(UIMessagePart.Text(content))
        }
    ),
    Tool(
        name = "browser_navigate",
        description = "Navigate the in-app browser to a URL, or go back, forward, or reload.\n\nUsage notes:\n- The page is fully loaded and ready when this tool returns\n- Set type to \"back\", \"forward\", or \"reload\" to navigate history instead of opening a URL",
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
        description = "Read the current page as markdown (main article content with links resolved to absolute URLs), paginated.\n\nUsage notes:\n- Use this to read content from a page you have already navigated to and possibly interacted with\n- For reading a new URL, use browser_fetch instead (it combines navigation and content extraction in one call)\n- For search results or structured list pages, use browser_dom_snapshot instead\n- If the result ends with a truncation notice, call this tool again with the start_index from that notice until the whole page is read",
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
                listOf(UIMessagePart.Image(path))
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
        name = "browser_logs",
        description = "Return captured browser logs.\n\nUsage notes:\n- Use type \"console\" for console output, or \"network\" for network request URLs (no response bodies)",
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
    ),
    Tool(
        name = "browser_waitfor",
        description = "Wait for an element to appear on the current page. Supports both CSS selectors and text search.\n\n- If the selector contains CSS metacharacters (#.>[:*), it is treated as a CSS selector\n- Otherwise it is treated as a text search (e.g., \"Login\" finds a button with that text)\n- Returns whether the element was found within the timeout\n- Requires an active page (call browser_navigate or browser_fetch first)\n- Useful for waiting for Cloudflare challenges to complete, dynamic content to load, or popups to appear",
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
    )
)
