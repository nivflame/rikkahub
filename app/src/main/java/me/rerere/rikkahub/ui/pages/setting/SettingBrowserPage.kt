package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

private val BROWSER_TOOL_LABELS: Map<String, String> = mapOf(
    "browser_navigate" to "Navigate",
    "browser_get_text" to "Get page text",
    "browser_get_links" to "Get links",
    "browser_screenshot" to "Screenshot",
    "browser_interact" to "Interact",
    "browser_dom_snapshot" to "DOM snapshot",
    "browser_execute_script" to "Execute script",
    "browser_logs" to "Logs",
    "browser_close" to "Close",
)

@Composable
fun SettingBrowserPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Browser") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardGroup {
                ALL_BROWSER_TOOL_NAMES.forEach { toolName ->
                    item(
                        headlineContent = { Text(BROWSER_TOOL_LABELS[toolName] ?: toolName) },
                        supportingContent = { Text("Let the AI use $toolName") },
                        trailingContent = {
                            Switch(
                                checked = settings.enabledBrowserTools.contains(toolName),
                                onCheckedChange = {
                                    val next = if (it) {
                                        settings.enabledBrowserTools + toolName
                                    } else {
                                        settings.enabledBrowserTools - toolName
                                    }
                                    vm.updateSettings(settings.copy(enabledBrowserTools = next))
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
