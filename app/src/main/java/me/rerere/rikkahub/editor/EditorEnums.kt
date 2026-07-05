package me.rerere.rikkahub.editor

import androidx.compose.ui.graphics.Color

enum class EditorTab { CROP, DRAW }

enum class EditorTool { CROP, BRUSH, SHAPE, TEXT, ERASER, DRAG }

enum class ToolbarCategory { NONE, COLOR, SIZE, MODE }

enum class ShapeMode { TAP, DRAG }

enum class ShapeType { RECTANGLE, CIRCLE, ARROW }

enum class PresetColor(val color: Color) {
    RED(Color.Red),
    YELLOW(Color.Yellow),
    GREEN(Color(0xFF4CAF50)),
    BLUE(Color(0xFF2196F3)),
    BLACK(Color.Black),
    WHITE(Color.White),
}
