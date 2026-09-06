package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.PoolableProvider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findProvider

sealed interface PoolTogglePlan {
    data object ConfirmDisable : PoolTogglePlan
    data class Apply(
        val newApiKey: String?,
        val enabled: Boolean,
        val accounts: List<PoolAccount>,
    ) : PoolTogglePlan
}

fun planPoolToggle(
    currentApiKey: String,
    accounts: List<PoolAccount>,
    enable: Boolean,
): PoolTogglePlan = when {
    enable && accounts.isEmpty() && currentApiKey.isNotBlank() -> PoolTogglePlan.Apply(
        newApiKey = null,
        enabled = true,
        accounts = listOf(PoolAccount(name = "Account 1", apiKey = currentApiKey)),
    )

    enable -> PoolTogglePlan.Apply(null, true, accounts)
    accounts.size >= 2 -> PoolTogglePlan.ConfirmDisable
    accounts.size == 1 -> PoolTogglePlan.Apply(accounts[0].apiKey, false, emptyList())
    else -> PoolTogglePlan.Apply(null, false, emptyList())
}

class ProviderPoolSelector {
    private val counters = ConcurrentHashMap<Uuid, AtomicInteger>()

    fun select(provider: ProviderSetting): ProviderSetting {
        if (provider !is PoolableProvider) return provider
        if (!provider.poolEnabled) return provider
        val accounts = provider.poolAccounts
        if (accounts.isEmpty()) return provider
        val counter = counters
            .computeIfAbsent(provider.id) { AtomicInteger(0) }
            .getAndIncrement()
        val index = selectPoolAccountIndex(accounts, counter)
        return provider.withApiKey(accounts[index].apiKey)
    }
}

internal fun selectPoolAccountIndex(
    accounts: List<PoolAccount>,
    counterValue: Int,
    nowMillis: Long = System.currentTimeMillis(),
): Int {
    val candidates = accounts.withIndex().filter { (_, account) -> account.rateLimitedUntil <= nowMillis }
        .ifEmpty { accounts.withIndex().toList() }
    return candidates[counterValue.mod(candidates.size)].index
}

internal fun isPoolRateLimitError(message: String?): Boolean {
    if (message == null) return false
    val lower = message.lowercase()
    return lower.contains(poolRateLimitRegex) &&
        (lower.contains("limit") || lower.contains("quota") || lower.contains("rate"))
}

fun ProviderPoolSelector.resolve(model: Model, settings: Settings): ProviderSetting? {
    val base = model.findProvider(settings.providers) ?: return null
    return select(base)
}

/**
 * Failover contract: each retry re-runs the flow body so the selection counter advances,
 * the fresh settingsFlow read excludes the account just marked by the completed predicate,
 * and `emittedContent` blocks retries once any chunk reached the UI.
 */
internal fun isPoolRateLimitRetry(
    cause: Throwable,
    attempt: Long,
    maxAttempts: Int,
    emittedContent: Boolean,
): Boolean {
    if (cause is CancellationException) return false
    if (emittedContent) return false
    if (attempt + 1 >= maxAttempts) return false
    return isPoolRateLimitError(cause.message)
}

private val poolRetryAfterRegex = Regex("""(\d+(?:\.\d+)?)\s*h(?:\s*(\d+)\s*m)?|\b(\d+)\s*m\b""")
private val poolRateLimitedModelRegex = Regex("""on model ([\w.\-/]+)""")
private val poolRateLimitRegex = Regex("""\b429\b""")
private const val DEFAULT_POOL_COOLDOWN_MILLIS = 60 * 60_000L

internal fun parsePoolRateLimitedModel(message: String?): String? {
    if (message == null) return null
    return poolRateLimitedModelRegex.find(message.lowercase())?.groupValues?.get(1)?.trimEnd('.')
        ?.takeIf { it.isNotBlank() }
}

fun formatPoolCooldown(remainingMillis: Long): String {
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

internal fun parsePoolRetryAfterMillis(message: String?): Long {
    if (message == null) return DEFAULT_POOL_COOLDOWN_MILLIS
    val lower = message.lowercase()
    val region = lower.substringAfter("try again", lower)
    val match = poolRetryAfterRegex.find(region) ?: return DEFAULT_POOL_COOLDOWN_MILLIS
    val hours = match.groups[1]?.value?.toDoubleOrNull() ?: 0.0
    val minutes = (match.groups[2]?.value ?: match.groups[3]?.value)?.toIntOrNull() ?: 0
    val totalMillis = ((hours * 60 + minutes) * 60_000L).toLong()
    return if (totalMillis > 0) totalMillis else DEFAULT_POOL_COOLDOWN_MILLIS
}

suspend fun markPoolAccountRateLimited(
    provider: ProviderSetting?,
    selected: ProviderSetting?,
    errorMessage: String?,
    settingsStore: SettingsStore?,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val baseProvider = provider ?: return
    val poolProvider = baseProvider as? PoolableProvider ?: return
    val selectedKey = selected?.apiKey ?: return
    if (settingsStore == null) return
    val message = errorMessage ?: return
    if (!isPoolRateLimitError(message)) return
    val account = poolProvider.poolAccounts.firstOrNull { it.apiKey == selectedKey } ?: return
    val until = nowMillis + parsePoolRetryAfterMillis(message)
    if (account.rateLimitedUntil >= until) return
    val modelSlug = parsePoolRateLimitedModel(message)
    settingsStore.update { settings ->
        settings.copy(
            providers = settings.providers.map { p ->
                if (p.id != baseProvider.id) {
                    p
                } else {
                    val poolProvider = p as? PoolableProvider
                    if (poolProvider != null) {
                        poolProvider.withPoolAccounts(
                            poolProvider.poolAccounts.map { acc ->
                                when {
                                    acc.id != account.id -> acc
                                    acc.rateLimitedUntil >= until -> acc
                                    else -> acc.copy(rateLimitedUntil = until, rateLimitedModel = modelSlug)
                                }
                            }
                        )
                    } else {
                        p
                    }
                }
            }
        )
    }
}
