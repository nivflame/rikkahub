package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.UserMultiple02
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codex.CodexAccount
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.codex.CodexOAuthManager
import me.rerere.rikkahub.data.codex.CodexOAuthStatus
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.ExpressiveEmptyState
import me.rerere.rikkahub.utils.plus
import org.koin.compose.koinInject

@Composable
fun CodexAccountsSection() {
    val repository = koinInject<CodexAccountRepository>()
    val oauthManager = koinInject<CodexOAuthManager>()
    val accounts by repository.accounts.collectAsStateWithLifecycle()
    val oauthStatus by oauthManager.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var toolbarExpanded by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = toolbarExpanded,
                    onExpand = { toolbarExpanded = true },
                    onCollapse = { toolbarExpanded = false },
                ),
            contentPadding = PaddingValues(16.dp) + PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                val pendingStatus = oauthStatus as? CodexOAuthStatus.Waiting
                var frozenAccounts by remember { mutableStateOf<List<CodexAccount>>(emptyList()) }
                LaunchedEffect(pendingStatus != null) {
                    if (pendingStatus != null) frozenAccounts = accounts
                }
                val waitingAccounts = if (pendingStatus != null) frozenAccounts else accounts
                if (pendingStatus == null && accounts.isEmpty()) {
                    ExpressiveEmptyState(
                        shape = MaterialShapes.Clover4Leaf.toShape(),
                        icon = HugeIcons.UserMultiple02,
                        title = "No accounts",
                        hint = "Sign in with ChatGPT to add one",
                        modifier = Modifier.fillParentMaxHeight(0.85f),
                    )
                } else {
                    CardGroup {
                        val reauthId = pendingStatus?.reauthAccountId
                        waitingAccounts.forEach { account ->
                            if (reauthId == account.id) {
                                codexAuthorizationPendingItem(
                                    onRetry = { oauthManager.startLogin(reauthId) },
                                    onCancel = { oauthManager.consumeResult() },
                                )
                            } else {
                                codexAccountItem(
                                    account = account,
                                    showUsagePercent = true,
                                    onClick = { selectedAccountId = account.id },
                                )
                            }
                        }
                        if (pendingStatus != null &&
                            (reauthId == null || waitingAccounts.none { it.id == reauthId })
                        ) {
                            codexAuthorizationPendingItem(
                                onRetry = { oauthManager.startLogin(reauthId) },
                                onCancel = { oauthManager.consumeResult() },
                            )
                        }
                    }
                }
            }
        }

        accounts.firstOrNull { it.id == selectedAccountId }?.let { account ->
            CodexAccountSheet(
                account = account,
                onToggleEnabled = {
                    scope.launch { repository.setEnabled(account.id, !account.enabled) }
                },
                onRefresh = {
                    scope.launch { runCatching { repository.refreshAccount(account.id) } }
                },
                onReauthenticate = {
                    selectedAccountId = null
                    oauthManager.startLogin(account.id)
                },
                onSignOut = {
                    scope.launch {
                        repository.delete(account.id)
                    }
                },
                onDismiss = { selectedAccountId = null },
            )
        }

        HorizontalFloatingToolbar(
            expanded = toolbarExpanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -ScreenOffset),
        ) {
            Button(onClick = { oauthManager.startLogin(null) }) {
                Icon(HugeIcons.Add01, null)
                AnimatedVisibility(toolbarExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.codex_sign_in))
                    }
                }
            }
        }
    }
}

@Composable
private fun CodexAccountSheet(
    account: CodexAccount,
    onToggleEnabled: () -> Unit,
    onRefresh: () -> Unit,
    onReauthenticate: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSignOutDialog by remember { mutableStateOf(false) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.codex_remove_account_title)) },
            text = { Text(stringResource(R.string.codex_remove_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                        onDismiss()
                    }
                ) {
                    Text(
                        stringResource(R.string.codex_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        LaunchedEffect(account.id) { onRefresh() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = account.email.ifBlank { account.name },
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            val enableAccountLabel = stringResource(R.string.codex_enable_account)
            CardGroup {
                toggleItem(
                    label = enableAccountLabel,
                    checked = account.enabled,
                    onClick = onToggleEnabled,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.codex_refresh))
                }
                FilledTonalButton(
                    onClick = onReauthenticate,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.codex_reauthenticate))
                }
                FilledTonalButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(stringResource(R.string.codex_sign_out))
                }
            }
                val windows = listOfNotNull(account.usage?.primary, account.usage?.secondary)
                if (windows.isNotEmpty()) {
                    CardGroup(
                        title = { Text(stringResource(R.string.codex_usage_title)) },
                    ) {
                        codexUsageItems(windows)
                    }
                }
        }
    }
}
