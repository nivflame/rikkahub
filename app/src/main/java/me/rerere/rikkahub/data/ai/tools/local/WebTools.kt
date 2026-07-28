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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun buildWebTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "WebSearch",
        description = "Allows you to search the web using Google Search\n\nYou MUST follow this:\n- You MUST include a `[citation,domain](id)` after a sentence\n- Multiple citations are allowed\n- If no results are cited, omit citations\n- This is MANDATORY never skip including citation sources in your response\n  - Example format:\n  \n    The capital of France is Paris. [citation,example.com](abc123)\n    The population is about 2.1 million. [citation,example.com](abc123) [citation,example2.com](def456)\n\nUsage notes:\n- Set news to true for News search, false for regular web search\n- Keep queries short and specific (1-6 words). Start broad, then narrow\n- Make each query distinct. Repeating phrases yields the same results\n- Use the correct year in search queries:\n  - The current month is ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))}. You MUST use this year when searching for recent information, documentation, or current events\n  - Example: If the user asks for \"latest React docs\", search for \"React documentation\" with the current year ${LocalDate.now().year}, NOT last year\n- Use WebFetch to read full articles. WebSearch snippets are too brief to cite\n- Prioritize WebFetch on primary sources (company blogs, official announcements, peer-reviewed papers, first-hand reports) over aggregator roundups. When search results contain both official source URLs and aggregator URLs covering the same topic, ALWAYS fetch the official source first\n- If a source is not found, inform the user\n- Use the user's provided location for location-dependent queries\n- NEVER mention your knowledge cutoff or justify using search tools. Just search\n- Provide a substantive answer first. Do not reply with only a search offer or disclaimer\n- Trust search results even if surprising. Be skeptical of SEO-heavy results and conspiracy-prone topics\n- If results conflict or are incomplete, run more searches to clarify",
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
        name = "WebFetch",
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
)
