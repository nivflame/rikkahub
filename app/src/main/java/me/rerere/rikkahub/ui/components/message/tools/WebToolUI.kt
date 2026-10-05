package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.common.http.jsonArrayOrNull
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
    override fun title(context: ToolUIContext): String = when (toolName) {
        "WebSearch" -> "Search: ${context.arguments.getStringContent("query") ?: ""}"
        "WebFetch" -> "Fetch: ${context.arguments.getStringContent("url") ?: ""}"
        else -> toolName
    }

    @Composable
    override fun Label(context: ToolUIContext) {
        if (toolName == "WebSearch") {
            val resultCount = context.content?.jsonArrayOrNull?.size
            val news = context.arguments.getStringContent("news") == "true"
            val meta = buildList {
                if (resultCount != null && resultCount > 0) add("$resultCount results")
                if (news) add("News")
            }.joinToString(" · ")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title(context),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Text(
                text = title(context),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

}
