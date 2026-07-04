package me.rerere.rikkahub.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
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
    var imageRect by remember { mutableStateOf<Rect?>(null) }

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
                                    val imgRect = imageRect
                                    croppedBitmap = if (imgRect != null) {
                                        cropBitmap(originalBitmap, rect, imgRect)
                                    } else {
                                        originalBitmap
                                    }
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
                                    state.drawImageRect,
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
                    onCropRectChange = { rect, imgRect ->
                        state.cropRect = rect
                        imageRect = imgRect
                    },
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

private fun cropBitmap(bitmap: Bitmap, rect: Rect, imageRect: Rect): Bitmap {
    if (imageRect.width == 0f || imageRect.height == 0f) return bitmap
    val scaleX = bitmap.width.toFloat() / imageRect.width
    val scaleY = bitmap.height.toFloat() / imageRect.height
    val left = ((rect.left - imageRect.left) * scaleX).toInt().coerceIn(0, bitmap.width - 1)
    val top = ((rect.top - imageRect.top) * scaleY).toInt().coerceIn(0, bitmap.height - 1)
    val right = ((rect.right - imageRect.left) * scaleX).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ((rect.bottom - imageRect.top) * scaleY).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}

private fun flattenBitmap(
    bitmap: Bitmap,
    actions: List<DrawingAction>,
    imageRect: Rect?,
): Bitmap {
    if (imageRect == null || imageRect.width == 0f || imageRect.height == 0f) return bitmap
    if (actions.isEmpty()) return bitmap
    val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(result)
    val scaleX = result.width.toFloat() / imageRect.width
    val scaleY = result.height.toFloat() / imageRect.height
    val offsetX = imageRect.left
    val offsetY = imageRect.top
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    actions.forEach { action ->
        when (action) {
            is DrawingAction.Freehand -> {
                if (action.points.size < 2) return@forEach
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                for (i in 0 until action.points.size - 1) {
                    canvas.drawLine(
                        (action.points[i].x - offsetX) * scaleX,
                        (action.points[i].y - offsetY) * scaleY,
                        (action.points[i + 1].x - offsetX) * scaleX,
                        (action.points[i + 1].y - offsetY) * scaleY,
                        paint,
                    )
                }
            }
            is DrawingAction.Line -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawLine(
                    (action.start.x - offsetX) * scaleX, (action.start.y - offsetY) * scaleY,
                    (action.end.x - offsetX) * scaleX, (action.end.y - offsetY) * scaleY,
                    paint,
                )
            }
            is DrawingAction.Rectangle -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawRect(
                    (action.rect.left - offsetX) * scaleX,
                    (action.rect.top - offsetY) * scaleY,
                    (action.rect.right - offsetX) * scaleX,
                    (action.rect.bottom - offsetY) * scaleY,
                    paint,
                )
            }
            is DrawingAction.Circle -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawCircle(
                    (action.center.x - offsetX) * scaleX,
                    (action.center.y - offsetY) * scaleY,
                    action.radius * scaleX,
                    paint,
                )
            }
            is DrawingAction.Arrow -> {
                paint.color = action.color.toArgb()
                paint.strokeWidth = action.strokeWidth * scaleX
                canvas.drawLine(
                    (action.start.x - offsetX) * scaleX, (action.start.y - offsetY) * scaleY,
                    (action.end.x - offsetX) * scaleX, (action.end.y - offsetY) * scaleY,
                    paint,
                )
                val (leftEnd, rightEnd) = arrowheadEndpoints(action.start, action.end, action.strokeWidth)
                canvas.drawLine(
                    (action.end.x - offsetX) * scaleX, (action.end.y - offsetY) * scaleY,
                    (leftEnd.x - offsetX) * scaleX, (leftEnd.y - offsetY) * scaleY,
                    paint,
                )
                canvas.drawLine(
                    (action.end.x - offsetX) * scaleX, (action.end.y - offsetY) * scaleY,
                    (rightEnd.x - offsetX) * scaleX, (rightEnd.y - offsetY) * scaleY,
                    paint,
                )
            }
            is DrawingAction.Text -> {
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = action.color.toArgb()
                paint.textSize = action.textSize * scaleX
                canvas.drawText(
                    action.text,
                    (action.position.x - offsetX) * scaleX,
                    (action.position.y - offsetY) * scaleY + action.textSize * scaleX,
                    paint,
                )
                paint.style = android.graphics.Paint.Style.STROKE
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
