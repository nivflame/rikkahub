package me.rerere.rikkahub.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowTurnBackward
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Tick01
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    sourceUri: Uri,
    onResult: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val state = remember { ImageEditorState() }

    val originalBitmap = remember {
        context.contentResolver.openInputStream(sourceUri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }

    var croppedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    if (originalBitmap == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Failed to load image")
        }
        return
    }

    val displayBitmap = if (state.cropApplied) croppedBitmap ?: originalBitmap else originalBitmap

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.tab == EditorTab.CROP) "Crop" else "Edit",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(HugeIcons.Cancel01, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (state.tab == EditorTab.DRAW) {
                        IconButton(
                            onClick = { state.undo() },
                            enabled = state.canUndo,
                        ) {
                            Icon(
                                HugeIcons.ArrowTurnBackward,
                                contentDescription = "Undo",
                                tint = if (state.canUndo) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                    if (state.tab == EditorTab.CROP) {
                        TextButton(
                            onClick = {
                                state.cropRect?.let { rect ->
                                    croppedBitmap = cropBitmap(originalBitmap, rect, canvasSize)
                                    state.cropApplied = true
                                    state.tab = EditorTab.DRAW
                                }
                            },
                        ) {
                            Text("Next")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                val result = flattenBitmap(
                                    displayBitmap,
                                    state.actions,
                                    canvasSize,
                                )
                                val outputFile = File(
                                    context.cacheDir,
                                    "edit_output_${System.currentTimeMillis()}.png",
                                )
                                FileOutputStream(outputFile).use {
                                    result.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                                onResult(Uri.fromFile(outputFile))
                            },
                        ) {
                            Icon(HugeIcons.Tick01, contentDescription = null)
                        }
                    }
                },
            )
        },
        bottomBar = {
            ImageEditorToolbar(state)
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
        ) {
            if (state.tab == EditorTab.CROP) {
                CropOverlay(
                    bitmap = originalBitmap,
                    canvasSize = canvasSize,
                    onCropRectChange = { rect -> state.cropRect = rect },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                DrawingCanvas(
                    bitmap = displayBitmap,
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun cropBitmap(bitmap: Bitmap, rect: Rect, canvasSize: Size): Bitmap {
    if (canvasSize.width == 0f || canvasSize.height == 0f) return bitmap
    val scaleX = bitmap.width.toFloat() / canvasSize.width
    val scaleY = bitmap.height.toFloat() / canvasSize.height
    val left = (rect.left * scaleX).toInt().coerceIn(0, bitmap.width - 1)
    val top = (rect.top * scaleY).toInt().coerceIn(0, bitmap.height - 1)
    val right = (rect.right * scaleX).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = (rect.bottom * scaleY).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private fun flattenBitmap(
    bitmap: Bitmap,
    actions: List<DrawingAction>,
    canvasSize: Size,
): Bitmap {
    if (canvasSize.width == 0f || canvasSize.height == 0f) return bitmap
    if (actions.isEmpty()) return bitmap
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(result)
    val scaleX = result.width.toFloat() / canvasSize.width
    val scaleY = result.height.toFloat() / canvasSize.height
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    val erasePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    actions.forEach { action ->
        when (action) {
            is DrawingAction.Freehand -> {
                if (action.points.size < 2) return@forEach
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                for (i in 0 until action.points.size - 1) {
                    canvas.drawLine(
                        action.points[i].x * scaleX,
                        action.points[i].y * scaleY,
                        action.points[i + 1].x * scaleX,
                        action.points[i + 1].y * scaleY,
                        paint,
                    )
                }
            }
            is DrawingAction.Line -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawLine(
                    action.start.x * scaleX, action.start.y * scaleY,
                    action.end.x * scaleX, action.end.y * scaleY,
                    paint,
                )
            }
            is DrawingAction.Rectangle -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawRect(
                    action.rect.left * scaleX,
                    action.rect.top * scaleY,
                    action.rect.right * scaleX,
                    action.rect.bottom * scaleY,
                    paint,
                )
            }
            is DrawingAction.Circle -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawCircle(
                    action.center.x * scaleX,
                    action.center.y * scaleY,
                    action.radius * scaleX,
                    paint,
                )
            }
            is DrawingAction.Arrow -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawLine(
                    action.start.x * scaleX, action.start.y * scaleY,
                    action.end.x * scaleX, action.end.y * scaleY,
                    paint,
                )
                val (leftEnd, rightEnd) = arrowheadEndpoints(action.start, action.end, action.strokeWidth)
                canvas.drawLine(
                    action.end.x * scaleX, action.end.y * scaleY,
                    leftEnd.x * scaleX, leftEnd.y * scaleY,
                    paint,
                )
                canvas.drawLine(
                    action.end.x * scaleX, action.end.y * scaleY,
                    rightEnd.x * scaleX, rightEnd.y * scaleY,
                    paint,
                )
            }
            is DrawingAction.Text -> {
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = action.color.toArgb()
                paint.textSize = action.textSize * scaleX
                canvas.drawText(
                    action.text,
                    action.position.x * scaleX,
                    action.position.y * scaleY + action.textSize * scaleX,
                    paint,
                )
                paint.style = android.graphics.Paint.Style.STROKE
            }
            is DrawingAction.Erase -> {
                if (action.points.size < 2) return@forEach
                erasePaint.strokeWidth = action.strokeWidth * scaleX
                for (i in 0 until action.points.size - 1) {
                    canvas.drawLine(
                        action.points[i].x * scaleX,
                        action.points[i].y * scaleY,
                        action.points[i + 1].x * scaleX,
                        action.points[i + 1].y * scaleY,
                        erasePaint,
                    )
                }
            }
        }
    }
    return result
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)
