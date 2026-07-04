package me.rerere.rikkahub.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

sealed class DrawingAction {
    abstract val color: Color
    abstract val strokeWidth: Float

    abstract fun contains(point: Offset, tolerance: Float = 24f): Boolean

    abstract fun translate(delta: Offset): DrawingAction

    data class Freehand(
        val points: List<Offset>,
        override val color: Color,
        override val strokeWidth: Float,
    ) : DrawingAction() {
        override fun contains(point: Offset, tolerance: Float): Boolean {
            val effective = tolerance + strokeWidth / 2f
            for (i in 0 until points.size - 1) {
                if (distanceToSegment(point, points[i], points[i + 1]) <= effective) return true
            }
            return false
        }

        override fun translate(delta: Offset) = copy(points = points.map { it + delta })
    }

    data class Rectangle(
        val rect: Rect,
        override val color: Color,
        override val strokeWidth: Float,
    ) : DrawingAction() {
        override fun contains(point: Offset, tolerance: Float): Boolean {
            val effective = tolerance + strokeWidth / 2f
            val outer = rect.inflate(effective)
            val inner = rect.deflate(effective)
            return outer.contains(point) && !inner.contains(point)
        }

        override fun translate(delta: Offset) = copy(rect = rect.translate(delta))
    }

    data class Circle(
        val center: Offset,
        val radius: Float,
        override val color: Color,
        override val strokeWidth: Float,
    ) : DrawingAction() {
        override fun contains(point: Offset, tolerance: Float): Boolean {
            val effective = tolerance + strokeWidth / 2f
            val distance = sqrt(
                (point.x - center.x) * (point.x - center.x) +
                    (point.y - center.y) * (point.y - center.y)
            )
            return abs(distance - radius) <= effective
        }

        override fun translate(delta: Offset) = copy(center = center + delta)
    }

    data class Arrow(
        val start: Offset,
        val end: Offset,
        override val color: Color,
        override val strokeWidth: Float,
    ) : DrawingAction() {
        override fun contains(point: Offset, tolerance: Float): Boolean =
            distanceToSegment(point, start, end) <= tolerance + strokeWidth / 2f

        override fun translate(delta: Offset) = copy(start = start + delta, end = end + delta)
    }

    data class CurvedArrow(
        val points: List<Offset>,
        override val color: Color,
        override val strokeWidth: Float,
    ) : DrawingAction() {
        override fun contains(point: Offset, tolerance: Float): Boolean {
            val effective = tolerance + strokeWidth / 2f
            for (i in 0 until points.size - 1) {
                if (distanceToSegment(point, points[i], points[i + 1]) <= effective) return true
            }
            return false
        }

        override fun translate(delta: Offset) = copy(points = points.map { it + delta })
    }

    data class Text(
        val position: Offset,
        val text: String,
        override val color: Color,
        val textSize: Float,
        val rotation: Float = 0f,
        val scale: Float = 1f,
    ) : DrawingAction() {
        override val strokeWidth: Float get() = textSize
        override fun contains(point: Offset, tolerance: Float): Boolean {
            val approxWidth = text.length * textSize * 0.6f * scale
            val approxHeight = textSize * 1.2f * scale
            val centerX = position.x + approxWidth / 2f
            val centerY = position.y + approxHeight / 2f
            val dx = point.x - centerX
            val dy = point.y - centerY
            val cos = kotlin.math.cos(-rotation * Math.PI.toFloat() / 180f)
            val sin = kotlin.math.sin(-rotation * Math.PI.toFloat() / 180f)
            val localX = dx * cos - dy * sin
            val localY = dx * sin + dy * cos
            val halfW = approxWidth / 2f + tolerance
            val halfH = approxHeight / 2f + tolerance
            return localX in -halfW..halfW && localY in -halfH..halfH
        }

        override fun translate(delta: Offset) = copy(position = position + delta)

        fun bounds(): Rect {
            val w = text.length * textSize * 0.6f * scale
            val h = textSize * 1.2f * scale
            return Rect(position.x, position.y, position.x + w, position.y + h)
        }

        fun center(): Offset {
            val b = bounds()
            return Offset(b.center.x, b.center.y)
        }
    }
}

internal fun arrowheadEndpoints(
    start: Offset,
    end: Offset,
    strokeWidth: Float,
): Pair<Offset, Offset> {
    val headLen = max(strokeWidth * 4f, 20f)
    val headAngle = 30f * (Math.PI / 180f).toFloat()
    val angle = atan2(end.y - start.y, end.x - start.x)
    val left = Offset(
        end.x + headLen * cos(angle + Math.PI.toFloat() - headAngle),
        end.y + headLen * sin(angle + Math.PI.toFloat() - headAngle),
    )
    val right = Offset(
        end.x + headLen * cos(angle + Math.PI.toFloat() + headAngle),
        end.y + headLen * sin(angle + Math.PI.toFloat() + headAngle),
    )
    return left to right
}

internal fun curvedArrowheadEndpoints(
    points: List<Offset>,
    strokeWidth: Float,
): Pair<Offset, Offset>? {
    if (points.size < 2) return null
    val end = points.last()
    val before = points[points.size - 2]
    val headLen = max(strokeWidth * 4f, 20f)
    val headAngle = 30f * (Math.PI / 180f).toFloat()
    val angle = atan2(end.y - before.y, end.x - before.x)
    val left = Offset(
        end.x + headLen * cos(angle + Math.PI.toFloat() - headAngle),
        end.y + headLen * sin(angle + Math.PI.toFloat() - headAngle),
    )
    val right = Offset(
        end.x + headLen * cos(angle + Math.PI.toFloat() + headAngle),
        end.y + headLen * sin(angle + Math.PI.toFloat() + headAngle),
    )
    return left to right
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) return sqrt((point.x - start.x).pow2() + (point.y - start.y).pow2())
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lenSq).coerceIn(0f, 1f)
    val projX = start.x + t * dx
    val projY = start.y + t * dy
    return sqrt((point.x - projX).pow2() + (point.y - projY).pow2())
}

private fun Float.pow2(): Float = this * this
