package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

private val DEVICE_TOOL_NAMES = listOf(
    "device_state",
    "device_tap",
    "device_swipe",
    "device_drag",
    "device_type",
    "device_key",
)

internal fun deferredGroupToolNames(group: String, mcpServers: List<McpServerConfig>): List<String> = when {
    group == "Browser" -> ALL_BROWSER_TOOL_NAMES
    group == "Device" -> DEVICE_TOOL_NAMES
    group.startsWith("mcp:") -> {
        val serverName = group.removePrefix("mcp:")
        mcpServers
            .filter { it.commonOptions.name == serverName }
            .flatMap { server -> server.commonOptions.tools.map { "mcp__${server.commonOptions.name}__${it.name}" } }
    }
    else -> emptyList()
}

@Composable
fun SettingDeferredToolsDetailPage(group: String, vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val deferred = settings.deferredTools
    val toolNames = deferredGroupToolNames(group, settings.mcpServers)
    val title = when {
        group.startsWith("mcp:") -> group.removePrefix("mcp:")
        else -> group
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
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
            if (toolNames.isEmpty()) {
                Text(
                    text = "No tools available in this group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CardGroup(
                    title = { Text(title) },
                ) {
                    toolNames.forEach { name ->
                        item(
                            headlineContent = { Text(name.substringAfterLast("__").ifBlank { name }) },
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
