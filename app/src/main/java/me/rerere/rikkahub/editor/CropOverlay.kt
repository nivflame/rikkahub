package me.rerere.rikkahub.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun CropOverlay(
    bitmap: android.graphics.Bitmap,
    canvasSize: Size,
    onCropRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleRadius = with(density) { 10.dp.toPx() }
    val handleTouchRadius = with(density) { 24.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }

    var cropRect by remember {
        mutableStateOf(
            Rect(
                left = canvasSize.width * 0.1f,
                top = canvasSize.height * 0.1f,
                right = canvasSize.width * 0.9f,
                bottom = canvasSize.height * 0.9f,
            ),
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(cropRect, canvasSize) {
                val corners = listOf(
                    CropHandle.TopLeft to cropRect.topLeft,
                    CropHandle.TopRight to cropRect.topRight,
                    CropHandle.BottomLeft to cropRect.bottomLeft,
                    CropHandle.BottomRight to cropRect.bottomRight,
                )
                detectDragGestures(
                    onDragStart = { offset ->
                        corners.forEach { (handle, pos) ->
                            if (distance(offset, pos) <= handleTouchRadius) {
                                activeHandle = handle
                                return@detectDragGestures
                            }
                        }
                        activeHandle = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val handle = activeHandle ?: return@detectDragGestures
                        val pos = change.position
                        cropRect = when (handle) {
                            CropHandle.TopLeft -> Rect(
                                left = pos.x.coerceIn(0f, cropRect.right),
                                top = pos.y.coerceIn(0f, cropRect.bottom),
                                right = cropRect.right,
                                bottom = cropRect.bottom,
                            )
                            CropHandle.TopRight -> Rect(
                                left = cropRect.left,
                                top = pos.y.coerceIn(0f, cropRect.bottom),
                                right = pos.x.coerceIn(cropRect.left, canvasSize.width),
                                bottom = cropRect.bottom,
                            )
                            CropHandle.BottomLeft -> Rect(
                                left = pos.x.coerceIn(0f, cropRect.right),
                                top = cropRect.top,
                                right = cropRect.right,
                                bottom = pos.y.coerceIn(cropRect.top, canvasSize.height),
                            )
                            CropHandle.BottomRight -> Rect(
                                left = cropRect.left,
                                top = cropRect.top,
                                right = pos.x.coerceIn(cropRect.left, canvasSize.width),
                                bottom = pos.y.coerceIn(cropRect.top, canvasSize.height),
                            )
                        }
                        onCropRectChange(cropRect)
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                )
            },
    ) {
        drawImage(
            image = bitmap.asImageBitmap(),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )

        val dimPath = Path().apply {
            addRect(Rect(Offset.Zero, size))
            addRect(cropRect)
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(dimPath, Color.Black.copy(alpha = 0.5f))

        drawRect(
            color = Color.White,
            topLeft = cropRect.topLeft,
            size = cropRect.size,
            style = Stroke(
                width = borderWidth,
                join = StrokeJoin.Miter,
                cap = StrokeCap.Square,
            ),
        )

        listOf(
            cropRect.topLeft,
            cropRect.topRight,
            cropRect.bottomLeft,
            cropRect.bottomRight,
        ).forEach { corner ->
            drawCircle(Color.White, handleRadius, corner)
            drawCircle(
                color = Color.Black,
                radius = handleRadius,
                center = corner,
                style = Stroke(width = borderWidth / 2f),
            )
        }
    }
}

private var activeHandle: CropHandle? = null

private enum class CropHandle { TopLeft, TopRight, BottomLeft, BottomRight }

private fun distance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}
