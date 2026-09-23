package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.service.DeviceActionResult
import me.rerere.rikkahub.service.DeviceState
import me.rerere.rikkahub.service.ScreenshotService
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private fun coordParam(name: String, description: String) = buildJsonObject {
    put("type", "number")
    put("description", description)
}

private fun kotlinx.serialization.json.JsonElement?.numOrNull(): Double? = try {
    this?.jsonPrimitive?.doubleOrNull ?: this?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
} catch (_: Exception) {
    null
}

private suspend fun saveDeviceBitmap(context: Context, bitmap: Bitmap): String {
    val dir = File(context.cacheDir, "device").apply { mkdirs() }
    val file = File(dir, "shot-${UUID.randomUUID()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return "file://${file.absolutePath}"
}

private suspend fun renderState(context: Context, state: DeviceState): List<UIMessagePart> {
    val tree = state.elements.joinToString("\n") { e ->
        "[${e.index}] \"${e.text}\" (${e.className.substringAfterLast('.')}) [${e.bounds.left},${e.bounds.top},${e.bounds.right},${e.bounds.bottom}]"
    }
    val text = "Screen: ${state.width}x${state.height}. Coordinates in device tools are 0-1000 normalized against this screenshot.\n$tree"
    return listOf(
        UIMessagePart.Text(text),
        UIMessagePart.Image(saveDeviceBitmap(context, state.bitmap)),
    )
}

private suspend fun renderResult(context: Context, result: DeviceActionResult, note: String): List<UIMessagePart> =
    when (result) {
        is DeviceActionResult.Success -> listOf(UIMessagePart.Text(note)) + renderState(context, result.state)
        is DeviceActionResult.Error -> listOf(UIMessagePart.Text("Error: ${result.message}"))
    }

private fun unsupported(): List<UIMessagePart> =
    listOf(UIMessagePart.Text("Error: Device tools require Android 11 (API 30) or newer"))

fun buildDeviceTools(context: Context): List<Tool> = listOf(
    Tool(
        name = "device_state",
        description = "Get the current device screen: screenshot plus indexed UI element tree. Call once per turn before interacting.",
        parameters = { InputSchema.Obj(buildJsonObject {}) },
        execute = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@Tool unsupported()
            when (val result = ScreenshotService.deviceState()) {
                is DeviceActionResult.Success -> renderState(context, result.state)
                is DeviceActionResult.Error -> listOf(UIMessagePart.Text("Error: ${result.message}"))
            }
        }
    ),
    Tool(
        name = "device_tap",
        description = "Tap an element by index or coordinates. Coordinates are 0-1000 normalized against the latest device_state screenshot.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("element_index", buildJsonObject {
                        put("type", "string")
                        put("description", "Element index from device_state")
                    })
                    put("x", coordParam("x", "X coordinate 0-1000"))
                    put("y", coordParam("y", "Y coordinate 0-1000"))
                    put("kind", buildJsonObject {
                        put("type", "string")
                        put("description", "tap, long_press, or double_tap. Defaults to tap")
                    })
                }
            )
        },
        execute = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@Tool unsupported()
            val args = it.jsonObject
            val result = ScreenshotService.deviceTap(
                elementIndex = args["element_index"].numOrNull()?.toInt(),
                x = args["x"].numOrNull()?.toInt(),
                y = args["y"].numOrNull()?.toInt(),
                kind = args["kind"]?.jsonPrimitive?.contentOrNull ?: "tap",
            )
            renderResult(context, result, "Tap done")
        }
    ),
    Tool(
        name = "device_swipe",
        description = "Scroll an element in a direction. Use device_state to find the scrollable element index.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("element_index", buildJsonObject {
                        put("type", "string")
                        put("description", "Scrollable element index from device_state")
                    })
                    put("direction", buildJsonObject {
                        put("type", "string")
                        put("description", "up, down, left, or right")
                    })
                    put("pages", buildJsonObject {
                        put("type", "number")
                        put("description", "Number of pages to scroll. Defaults to 1")
                    })
                },
                required = listOf("element_index", "direction"),
            )
        },
        execute = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@Tool unsupported()
            val args = it.jsonObject
            val index = args["element_index"].numOrNull()?.toInt()
                ?: return@Tool listOf(UIMessagePart.Text("Error: element_index is required"))
            val result = ScreenshotService.deviceSwipe(
                elementIndex = index,
                direction = args["direction"]?.jsonPrimitive?.contentOrNull ?: "down",
                pages = args["pages"].numOrNull() ?: 1.0,
            )
            renderResult(context, result, "Swipe done")
        }
    ),
    Tool(
        name = "device_drag",
        description = "Drag from one point to another. Coordinates are 0-1000 normalized against the latest device_state screenshot.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("from_x", coordParam("from_x", "Start X 0-1000"))
                    put("from_y", coordParam("from_y", "Start Y 0-1000"))
                    put("to_x", coordParam("to_x", "End X 0-1000"))
                    put("to_y", coordParam("to_y", "End Y 0-1000"))
                },
                required = listOf("from_x", "from_y", "to_x", "to_y"),
            )
        },
        execute = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@Tool unsupported()
            val args = it.jsonObject
            fun num(key: String) = args[key].numOrNull()?.toInt()
            val sx = num("from_x")
            val sy = num("from_y")
            val ex = num("to_x")
            val ey = num("to_y")
            if (sx == null || sy == null || ex == null || ey == null) {
                return@Tool listOf(UIMessagePart.Text("Error: from_x, from_y, to_x, to_y are all required"))
            }
            renderResult(context, ScreenshotService.deviceDrag(sx, sy, ex, ey), "Drag done")
        }
    ),
    Tool(
        name = "device_type",
        description = "Set text on an editable element by index or coordinates. The field gains focus first.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("element_index", buildJsonObject {
                        put("type", "string")
                        put("description", "Editable element index from device_state")
                    })
                    put("x", coordParam("x", "X coordinate 0-1000"))
                    put("y", coordParam("y", "Y coordinate 0-1000"))
                    put("text", buildJsonObject {
                        put("type", "string")
                        put("description", "Text to enter")
                    })
                },
                required = listOf("text"),
            )
        },
        execute = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@Tool unsupported()
            val args = it.jsonObject
            val text = args["text"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("Error: text is required"))
            val result = ScreenshotService.deviceType(
                elementIndex = args["element_index"].numOrNull()?.toInt(),
                x = args["x"].numOrNull()?.toInt(),
                y = args["y"].numOrNull()?.toInt(),
                text = text,
            )
            renderResult(context, result, "Type done")
        }
    ),
    Tool(
        name = "device_key",
        description = "Press a system key: back, home, or recents.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("key", buildJsonObject {
                        put("type", "string")
                        put("description", "back, home, or recents")
                    })
                },
                required = listOf("key"),
            )
        },
        execute = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@Tool unsupported()
            val key = it.jsonObject["key"]?.jsonPrimitive?.contentOrNull
                ?: return@Tool listOf(UIMessagePart.Text("Error: key is required"))
            renderResult(context, ScreenshotService.deviceKey(key), "Key $key pressed")
        }
    ),
)
