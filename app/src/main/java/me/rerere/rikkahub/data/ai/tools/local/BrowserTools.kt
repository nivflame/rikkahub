package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
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
You have browser tools to navigate the web. Use browser_open(url) to navigate to a URL,
browser_get_text to read the main text of the current page, browser_get_links to list the
links on the page so you can decide where to go next, browser_back to return to the previous
page, and browser_screenshot to capture the current page as an image. Call browser_done when
the browsing task is complete. Prefer browser_get_text for reading, and use browser_get_links
to find the next page to open.
""".trimIndent().replace("\n", " ")

internal fun buildBrowserTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "browser_open",
        description = "Navigate the browser to a URL. The page is loaded and ready to read " +
            "when this returns. Use this to start browsing or to go to a specific page.",
        systemPrompt = { _, _ -> BROWSER_SYSTEM_PROMPT },
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("url", buildJsonObject {
                        put("type", "string")
                        put("description", "The full URL to navigate to, including the scheme")
                    })
                },
                required = listOf("url")
            )
        },
        execute = {
            val url = it.jsonObject["url"]?.jsonPrimitive?.contentOrNull ?: ""
            val result = HeadlessBrowserSession.withController(context) { controller ->
                controller.open(url)
            }
            listOf(UIMessagePart.Text("navigated to: $result"))
        }
    ),
    Tool(
        name = "browser_current_url",
        description = "Return the URL the browser is currently on.",
        execute = {
            val url = HeadlessBrowserSession.withController(context) { it.currentUrl() }
            listOf(UIMessagePart.Text(if (url.isBlank()) "no page loaded" else url))
        }
    ),
    Tool(
        name = "browser_get_text",
        description = "Return the main visible text of the current page, capped to " +
            "${BrowserController.MAX_TEXT_CHARS} characters. Use this to read what is on the page.",
        execute = {
            val text = HeadlessBrowserSession.withController(context) {
                it.getText(BrowserController.MAX_TEXT_CHARS)
            }
            listOf(UIMessagePart.Text(text.ifBlank { "no text on the current page" }))
        }
    ),
    Tool(
        name = "browser_get_links",
        description = "Return the links on the current page as JSON (href and text), capped " +
            "to ${BrowserController.MAX_LINKS} links. Use this to find where to navigate next.",
        execute = {
            val links = HeadlessBrowserSession.withController(context) {
                it.getLinks(BrowserController.MAX_LINKS)
            }
            listOf(UIMessagePart.Text(links))
        }
    ),
    Tool(
        name = "browser_back",
        description = "Go back to the previous page in the browser history.",
        execute = {
            val result = HeadlessBrowserSession.withController(context) { it.back() }
            listOf(UIMessagePart.Text(result))
        }
    ),
    Tool(
        name = "browser_screenshot",
        description = "Capture the current page as a JPEG image and return it. Use this only " +
            "when you need to see the visual layout, not for reading text (use browser_get_text).",
        execute = {
            val path = HeadlessBrowserSession.withController(context) {
                it.screenshot(BrowserController.MAX_SCREENSHOT_HEIGHT_PX, context)
            }
            if (path != null) {
                listOf(UIMessagePart.Image(path))
            } else {
                listOf(UIMessagePart.Text("failed to capture screenshot"))
            }
        }
    ),
    Tool(
        name = "browser_done",
        description = "Signal that the browsing task is complete. Call this when you have " +
            "finished navigating and reading, so the conversation can continue.",
        execute = {
            listOf(UIMessagePart.Text("browser task complete"))
        }
    )
)
