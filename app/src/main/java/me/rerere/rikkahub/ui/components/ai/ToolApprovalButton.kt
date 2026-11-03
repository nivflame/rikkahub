package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ShieldKey
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.ToolApprovalMode
import me.rerere.rikkahub.ui.components.ui.ToggleSurface

private val MODE_LABELS = mapOf(
    ToolApprovalMode.ASK to "Ask",
    ToolApprovalMode.YOLO to "YOLO",
)

private val MODE_DESCRIPTIONS = mapOf(
    ToolApprovalMode.ASK to "Tools follow the approvals set in the approval page",
    ToolApprovalMode.YOLO to "Auto-execute all tools, bypassing approvals",
)

@Composable
fun ToolApprovalButton(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by remember { mutableStateOf(false) }

    val yolo = assistant.toolApprovalMode == ToolApprovalMode.YOLO
    ToggleSurface(
        checked = !yolo,
        onClick = { showSheet = true },
        modifier = modifier,
        contentColor = if (yolo) MaterialTheme.colorScheme.error else Color.Unspecified,
    ) {
        Icon(
            imageVector = HugeIcons.ShieldKey,
            contentDescription = if (yolo) "Tool Permissions (YOLO)" else "Tool Permissions (Ask)",
            modifier = Modifier
                .padding(8.dp)
                .size(24.dp),
        )
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tool Permissions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                ToolApprovalMode.entries.forEach { mode ->
                    ToolApprovalModeCard(
                        title = MODE_LABELS.getValue(mode),
                        description = MODE_DESCRIPTIONS.getValue(mode),
                        selected = assistant.toolApprovalMode == mode,
                        onClick = {
                            onUpdateAssistant(assistant.copy(toolApprovalMode = mode))
                            showSheet = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolApprovalModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (selected) {
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.outlinedCardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
