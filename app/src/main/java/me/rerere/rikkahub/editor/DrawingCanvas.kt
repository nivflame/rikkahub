package me.rerere.rikkahub.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tick01
import kotlin.math.sqrt

@Composable
fun DrawingCanvas(
    bitmap: android.graphics.Bitmap,
    state: ImageEditorState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val strokeWidth = with(density) { state.brushSize.strokeWidth.dp.toPx() }
    val color = state.selectedColor.color
    val tapShapeSize = with(density) { 48.dp.toPx() }

    var previewAction by remember { mutableStateOf<DrawingAction?>(null) }
    var textInputState by remember { mutableStateOf<TextInputState?>(null) }
    var dragTarget by remember { mutableStateOf<DrawingAction?>(null) }
    var dragLastPos by remember { mutableStateOf<Offset?>(null) }
    var dragHandle by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(state.tool) {
        if (state.tool != EditorTool.TEXT) {
            textInputState = null
        }
        if (state.tool != EditorTool.DRAG) {
            state.selectedAction = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f) {
                                zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                            }
                            if (zoom > 1f && panChange != Offset.Zero) {
                                pan += panChange
                            } else if (zoom <= 1f) {
                                pan = Offset.Zero
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = pan.x,
                    translationY = pan.y,
                )
                .pointerInput(state.tool, state.selectedColor, state.brushSize, state.shapeMode, state.shapeType) {
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
                                val hit = state.actions.lastOrNull { it.contains(offset, tolerance = 48f) }
                                if (hit != null) {
                                    state.removeAction(hit)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val hit = state.actions.lastOrNull { it.contains(change.position, tolerance = 48f) }
                                if (hit != null) {
                                    state.removeAction(hit)
                                }
                            },
                            onDragEnd = { },
                            onDragCancel = { },
                        )

                        EditorTool.SHAPE -> {
                            if (state.shapeType == ShapeType.ARROW) {
                                if (state.shapeMode == ShapeMode.DRAG) {
                                    detectDragGestures(
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
                                } else {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            previewAction = DrawingAction.CurvedArrow(
                                                points = listOf(offset),
                                                color = color,
                                                strokeWidth = strokeWidth,
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val current = previewAction as? DrawingAction.CurvedArrow ?: return@detectDragGestures
                                            previewAction = current.copy(points = current.points + change.position)
                                        },
                                        onDragEnd = {
                                            previewAction?.let {
                                                val curved = it as DrawingAction.CurvedArrow
                                                if (curved.points.size >= 2) {
                                                    state.addAction(it)
                                                }
                                            }
                                            previewAction = null
                                        },
                                        onDragCancel = { previewAction = null },
                                    )
                                }
                            } else if (state.shapeMode == ShapeMode.TAP) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        val halfSize = tapShapeSize / 2f
                                        val action: DrawingAction = if (state.shapeType == ShapeType.RECTANGLE) {
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
                                        if (state.shapeType == ShapeType.RECTANGLE) {
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
                                        if (state.shapeType == ShapeType.RECTANGLE) {
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

                        EditorTool.DRAG -> detectDragGestures(
                            onDragStart = { offset ->
                                val selected = state.selectedAction
                                if (selected is DrawingAction.Text) {
                                    val handlePos = getTextHandlePositions(selected)
                                    val hitHandle = handlePos.entries.firstOrNull { distance(offset, it.value) <= 30f }
                                    if (hitHandle != null) {
                                        dragHandle = hitHandle.key
                                        dragLastPos = offset
                                        return@detectDragGestures
                                    }
                                    if (selected.contains(offset, tolerance = 48f)) {
                                        dragTarget = selected
                                        dragLastPos = offset
                                        return@detectDragGestures
                                    }
                                }
                                val hit = state.actions.lastOrNull { it.contains(offset, tolerance = 48f) }
                                if (hit != null) {
                                    state.selectedAction = hit
                                } else {
                                    state.selectedAction = null
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val handle = dragHandle
                                val target = dragTarget
                                val lastPos = dragLastPos
                                if (handle != null) {
                                    val selected = state.selectedAction as? DrawingAction.Text ?: return@detectDragGestures
                                    val center = selected.center()
                                    when (handle) {
                                        "rot" -> {
                                            val angle = Math.atan2(
                                                (change.position.y - center.y).toDouble(),
                                                (change.position.x - center.x).toDouble(),
                                            ).toFloat() * 180f / Math.PI.toFloat() + 90f
                                            updateSelectedText(state, rotation = angle)
                                        }
                                        else -> {
                                            val newDist = distance(center, change.position)
                                            val baseDist = distance(center, getTextHandlePositions(selected.copy(scale = 1f, rotation = 0f))[handle]!!)
                                            val newScale = (newDist / baseDist).coerceIn(0.5f, 5f)
                                            updateSelectedText(state, scale = newScale)
                                        }
                                    }
                                    return@detectDragGestures
                                }
                                if (target != null && lastPos != null) {
                                    val delta = change.position - lastPos
                                    val index = state.actions.indexOf(target)
                                    if (index >= 0) {
                                        val translated = target.translate(delta)
                                        state.removeAction(target)
                                        state.addAction(translated)
                                        dragTarget = translated
                                        state.selectedAction = translated
                                    }
                                    dragLastPos = change.position
                                }
                            },
                            onDragEnd = {
                                dragTarget = null
                                dragLastPos = null
                                dragHandle = null
                            },
                            onDragCancel = {
                                dragTarget = null
                                dragLastPos = null
                                dragHandle = null
                            },
                        )

                        EditorTool.CROP -> {}
                    }
                },
        ) {
            if (state.drawImageRect == null) {
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
                state.drawImageRect = fitRect
            }

            val imgRect = state.drawImageRect!!
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    imgRect.left.toInt(),
                    imgRect.top.toInt(),
                ),
                dstSize = IntSize(
                    imgRect.width.toInt(),
                    imgRect.height.toInt(),
                ),
            )

            state.actions.forEach { action -> drawAction(action, textMeasurer) }

            previewAction?.let { drawAction(it, textMeasurer) }

            val selected = state.selectedAction
            if (selected is DrawingAction.Text) {
                val handles = getTextHandlePositions(selected)
                val handleColor = Color.White
                val borderColor = Color(0xFF2196F3)
                val handleR = 8f
                drawLine(borderColor, handles["tl"]!!, handles["tr"]!!, 2f)
                drawLine(borderColor, handles["tr"]!!, handles["br"]!!, 2f)
                drawLine(borderColor, handles["br"]!!, handles["bl"]!!, 2f)
                drawLine(borderColor, handles["bl"]!!, handles["tl"]!!, 2f)
                val topCenter = Offset(
                    (handles["tl"]!!.x + handles["tr"]!!.x) / 2f,
                    (handles["tl"]!!.y + handles["tr"]!!.y) / 2f,
                )
                drawLine(borderColor, topCenter, handles["rot"]!!, 2f)
                handles.forEach { (_, pos) ->
                    drawCircle(handleColor, handleR, pos)
                    drawCircle(borderColor, handleR, pos, style = Stroke(2f))
                }
            }
        }

        textInputState?.let { inputState ->
            Row(
                modifier = Modifier
                    .offset(
                        x = with(density) { inputState.position.x.toDp() },
                        y = with(density) { inputState.position.y.toDp() },
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicTextField(
                    value = inputState.text,
                    onValueChange = { newText ->
                        textInputState = inputState.copy(text = newText)
                    },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = with(density) { 16.dp.toSp() },
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier
                        .widthIn(min = 100.dp)
                        .padding(4.dp),
                )
                IconButton(
                    onClick = {
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
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = HugeIcons.Tick01,
                        contentDescription = "Confirm text",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
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
        is DrawingAction.CurvedArrow -> {
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
            val headEnds = curvedArrowheadEndpoints(action.points, action.strokeWidth) ?: return
            val end = action.points.last()
            drawLine(action.color, end, headEnds.first, action.strokeWidth, StrokeCap.Round)
            drawLine(action.color, end, headEnds.second, action.strokeWidth, StrokeCap.Round)
        }
        is DrawingAction.Text -> {
            val center = action.center()
            rotate(action.rotation, center) {
                scale(action.scale, action.scale, center) {
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
            }
        }
    }
}

private fun getTextHandlePositions(text: DrawingAction.Text): Map<String, Offset> {
    val b = text.bounds()
    val center = text.center()
    val rotOffset = 40f
    val raw = mapOf(
        "tl" to Offset(b.left, b.top),
        "tr" to Offset(b.right, b.top),
        "bl" to Offset(b.left, b.bottom),
        "br" to Offset(b.right, b.bottom),
        "rot" to Offset(b.center.x, b.top - rotOffset),
    )
    val rad = text.rotation * Math.PI.toFloat() / 180f
    val cos = kotlin.math.cos(rad)
    val sin = kotlin.math.sin(rad)
    return raw.mapValues { (_, pos) ->
        val dx = pos.x - center.x
        val dy = pos.y - center.y
        Offset(center.x + dx * cos - dy * sin, center.y + dx * sin + dy * cos)
    }
}

private fun updateSelectedText(
    state: ImageEditorState,
    rotation: Float? = null,
    scale: Float? = null,
) {
    val current = state.selectedAction as? DrawingAction.Text ?: return
    val updated = current.copy(
        rotation = rotation ?: current.rotation,
        scale = scale ?: current.scale,
    )
    val index = state.actions.indexOf(current)
    if (index >= 0) {
        state.removeAction(current)
        state.addAction(updated)
        state.selectedAction = updated
    }
}

private fun distance(a: Offset, b: Offset): Float =
    sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

private fun minOf(a: Float, b: Float): Float = if (a < b) a else b
private fun maxOf(a: Float, b: Float): Float = if (a > b) a else b
