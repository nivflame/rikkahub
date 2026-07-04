package me.rerere.rikkahub.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Text

@Composable
fun ImageEditorToolbar(
    state: ImageEditorState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.tab == EditorTab.DRAW) {
                SecondaryRow(state)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            ToolRow(state)
        }
    }
}

@Composable
private fun ToolRow(state: ImageEditorState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.tab == EditorTab.CROP) {
            ToolLabel("Crop", state.tool == EditorTool.CROP)
        } else {
            ToolButton("Brush", state.tool == EditorTool.BRUSH) { state.tool = EditorTool.BRUSH }
            ToolButton("Line", state.tool == EditorTool.LINE) { state.tool = EditorTool.LINE }
            ToolButton("Rect", state.tool == EditorTool.RECTANGLE) { state.tool = EditorTool.RECTANGLE }
            ToolButton("Circle", state.tool == EditorTool.CIRCLE) { state.tool = EditorTool.CIRCLE }
            ToolButton("Arrow", state.tool == EditorTool.ARROW) { state.tool = EditorTool.ARROW }
            ToolButton("Text", state.tool == EditorTool.TEXT) { state.tool = EditorTool.TEXT }
            ToolButton("Erase", state.tool == EditorTool.ERASER) { state.tool = EditorTool.ERASER }
        }
    }
}

@Composable
private fun SecondaryRow(state: ImageEditorState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresetColor.entries.forEach { presetColor ->
            ColorChip(
                color = presetColor.color,
                selected = state.selectedColor == presetColor,
                onClick = { state.selectedColor = presetColor },
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrushSize.entries.forEach { size ->
            ToolLabel(
                text = size.name,
                selected = state.brushSize == size,
                onClick = { state.brushSize = size },
            )
        }
        if (state.tool == EditorTool.RECTANGLE || state.tool == EditorTool.CIRCLE) {
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

@Composable
private fun ToolButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun ToolLabel(
    text: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    } else {
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
    }
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ColorChip(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent),
            )
        }
    }
}
