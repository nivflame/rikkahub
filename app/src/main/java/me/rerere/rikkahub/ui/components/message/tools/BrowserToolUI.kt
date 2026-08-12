package me.rerere.rikkahub.ui.components.message.tools

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth

class BrowserToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext) = HugeIcons.Earth

    @Composable
    override fun title(context: ToolUIContext): String = "Browser: ${browserActionLabel(toolName)}"

    @Composable
    override fun Label(context: ToolUIContext) {
        if (toolName == "browser_screenshot") {
            val image = context.tool.output.firstOrNull { it is UIMessagePart.Image } as? UIMessagePart.Image
            val dimensions = image?.let { getImageDimensions(it.url, LocalContext.current) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title(context),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dimensions != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = "${dimensions.first}x${dimensions.second}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        } else {
            super.Label(context)
        }
    }

    override fun hasSummary(context: ToolUIContext): Boolean = toolName == "browser_navigate"

    @Composable
    override fun Summary(context: ToolUIContext) {
        if (toolName != "browser_navigate") return
        val url = context.arguments.getStringContent("url") ?: ""
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

internal fun getImageDimensions(url: String, context: android.content.Context): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when {
        url.startsWith("file://") -> {
            val path = Uri.parse(url).path ?: return null
            BitmapFactory.decodeFile(path, options)
        }
        url.startsWith("content://") -> {
            context.contentResolver.openInputStream(Uri.parse(url))?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null
        }
        else -> return null
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    return Pair(options.outWidth, options.outHeight)
}

private fun browserActionLabel(toolName: String): String = when (toolName) {
    "browser_navigate" -> "Navigate"
    "browser_get_content" -> "Get Content"
    "browser_screenshot" -> "Screenshot"
    "browser_interact" -> "Interact"
    "browser_dom_snapshot" -> "DOM Snapshot"
    "browser_execute_script" -> "Execute Script"
    "browser_waitfor" -> "Wait"
    else -> toolName
}
