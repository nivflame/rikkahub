package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.hazeEffect
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Tools
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import org.koin.compose.koinInject

@Composable
fun ToolsButton(
    assistant: Assistant,
    settings: Settings,
    enableSearch: Boolean,
    onUpdateAssistant: (Assistant) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val mcpManager: McpManager = koinInject()
    var expanded by remember { mutableStateOf(false) }
    val enableBlur = settings.displaySetting.enableBlurEffect
    val menuShape = RoundedCornerShape(24.dp)
    val menuTint = MaterialTheme.colorScheme.surfaceContainerHigh

    val localCount = assistant.localTools.sumOf {
        if (it == LocalToolOption.Browser) settings.enabledBrowserTools.size else 1
    }
    val mcpCount = runCatching {
        mcpManager.getAllAvailableTools().count { it.first in assistant.mcpServers }
    }.getOrDefault(0)
    val total = localCount + mcpCount + (if (enableSearch) 1 else 0)

    Box(modifier) {
        BadgedBox(badge = { if (total > 0) Badge { Text(total.toString()) } }) {
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = HugeIcons.Tools, contentDescription = "Tools")
            }
        }
        if (expanded) {
            Popup(
                popupPositionProvider = AboveEndPopupProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier
                        .width(280.dp)
                        .clip(menuShape)
                        .then(
                            if (enableBlur) Modifier.hazeEffect(state = hazeState) {
                                blurEffect { style = HazeMaterials.thin(containerColor = menuTint) }
                            } else Modifier
                        ),
                    shape = menuShape,
                    color = if (enableBlur) Color.Transparent else menuTint,
                    tonalElevation = if (enableBlur) 0.dp else 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        SectionHeader("Local Tools")
                        localToolOptions().forEach { option ->
                            ToggleRow(
                                label = localToolLabel(option),
                                checked = option in assistant.localTools,
                                onCheckedChange = { checked ->
                                    val newTools = if (checked) {
                                        assistant.localTools + option
                                    } else {
                                        assistant.localTools - option
                                    }
                                    onUpdateAssistant(assistant.copy(localTools = newTools))
                                },
                            )
                        }
                        if (settings.mcpServers.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            SectionHeader("MCP Servers")
                            settings.mcpServers.forEach { server ->
                                ToggleRow(
                                    label = server.commonOptions.name.ifBlank { server.id.toString() },
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
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ToggleRow(
                            label = "Web Search",
                            checked = enableSearch,
                            onCheckedChange = onToggleSearch,
                        )
                    }
                }
            }
        }
    }
}

private object AboveEndPopupProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        popupSize: IntSize,
        layoutDirection: LayoutDirection,
    ): IntOffset {
        val x = (anchorBounds.right - popupSize.width).coerceAtLeast(0)
        val y = (anchorBounds.top - popupSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
