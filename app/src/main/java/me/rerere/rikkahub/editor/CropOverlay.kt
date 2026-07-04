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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun CropOverlay(
    bitmap: android.graphics.Bitmap,
    onCropRectChange: (Rect, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val handleRadius = with(density) { 10.dp.toPx() }
    val handleTouchRadius = with(density) { 24.dp.toPx() }
    val borderWidth = with(density) { 2.dp.toPx() }

    var cropRect by remember { mutableStateOf<Rect?>(null) }
    var imageRect by remember { mutableStateOf<Rect?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val currentCrop = cropRect
                        val currentImage = imageRect
                        if (currentCrop != null && currentImage != null) {
                            val corners = listOf(
                                CropHandle.TopLeft to currentCrop.topLeft,
                                CropHandle.TopRight to currentCrop.topRight,
                                CropHandle.BottomLeft to currentCrop.bottomLeft,
                                CropHandle.BottomRight to currentCrop.bottomRight,
                            )
                            corners.forEach { (handle, pos) ->
                                if (distance(offset, pos) <= handleTouchRadius) {
                                    activeHandle = handle
                                    return@detectDragGestures
                                }
                            }
                        }
                        activeHandle = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val handle = activeHandle ?: return@detectDragGestures
                        val currentCrop = cropRect ?: return@detectDragGestures
                        val currentImage = imageRect ?: return@detectDragGestures
                        val pos = change.position
                        cropRect = when (handle) {
                            CropHandle.TopLeft -> Rect(
                                left = pos.x.coerceIn(currentImage.left, currentCrop.right),
                                top = pos.y.coerceIn(currentImage.top, currentCrop.bottom),
                                right = currentCrop.right,
                                bottom = currentCrop.bottom,
                            )
                            CropHandle.TopRight -> Rect(
                                left = currentCrop.left,
                                top = pos.y.coerceIn(currentImage.top, currentCrop.bottom),
                                right = pos.x.coerceIn(currentCrop.left, currentImage.right),
                                bottom = currentCrop.bottom,
                            )
                            CropHandle.BottomLeft -> Rect(
                                left = pos.x.coerceIn(currentImage.left, currentCrop.right),
                                top = currentCrop.top,
                                right = currentCrop.right,
                                bottom = pos.y.coerceIn(currentCrop.top, currentImage.bottom),
                            )
                            CropHandle.BottomRight -> Rect(
                                left = currentCrop.left,
                                top = currentCrop.top,
                                right = pos.x.coerceIn(currentCrop.left, currentImage.right),
                                bottom = pos.y.coerceIn(currentCrop.top, currentImage.bottom),
                            )
                        }
                        imageRect?.let { img -> onCropRectChange(cropRect!!, img) }
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                )
            },
    ) {
        if (imageRect == null) {
            val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val canvasAspect = size.width / size.height
            val fitRect = if (bitmapAspect > canvasAspect) {
                val scaledWidth = size.width
                val scaledHeight = size.width / bitmapAspect
                val top = (size.height - scaledHeight) / 2f
                Rect(0f, top, scaledWidth, top + scaledHeight)
            } else {
                val scaledHeight = size.height
                val scaledWidth = size.height * bitmapAspect
                val left = (size.width - scaledWidth) / 2f
                Rect(left, 0f, left + scaledWidth, scaledHeight)
            }
            imageRect = fitRect
            cropRect = Rect(
                fitRect.left + fitRect.width * 0.05f,
                fitRect.top + fitRect.height * 0.05f,
                fitRect.right - fitRect.width * 0.05f,
                fitRect.bottom - fitRect.height * 0.05f,
            )
            onCropRectChange(cropRect!!, fitRect)
        }

        val imgRect = imageRect!!
        val cr = cropRect!!

        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(
                imgRect.left.toInt(),
                imgRect.top.toInt(),
            ),
            dstSize = IntSize(
                imgRect.width.toInt(),
                imgRect.height.toInt(),
            ),
        )

        val dimPath = Path().apply {
            addRect(Rect(Offset.Zero, size))
            addRect(cr)
            fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
        }
        drawPath(dimPath, Color.Black.copy(alpha = 0.5f))

        drawRect(
            color = Color.White,
            topLeft = cr.topLeft,
            size = cr.size,
            style = Stroke(
                width = borderWidth,
                join = StrokeJoin.Miter,
                cap = StrokeCap.Square,
            ),
        )

        listOf(
            cr.topLeft,
            cr.topRight,
            cr.bottomLeft,
            cr.bottomRight,
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
