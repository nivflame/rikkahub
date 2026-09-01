package me.rerere.rikkahub.data.ai

import kotlin.uuid.Uuid
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProviderPoolTest {
    private fun account(name: String) = PoolAccount(name = name, apiKey = "key-$name")

    private fun openaiProvider(
        id: Uuid = Uuid.random(),
        enabled: Boolean = true,
        accounts: List<PoolAccount> = listOf(account("one"), account("two")),
    ) = ProviderSetting.OpenAI(
        id = id,
        name = "Test",
        apiKey = "original-key",
        poolEnabled = enabled,
        poolAccounts = accounts,
    )

    @Test
    fun `pool cycles accounts round robin`() {
        val selector = ProviderPoolSelector()
        val provider = openaiProvider()

        val keys = (1..4).map { selector.select(provider).apiKey }

        assertEquals(listOf("key-one", "key-two", "key-one", "key-two"), keys)
    }

    @Test
    fun `counters are independent per provider`() {
        val selector = ProviderPoolSelector()
        val first = openaiProvider()
        val second = openaiProvider()

        assertEquals("key-one", selector.select(first).apiKey)
        assertEquals("key-one", selector.select(second).apiKey)
        assertEquals("key-two", selector.select(first).apiKey)
    }

    @Test
    fun `disabled pool returns provider unchanged`() {
        val selector = ProviderPoolSelector()
        val provider = openaiProvider(enabled = false)

        assertSame(provider, selector.select(provider))
    }

    @Test
    fun `empty pool returns provider unchanged`() {
        val selector = ProviderPoolSelector()
        val provider = openaiProvider(accounts = emptyList())

        assertSame(provider, selector.select(provider))
    }

    @Test
    fun `selection replaces only api key`() {
        val selector = ProviderPoolSelector()
        val provider = openaiProvider()

        val selected = selector.select(provider) as ProviderSetting.OpenAI

        assertEquals("key-one", selected.apiKey)
        assertEquals("Test", selected.name)
        assertEquals(provider.id, selected.id)
        assertEquals(provider.baseUrl, selected.baseUrl)
    }

    @Test
    fun `works for google and claude providers`() {
        val selector = ProviderPoolSelector()
        val accounts = listOf(account("one"), account("two"))

        val google = ProviderSetting.Google(poolEnabled = true, poolAccounts = accounts)
        val claude = ProviderSetting.Claude(poolEnabled = true, poolAccounts = accounts)

        assertEquals("key-one", selector.select(google).apiKey)
        assertEquals("key-one", selector.select(claude).apiKey)
        assertEquals("key-two", selector.select(google).apiKey)
        assertEquals("key-two", selector.select(claude).apiKey)
    }
}
