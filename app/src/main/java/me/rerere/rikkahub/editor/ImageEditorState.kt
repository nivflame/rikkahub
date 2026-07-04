package me.rerere.rikkahub.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

class ImageEditorState {
    var tab: EditorTab by mutableStateOf(EditorTab.CROP)
    var tool: EditorTool by mutableStateOf(EditorTool.BRUSH)
    var shapeMode: ShapeMode by mutableStateOf(ShapeMode.DRAG)
    var shapeType: ShapeType by mutableStateOf(ShapeType.RECTANGLE)
    var arrowMode: ArrowMode by mutableStateOf(ArrowMode.STRAIGHT)
    var selectedColor: PresetColor by mutableStateOf(PresetColor.RED)
    var brushSize: BrushSize by mutableStateOf(BrushSize.MEDIUM)
    var cropRect: Rect? by mutableStateOf(null)
    var cropApplied: Boolean by mutableStateOf(false)
    var drawImageRect: Rect? by mutableStateOf(null)

    private val undoStack = mutableStateListOf<DrawingAction>()
    private val redoStack = mutableStateListOf<DrawingAction>()

    val actions: List<DrawingAction> get() = undoStack.toList()
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun addAction(action: DrawingAction) {
        undoStack.add(action)
        redoStack.clear()
    }

    fun removeAction(action: DrawingAction) {
        undoStack.remove(action)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(undoStack.removeAt(undoStack.lastIndex))
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(redoStack.removeAt(redoStack.lastIndex))
    }

    fun clearAll() {
        undoStack.clear()
        redoStack.clear()
    }
}
