package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.UserMultiple02
import me.rerere.hugeicons.stroke.LoginSquare01
import me.rerere.hugeicons.stroke.Logout01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codex.CodexAccount
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.codex.CodexOAuthManager
import me.rerere.rikkahub.data.codex.CodexOAuthStatus
import me.rerere.rikkahub.data.codex.codexPlanDisplayName
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.dimmed
import me.rerere.rikkahub.ui.components.ui.ExpressiveEmptyState
import org.koin.compose.koinInject
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CodexProviderConfigure(
    provider: ProviderSetting.Codex,
    onEdit: (ProviderSetting.Codex) -> Unit,
) {
    val repository = koinInject<CodexAccountRepository>()
    val oauthManager = koinInject<CodexOAuthManager>()
    val providerManager = koinInject<ProviderManager>()
    val accounts by repository.accounts.collectAsStateWithLifecycle()
    val oauthStatus by oauthManager.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    LaunchedEffect(oauthStatus) {
        when (val status = oauthStatus) {
            is CodexOAuthStatus.Success -> {
                runCatching {
                    providerManager.getProviderByType(provider).listModels(provider)
                }.onSuccess { models ->
                    onEdit(provider.copy(models = mergeCodexModels(provider.models, models)))
                }
                oauthManager.consumeResult()
            }

            is CodexOAuthStatus.Error -> {
                toaster.show(status.message, type = ToastType.Error)
                oauthManager.consumeResult()
            }

            else -> Unit
        }
    }

    LaunchedEffect(Unit) {
        if (accounts.isNotEmpty()) {
            runCatching { repository.refreshAll() }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CardGroup {
            item(
                onClick = { onEdit(provider.copy(enabled = !provider.enabled)) },
                headlineContent = {
                    Text(
                        text = stringResource(R.string.setting_provider_page_enable),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = null,
                    )
                },
            )
            item(
                onClick = { onEdit(provider.copy(poolEnabled = !provider.poolEnabled)) },
                headlineContent = {
                    Text(
                        text = "Rotate Accounts",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = provider.poolEnabled,
                        onCheckedChange = null,
                    )
                },
            )
        }

        CardGroup(
            title = { Text("Account") },
            modifier = Modifier.dimmed(provider.poolEnabled),
        ) {
            if (!provider.poolEnabled) {
                if (oauthStatus is CodexOAuthStatus.Waiting) {
                    codexWaitingItem(
                        onRetry = oauthManager::startLogin,
                        onCancel = { oauthManager.consumeResult() },
                    )
                } else if (accounts.isEmpty()) {
                    item(
                        onClick = oauthManager::startLogin,
                        leadingContent = {
                            Icon(
                                HugeIcons.LoginSquare01,
                                contentDescription = null,
                            )
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.codex_sign_in),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                    )
                }
                accounts.take(1).forEach { account ->
                    codexAccountItem(
                        account = account,
                        onSignOut = {
                            scope.launch {
                                repository.delete(account.id)
                            }
                        },
                    )
                }
            } else {
                accounts.take(1).forEach { account ->
                    codexAccountItem(account)
                }
            }
        }

        if (!provider.poolEnabled && accounts.firstOrNull()?.usage != null) {
            CardGroup(
                title = { Text("Usage") }
            ) {
                codexUsageItems(accounts.first())
            }
        }
    }
}

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
        Column(
            modifier = Modifier
                .fillMaxSize()
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
            if (accounts.isEmpty() && oauthStatus !is CodexOAuthStatus.Waiting) {
                ExpressiveEmptyState(
                    shape = MaterialShapes.Clover4Leaf.toShape(),
                    icon = HugeIcons.UserMultiple02,
                    title = "No accounts",
                    hint = "Sign in with ChatGPT to add one",
                )
            }
            if (accounts.isNotEmpty() || oauthStatus is CodexOAuthStatus.Waiting) {
                CardGroup {
                    if (oauthStatus is CodexOAuthStatus.Waiting) {
                codexWaitingItem(
                    onRetry = oauthManager::startLogin,
                    onCancel = { oauthManager.consumeResult() },
                )
            }
            accounts.forEach { account ->
                    item(
                        leadingContent = if (account.usage == null) {
                            {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    RabbitLoadingIndicator(
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                        } else null,
                        headlineContent = {
                            val busiestWindow = listOfNotNull(
                                account.usage?.primary,
                                account.usage?.secondary,
                            ).maxByOrNull { it.usedPercent }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = account.email.ifBlank { account.name },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                busiestWindow?.let {
                                    val remaining = (100.0 - it.usedPercent).coerceIn(0.0, 100.0).roundToInt()
                                    Text(
                                        text = stringResource(
                                            R.string.codex_percent,
                                            remaining,
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (remaining == 0) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                }
                            }
                        },
                        supportingContent = account.planType?.let { plan ->
                            {
                                Text(
                                    text = "ChatGPT " + codexPlanDisplayName(plan),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            selectedAccountId = account.id
                        },
                    )
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
                onReauthenticate = oauthManager::startLogin,
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
            Button(onClick = oauthManager::startLogin) {
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
            CardGroup {
                item(
                    onClick = onToggleEnabled,
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.codex_enable_account),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = account.enabled,
                            onCheckedChange = null,
                        )
                    },
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
                        codexUsageItems(account)
                    }
                }
        }
    }
}

@Composable
private fun CardGroupScope.codexWaitingItem(
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    item(
        onClick = onRetry,
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                RabbitLoadingIndicator(
                    modifier = Modifier.padding(8.dp),
                )
            }
        },
        headlineContent = {
            Text(stringResource(R.string.codex_waiting_authorization))
        },
        supportingContent = {
            Text(stringResource(R.string.codex_waiting_authorization_hint))
        },
        trailingContent = {
            FilledIconButton(
                onClick = onCancel,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(
                    HugeIcons.Cancel01,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
        },
    )
}

@Composable
private fun CardGroupScope.codexAccountItem(
    account: CodexAccount,
    onSignOut: (() -> Unit)? = null,
) {
    item(
        leadingContent = if (account.usage == null) {
            {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    RabbitLoadingIndicator(
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        } else null,
        headlineContent = {
            Text(
                text = account.email.ifBlank { account.name },
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = account.planType?.let { plan ->
            {
                Text(
                    text = "ChatGPT " + codexPlanDisplayName(plan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = onSignOut?.let { signOut ->
            {
                FilledIconButton(
                    onClick = signOut,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        HugeIcons.Logout01,
                        contentDescription = stringResource(R.string.codex_sign_out),
                    )
                }
            }
        },
    )
}

@Composable
private fun CardGroupScope.codexUsageItems(account: CodexAccount) {
    val windows = listOfNotNull(account.usage?.primary, account.usage?.secondary)
    listOf(
        300L to R.string.codex_five_hour_limit,
        10_080L to R.string.codex_weekly_limit,
        43_200L to R.string.codex_monthly_limit,
    ).forEach { (windowMinutes, labelRes) ->
        val window = windows.firstOrNull { it.windowMinutes == windowMinutes }
            ?: return@forEach
        val remaining = (100.0 - window.usedPercent)
            .coerceIn(0.0, 100.0).roundToInt()
        item(
            headlineContent = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.codex_percent, remaining),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (remaining == 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                    )
                }
            },
            supportingContent = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    formatCodexResetText(window.resetsAt)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val progress by animateFloatAsState(
                        targetValue = remaining / 100f,
                        label = "usageProgress",
                    )
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        amplitude = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
private fun formatCodexResetText(resetsAt: Long?): String? {
    if (resetsAt == null) return null
    val zone = ZoneId.systemDefault()
    val now = ZonedDateTime.now(zone)
    val reset = Instant.ofEpochSecond(resetsAt).atZone(zone)
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), reset.toLocalDate())
    val time = reset.format(DateTimeFormatter.ofPattern("h:mm a"))
    return when {
        days == 0L -> {
            val minutes = ChronoUnit.MINUTES.between(now, reset)
            if (minutes >= 60) stringResource(R.string.codex_resets_in_hours, minutes / 60)
            else stringResource(R.string.codex_resets_in_minutes, minutes.coerceAtLeast(1))
        }
        days == 1L -> stringResource(R.string.codex_resets_tomorrow_at, time)
        days in 2..7 -> stringResource(
            R.string.codex_resets_weekday_at,
            reset.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
            time,
        )
        else -> stringResource(
            R.string.codex_resets_on_date_at,
            reset.format(DateTimeFormatter.ofPattern("MMM d")),
            time,
        )
    }
}

internal fun mergeCodexModels(existing: List<Model>, refreshed: List<Model>): List<Model> {
    val refreshedByModelId = refreshed.associateBy(Model::modelId)
    val merged = existing.map { model ->
        refreshedByModelId[model.modelId]?.let { refreshedModel ->
            model.copy(
                inputModalities = refreshedModel.inputModalities,
                outputModalities = refreshedModel.outputModalities,
                abilities = refreshedModel.abilities,
            )
        } ?: model
    }
    val existingModelIds = existing.mapTo(mutableSetOf(), Model::modelId)
    return merged + refreshed.filterNot { it.modelId in existingModelIds }
}
