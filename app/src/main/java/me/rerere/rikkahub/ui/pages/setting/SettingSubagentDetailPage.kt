package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PencilEdit02
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.tools.local.ALL_BROWSER_TOOL_NAMES
import me.rerere.rikkahub.data.ai.tools.local.SubagentPrompt
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SettingSubagentDetailPage(
    promptId: Uuid,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val promptState = remember { mutableStateOf(settings.subagentPrompts.find { it.id == promptId }) }
    settings.subagentPrompts.find { it.id == promptId }?.let { promptState.value = it }
    val prompt = promptState.value ?: return
    var showEditDialog by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    fun updatePrompt(updated: SubagentPrompt) {
        vm.updateSettings(
            settings.copy(
                subagentPrompts = settings.subagentPrompts.map { if (it.id == updated.id) updated else it }
            )
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(prompt.name.ifBlank { "(unnamed)" }) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(imageVector = HugeIcons.PencilEdit02, contentDescription = "Edit")
                    }
                    if (!prompt.isBuiltIn) {
                        IconButton(onClick = { showRemoveConfirm = true }) {
                            Icon(imageVector = HugeIcons.Delete01, contentDescription = "Remove")
                        }
                    }
                },
                colors = CustomColors.topBarColors,
            )
        },
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
            FormItem(
                label = { Text("Enable") },
                tail = {
                    Switch(
                        checked = prompt.enabled,
                        onCheckedChange = { checked -> updatePrompt(prompt.copy(enabled = checked)) },
                    )
                },
            )
            CardGroup {
                item(
                    onClick = { nav.navigate(Screen.SettingSubagentDetailInjections(promptId = prompt.id.toString())) },
                    headlineContent = { Text("Prompt Injection") },
                )
                item(
                    onClick = { nav.navigate(Screen.SettingSubagentDetailTools(promptId = prompt.id.toString())) },
                    headlineContent = { Text("Tools") },
                )
            }
        }
    }

    if (showEditDialog) {
        SubagentDetailEditDialog(
            prompt = prompt,
            title = "Edit subagent",
            onConfirm = { updated ->
                updatePrompt(updated)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove subagent") },
            text = { Text("This will remove the subagent and its configuration. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirm = false
                        vm.updateSettings(
                            settings.copy(subagentPrompts = settings.subagentPrompts.filter { it.id != prompt.id })
                        )
                        nav.popBackStack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun SubagentDetailEditDialog(
    prompt: SubagentPrompt,
    title: String,
    onConfirm: (SubagentPrompt) -> Unit,
    onDismiss: () -> Unit,
) {
    var editName by remember(prompt.id) { mutableStateOf(prompt.name) }
    var editDesc by remember(prompt.id) { mutableStateOf(prompt.description) }
    var editSystem by remember(prompt.id) { mutableStateOf(prompt.systemPrompt) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = HugeIcons.Cancel01, contentDescription = "Close")
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        onConfirm(prompt.copy(name = editName.trim(), description = editDesc.trim(), systemPrompt = editSystem))
                    }) { Text("Save") }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
            }
        }
    }
}

@Composable
fun SettingSubagentDetailInjectionsPage(
    promptId: Uuid,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prompt = settings.subagentPrompts.find { it.id == promptId } ?: return
    val modeInjections = settings.modeInjections

    fun updatePrompt(updated: SubagentPrompt) {
        vm.updateSettings(
            settings.copy(
                subagentPrompts = settings.subagentPrompts.map { if (it.id == updated.id) updated else it }
            )
        )
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Prompt Injection") },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
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
            if (modeInjections.isEmpty()) {
                Text(
                    text = "No mode injections configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                modeInjections.forEach { injection ->
                    Surface(
                        color = CustomColors.cardColorsOnSurfaceContainer.containerColor,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = injection.name.ifBlank { "(unnamed)" },
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = injection.id in prompt.modeInjectionIds,
                                onCheckedChange = { enabled ->
                                    val next = if (enabled) prompt.modeInjectionIds + injection.id else prompt.modeInjectionIds - injection.id
                                    updatePrompt(prompt.copy(modeInjectionIds = next))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSubagentDetailToolsPage(
    promptId: Uuid,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val prompt = settings.subagentPrompts.find { it.id == promptId } ?: return

    fun updatePrompt(updated: SubagentPrompt) {
        vm.updateSettings(
            settings.copy(
                subagentPrompts = settings.subagentPrompts.map { if (it.id == updated.id) updated else it }
            )
        )
    }

    val toolGroups = remember(settings.mcpServers) {
        val core = listOf(
            "Subagent",
            "Bash",
            "Read",
            "Write",
            "Edit",
            "AskQuestion",
            "Skill",
            "WebSearch",
            "WebFetch",
            "ToolSearch",
        )
        val mcpGroups = settings.mcpServers.associate { server ->
            "MCP: ${server.commonOptions.name}" to server.commonOptions.tools.map { "mcp__${server.commonOptions.name}__${it.name}" }
        }
        linkedMapOf(
            "Core" to core,
            "Browser" to ALL_BROWSER_TOOL_NAMES,
        ).apply { putAll(mcpGroups) }
    }
    var selectedToolCategory by remember { mutableStateOf(toolGroups.keys.first()) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Tools") },
                navigationIcon = { BackButton() },
                colors = CustomColors.topBarColors,
            )
        },
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
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(toolGroups.keys.toList()) { category ->
                    FilterChip(
                        selected = selectedToolCategory == category,
                        onClick = { selectedToolCategory = category },
                        label = { Text(category) },
                    )
                }
            }
            val visibleToolGroups = linkedMapOf(
                selectedToolCategory to (toolGroups[selectedToolCategory] ?: emptyList())
            )
            visibleToolGroups.forEach { (category, names) ->
                CardGroup(
                    title = { Text(category) },
                ) {
                    names.forEach { name ->
                        item(
                            headlineContent = { Text(name) },
                            trailingContent = {
                                Switch(
                                    checked = name in prompt.enabledTools,
                                    onCheckedChange = { enabled ->
                                        val next = if (enabled) prompt.enabledTools + name else prompt.enabledTools - name
                                        updatePrompt(prompt.copy(enabledTools = next))
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
