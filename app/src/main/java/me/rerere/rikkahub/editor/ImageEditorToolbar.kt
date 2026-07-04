package me.rerere.rikkahub.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CursorPointer01
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Square
import me.rerere.hugeicons.stroke.Text

private val CircleIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Circle",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
    ) {
        moveTo(21f, 12f)
        curveTo(21f, 16.971f, 16.971f, 21f, 12f, 21f)
        curveTo(7.029f, 21f, 3f, 16.971f, 3f, 12f)
        curveTo(3f, 7.029f, 7.029f, 3f, 12f, 3f)
        curveTo(16.971f, 3f, 21f, 7.029f, 21f, 12f)
        close()
    }.build()
}

@Composable
fun ImageEditorToolbar(
    state: ImageEditorState,
    modifier: Modifier = Modifier,
) {
    if (state.tab != EditorTab.DRAW) return

    val showColors = state.tool != EditorTool.DRAG && state.tool != EditorTool.ERASER
    val showSizes = state.tool != EditorTool.DRAG
    val showShapeOptions = state.tool == EditorTool.SHAPE
    val hasContextRow = showColors || showSizes || showShapeOptions

    Surface(
        modifier = modifier
            .wrapContentWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (hasContextRow) {
                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showColors) {
                        PresetColor.entries.forEach { presetColor ->
                            ColorChip(
                                color = presetColor.color,
                                selected = state.selectedColor == presetColor,
                                onClick = { state.selectedColor = presetColor },
                            )
                        }
                    }
                    if (showColors && showSizes) {
                        VerticalDivider(
                            modifier = Modifier.size(height = 20.dp, width = 1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    if (showSizes) {
                        BrushSize.entries.forEach { size ->
                            ToolLabel(
                                text = size.name,
                                selected = state.brushSize == size,
                                onClick = { state.brushSize = size },
                            )
                        }
                    }
                    if (showSizes && showShapeOptions) {
                        VerticalDivider(
                            modifier = Modifier.size(height = 20.dp, width = 1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    if (showShapeOptions) {
                        ToolLabel(
                            text = if (state.shapeType == ShapeType.RECTANGLE) "Rect" else if (state.shapeType == ShapeType.CIRCLE) "Circle" else "Arrow",
                            selected = true,
                            onClick = {
                                state.shapeType = when (state.shapeType) {
                                    ShapeType.RECTANGLE -> ShapeType.CIRCLE
                                    ShapeType.CIRCLE -> ShapeType.ARROW
                                    ShapeType.ARROW -> ShapeType.RECTANGLE
                                }
                            },
                        )
                        VerticalDivider(
                            modifier = Modifier.size(height = 20.dp, width = 1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        if (state.shapeType == ShapeType.ARROW) {
                            ToolLabel(
                                text = if (state.shapeMode == ShapeMode.DRAG) "Straight" else "Curved",
                                selected = true,
                                onClick = {
                                    state.shapeMode = if (state.shapeMode == ShapeMode.DRAG) ShapeMode.TAP else ShapeMode.DRAG
                                },
                            )
                        } else {
                            ToolLabel(
                                text = if (state.shapeMode == ShapeMode.TAP) "Tap" else "Drag",
                                selected = true,
                                onClick = {
                                    state.shapeMode = if (state.shapeMode == ShapeMode.TAP) ShapeMode.DRAG else ShapeMode.TAP
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolIcon(HugeIcons.PencilEdit01, state.tool == EditorTool.BRUSH) { state.tool = EditorTool.BRUSH }
                ToolIcon(HugeIcons.Square, state.tool == EditorTool.SHAPE) { state.tool = EditorTool.SHAPE }
                ToolIcon(HugeIcons.Text, state.tool == EditorTool.TEXT) { state.tool = EditorTool.TEXT }
                ToolIcon(HugeIcons.Eraser, state.tool == EditorTool.ERASER) { state.tool = EditorTool.ERASER }
                ToolIcon(HugeIcons.CursorPointer01, state.tool == EditorTool.DRAG) { state.tool = EditorTool.DRAG }
            }
        }
    }
}

@Composable
private fun ToolIcon(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    )
}

@Composable
private fun ToolLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ColorChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent),
            )
        }
    }
}
