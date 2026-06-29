package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import org.koin.compose.koinInject

@Composable
fun ToolsButton(
    assistant: Assistant,
    settings: Settings,
    enableSearch: Boolean,
    onUpdateAssistant: (Assistant) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mcpManager: McpManager = koinInject()
    var showSheet by remember { mutableStateOf(false) }

    val allMcpTools = runCatching { mcpManager.getAllAvailableTools() }.getOrDefault(emptyList())
    val mcpCountByServer = allMcpTools.groupingBy { it.first }.eachCount()
    val localCount = assistant.localTools.sumOf {
        if (it == LocalToolOption.Browser) settings.enabledBrowserTools.size else 1
    }
    val mcpCount = allMcpTools.count { it.first in assistant.mcpServers }
    val total = localCount + mcpCount + (if (enableSearch) 1 else 0)

    Box(modifier = modifier) {
        ToggleSurface(
            checked = total > 0,
            onClick = { showSheet = true },
        ) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Tools,
                    contentDescription = "Tools",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (total > 0) {
            Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                Text(total.toString())
            }
        }
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
                    .fillMaxHeight(0.8f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )

                SectionHeader("Local Tools")
                localToolOptions().forEach { option ->
                    val count = if (option == LocalToolOption.Browser) settings.enabledBrowserTools.size else 1
                    ToolEntryRow(
                        label = localToolLabel(option),
                        count = count,
                        checked = option in assistant.localTools,
                        onCheckedChange = { checked ->
                            val newTools = if (checked) assistant.localTools + option else assistant.localTools - option
                            onUpdateAssistant(assistant.copy(localTools = newTools))
                        },
                    )
                }

                if (settings.mcpServers.isNotEmpty()) {
                    SectionHeader("MCP Servers")
                    settings.mcpServers.forEach { server ->
                        ToolEntryRow(
                            label = server.commonOptions.name.ifBlank { server.id.toString() },
                            count = mcpCountByServer[server.id] ?: 0,
                            checked = server.id in assistant.mcpServers,
                            onCheckedChange = { checked ->
                                val newServers = if (checked) {
                                    assistant.mcpServers + server.id
                                } else {
                                    assistant.mcpServers - server.id
                                }
                                onUpdateAssistant(assistant.copy(mcpServers = newServers))
                            },
                        )
                    }
                }

                SectionHeader("Web Search")
                ToolEntryRow(
                    label = "Web Search",
                    count = 1,
                    checked = enableSearch,
                    onCheckedChange = onToggleSearch,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ToolEntryRow(
    label: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
            }
            if (count > 1) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun localToolOptions(): List<LocalToolOption> = listOf(
    LocalToolOption.TimeInfo,
    LocalToolOption.Tts,
    LocalToolOption.AskQuestion,
    LocalToolOption.Browser,
    LocalToolOption.Subagent,
    LocalToolOption.Skill,
)

private fun localToolLabel(option: LocalToolOption): String = when (option) {
    LocalToolOption.TimeInfo -> "Time"
    LocalToolOption.Tts -> "TTS"
    LocalToolOption.AskQuestion -> "AskQuestion"
    LocalToolOption.Browser -> "Browser"
    LocalToolOption.Subagent -> "Subagent"
    LocalToolOption.Skill -> "Skill"
}
