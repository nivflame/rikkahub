package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.UserCircle
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codex.CodexAccount
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.codex.CodexOAuthManager
import me.rerere.rikkahub.data.codex.CodexOAuthStatus
import me.rerere.rikkahub.data.codex.CodexTokenStatus
import me.rerere.rikkahub.data.codex.CodexUsageWindow
import me.rerere.rikkahub.data.codex.codexPlanDisplayName
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.CardGroup
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
    val context = LocalContext.current
    var selectedAccountId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(oauthStatus) {
        when (val status = oauthStatus) {
            is CodexOAuthStatus.Success -> {
                runCatching {
                    providerManager.getProviderByType(provider).listModels(provider)
                }.onSuccess { models ->
                    onEdit(provider.copy(models = mergeCodexModels(provider.models, models)))
                }
                toaster.show(
                    context.getString(R.string.codex_oauth_success),
                    type = ToastType.Success,
                )
                oauthManager.consumeResult()
            }

            is CodexOAuthStatus.Error -> {
                toaster.show(status.message, type = ToastType.Error)
                oauthManager.consumeResult()
            }

            else -> Unit
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
        }

        CardGroup(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Accounts")
                        Text(
                            text = "${accounts.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = oauthManager::startLogin) {
                        Icon(
                            HugeIcons.Add01,
                            contentDescription = stringResource(R.string.codex_sign_in),
                        )
                    }
                }
            }
        ) {
            if (oauthStatus is CodexOAuthStatus.Waiting) {
                item(
                    onClick = oauthManager::startLogin,
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
                            onClick = { oauthManager.consumeResult() },
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
            if (accounts.isEmpty()) {
                item(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.codex_no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            } else {
                accounts.forEach { account ->
                    item(
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = HugeIcons.UserCircle,
                                    contentDescription = null,
                                    modifier = Modifier.padding(8.dp),
                                )
                            }
                        },
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
                                    Text(
                                        text = stringResource(
                                            R.string.codex_percent,
                                            (100.0 - it.usedPercent).coerceIn(0.0, 100.0).roundToInt(),
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
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
                        if (accounts.none { it.id != account.id } && provider.enabled) {
                            onEdit(provider.copy(enabled = false))
                        }
                    }
                },
                onDismiss = { selectedAccountId = null },
            )
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
                }
        }
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

@Composable
private fun CodexAccountCard(
    account: CodexAccount,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onReauthenticate: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.codex_remove_account_title)) },
            text = { Text(stringResource(R.string.codex_remove_account_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(
                        stringResource(R.string.codex_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleMedium)
                    if (account.email.isNotBlank()) {
                        Text(
                            account.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = account.enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            Text(
                text = when (account.tokenStatus) {
                    CodexTokenStatus.AVAILABLE -> stringResource(R.string.codex_token_available)
                    CodexTokenStatus.EXPIRED -> stringResource(R.string.codex_token_expired)
                    CodexTokenStatus.INVALID -> stringResource(R.string.codex_token_unavailable)
                    CodexTokenStatus.UNKNOWN -> stringResource(R.string.codex_token_not_checked)
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (account.tokenStatus) {
                    CodexTokenStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
                    CodexTokenStatus.INVALID, CodexTokenStatus.EXPIRED -> MaterialTheme.colorScheme.error
                    CodexTokenStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            account.usage?.primary?.let {
                CodexUsageRow(
                    window = it,
                    fallbackName = stringResource(R.string.codex_five_hour_limit),
                )
            }
            account.usage?.secondary?.let {
                CodexUsageRow(
                    window = it,
                    fallbackName = stringResource(R.string.codex_weekly_limit),
                )
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.codex_refresh))
                }
                TextButton(onClick = onReauthenticate) {
                    Text(stringResource(R.string.codex_reauthenticate))
                }
                TextButton(onClick = { showDeleteConfirmation = true }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodexUsageRow(
    window: CodexUsageWindow,
    fallbackName: String,
) {
    val remaining = (100.0 - window.usedPercent).coerceIn(0.0, 100.0)
    val name = when (window.windowMinutes) {
        300L -> stringResource(R.string.codex_five_hour_limit)
        10_080L -> stringResource(R.string.codex_weekly_limit)
        43_200L -> stringResource(R.string.codex_monthly_limit)
        null -> fallbackName
        else -> stringResource(R.string.codex_minute_limit, window.windowMinutes)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.codex_percent_remaining, remaining.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { (remaining / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        window.resetsAt?.let { epochSeconds ->
            Text(
                text = stringResource(
                    R.string.codex_resets_at,
                    RESET_FORMAT.format(Instant.ofEpochSecond(epochSeconds)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RESET_FORMAT: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.systemDefault())
