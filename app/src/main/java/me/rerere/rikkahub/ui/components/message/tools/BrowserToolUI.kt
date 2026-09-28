package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.booleanOrNull
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull

class BrowserToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext) = HugeIcons.Earth

    @Composable
    override fun title(context: ToolUIContext): String = browserLabel(context)

    override fun hasSummary(context: ToolUIContext): Boolean = false
}

private fun browserLabel(context: ToolUIContext): String = when (context.tool.toolName) {
    "browser_navigate" -> when (context.arguments.getStringContent("type")) {
        "back" -> "Navigate back"
        "forward" -> "Navigate forward"
        "reload" -> "Reload page"
        else -> "Navigate: ${context.arguments.getStringContent("url") ?: ""}"
    }
    "browser_resize_window" -> {
        val width = context.arguments.getStringContent("width") ?: ""
        val height = context.arguments.getStringContent("height") ?: ""
        "Resize: ${width}x${height}"
    }
    "browser_screenshot" -> {
        val selector = context.arguments.getStringContent("selector")
        val fullPage = context.arguments?.jsonObjectOrNull?.get("fullPage")?.jsonPrimitiveOrNull?.booleanOrNull
        when {
            selector != null -> "Screenshot: $selector"
            fullPage == true -> "Screenshot: full page"
            else -> "Screenshot page"
        }
    }
    "browser_interact" -> {
        val action = context.arguments.getStringContent("action")?.replaceFirstChar { it.uppercase() } ?: "Interact"
        val target = context.arguments.getStringContent("selector")
            ?: context.arguments.getStringContent("value")
            ?: context.arguments.getStringContent("key")
            ?: context.arguments.getStringContent("text")
        if (target != null) "Interact: $action $target" else "Interact: $action"
    }
    "browser_dom_snapshot" -> {
        val selector = context.arguments.getStringContent("selector")
        if (selector != null) "Inspect page: $selector" else "Inspect page"
    }
    "browser_execute_script" -> "Execute script"
    "browser_waitfor" -> "Wait: ${context.arguments.getStringContent("selector") ?: ""}"
    "browser_logs" -> "Inspect logs"
    else -> context.tool.toolName
}
