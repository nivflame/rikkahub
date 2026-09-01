package me.rerere.rikkahub.data.ai

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.PoolableProvider
import me.rerere.ai.provider.ProviderSetting

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
        val index = counters
            .computeIfAbsent(provider.id) { AtomicInteger(0) }
            .getAndIncrement()
            .mod(accounts.size)
        return provider.withApiKey(accounts[index].apiKey)
    }
}
