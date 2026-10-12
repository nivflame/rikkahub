package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.local.SubagentPrompt
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
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
                    FilledTonalIconButton(
                        onClick = {
                            editing = SubagentPrompt()
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CustomColors.listItemColors.containerColor
                        ),
                    ) {
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
            val modelListState = rememberModelListState(
                modelId = settings.subagentModelId,
                providers = settings.providers,
                type = ModelType.CHAT,
            )
            CardGroup(
                modifier = Modifier.fillMaxWidth(),
                title = { Text("Configuration") },
            ) {
                item(
                    onClick = { modelListState.open() },
                    headlineContent = { Text("Model") },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = modelListState.currentModel?.displayName
                                    ?: stringResource(R.string.model_list_select_model),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (modelListState.currentModel != null) {
                                IconButton(
                                    onClick = { vm.updateSettings(settings.copy(subagentModelId = null)) },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(HugeIcons.Cancel01, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            } else {
                                Icon(
                                    HugeIcons.ArrowRight01,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
                item(
                    headlineContent = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Max Concurrent")
                                Text(
                                    text = "${settings.subagentConcurrency}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = settings.subagentConcurrency.toFloat(),
                                onValueChange = {
                                    vm.updateSettings(settings.copy(subagentConcurrency = it.toInt().coerceIn(1, 10)))
                                },
                                valueRange = 1f..10f,
                                steps = 8,
                            )
                        }
                    },
                )
            }
            ModelListSheet(
                state = modelListState,
                onSelect = { vm.updateSettings(settings.copy(subagentModelId = it.id)) },
            )
            CardGroup(
                modifier = Modifier.fillMaxWidth(),
                title = { Text("Profiles") },
            ) {
                settings.subagentPrompts.forEach { prompt ->
                    val contentAlpha = if (prompt.enabled) 1f else 0.38f
                    item(
                        onClick = { nav.navigate(Screen.SettingSubagentDetail(promptId = prompt.id.toString())) },
                        headlineContent = {
                            Text(
                                text = prompt.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.alpha(contentAlpha),
                            )
                        },
                        supportingContent = {
                            Text(
                                text = prompt.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.alpha(contentAlpha)
                            )
                        },
                    )
                }
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
