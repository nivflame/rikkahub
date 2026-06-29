package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.data.ai.tools.local.SUBAGENT_LOCAL_TOOL_NAMES
import me.rerere.rikkahub.data.ai.tools.local.SubagentPrompt
import me.rerere.rikkahub.data.ai.tools.local.buildSubagentTool
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingSubagentPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SubagentPrompt?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editSystem by remember { mutableStateOf("") }

    val toolNames = remember(settings.mcpServers) {
        SUBAGENT_LOCAL_TOOL_NAMES + ALL_BROWSER_TOOL_NAMES + settings.mcpServers.flatMap { server ->
            server.commonOptions.tools.filter { it.enable }.map { "mcp__${server.commonOptions.name}__${it.name}" }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Subagent") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = {
                        editing = SubagentPrompt()
                        editName = ""
                        editDesc = ""
                        editSystem = ""
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                val subagentTool = buildSubagentTool(settings)
                ToolSchemaCard(
                    tool = subagentTool.copy(description = settings.toolDescriptions["Subagent"] ?: subagentTool.description),
                    modifier = Modifier.padding(12.dp),
                    onEditDescription = { desc ->
                        vm.updateSettings(settings.copy(toolDescriptions = settings.toolDescriptions + ("Subagent" to desc)))
                    },
                )
            }
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Subagent model", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The model used by all subagents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ModelSelector(
                        modelId = settings.subagentModelId,
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        onSelect = { vm.updateSettings(settings.copy(subagentModelId = it.id)) }
                    )
                }
            }
            Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Max concurrent subagents", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "How many subagents can run at the same time: ${settings.subagentConcurrency}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.subagentConcurrency.toFloat(),
                        onValueChange = {
                            vm.updateSettings(settings.copy(subagentConcurrency = it.toInt().coerceIn(1, 10)))
                        },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }
            Text("Subagent prompts", style = MaterialTheme.typography.titleMedium)
            settings.subagentPrompts.forEach { prompt ->
                SubagentPromptItem(
                    prompt = prompt,
                    toolNames = toolNames,
                    onChange = { updated ->
                        vm.updateSettings(
                            settings.copy(
                                subagentPrompts = settings.subagentPrompts.map { if (it.id == updated.id) updated else it }
                            )
                        )
                    },
                    onRemove = {
                        vm.updateSettings(
                            settings.copy(subagentPrompts = settings.subagentPrompts.filter { it.id != prompt.id })
                        )
                    },
                    onEdit = {
                        editing = prompt
                        editName = prompt.name
                        editDesc = prompt.description
                        editSystem = prompt.systemPrompt
                    }
                )
            }
        }
    }

    editing?.let { prompt ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (settings.subagentPrompts.any { it.id == prompt.id }) "Edit subagent" else "New subagent") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editSystem,
                        onValueChange = { editSystem = it },
                        label = { Text("System prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = prompt.copy(name = editName.trim(), description = editDesc.trim(), systemPrompt = editSystem)
                    val list = settings.subagentPrompts
                    val newList = if (list.any { it.id == prompt.id }) list.map { if (it.id == prompt.id) updated else it } else list + updated
                    vm.updateSettings(settings.copy(subagentPrompts = newList))
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SubagentPromptItem(
    prompt: SubagentPrompt,
    toolNames: List<String>,
    onChange: (SubagentPrompt) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = prompt.name.ifBlank { "(unnamed)" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(imageVector = HugeIcons.Edit01, contentDescription = "Edit")
                }
                IconButton(onClick = onRemove) {
                    Icon(imageVector = HugeIcons.Delete01, contentDescription = "Remove")
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) HugeIcons.ArrowUp01 else HugeIcons.ArrowDown01,
                        contentDescription = "Expand"
                    )
                }
            }
            Text(
                text = prompt.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (expanded) {
                Text("Tools", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                toolNames.forEach { name ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(
                            checked = name in prompt.enabledTools,
                            onCheckedChange = { enabled ->
                                val next = if (enabled) prompt.enabledTools + name else prompt.enabledTools - name
                                onChange(prompt.copy(enabledTools = next))
                            }
                        )
                    }
                }
            }
        }
    }
}
