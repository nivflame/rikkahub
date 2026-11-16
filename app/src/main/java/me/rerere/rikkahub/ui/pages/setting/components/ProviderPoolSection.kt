package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.PoolableProvider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.UserMultiple02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ExpressiveEmptyState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.utils.writeClipboardText

internal fun ProviderSetting.updatePool(
    enabled: Boolean,
    accounts: List<PoolAccount>,
): ProviderSetting = when (this) {
    is ProviderSetting.OpenAI -> copy(
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    is ProviderSetting.Google -> copy(
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    is ProviderSetting.Claude -> copy(
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    else -> this
}

@Composable
fun ProviderPoolSection(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    val poolable = provider as? PoolableProvider ?: return
    val poolAccounts = poolable.poolAccounts
    val context = LocalContext.current
    val toaster = LocalToaster.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PoolAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<PoolAccount?>(null) }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }

    val poolEnabled = poolable.poolEnabled

    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .floatingToolbarVerticalNestedScroll(
                    expanded = toolbarExpanded,
                    onExpand = { toolbarExpanded = true },
                    onCollapse = { toolbarExpanded = false },
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (poolAccounts.isEmpty()) {
                ExpressiveEmptyState(
                    shape = MaterialShapes.Clover4Leaf.toShape(),
                    icon = HugeIcons.UserMultiple02,
                    title = "No accounts",
                    hint = "Add an account to rotate between them",
                )
            } else {
                CardGroup(
                    title = {
                        Text(text = "Accounts")
                    }
                ) {
                    poolAccounts.forEachIndexed { index, account ->
                        item(
                            onClick = { editTarget = account },
                headlineContent = {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val maskedKey = if (account.apiKey.length > 12) {
                        account.apiKey.take(6) + "..." + account.apiKey.takeLast(4)
                    } else {
                        account.apiKey
                    }
                    Text(
                        text = maskedKey,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetbrainsMono,
                    )
                    PoolAccountCooldown(account) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = {
                        expandedMenuId = if (expandedMenuId == account.id.toString()) null else account.id.toString()
                    }) {
                        Icon(HugeIcons.MoreVertical, contentDescription = "More")
                        DropdownMenu(
                            expanded = expandedMenuId == account.id.toString(),
                            onDismissRequest = { expandedMenuId = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Move up") },
                                leadingIcon = { Icon(HugeIcons.ArrowUp01, null) },
                                enabled = index > 0,
                                onClick = {
                                    val updated = poolAccounts.toMutableList()
                                    val previous = updated.removeAt(index - 1)
                                    updated.add(index, previous)
                                    onEdit(provider.updatePool(poolEnabled, updated))
                                    expandedMenuId = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Move down") },
                                leadingIcon = { Icon(HugeIcons.ArrowDown01, null) },
                                enabled = index < poolAccounts.lastIndex,
                                onClick = {
                                    val updated = poolAccounts.toMutableList()
                                    val next = updated.removeAt(index + 1)
                                    updated.add(index, next)
                                    onEdit(provider.updatePool(poolEnabled, updated))
                                    expandedMenuId = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Copy API key") },
                                leadingIcon = { Icon(HugeIcons.Copy01, null) },
                                onClick = {
                                    context.writeClipboardText(account.apiKey, sensitive = true)
                                    toaster.show("Copied", ToastType.Success)
                                    expandedMenuId = null
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        HugeIcons.Delete01,
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    deleteTarget = account
                                    expandedMenuId = null
                                },
                            )
                        }
                    }
                },
            )
        }
        }
        }
        }

        HorizontalFloatingToolbar(
            expanded = toolbarExpanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            Button(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.Add01, null)
                AnimatedVisibility(toolbarExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text("Add account")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        PoolAccountDialog(
            account = null,
            accounts = poolAccounts,
            onConfirm = { name, apiKey ->
                onEdit(provider.updatePool(poolEnabled, poolAccounts + PoolAccount(name = name, apiKey = apiKey)))
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editTarget?.let { account ->
        PoolAccountDialog(
            account = account,
            accounts = poolAccounts,
            onConfirm = { name, apiKey ->
                onEdit(
                    provider.updatePool(
                        enabled = poolEnabled,
                        accounts = poolAccounts.map {
                            if (it.id == account.id) it.copy(name = name, apiKey = apiKey) else it
                        }
                    )
                )
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this account?") },
            text = { Text("This account will be removed from rotation. The API key cannot be recovered.") },
            confirmButton = {
                Button(
                    onClick = {
                        onEdit(provider.updatePool(poolEnabled, poolAccounts - account))
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun PoolAccountCooldown(
    account: PoolAccount,
    content: @Composable (String) -> Unit,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(account.rateLimitedUntil) {
        while (account.rateLimitedUntil > System.currentTimeMillis()) {
            delay(30_000)
            nowMillis = System.currentTimeMillis()
        }
        nowMillis = System.currentTimeMillis()
    }
    val remainingMillis = account.rateLimitedUntil - nowMillis
    if (remainingMillis > 0) {
        content(
            buildString {
                append("Rate limited")
                account.rateLimitedModel?.let { append(" · $it") }
                append(" · ")
                append(formatPoolCooldown(remainingMillis))
                append(" left")
            }
        )
    }
}

@Composable
private fun PoolAccountDialog(
    account: PoolAccount?,
    accounts: List<PoolAccount>,
    onConfirm: (name: String, apiKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(account?.id) { mutableStateOf(account?.name.orEmpty()) }
    var apiKey by remember(account?.id) { mutableStateOf(account?.apiKey.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Add Account" else "Edit Account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setting_provider_page_name) + " (optional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        name.trim().ifBlank { nextAccountName(accounts, account?.id) },
                        apiKey.trim(),
                    )
                },
                enabled = apiKey.isNotBlank(),
            ) {
                Text(if (account == null) stringResource(R.string.setting_provider_page_add) else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

internal fun nextAccountName(accounts: List<PoolAccount>, excludeId: Uuid? = null): String {
    val max = accounts.asSequence()
        .filter { it.id != excludeId }
        .mapNotNull { it.name.toIntOrNull() }
        .maxOrNull() ?: 0
    return (max + 1).toString().padStart(2, '0')
}

internal fun formatPoolCooldown(remainingMillis: Long): String {
    if (remainingMillis <= 0) return "0s"
    val totalSeconds = (remainingMillis + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
