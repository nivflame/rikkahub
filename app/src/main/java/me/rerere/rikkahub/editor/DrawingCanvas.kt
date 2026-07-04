package me.rerere.rikkahub.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun DrawingCanvas(
    bitmap: android.graphics.Bitmap,
    state: ImageEditorState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = LocalTextMeasurer.current
    val strokeWidth = with(density) { state.brushSize.strokeWidth.dp.toPx() }
    val color = state.selectedColor.color
    val tapShapeSize = with(density) { 48.dp.toPx() }

    var previewAction by remember { mutableStateOf<DrawingAction?>(null) }
    var textInputState by remember { mutableStateOf<TextInputState?>(null) }
    var lineStart by remember { mutableStateOf<Offset?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.tool) {
                    when (state.tool) {
                        EditorTool.BRUSH -> detectDragGestures(
                            onDragStart = { offset ->
                                previewAction = DrawingAction.Freehand(
                                    points = listOf(offset),
                                    color = color,
                                    strokeWidth = strokeWidth,
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val current = previewAction as? DrawingAction.Freehand ?: return@detectDragGestures
                                previewAction = current.copy(points = current.points + change.position)
                            },
                            onDragEnd = {
                                previewAction?.let { state.addAction(it) }
                                previewAction = null
                            },
                            onDragCancel = { previewAction = null },
                        )

                        EditorTool.ERASER -> detectDragGestures(
                            onDragStart = { offset ->
                                val hit = state.actions.lastOrNull { it.contains(offset) }
                                if (hit != null && hit !is DrawingAction.Erase) {
                                    state.removeAction(hit)
                                    previewAction = null
                                } else {
                                    previewAction = DrawingAction.Erase(
                                        points = listOf(offset),
                                        strokeWidth = strokeWidth * 2f,
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val current = previewAction as? DrawingAction.Erase ?: run {
                                    val hit = state.actions.lastOrNull { it.contains(change.position) }
                                    if (hit != null && hit !is DrawingAction.Erase) {
                                        state.removeAction(hit)
                                    }
                                    return@detectDragGestures
                                }
                                previewAction = current.copy(points = current.points + change.position)
                            },
                            onDragEnd = {
                                if (previewAction is DrawingAction.Erase) {
                                    state.addAction(previewAction!!)
                                }
                                previewAction = null
                            },
                            onDragCancel = { previewAction = null },
                        )

                        EditorTool.ARROW -> detectDragGestures(
                            onDragStart = { offset ->
                                previewAction = DrawingAction.Arrow(
                                    start = offset,
                                    end = offset,
                                    color = color,
                                    strokeWidth = strokeWidth,
                                )
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val current = previewAction as? DrawingAction.Arrow ?: return@detectDragGestures
                                previewAction = current.copy(end = change.position)
                            },
                            onDragEnd = {
                                previewAction?.let {
                                    val arrow = it as DrawingAction.Arrow
                                    if (distance(arrow.start, arrow.end) > 10f) {
                                        state.addAction(it)
                                    }
                                }
                                previewAction = null
                            },
                            onDragCancel = { previewAction = null },
                        )

                        EditorTool.LINE -> detectTapGestures(
                            onTap = { offset ->
                                val start = lineStart
                                if (start == null) {
                                    lineStart = offset
                                    previewAction = DrawingAction.Line(
                                        start = offset,
                                        end = offset,
                                        color = color,
                                        strokeWidth = strokeWidth,
                                    )
                                } else {
                                    previewAction = null
                                    lineStart = null
                                    state.addAction(
                                        DrawingAction.Line(
                                            start = start,
                                            end = offset,
                                            color = color,
                                            strokeWidth = strokeWidth,
                                        ),
                                    )
                                }
                            },
                        )

                        EditorTool.RECTANGLE, EditorTool.CIRCLE -> {
                            if (state.shapeMode == ShapeMode.TAP) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val halfSize = tapShapeSize / 2f
                                        val action: DrawingAction = if (state.tool == EditorTool.RECTANGLE) {
                                            DrawingAction.Rectangle(
                                                rect = Rect(
                                                    offset.x - halfSize,
                                                    offset.y - halfSize,
                                                    offset.x + halfSize,
                                                    offset.y + halfSize,
                                                ),
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        } else {
                                            DrawingAction.Circle(
                                                center = offset,
                                                radius = halfSize,
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        }
                                        state.addAction(action)
                                    },
                                )
                            } else {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (state.tool == EditorTool.RECTANGLE) {
                                            previewAction = DrawingAction.Rectangle(
                                                rect = Rect(offset.x, offset.y, offset.x, offset.y),
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        } else {
                                            previewAction = DrawingAction.Circle(
                                                center = offset,
                                                radius = 0f,
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val start = (previewAction as? DrawingAction.Rectangle)?.rect?.topLeft
                                            ?: (previewAction as? DrawingAction.Circle)?.center
                                            ?: return@detectDragGestures
                                        if (state.tool == EditorTool.RECTANGLE) {
                                            previewAction = DrawingAction.Rectangle(
                                                rect = Rect(
                                                    minOf(start.x, change.position.x),
                                                    minOf(start.y, change.position.y),
                                                    maxOf(start.x, change.position.x),
                                                    maxOf(start.y, change.position.y),
                                                ),
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        } else {
                                            val radius = distance(start, change.position)
                                            previewAction = DrawingAction.Circle(
                                                center = start,
                                                radius = radius,
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        previewAction?.let { state.addAction(it) }
                                        previewAction = null
                                    },
                                    onDragCancel = { previewAction = null },
                                )
                            }
                        }

                        EditorTool.TEXT -> detectTapGestures(
                            onTap = { offset ->
                                textInputState = TextInputState(position = offset, text = "")
                            },
                        )

                        EditorTool.CROP -> {}
                    }
                },
        ) {
            drawImage(
                image = bitmap.asImageBitmap(),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )

            state.actions.forEach { action -> drawAction(action, textMeasurer) }

            previewAction?.let { drawAction(it, textMeasurer) }

            if (lineStart != null && state.tool == EditorTool.LINE) {
                drawCircle(
                    color = color,
                    radius = strokeWidth / 2f,
                    center = lineStart!!,
                )
            }
        }

        textInputState?.let { inputState ->
            BasicTextField(
                value = inputState.text,
                onValueChange = { newText ->
                    textInputState = inputState.copy(text = newText)
                },
                textStyle = TextStyle(
                    color = color,
                    fontSize = with(density) { 16.dp.toSp() },
                ),
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { _ ->
                                if (inputState.text.isNotBlank()) {
                                    state.addAction(
                                        DrawingAction.Text(
                                            position = inputState.position,
                                            text = inputState.text,
                                            color = color,
                                            textSize = with(density) { 16.dp.toPx() },
                                        ),
                                    )
                                }
                                textInputState = null
                            },
                        )
                    },
                cursorBrush = androidx.compose.ui.graphics.SolidColor(color),
            )
        }
    }
}

private data class TextInputState(
    val position: Offset,
    val text: String,
)

private fun DrawScope.drawAction(action: DrawingAction, textMeasurer: TextMeasurer) {
    when (action) {
        is DrawingAction.Freehand -> {
            if (action.points.size < 2) return
            val path = Path().apply {
                moveTo(action.points.first().x, action.points.first().y)
                for (i in 1 until action.points.size) {
                    lineTo(action.points[i].x, action.points[i].y)
                }
            }
            drawPath(
                path = path,
                color = action.color,
                style = Stroke(
                    width = action.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        is DrawingAction.Line -> {
            drawLine(
                color = action.color,
                start = action.start,
                end = action.end,
                strokeWidth = action.strokeWidth,
                cap = StrokeCap.Round,
            )
        }
        is DrawingAction.Rectangle -> {
            drawRect(
                color = action.color,
                topLeft = action.rect.topLeft,
                size = action.rect.size,
                style = Stroke(width = action.strokeWidth, join = StrokeJoin.Miter),
            )
        }
        is DrawingAction.Circle -> {
            drawCircle(
                color = action.color,
                radius = action.radius,
                center = action.center,
                style = Stroke(width = action.strokeWidth),
            )
        }
        is DrawingAction.Arrow -> {
            drawLine(
                color = action.color,
                start = action.start,
                end = action.end,
                strokeWidth = action.strokeWidth,
                cap = StrokeCap.Round,
            )
            val (leftEnd, rightEnd) = arrowheadEndpoints(action.start, action.end, action.strokeWidth)
            drawLine(action.color, action.end, leftEnd, action.strokeWidth, StrokeCap.Round)
            drawLine(action.color, action.end, rightEnd, action.strokeWidth, StrokeCap.Round)
        }
        is DrawingAction.Text -> {
            drawText(
                textMeasurer = textMeasurer,
                text = action.text,
                topLeft = action.position,
                style = TextStyle(
                    color = action.color,
                    fontSize = action.textSize.sp,
                ),
            )
        }
        is DrawingAction.Erase -> {
            if (action.points.size < 2) return
            val path = Path().apply {
                moveTo(action.points.first().x, action.points.first().y)
                for (i in 1 until action.points.size) {
                    lineTo(action.points[i].x, action.points[i].y)
                }
            }
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(
                    width = action.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
                blendMode = BlendMode.Clear,
            )
        }
    }
}

private fun distance(a: Offset, b: Offset): Float =
    sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

private fun minOf(a: Float, b: Float): Float = if (a < b) a else b
private fun maxOf(a: Float, b: Float): Float = if (a > b) a else b
