package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid
import me.rerere.ai.provider.PoolAccount
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `rate limited account is skipped`() {
        val selector = ProviderPoolSelector()
        val limited = account("one").copy(rateLimitedUntil = Long.MAX_VALUE)
        val provider = openaiProvider(accounts = listOf(limited, account("two")))

        val keys = (1..3).map { selector.select(provider).apiKey }

        assertEquals(listOf("key-two", "key-two", "key-two"), keys)
    }

    @Test
    fun `rate limited account re-enters rotation after cooldown`() {
        val accounts = listOf(
            account("one").copy(rateLimitedUntil = 1000L),
            account("two"),
        )

        assertEquals(1, selectPoolAccountIndex(accounts, counterValue = 0, nowMillis = 500L))
        assertEquals(0, selectPoolAccountIndex(accounts, counterValue = 1, nowMillis = 2000L))
    }

    @Test
    fun `all limited accounts fall back to rotation`() {
        val selector = ProviderPoolSelector()
        val accounts = listOf(
            account("one").copy(rateLimitedUntil = Long.MAX_VALUE),
            account("two").copy(rateLimitedUntil = Long.MAX_VALUE),
        )
        val provider = openaiProvider(accounts = accounts)

        val keys = (1..2).map { selector.select(provider).apiKey }

        assertEquals(listOf("key-one", "key-two"), keys)
    }

    @Test
    fun `rate limit error detection and duration parsing`() {
        val message = "Error 429: Daily free limit reached on model z-ai/glm-5.3-flash. Try again in 2h 7m"
        assertTrue(isPoolRateLimitError(message))
        assertEquals(false, isPoolRateLimitError("Error 500: internal server error"))
        assertEquals(127 * 60_000L, parsePoolRetryAfterMillis(message))
        assertEquals(
            120 * 60_000L,
            parsePoolRetryAfterMillis("Error 429: daily usage 45m tokens exceeded. Try again in 2h"),
        )
        assertEquals(45 * 60_000L, parsePoolRetryAfterMillis("Error 429: limit. Try again in 45m"))
        assertEquals(60 * 60_000L, parsePoolRetryAfterMillis("Error 429: limit reached"))
    }

    @Test
    fun `rate limited model slug is parsed from error`() {
        assertEquals(
            "z-ai/glm-5.3-flash",
            parsePoolRateLimitedModel("Error 429: Daily free limit reached on model z-ai/glm-5.3-flash. Try again in 2h 7m"),
        )
        assertEquals(null, parsePoolRateLimitedModel("Error 429: limit reached. Try again in 45m"))
        assertEquals(null, parsePoolRateLimitedModel(null))
    }

    @Test
    fun `next account name increments numeric names`() {
        assertEquals("01", nextAccountName(emptyList()))
        assertEquals(
            "03",
            nextAccountName(
                listOf(
                    PoolAccount(name = "01", apiKey = "k"),
                    PoolAccount(name = "02", apiKey = "k"),
                )
            )
        )
        assertEquals(
            "01",
            nextAccountName(listOf(PoolAccount(name = "primary", apiKey = "k"))),
        )
        val first = PoolAccount(name = "01", apiKey = "k")
        val second = PoolAccount(name = "02", apiKey = "k")
        assertEquals(
            "02",
            nextAccountName(listOf(first, second), excludeId = second.id),
        )
    }

    @Test
    fun `pool retry decision gates on emission attempts and error type`() {
        val cause429 = Exception("Error 429: Daily free limit reached. Try again in 2h 7m")
        val causeOther = Exception("Error 500: internal server error")
        assertTrue(isPoolRateLimitRetry(cause429, attempt = 0, maxAttempts = 3, emittedContent = false))
        assertTrue(isPoolRateLimitRetry(cause429, attempt = 1, maxAttempts = 3, emittedContent = false))
        assertFalse(isPoolRateLimitRetry(cause429, attempt = 0, maxAttempts = 3, emittedContent = true))
        assertFalse(isPoolRateLimitRetry(cause429, attempt = 2, maxAttempts = 3, emittedContent = false))
        assertFalse(isPoolRateLimitRetry(cause429, attempt = 1, maxAttempts = 2, emittedContent = false))
        assertFalse(isPoolRateLimitRetry(cause429, attempt = 0, maxAttempts = 1, emittedContent = false))
        assertFalse(isPoolRateLimitRetry(causeOther, attempt = 0, maxAttempts = 3, emittedContent = false))
        assertFalse(
            isPoolRateLimitRetry(
                Exception("Error 429: upstream unavailable"),
                attempt = 0,
                maxAttempts = 3,
                emittedContent = false,
            )
        )
        assertFalse(
            isPoolRateLimitRetry(
                CancellationException("cancelled"),
                attempt = 0,
                maxAttempts = 3,
                emittedContent = true,
            )
        )
    }

    @Test
    fun `cooldown formats as h m s`() {
        assertEquals("2h 7m", formatPoolCooldown(127 * 60_000L))
        assertEquals("2h 17m", formatPoolCooldown(137 * 60_000L + 20_000L))
        assertEquals("45m", formatPoolCooldown(45 * 60_000L))
        assertEquals("30s", formatPoolCooldown(30_000L))
        assertEquals("0s", formatPoolCooldown(0L))
    }
}
