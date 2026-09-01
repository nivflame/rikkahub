package me.rerere.rikkahub.data.ai

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid
import me.rerere.ai.provider.PoolableProvider
import me.rerere.ai.provider.ProviderSetting

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
