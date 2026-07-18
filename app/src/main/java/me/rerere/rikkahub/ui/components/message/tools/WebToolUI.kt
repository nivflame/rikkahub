package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Search01

class WebToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext): ImageVector = when (toolName) {
        "WebSearch" -> HugeIcons.Search01
        "WebFetch" -> HugeIcons.Earth
        else -> HugeIcons.Earth
    }

    @Composable
    override fun title(context: ToolUIContext): String = toolName

    override fun hasSummary(context: ToolUIContext): Boolean = when (toolName) {
        "WebSearch" -> context.arguments.getStringContent("query") != null
        "WebFetch" -> context.arguments.getStringContent("url") != null
        else -> false
    }

    @Composable
    override fun Summary(context: ToolUIContext) {
        when (toolName) {
            "WebSearch" -> {
                val query = context.arguments.getStringContent("query") ?: return
                val news = context.arguments.getStringContent("news") == "true"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (news) {
                        Text(
                            text = "News search",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            "WebFetch" -> {
                val url = context.arguments.getStringContent("url") ?: return
                val startIndex = context.arguments.getStringContent("start_index")?.toIntOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (startIndex != null && startIndex > 0) {
                        Text(
                            text = "from line $startIndex",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
