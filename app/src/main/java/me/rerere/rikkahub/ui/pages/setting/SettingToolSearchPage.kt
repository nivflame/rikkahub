package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiBrain01
import me.rerere.hugeicons.stroke.Bash
import me.rerere.hugeicons.stroke.BubbleChatQuestion
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FileAdd
import me.rerere.hugeicons.stroke.FileView
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.hugeicons.stroke.Search01
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

            val grouped = linkedMapOf(
                "Core" to coreToolNames,
                "Browser" to ALL_BROWSER_TOOL_NAMES,
            )
            val mcpGrouped = allToolNames.filter { it.startsWith("mcp__") }.groupBy { name ->
                name.substringAfter("mcp__").substringBefore("__")
            }
            mcpGrouped.forEach { (server, names) ->
                grouped["MCP: $server"] = names
            }

            val categoryDescriptions = mapOf(
                "Core" to "Tools available to all assistants",
                "Browser" to "Browser automation tools",
            )

            grouped.forEach { (category, names) ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                categoryDescriptions[category]?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                CardGroup {
                    names.forEach { name ->
                        item(
                            leadingContent = {
                                Icon(
                                    imageVector = toolIcon(name),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
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

private fun toolIcon(name: String) = when (name) {
    "Subagent" -> HugeIcons.AiBrain01
    "Bash" -> HugeIcons.Bash
    "Read" -> HugeIcons.FileView
    "Write" -> HugeIcons.FileAdd
    "Edit" -> HugeIcons.Edit01
    "AskQuestion" -> HugeIcons.BubbleChatQuestion
    "Skill" -> HugeIcons.Puzzle
    "WebSearch" -> HugeIcons.Search01
    "WebFetch" -> HugeIcons.Earth
    else -> HugeIcons.Earth
}
