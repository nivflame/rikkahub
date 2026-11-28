package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01

class SwipeToRevealState(private val revealPx: Float) {
    val offset = Animatable(0f)
    val open: Boolean get() = offset.value <= -revealPx / 2f

    suspend fun settle() {
        val target = if (offset.value <= -revealPx / 2f) -revealPx else 0f
        offset.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow))
    }

    suspend fun close() {
        offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
    }

    suspend fun dragBy(deltaPx: Float) {
        val next = (offset.value + deltaPx).coerceIn(-revealPx, 0f)
        offset.snapTo(next)
    }
}

class SwipeRevealCoordinator {
    var activeState: SwipeToRevealState? = null
        private set

    fun open(state: SwipeToRevealState, scope: CoroutineScope) {
        val previous = activeState
        if (previous != null && previous !== state) {
            scope.launch { previous.close() }
        }
        activeState = state
    }

    fun release(state: SwipeToRevealState) {
        if (activeState === state) activeState = null
    }

    fun closeActive(scope: CoroutineScope) {
        val state = activeState ?: return
        activeState = null
        scope.launch { state.close() }
    }
}

@Composable
fun rememberSwipeRevealCoordinator(): SwipeRevealCoordinator = remember { SwipeRevealCoordinator() }

@Composable
fun rememberSwipeToRevealState(revealWidth: Dp = 120.dp): SwipeToRevealState {
    val revealPx = with(LocalDensity.current) { revealWidth.toPx() }
    return remember { SwipeToRevealState(revealPx) }
}

@Composable
fun SwipeToRevealBox(
    state: SwipeToRevealState,
    gesturesEnabled: Boolean,
    coordinator: SwipeRevealCoordinator? = null,
    modifier: Modifier = Modifier,
    backgroundContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(modifier) {
        backgroundContent()
        Box(
            modifier = Modifier
                .offset { IntOffset(state.offset.value.roundToInt(), 0) }
                .then(
                    if (gesturesEnabled) Modifier.pointerInput(state) {
                        detectHorizontalDragGestures(
                            onDragStart = { coordinator?.open(state, scope) },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                scope.launch { state.dragBy(amount) }
                            },
                            onDragEnd = { scope.launch { state.settle(); if (!state.open) coordinator?.release(state) } },
                            onDragCancel = { scope.launch { state.settle(); if (!state.open) coordinator?.release(state) } },
                        )
                    } else Modifier
                ),
        ) {
            content()
        }
    }
}

@Composable
fun SwipeActionButtons(
    state: SwipeToRevealState,
    onDelete: () -> Unit,
    deleteContentDescription: String,
    cancelContentDescription: String,
    onDuplicate: (() -> Unit)? = null,
    duplicateContentDescription: String = "Duplicate",
    coordinator: SwipeRevealCoordinator? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDuplicate != null) {
            Box(
                modifier = Modifier
                    .height(56.dp)
                    .width(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge)
                    .clickable {
                        onDuplicate()
                        scope.launch { state.close(); coordinator?.release(state) }
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = HugeIcons.Copy01,
                    contentDescription = duplicateContentDescription,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Box(
            modifier = Modifier
                .height(56.dp)
                .width(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .clickable { scope.launch { state.close(); coordinator?.release(state) } }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Cancel01,
                contentDescription = cancelContentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .height(56.dp)
                .width(48.dp)
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraLarge)
                .clickable {
                    onDelete()
                    scope.launch { state.close(); coordinator?.release(state) }
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = HugeIcons.Delete01,
                contentDescription = deleteContentDescription,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
