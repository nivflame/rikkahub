package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.data.ai.tools.local.SUBAGENT_LOCAL_TOOL_NAMES
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingToolSearchPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val deferred = settings.deferredTools

    val allToolNames = remember(settings.mcpServers) {
        val mcpNames = settings.mcpServers.flatMap { server ->
            server.commonOptions.tools.map { "mcp__${server.commonOptions.name}__${it.name}" }
        }
        listOf("WebSearch", "scrape_web") +
            SUBAGENT_LOCAL_TOOL_NAMES +
            ALL_BROWSER_TOOL_NAMES +
            listOf("workspace_read_file", "workspace_write_file", "workspace_edit_file", "workspace_shell") +
            listOf("Subagent") +
            mcpNames
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("ToolSearch") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Select tools to defer. Deferred tools are hidden from the agent context and must be fetched via ToolSearch on demand, saving tokens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            val grouped = allToolNames.groupBy { name ->
                when {
                    name == "WebSearch" || name == "scrape_web" -> "Search"
                    name in SUBAGENT_LOCAL_TOOL_NAMES -> "Local"
                    name.startsWith("browser_") -> "Browser"
                    name.startsWith("workspace_") -> "Workspace"
                    name.startsWith("mcp__") -> "MCP"
                    else -> "Other"
                }
            }

            grouped.forEach { (category, names) ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                CardGroup {
                    names.forEach { name ->
                        item(
                            headlineContent = { Text(name) },
                            trailingContent = {
                                Switch(
                                    checked = name in deferred,
                                    onCheckedChange = { checked ->
                                        val newDeferred = if (checked) {
                                            deferred + name
                                        } else {
                                            deferred - name
                                        }
                                        vm.updateSettings(settings.copy(deferredTools = newDeferred))
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
