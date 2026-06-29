package me.rerere.rikkahub.ui.components.message.tools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Eye
import me.rerere.rikkahub.browser.BrowserViewerActivity

private const val PREVIEW_TEXT_CAP = 4000

/**
 * Card UI for the browser tools. Renders a safe plain-text preview (the default highlighter
 * chokes on the large, non-JSON page markdown returned by browser_get_content) and a "View" button
 * that opens [BrowserViewerActivity] to watch the headless session.
 */
class BrowserToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext) = HugeIcons.Earth

    @Composable
    override fun title(context: ToolUIContext): String = "Browser: ${browserActionLabel(toolName)}"

    override fun hasSummary(context: ToolUIContext): Boolean = toolName == "browser_navigate"

    @Composable
    override fun Summary(context: ToolUIContext) {
        if (toolName != "browser_navigate") return
        val activityContext = LocalContext.current
        val url = context.arguments.getStringContent("url") ?: ""
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                activityContext.startActivity(
                    Intent(activityContext, BrowserViewerActivity::class.java)
                )
            }) {
                Icon(imageVector = HugeIcons.Eye, contentDescription = "View browser")
            }
        }
    }

    @Composable
    override fun Preview(context: ToolUIContext, onDismissRequest: () -> Unit) {
        val activityContext = LocalContext.current
        Column(
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Browser",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = {
                    activityContext.startActivity(
                        Intent(activityContext, BrowserViewerActivity::class.java)
                    )
                }) {
                    Icon(imageVector = HugeIcons.Eye, contentDescription = "View browser")
                }
            }
            context.arguments.getStringContent("url")?.let { url ->
                if (url.isNotBlank()) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (context.tool.output.isNotEmpty()) {
                Text("Result", style = MaterialTheme.typography.titleSmall)
                context.tool.output.forEach { part ->
                    when (part) {
                        is UIMessagePart.Text -> Text(
                            text = part.text.take(PREVIEW_TEXT_CAP),
                            style = MaterialTheme.typography.bodySmall
                        )
                        is UIMessagePart.Image -> AsyncImage(
                            model = part.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> {}
                    }
                }
            }
        }
    }
}

private fun browserActionLabel(toolName: String): String = when (toolName) {
    "browser_navigate" -> "Navigate"
    "browser_get_content" -> "Get Content"
    "browser_screenshot" -> "Screenshot"
    "browser_interact" -> "Interact"
    "browser_dom_snapshot" -> "DOM Snapshot"
    "browser_execute_script" -> "Execute Script"
    "browser_logs" -> "Logs"
    "browser_close" -> "Close"
    else -> toolName
}
