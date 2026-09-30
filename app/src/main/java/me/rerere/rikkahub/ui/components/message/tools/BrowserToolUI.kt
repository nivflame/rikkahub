package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import me.rerere.common.http.jsonObjectOrNull
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Earth

class BrowserToolUI(override val toolName: String) : ToolUIRenderer {
    override fun icon(context: ToolUIContext) = HugeIcons.Earth

    @Composable
    override fun title(context: ToolUIContext): String = browserLabel(context)

    override fun hasSummary(context: ToolUIContext): Boolean = false
}

private fun browserLabel(context: ToolUIContext): String = when (context.tool.toolName) {
    "browser_navigate" -> when (context.arguments.getStringContent("action")) {
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
        val region = run {
            val x = context.arguments.getStringContent("x")
            val y = context.arguments.getStringContent("y")
            val w = context.arguments.getStringContent("w")
            val h = context.arguments.getStringContent("h")
            if (x != null && y != null && w != null && h != null) "$x,$y ${w}x$h" else null
        }
        val fullPage = context.arguments?.jsonObjectOrNull?.get("full_page")?.jsonPrimitiveOrNull?.booleanOrNull
        when {
            region != null -> "Screenshot: $region"
            selector != null -> "Screenshot: $selector"
            fullPage == true -> "Screenshot: full page"
            else -> "Screenshot page"
        }
    }
    "browser_click" -> {
        val point = run {
            val x = context.arguments.getStringContent("x")
            val y = context.arguments.getStringContent("y")
            if (x != null && y != null) "$x,$y" else null
        }
        val target = point ?: context.arguments.getStringContent("selector") ?: ""
        val button = context.arguments.getStringContent("button") ?: "left"
        val clicks = context.arguments.getStringContent("clicks")?.toIntOrNull() ?: 1
        val suffix = buildList {
            if (button != "left") add(button)
            if (clicks > 1) add("x$clicks")
        }.joinToString(" ").takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
        "Click $target$suffix"
    }
    "browser_fill" -> "Fill ${context.arguments.getStringContent("selector") ?: ""}: ${context.arguments.getStringContent("text") ?: ""}"
    "browser_type_text" -> "Type ${context.arguments.getStringContent("text") ?: ""}"
    "browser_press_key" -> {
        val key = context.arguments.getStringContent("key") ?: ""
        val mods = context.arguments?.jsonObjectOrNull?.get("modifiers")?.jsonArray
            ?.mapNotNull { it.jsonPrimitiveOrNull?.contentOrNull }
            ?.map { it.replaceFirstChar(Char::uppercase) }
            .orEmpty()
        "Press ${(mods + key).joinToString("+")}"
    }
    "browser_scroll" -> "Scroll ${context.arguments.getStringContent("dy") ?: ""}"
    "browser_upload_file" -> {
        val name = context.arguments.getStringContent("file_path")?.substringAfterLast('/') ?: ""
        "Upload $name"
    }
    "browser_dom_snapshot" -> {
        val selector = context.arguments.getStringContent("selector")
        val depth = context.arguments.getStringContent("depth")?.toIntOrNull()
        val interactive = context.arguments.getStringContent("filter") == "interactive"
        val suffix = buildList {
            if (selector != null) add(selector)
            if (interactive) add("interactive")
            if (depth != null && depth != 15) add("d$depth")
        }.joinToString(" ").takeIf { it.isNotEmpty() }?.let { ": $it" } ?: ""
        "Inspect page$suffix"
    }
    "browser_execute_script" -> "Execute script"
    "browser_waitfor" -> "Wait: ${context.arguments.getStringContent("selector") ?: ""}"
    "browser_logs" -> {
        val type = context.arguments.getStringContent("type") ?: "logs"
        val limit = context.arguments.getStringContent("limit")?.toIntOrNull()
        val suffix = if (limit != null && limit != 100) " ($limit)" else ""
        "Inspect ${type}$suffix"
    }
    else -> context.tool.toolName
}
