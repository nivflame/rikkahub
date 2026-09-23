package me.rerere.rikkahub.ui.pages.setting

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.ai.tools.local.ALL_DEVICE_TOOL_NAMES
import me.rerere.rikkahub.service.ScreenshotService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingDevicePage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Device") },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Device Tools",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Text(
                text = "Control this device with taps, swipes, typing and screenshots. Requires the accessibility service, and works on Android 11+.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            CardGroup {
                item(
                    headlineContent = { Text("Accessibility service") },
                    supportingContent = {
                        Text(if (ScreenshotService.isEnabled()) "On" else "Off, tap to open settings")
                    },
                    trailingContent = {
                        Switch(
                            checked = ScreenshotService.isEnabled(),
                            onCheckedChange = { _ ->
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                        )
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CardGroup {
                    ALL_DEVICE_TOOL_NAMES.forEach { toolName ->
                        item(
                            headlineContent = { Text(toolName) },
                            trailingContent = {
                                Switch(
                                    checked = settings.enabledDeviceTools.contains(toolName),
                                    onCheckedChange = { enabled ->
                                        val next = if (enabled) {
                                            settings.enabledDeviceTools + toolName
                                        } else {
                                            settings.enabledDeviceTools - toolName
                                        }
                                        vm.updateSettings(settings.copy(enabledDeviceTools = next))
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
