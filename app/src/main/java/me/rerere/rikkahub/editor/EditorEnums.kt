package me.rerere.rikkahub.editor

import androidx.compose.ui.graphics.Color

enum class EditorTab { CROP, DRAW }

enum class EditorTool { CROP, BRUSH, SHAPE, ARROW, TEXT, ERASER, DRAG }

enum class ShapeMode { TAP, DRAG }

enum class ShapeType { RECTANGLE, CIRCLE }

enum class ArrowMode { STRAIGHT, CURVED }

enum class BrushSize(val strokeWidth: Float) {
    THIN(4f),
    MEDIUM(8f),
    THICK(16f),
}

enum class PresetColor(val color: Color) {
    RED(Color.Red),
    YELLOW(Color.Yellow),
    GREEN(Color(0xFF4CAF50)),
    BLUE(Color(0xFF2196F3)),
    BLACK(Color.Black),
    WHITE(Color.White),
}
