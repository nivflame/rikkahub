package me.rerere.rikkahub.data.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CodexRateLimitParsingTest {
    @Test
    fun `quota headers parse both windows`() {
        val snapshot = parseCodexQuotaHeaders(
            mapOf(
                "x-codex-primary-used-percent" to listOf("41.5"),
                "X-Codex-Secondary-Used-Percent" to listOf("88"),
            )
        )
        assertNotNull(snapshot)
        assertEquals(41.5, snapshot!!.primary!!.usedPercent, 0.001)
        assertEquals(88.0, snapshot.secondary!!.usedPercent, 0.001)
    }

    @Test
    fun `quota headers parse primary only`() {
        val snapshot = parseCodexQuotaHeaders(
            mapOf("x-codex-primary-used-percent" to listOf("12.5"))
        )
        assertNotNull(snapshot)
        assertEquals(12.5, snapshot!!.primary!!.usedPercent, 0.001)
        assertNull(snapshot.secondary)
    }

    @Test
    fun `quota headers absent returns null`() {
        assertNull(parseCodexQuotaHeaders(mapOf("content-type" to listOf("application/json"))))
    }

    @Test
    fun `quota headers garbage value treated absent`() {
        assertNull(
            parseCodexQuotaHeaders(
                mapOf(
                    "x-codex-primary-used-percent" to listOf("not-a-number"),
                    "x-codex-secondary-used-percent" to listOf("73"),
                )
            )
        )
    }

    @Test
    fun `usage limit event parses resets_at`() {
        val (resetsAtMillis, reason) = parseCodexUsageLimitEvent(
            """{"error":{"type":"usage_limit_reached","resets_at":2000000000,"message":"limit hit"}}"""
        )!!
        assertEquals(2_000_000_000_000L, resetsAtMillis)
        assertEquals("limit hit", reason)
    }

    @Test
    fun `usage limit event parses resets_in_seconds from now`() {
        val before = System.currentTimeMillis()
        val (resetsAtMillis, reason) = parseCodexUsageLimitEvent(
            """{"type":"usage_limit_reached","resets_in_seconds":3600}"""
        )!!
        val after = System.currentTimeMillis()
        assert(resetsAtMillis in before + 3_600_000..after + 3_600_000)
        assertEquals("usage_limit_reached", reason)
    }

    @Test
    fun `usage limit event accepts body error nesting`() {
        val (resetsAtMillis, _) = parseCodexUsageLimitEvent(
            """{"body":{"error":{"type":"Usage_Limit_Reached","resets_at":1999999999}}}"""
        )!!
        assertEquals(1_999_999_999_000L, resetsAtMillis)
    }

    @Test
    fun `usage limit event rejects other types and missing resets`() {
        assertNull(
            parseCodexUsageLimitEvent(
                """{"error":{"type":"rate_limit_error","resets_at":2000000000}}"""
            )
        )
        assertNull(
            parseCodexUsageLimitEvent(
                """{"error":{"type":"usage_limit_reached"}}"""
            )
        )
    }

    @Test
    fun `classification covers all rate limit kinds`() {
        assertEquals(CodexRateLimitKind.QUOTA, classifyCodexRateLimit(429, "usage_limit_reached", null))
        assertEquals(CodexRateLimitKind.QUOTA, classifyCodexRateLimit(200, "Usage_Limit_Reached", null))
        assertEquals(CodexRateLimitKind.TRANSIENT, classifyCodexRateLimit(429, "rate_limit_error", null))
        assertEquals(CodexRateLimitKind.TRANSIENT, classifyCodexRateLimit(429, "rate_limit_exceeded", 60))
        assertEquals(CodexRateLimitKind.RETRY_AFTER, classifyCodexRateLimit(429, null, 120))
        assertEquals(CodexRateLimitKind.TRANSIENT, classifyCodexRateLimit(429, null, null))
        assertEquals(CodexRateLimitKind.TRANSIENT, classifyCodexRateLimit(429, "server_error", 0))
        assertEquals(CodexRateLimitKind.NONE, classifyCodexRateLimit(500, "server_error", null))
    }
}
