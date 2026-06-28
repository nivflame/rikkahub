package me.rerere.rikkahub.ui.components.message.tools

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Eye
import me.rerere.rikkahub.browser.BrowserViewerActivity

/**
 * Card UI for the browser_open tool. Shows the URL the agent navigated to and a "View" button
 * that opens [BrowserViewerActivity], the mirrored viewer that follows the headless session.
 */
object BrowserToolUI : ToolUIRenderer {
    override val toolName: String = "browser_open"

    override fun icon(context: ToolUIContext) = HugeIcons.Earth

    @Composable
    override fun title(context: ToolUIContext): String = "Browser"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
}
