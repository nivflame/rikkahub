package me.rerere.rikkahub.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

private val ArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Arrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
    ) {
        moveTo(4f, 20f)
        lineTo(20f, 4f)
        moveTo(20f, 4f)
        lineTo(14f, 4f)
        moveTo(20f, 4f)
        lineTo(20f, 10f)
    }.build()
}

@Composable
fun ImageEditorToolbar(
    state: ImageEditorState,
    modifier: Modifier = Modifier,
) {
    if (state.tab != EditorTab.DRAW) return
    val showColorAndSize = state.tool != EditorTool.DRAG && state.tool != EditorTool.ERASER
    val showSize = state.tool != EditorTool.DRAG
    val showShapeRow = state.tool == EditorTool.SHAPE
    val showModeRow = state.tool == EditorTool.SHAPE

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (showColorAndSize) {
                ScrollableRow {
                    PresetColor.entries.forEach { presetColor ->
                        ColorChip(
                            color = presetColor.color,
                            selected = state.selectedColor == presetColor,
                            onClick = { state.selectedColor = presetColor },
                        )
                    }
                }
            }
            if (showSize) {
                ScrollableRow {
                    BrushSize.entries.forEach { size ->
                        ToolLabel(
                            text = size.name,
                            selected = state.brushSize == size,
                            onClick = { state.brushSize = size },
                        )
                    }
                }
            }
            if (showModeRow) {
                ScrollableRow {
                    if (state.shapeType == ShapeType.ARROW) {
                        ToolLabel(
                            text = "Straight",
                            selected = state.shapeMode == ShapeMode.DRAG,
                            onClick = { state.shapeMode = ShapeMode.DRAG },
                        )
                        ToolLabel(
                            text = "Curved",
                            selected = state.shapeMode == ShapeMode.TAP,
                            onClick = { state.shapeMode = ShapeMode.TAP },
                        )
                    } else {
                        ToolLabel(
                            text = "Tap",
                            selected = state.shapeMode == ShapeMode.TAP,
                            onClick = { state.shapeMode = ShapeMode.TAP },
                        )
                        ToolLabel(
                            text = "Drag",
                            selected = state.shapeMode == ShapeMode.DRAG,
                            onClick = { state.shapeMode = ShapeMode.DRAG },
                        )
                    }
                }
            }
            if (showShapeRow) {
                ScrollableRow {
                    ToolLabel(
                        text = "Rect",
                        selected = state.shapeType == ShapeType.RECTANGLE,
                        onClick = { state.shapeType = ShapeType.RECTANGLE },
                    )
                    ToolLabel(
                        text = "Circle",
                        selected = state.shapeType == ShapeType.CIRCLE,
                        onClick = { state.shapeType = ShapeType.CIRCLE },
                    )
                    ToolLabel(
                        text = "Arrow",
                        selected = state.shapeType == ShapeType.ARROW,
                        onClick = { state.shapeType = ShapeType.ARROW },
                    )
                }
            }
            ScrollableRow {
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
private fun ScrollableRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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
