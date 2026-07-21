package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

private val CORE_TOOL_NAMES: List<String> = listOf(
    "Bash",
    "Read",
    "Write",
    "Edit",
    "AskQuestion",
    "WebSearch",
    "WebFetch",
    "Subagent",
)

@Composable
fun SettingToolApprovalPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("All") }

    val toolGroups = remember(settings.mcpServers) {
        val mcpGroups = settings.mcpServers.associate { server ->
            "MCP: ${server.commonOptions.name}" to server.commonOptions.tools.map {
                "mcp__${server.commonOptions.name}__${it.name}"
            }
        }
        linkedMapOf(
            "Core" to CORE_TOOL_NAMES,
            "Browser" to ALL_BROWSER_TOOL_NAMES,
        ).apply { putAll(mcpGroups) }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Tools Approval") },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Toggle which tools require approval before execution. When enabled, the agent must ask before running the tool.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            val categories = remember(toolGroups) { listOf("All") + toolGroups.keys.toList() }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(categories.size) { index ->
                    val category = categories[index]
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                    )
                }
            }

            CardGroup(
                title = { Text("Audit") },
            ) {
                item(
                    onClick = { navController.navigate(Screen.ToolCallHistory) },
                    headlineContent = { Text("Tool Call History") },
                    supportingContent = { Text("View all recorded tool calls for security audit") },
                )
            }

            val visibleGroups = if (selectedCategory == "All") toolGroups else linkedMapOf(selectedCategory to (toolGroups[selectedCategory] ?: emptyList()))
            visibleGroups.forEach { (category, toolNames) ->
                CardGroup(
                    title = { Text(category) },
                ) {
                    toolNames.forEach { toolName ->
                        val needsApproval = settings.toolApprovalOverrides[toolName] ?: false
                        item(
                            headlineContent = { Text(toolName) },
                            supportingContent = {
                                Text(
                                    if (needsApproval) "Requires approval"
                                    else "Auto-executed",
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = needsApproval,
                                    onCheckedChange = { newValue ->
                                        scope.launch {
                                            settingsStore.update { current ->
                                                val updated = if (newValue) {
                                                    current.toolApprovalOverrides + (toolName to true)
                                                } else {
                                                    current.toolApprovalOverrides - toolName
                                                }
                                                current.copy(toolApprovalOverrides = updated)
                                            }
                                        }
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
