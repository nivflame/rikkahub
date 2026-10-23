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
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingDeferredToolsPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val deferred = settings.deferredTools
    val navController = LocalNavController.current

    fun groupNames(group: String): List<String> = deferredGroupToolNames(group, settings.mcpServers)

    fun setGroupDeferred(group: String, on: Boolean) {
        val names = groupNames(group).toSet()
        val newDeferred = if (on) deferred + names else deferred - names
        vm.updateSettings(settings.copy(deferredTools = newDeferred))
    }

    fun CardGroupScope.groupCard(title: String, group: String) {
        val names = groupNames(group)
        val deferredCount = names.count { it in deferred }
        val supporting = when {
            names.isEmpty() -> "No tools"
            deferredCount == names.size -> "All ${names.size} tools deferred"
            deferredCount > 0 -> "$deferredCount of ${names.size} tools deferred"
            else -> "${names.size} tools"
        }
        item(
            onClick = { navController.navigate(Screen.SettingDeferredToolsDetail(group)) },
            headlineContent = { Text(title) },
            supportingContent = { Text(supporting) },
            trailingContent = {
                Switch(
                    checked = deferredCount == names.size && names.isNotEmpty(),
                    enabled = names.isNotEmpty(),
                    onCheckedChange = { setGroupDeferred(group, it) },
                )
            },
        )
    }
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Deferred Tools") },
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
                text = "Deferred tools are hidden from the agent context and are callable only inside Code Mode scripts, saving tokens. Core tools like Subagent, Bash, Read, Write, Edit, Skill, and AskQuestion cannot be deferred because they are primary tools the agent needs in every turn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            CardGroup(
                title = { Text("Core") },
            ) {
                item(
                    headlineContent = { Text("WebSearch") },
                    trailingContent = {
                        Switch(
                            checked = "WebSearch" in deferred,
                            onCheckedChange = {
                                val newDeferred = if (it) deferred + "WebSearch" else deferred - "WebSearch"
                                vm.updateSettings(settings.copy(deferredTools = newDeferred))
                            },
                        )
                    },
                )
                item(
                    headlineContent = { Text("WebFetch") },
                    trailingContent = {
                        Switch(
                            checked = "WebFetch" in deferred,
                            onCheckedChange = {
                                val newDeferred = if (it) deferred + "WebFetch" else deferred - "WebFetch"
                                vm.updateSettings(settings.copy(deferredTools = newDeferred))
                            },
                        )
                    },
                )
                groupCard("Browser", "Browser")
                groupCard("Device", "Device")
            }

            CardGroup(
                title = { Text("MCP Server") },
            ) {
                if (settings.mcpServers.isEmpty()) {
                    item(
                        headlineContent = { Text("No MCP servers configured") },
                        supportingContent = { Text("Add an MCP server to defer its tools") },
                    )
                } else {
                    settings.mcpServers.forEach { server ->
                        groupCard(server.commonOptions.name, "mcp:${server.commonOptions.name}")
                    }
                }
            }
        }
    }
}
