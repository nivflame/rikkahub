package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth

class BrowserToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext) = HugeIcons.Earth

    @Composable
    override fun title(context: ToolUIContext): String = "Browser: ${browserActionLabel(toolName)}"

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

private fun browserActionLabel(toolName: String): String = when (toolName) {
    "browser_navigate" -> "Navigate"
    "browser_screenshot" -> "Screenshot"
    "browser_interact" -> "Interact"
    "browser_dom_snapshot" -> "DOM Snapshot"
    "browser_execute_script" -> "Execute Script"
    "browser_waitfor" -> "Wait"
    else -> toolName
}
