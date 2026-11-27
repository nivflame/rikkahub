package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01

@Composable
fun SwipeActionButtons(
    state: SwipeToDismissBoxState,
    onDelete: () -> Unit,
    deleteContentDescription: String,
    cancelContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge)
                .clickable { scope.launch { state.reset() } }
                .padding(horizontal = 16.dp),
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
                .fillMaxHeight()
                .width(72.dp)
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.extraLarge)
                .clickable {
                    onDelete()
                    scope.launch { state.reset() }
                }
                .padding(horizontal = 16.dp),
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
