package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.local.SubagentPrompt
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingSubagentPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    var editing by remember { mutableStateOf<SubagentPrompt?>(null) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Subagent") },
                navigationIcon = { BackButton() },
                actions = {
                    FilledIconButton(onClick = {
                        editing = SubagentPrompt()
                    }) {
                        Icon(imageVector = HugeIcons.Add01, contentDescription = "Add")
                    }
                },
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
                text = "Configuration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            Surface(
                color = CustomColors.cardColorsOnSurfaceContainer.containerColor,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    FormItem(
                        label = { Text("Subagent model") },
                        description = { Text("The model used by all subagents.") },
                    ) {
                        ModelSelector(
                            modelId = settings.subagentModelId,
                            providers = settings.providers,
                            type = ModelType.CHAT,
                            onSelect = { vm.updateSettings(settings.copy(subagentModelId = it.id)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FormItem(
                        label = { Text("Max concurrent subagents") },
                        description = { Text("How many subagents can run at the same time.") },
                    ) {
                        var dragging by remember { mutableStateOf(false) }
                        val thumbInteraction = remember { MutableInteractionSource() }
                        Slider(
                            value = settings.subagentConcurrency.toFloat(),
                            onValueChange = {
                                dragging = true
                                vm.updateSettings(settings.copy(subagentConcurrency = it.toInt().coerceIn(1, 10)))
                            },
                            onValueChangeFinished = { dragging = false },
                            valueRange = 1f..10f,
                            steps = 8,
                            interactionSource = thumbInteraction,
                            thumb = { state ->
                                Box(contentAlignment = Alignment.TopCenter) {
                                    SliderDefaults.Thumb(
                                        interactionSource = thumbInteraction,
                                        sliderState = state,
                                    )
                                    if (dragging) {
                                        val bubbleOffset = with(LocalDensity.current) {
                                            IntOffset(0, (-38.dp).roundToPx())
                                        }
                                        Popup(
                                            alignment = Alignment.TopCenter,
                                            offset = bubbleOffset,
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "${settings.subagentConcurrency}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Text(
                text = "Profiles",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            settings.subagentPrompts.forEach { prompt ->
                SubagentPromptItem(
                    prompt = prompt,
                    onClick = { nav.navigate(Screen.SettingSubagentDetail(promptId = prompt.id.toString())) },
                )
            }
        }
    }

    editing?.let { prompt ->
        SubagentDetailEditDialog(
            prompt = prompt,
            title = if (settings.subagentPrompts.any { it.id == prompt.id }) "Edit subagent" else "New subagent",
            onConfirm = { updated ->
                val list = settings.subagentPrompts
                val newList = if (list.any { it.id == updated.id }) list.map { if (it.id == updated.id) updated else it } else list + updated
                vm.updateSettings(settings.copy(subagentPrompts = newList))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SubagentPromptItem(
    prompt: SubagentPrompt,
    onClick: () -> Unit,
) {
    Surface(
        color = CustomColors.cardColorsOnSurfaceContainer.containerColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val contentAlpha = if (prompt.enabled) 1f else 0.38f
            Text(
                text = prompt.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(contentAlpha),
            )
            Text(
                text = prompt.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(contentAlpha)
            )
        }
    }
}
