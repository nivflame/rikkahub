package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.CodexRotationMode
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.LoginSquare01
import me.rerere.hugeicons.stroke.Logout01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.ui.components.ui.RabbitLoadingIndicator
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.codex.CodexAccount
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.codex.CodexOAuthManager
import me.rerere.rikkahub.data.codex.CodexOAuthStatus
import me.rerere.rikkahub.data.codex.CodexUsageWindow
import me.rerere.rikkahub.data.codex.mergeCodexModels
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
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
    val currentProvider by rememberUpdatedState(provider)
    val pendingStatus = oauthStatus as? CodexOAuthStatus.Waiting
    val frozenAccounts = remember(pendingStatus) { accounts }
    val waitingAccounts = if (pendingStatus != null) frozenAccounts else accounts

    LaunchedEffect(oauthStatus) {
        when (val status = oauthStatus) {
            is CodexOAuthStatus.Success -> {
                runCatching {
                    providerManager.getProviderByType(currentProvider).listModels(currentProvider)
                }.onSuccess { models ->
                    onEdit(currentProvider.copy(models = mergeCodexModels(currentProvider.models, models)))
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
        val enableLabel = stringResource(R.string.setting_provider_page_enable)
        CardGroup {
            toggleItem(
                label = enableLabel,
                checked = provider.enabled,
                onClick = { onEdit(provider.copy(enabled = !provider.enabled)) },
            )
        }

        CardGroup {
            toggleItem(
                label = "Rotate Accounts",
                checked = provider.rotation != CodexRotationMode.OFF,
                onClick = {
                    onEdit(
                        provider.copy(
                            rotation = if (provider.rotation == CodexRotationMode.OFF) {
                                CodexRotationMode.FILL_FIRST
                            } else {
                                CodexRotationMode.OFF
                            }
                        )
                    )
                },
            )
            if (provider.rotation != CodexRotationMode.OFF) {
                item(
                    headlineContent = {
                        Text(
                            text = "Mode",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    supportingContent = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        ) {
                            listOf(
                                CodexRotationMode.FILL_FIRST,
                                CodexRotationMode.ROUND_ROBIN,
                            ).forEachIndexed { index, mode ->
                                val modeWeight by animateFloatAsState(
                                    targetValue = if (provider.rotation == mode) 1.5f else 1f,
                                    label = "modeWeight",
                                )
                                TonalToggleButton(
                                    checked = provider.rotation == mode,
                                    onCheckedChange = { onEdit(provider.copy(rotation = mode)) },
                                    modifier = Modifier.weight(modeWeight),
                                    colors = ToggleButtonDefaults.tonalToggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    },
                                ) {
                                    Text(
                                        text = when (mode) {
                                            CodexRotationMode.FILL_FIRST -> "Fill First"
                                            CodexRotationMode.ROUND_ROBIN -> "Round Robin"
                                            CodexRotationMode.OFF -> ""
                                        }
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }

        CardGroup(
            title = { Text("Account") },
            modifier = if (provider.rotation != CodexRotationMode.OFF) {
                Modifier.graphicsLayer { alpha = 0.38f }
            } else Modifier,
        ) {
            if (provider.rotation == CodexRotationMode.OFF) {
                val reauthId = pendingStatus?.reauthAccountId
                if (pendingStatus != null && reauthId == waitingAccounts.firstOrNull()?.id) {
                    codexAuthorizationPendingItem(
                        onRetry = { oauthManager.startLogin(reauthId) },
                        onCancel = { oauthManager.consumeResult() },
                    )
                } else if (pendingStatus != null) {
                    waitingAccounts.take(1).forEach { account ->
                        codexAccountItem(
                            account = account,
                            onSignOut = {
                                scope.launch {
                                    repository.delete(account.id)
                                }
                            },
                        )
                    }
                    codexAuthorizationPendingItem(
                        onRetry = { oauthManager.startLogin(reauthId) },
                        onCancel = { oauthManager.consumeResult() },
                    )
                } else if (accounts.isEmpty()) {
                    item(
                        onClick = { oauthManager.startLogin(null) },
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
                } else {
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
                }
            } else {
                accounts.take(1).forEach { account ->
                    codexAccountItem(account)
                }
            }
        }

        if (provider.rotation == CodexRotationMode.OFF && accounts.firstOrNull()?.usage != null) {
            CardGroup(
                title = { Text("Usage") }
            ) {
                val account = accounts.first()
                val windows = listOfNotNull(account.usage?.primary, account.usage?.secondary)
                codexUsageItems(windows)
            }
        }
    }
}

internal fun CardGroupScope.codexUsageItems(windows: List<CodexUsageWindow>) {
    listOf(
        300L to R.string.codex_five_hour_limit,
        10_080L to R.string.codex_weekly_limit,
        43_200L to R.string.codex_monthly_limit,
    ).forEach { (windowMinutes, labelRes) ->
        val window = windows.firstOrNull { it.windowMinutes == windowMinutes } ?: return@forEach
        val remaining = (100.0 - window.usedPercent).coerceIn(0.0, 100.0).roundToInt()
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

internal fun CardGroupScope.codexAccountItem(
    account: CodexAccount,
    onSignOut: (() -> Unit)? = null,
    showUsagePercent: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    item(
        onClick = onClick,
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
            if (showUsagePercent) {
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
            } else {
                Text(
                    text = account.email.ifBlank { account.name },
                    style = MaterialTheme.typography.titleMedium,
                )
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
        trailingContent = if (onSignOut != null) {
            {
                FilledIconButton(
                    onClick = onSignOut,
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
        } else null,
    )
}

internal fun CardGroupScope.codexAuthorizationPendingItem(
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

private fun codexPlanDisplayName(raw: String): String = when (raw.lowercase()) {
    "free" -> "Free"
    "go" -> "Go"
    "plus" -> "Plus"
    "pro" -> "Pro (More)"
    "prolite" -> "Pro"
    "promax" -> "Pro (Max)"
    "team" -> "Team"
    "self_serve_business_prolite" -> "Self Serve Business ProLite"
    "self_serve_business_usage_based" -> "Self Serve Business Usage Based"
    "business" -> "Business"
    "ent26" -> "Enterprise"
    "enterprise_cbp_automation" -> "Enterprise (Automation)"
    "enterprise_cbp_usage_based" -> "Enterprise CBP Usage Based"
    "enterprise", "hc" -> "Enterprise"
    "education", "edu" -> "Edu"
    "edu_plus" -> "Edu Plus"
    "edu_pro" -> "Edu Pro"
    "sci" -> "Sci"
    "sci_plus" -> "Sci Plus"
    else -> raw
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
