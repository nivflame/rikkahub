package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingToolSearchPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val deferred = settings.deferredTools
    var selectedCategory by remember { mutableStateOf("All") }

    val coreToolNames = listOf(
        "Subagent",
        "Bash",
        "Read",
        "Write",
        "Edit",
        "AskQuestion",
        "Skill",
        "WebSearch",
        "WebFetch",
    )

    val allToolNames = remember(settings.mcpServers) {
        val mcpNames = settings.mcpServers.flatMap { server ->
            server.commonOptions.tools.map { "mcp__${server.commonOptions.name}__${it.name}" }
        }
        coreToolNames + ALL_BROWSER_TOOL_NAMES + mcpNames
    }

    val grouped = remember(allToolNames) {
        val map = linkedMapOf(
            "Core" to coreToolNames,
            "Browser" to ALL_BROWSER_TOOL_NAMES,
        )
        allToolNames.filter { it.startsWith("mcp__") }.groupBy { name ->
            name.substringAfter("mcp__").substringBefore("__")
        }.forEach { (server, names) ->
            map["MCP: $server"] = names
        }
        map
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
                .fillMaxSize()
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

            val categories = listOf("All") + grouped.keys.toList()
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) },
                    )
                }
            }

            val visibleGroups = if (selectedCategory == "All") grouped else linkedMapOf(selectedCategory to (grouped[selectedCategory] ?: emptyList()))
            visibleGroups.forEach { (category, names) ->
                CardGroup(
                    title = { Text(category) },
                ) {
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
