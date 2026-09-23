package me.rerere.rikkahub.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ScreenshotService"

data class DeviceElement(
    val index: Int,
    val text: String,
    val className: String,
    val bounds: Rect,
)

data class DeviceState(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val elements: List<DeviceElement>,
)

sealed interface DeviceActionResult {
    data class Success(val state: DeviceState) : DeviceActionResult
    data class Error(val message: String) : DeviceActionResult
}

class ScreenshotService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    sealed interface CaptureResult {
        data class Success(val bitmap: Bitmap) : CaptureResult
        data object Throttled : CaptureResult
        data object Failed : CaptureResult
    }

    companion object {
        private var instance: ScreenshotService? = null

        fun isEnabled(): Boolean = instance != null

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun capture(): CaptureResult {
            val service = instance ?: return CaptureResult.Failed
            val result = suspendCancellableCoroutine { cont ->
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    { it.run() },
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            try {
                                val wrapped = Bitmap.wrapHardwareBuffer(buffer, null)
                                val bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                                if (bitmap != null) cont.resume(CaptureResult.Success(bitmap))
                                else cont.resume(CaptureResult.Failed)
                            } finally {
                                buffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.w(TAG, "takeScreenshot failed: $errorCode")
                            if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                                cont.resume(CaptureResult.Throttled)
                            } else {
                                cont.resume(CaptureResult.Failed)
                            }
                        }
                    }
                )
            }
            if (result is CaptureResult.Throttled) {
                delay(400)
                return capture()
            }
            return result
        }

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun deviceState(): DeviceActionResult {
            val service = instance ?: return DeviceActionResult.Error("Accessibility service is off")
            val result = capture()
            if (result !is CaptureResult.Success) {
                return DeviceActionResult.Error("Screenshot failed")
            }
            val elements = service.dumpElements()
            return DeviceActionResult.Success(
                DeviceState(result.bitmap, result.bitmap.width, result.bitmap.height, elements)
            )
        }

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun deviceTap(
            elementIndex: Int?,
            x: Int?,
            y: Int?,
            kind: String,
        ): DeviceActionResult {
            val service = instance ?: return DeviceActionResult.Error("Accessibility service is off")
            val duration = when (kind) {
                "long_press" -> 700L
                else -> 50L
            }
            if (elementIndex != null) {
                val node = service.findNodeByIndex(elementIndex)
                    ?: return DeviceActionResult.Error("Element $elementIndex not found, call device_state for a fresh tree")
                val performed = when (kind) {
                    "long_press" -> node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    else -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                if (kind != "double_tap" && !performed) {
                    val center = service.nodeCenter(node)
                    if (center != null) {
                        service.gestureTap(center.first, center.second, duration)
                    } else {
                        return DeviceActionResult.Error("Element $elementIndex has no clickable action, try x/y coordinates")
                    }
                }
                if (kind == "double_tap") {
                    val center = service.nodeCenter(node)
                    if (center != null) {
                        service.gestureDoubleTap(center.first, center.second)
                    } else {
                        return DeviceActionResult.Error("Element $elementIndex has no clickable frame, try x/y coordinates")
                    }
                }
                delay(300)
                return deviceState()
            }
            if (x == null || y == null) {
                return DeviceActionResult.Error("device_tap requires element_index or x/y")
            }
            val (px, py) = service.denormalize(x, y)
            if (kind == "double_tap") {
                service.gestureDoubleTap(px, py)
            } else {
                val node = service.findNodeAt(px, py)
                val performed = if (node != null) {
                    when (kind) {
                        "long_press" -> node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        else -> node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                } else false
                if (!performed) service.gestureTap(px, py, duration)
            }
            delay(300)
            return deviceState()
        }

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun deviceSwipe(elementIndex: Int, direction: String, pages: Double): DeviceActionResult {
            val service = instance ?: return DeviceActionResult.Error("Accessibility service is off")
            val node = service.findNodeByIndex(elementIndex)
                ?: return DeviceActionResult.Error("Element $elementIndex not found, call device_state for a fresh tree")
            val action = when (direction) {
                "up", "left" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            }
            val repeat = max(1, pages.toInt())
            var scrolled = false
            repeat(repeat) {
                if (node.performAction(action)) scrolled = true
                delay(100)
            }
            if (!scrolled) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val (sx, sy, ex, ey) = when (direction) {
                    "up" -> Quad(bounds.centerX(), bounds.bottom - 50, bounds.centerX(), bounds.top + 50)
                    "down" -> Quad(bounds.centerX(), bounds.top + 50, bounds.centerX(), bounds.bottom - 50)
                    "left" -> Quad(bounds.right - 50, bounds.centerY(), bounds.left + 50, bounds.centerY())
                    else -> Quad(bounds.left + 50, bounds.centerY(), bounds.right - 50, bounds.centerY())
                }
                service.gestureSwipe(sx, sy, ex, ey, 400)
            }
            delay(300)
            return deviceState()
        }

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun deviceDrag(fromX: Int, fromY: Int, toX: Int, toY: Int): DeviceActionResult {
            val service = instance ?: return DeviceActionResult.Error("Accessibility service is off")
            val (sx, sy) = service.denormalize(fromX, fromY)
            val (ex, ey) = service.denormalize(toX, toY)
            val distance = hypot((ex - sx).toDouble(), (ey - sy).toDouble())
            val steps = min(max((distance / 4).toInt(), 10), 60)
            val duration = (steps * 12L).coerceIn(200, 800)
            service.gestureSwipe(sx, sy, ex, ey, duration)
            delay(300)
            return deviceState()
        }

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun deviceType(elementIndex: Int?, x: Int?, y: Int?, text: String): DeviceActionResult {
            val service = instance ?: return DeviceActionResult.Error("Accessibility service is off")
            val node = when {
                elementIndex != null -> service.findNodeByIndex(elementIndex)
                x != null && y != null -> {
                    val (px, py) = service.denormalize(x, y)
                    service.findNodeAt(px, py)
                }
                else -> null
            } ?: return DeviceActionResult.Error("device_type requires element_index or x/y")
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(150)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return DeviceActionResult.Error("Cannot set text on that element, tap the field first so it gains focus")
            }
            delay(300)
            return deviceState()
        }

        @RequiresApi(Build.VERSION_CODES.R)
        suspend fun deviceKey(key: String): DeviceActionResult {
            val service = instance ?: return DeviceActionResult.Error("Accessibility service is off")
            val action = when (key) {
                "back" -> GLOBAL_ACTION_BACK
                "home" -> GLOBAL_ACTION_HOME
                "recents" -> GLOBAL_ACTION_RECENTS
                else -> return DeviceActionResult.Error("Unknown key $key, expected back, home, or recents")
            }
            service.performGlobalAction(action)
            delay(400)
            return deviceState()
        }

        private data class Quad(val a: Int, val b: Int, val c: Int, val d: Int)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun dumpElements(): List<DeviceElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val out = mutableListOf<DeviceElement>()
        collectElements(root, out, 1200)
        root.recycle()
        return out
    }

    private fun collectElements(node: AccessibilityNodeInfo, out: MutableList<DeviceElement>, limit: Int) {
        if (out.size >= limit) return
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val label = text.ifBlank { desc }
        if (label.isNotBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            out.add(
                DeviceElement(
                    index = out.size,
                    text = label.take(120),
                    className = node.className?.toString().orEmpty(),
                    bounds = bounds,
                )
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectElements(child, out, limit)
                child.recycle()
            }
        }
    }

    private fun findNodeByIndex(index: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val found = findIndexed(root, index, intArrayOf(-1))
        if (found == null) root.recycle()
        return found
    }

    private fun findIndexed(node: AccessibilityNodeInfo, target: Int, counter: IntArray): AccessibilityNodeInfo? {
        val label = node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() }
        if (label.isNotBlank()) {
            counter[0]++
            if (counter[0] == target) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findIndexed(child, target, counter)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findNodeAt(px: Int, py: Int): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val found = hitTest(root, px, py)
        if (found == null) root.recycle()
        return found
    }

    private fun hitTest(node: AccessibilityNodeInfo, px: Int, py: Int): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        var best: AccessibilityNodeInfo? = null
        var bestArea = Int.MAX_VALUE
        if (bounds.contains(px, py)) {
            val label = node.text?.toString().orEmpty().ifBlank { node.contentDescription?.toString().orEmpty() }
            if (label.isNotBlank()) {
                best = node
                bestArea = bounds.width() * bounds.height()
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val candidate = hitTest(child, px, py)
            if (candidate != null) {
                val cb = Rect()
                candidate.getBoundsInScreen(cb)
                val area = cb.width() * cb.height()
                if (area < bestArea) {
                    best = candidate
                    bestArea = area
                } else {
                    candidate.recycle()
                }
            }
            child.recycle()
        }
        return best
    }

    private fun nodeCenter(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return null
        return bounds.centerX() to bounds.centerY()
    }

    private fun denormalize(x: Int, y: Int): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        return (x * metrics.widthPixels / 1000) to (y * metrics.heightPixels / 1000)
    }

    private suspend fun gestureTap(px: Int, py: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(px.toFloat(), py.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null, null,
        )
    }

    private suspend fun gestureDoubleTap(px: Int, py: Int): Boolean {
        val path = Path().apply { moveTo(px.toFloat(), py.toFloat()) }
        val first = GestureDescription.StrokeDescription(path, 0, 50, true)
        val second = GestureDescription.StrokeDescription(path, 150, 50)
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(first).addStroke(second).build(),
            null, null,
        )
        delay(200)
        return ok
    }

    private suspend fun gestureSwipe(sx: Int, sy: Int, ex: Int, ey: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(sx.toFloat(), sy.toFloat())
            lineTo(ex.toFloat(), ey.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val ok = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            null, null,
        )
        delay(durationMs + 100)
        return ok
    }
}
