package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private val webSearchMutex = Mutex()

fun buildWebTools(
    context: Context,
    webSearchEngine: String = "google",
    webSearchResultCount: Int = 10,
    webSearchDelayMs: Long = 3000L,
): List<Tool> = listOf(
    Tool(
        name = "WebSearch",
        description = """Allows you to search the web using ${if (webSearchEngine == "brave") "Brave Search" else "Google Search"}

You MUST follow this:
- You MUST include a `[citation,domain](id)` after a sentence
- Multiple citations are allowed
- If no results are cited, omit citations
- This is MANDATORY never skip including citation sources in your response
  - Example format:
  
    The capital of France is Paris. [citation,example.com](abc123)
    The population is about 2.1 million. [citation,example.com](abc123) [citation,example2.com](def456)

Usage notes:
- Set news to true for News search, false for regular web search
- Keep queries short and specific (1-6 words). Start broad, then narrow
- Make each query distinct. Repeating phrases yields the same results
- Use the correct year in search queries:
  - The current month is ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))}. You MUST use this year when searching for recent information, documentation, or current events
  - Example: If the user asks for "latest React docs", search for "React documentation" with the current year ${LocalDate.now().year}, NOT last year
- Use WebFetch to read full articles. WebSearch snippets are too brief to cite
- Prioritize WebFetch on primary sources (company blogs, official announcements, peer-reviewed papers, first-hand reports) over aggregator roundups. When search results contain both official source URLs and aggregator URLs covering the same topic, ALWAYS fetch the official source first
- If a source is not found, inform the user
- Use the user's provided location for location-dependent queries
- NEVER mention your knowledge cutoff or justify using search tools. Just search
- Provide a substantive answer first. Do not reply with only a search offer or disclaimer
- Trust search results even if surprising. Be skeptical of SEO-heavy results and conspiracy-prone topics
- If results conflict or are incomplete, run more searches to clarify""".trimIndent(),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                        put("description", "The search query")
                    })
                    put("news", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Search news instead of regular web search. Defaults to false")
                    })
                },
                required = listOf("query")
            )
        },
        execute = {
            val query = it.jsonObject["query"]?.jsonPrimitive?.contentOrNull ?: ""
            val news = it.jsonObject["news"]?.jsonPrimitive?.contentOrNull == "true"
            val result = webSearchMutex.withLock {
                val searchResult = HeadlessBrowserSession.withController(context) { controller ->
                    if (webSearchEngine == "brave") {
                        controller.searchBrave(query, news, webSearchResultCount)
                    } else {
                        controller.search(query, news, webSearchResultCount)
                    }
                }
                if (webSearchDelayMs > 0) delay(webSearchDelayMs)
                searchResult
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
