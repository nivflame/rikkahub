package me.rerere.rikkahub.data.ai

import kotlin.uuid.Uuid
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun `enabling seeds account from existing api key`() {
        val plan = planPoolToggle("existing-key", emptyList(), enable = true)

        val apply = plan as PoolTogglePlan.Apply
        assertEquals(null, apply.newApiKey)
        assertTrue(apply.enabled)
        assertEquals(1, apply.accounts.size)
        assertEquals("Account 1", apply.accounts[0].name)
        assertEquals("existing-key", apply.accounts[0].apiKey)
    }

    @Test
    fun `enabling keeps accounts when pool already has them`() {
        val accounts = listOf(account("one"), account("two"))

        val plan = planPoolToggle("existing-key", accounts, enable = true)

        val apply = plan as PoolTogglePlan.Apply
        assertEquals(null, apply.newApiKey)
        assertTrue(apply.enabled)
        assertEquals(accounts, apply.accounts)
    }

    @Test
    fun `enabling with blank key starts empty pool`() {
        val plan = planPoolToggle("", emptyList(), enable = true)

        val apply = plan as PoolTogglePlan.Apply
        assertEquals(null, apply.newApiKey)
        assertTrue(apply.enabled)
        assertTrue(apply.accounts.isEmpty())
    }

    @Test
    fun `disabling with two or more accounts requires confirmation`() {
        val plan = planPoolToggle("original", listOf(account("one"), account("two")), enable = false)

        assertEquals(PoolTogglePlan.ConfirmDisable, plan)
    }

    @Test
    fun `disabling with single account migrates its key`() {
        val plan = planPoolToggle("original", listOf(account("one")), enable = false)

        val apply = plan as PoolTogglePlan.Apply
        assertEquals("key-one", apply.newApiKey)
        assertEquals(false, apply.enabled)
        assertTrue(apply.accounts.isEmpty())
    }

    @Test
    fun `disabling empty pool is a plain toggle`() {
        val plan = planPoolToggle("original", emptyList(), enable = false)

        val apply = plan as PoolTogglePlan.Apply
        assertEquals(null, apply.newApiKey)
        assertEquals(false, apply.enabled)
        assertTrue(apply.accounts.isEmpty())
    }
}
