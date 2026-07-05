package me.rerere.rikkahub.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.CursorPointer01
import me.rerere.hugeicons.stroke.Eraser
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Square
import me.rerere.hugeicons.stroke.Text as TextIcon

@Composable
fun ImageEditorToolbar(
    state: ImageEditorState,
    modifier: Modifier = Modifier,
) {
    if (state.tab != EditorTab.DRAW) return
    if (WindowInsets.isImeVisible) return

    LaunchedEffect(state.tool) {
        state.expandedCategory = ToolbarCategory.NONE
    }

    val showColor = state.tool != EditorTool.DRAG && state.tool != EditorTool.ERASER
    val showSize = state.tool != EditorTool.DRAG
    val showMode = state.tool == EditorTool.SHAPE
    val hasContext = showColor || showSize || showMode

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnimatedVisibility(
            visible = hasContext,
            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
        ) {
            ContextCapsule(state, showColor, showSize, showMode)
        }
        ToolsCapsule(state)
    }
}

@Composable
private fun ContextCapsule(
    state: ImageEditorState,
    showColor: Boolean,
    showSize: Boolean,
    showMode: Boolean,
) {
    CapsuleSurface {
        AnimatedContent(
            targetState = state.expandedCategory,
            transitionSpec = {
                if (initialState == ToolbarCategory.NONE) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else if (targetState == ToolbarCategory.NONE) {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "contextSwap",
        ) { category ->
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (category == ToolbarCategory.NONE) {
                    if (showColor) {
                        CategoryButton("Color", state.selectedColor.color) {
                            state.expandedCategory = ToolbarCategory.COLOR
                        }
                    }
                    if (showSize) {
                        CategoryButton("Size", null) {
                            state.expandedCategory = ToolbarCategory.SIZE
                        }
                    }
                    if (showMode) {
                        CategoryButton("Mode", null) {
                            state.expandedCategory = ToolbarCategory.MODE
                        }
                    }
                } else {
                    BackButton { state.expandedCategory = ToolbarCategory.NONE }
                    when (category) {
                        ToolbarCategory.COLOR -> {
                            PresetColor.entries.forEach { presetColor ->
                                ColorChip(
                                    color = presetColor.color,
                                    selected = state.selectedColor == presetColor,
                                    onClick = { state.selectedColor = presetColor },
                                )
                            }
                        }
                        ToolbarCategory.SIZE -> {
                            Text(
                                text = "${state.brushSize.toInt()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = state.brushSize,
                                onValueChange = { state.brushSize = it },
                                valueRange = 1f..30f,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                        ToolbarCategory.MODE -> {
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
                        }
                        ToolbarCategory.NONE -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsCapsule(state: ImageEditorState) {
    CapsuleSurface {
        Row(
            modifier = Modifier.wrapContentWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolIcon(HugeIcons.CursorPointer01, state.tool == EditorTool.DRAG) { state.tool = EditorTool.DRAG }
            ToolIcon(HugeIcons.PencilEdit01, state.tool == EditorTool.BRUSH) { state.tool = EditorTool.BRUSH }
            ToolIcon(HugeIcons.Square, state.tool == EditorTool.SHAPE) { state.tool = EditorTool.SHAPE }
            ToolIcon(HugeIcons.TextIcon, state.tool == EditorTool.TEXT) { state.tool = EditorTool.TEXT }
            ToolIcon(HugeIcons.Eraser, state.tool == EditorTool.ERASER) { state.tool = EditorTool.ERASER }
        }
    }
}

@Composable
private fun CapsuleSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .animateContentSize(animationSpec = tween(300))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun CategoryButton(label: String, indicatorColor: Color?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (indicatorColor != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Icon(
        imageVector = HugeIcons.ArrowLeft01,
        contentDescription = "Back",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(8.dp),
    )
}

@Composable
private fun ToolIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
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
private fun ToolLabel(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun ColorChip(color: Color, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}
