package me.rerere.rikkahub.data.codex

import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.TextGenerationParams
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexProviderTest {
    @Test
    fun `codex params drop unsupported max tokens`() {
        val params = TextGenerationParams(
            model = Model(modelId = "gpt-5.2-codex", abilities = listOf(ModelAbility.REASONING)),
            maxTokens = 4096,
            reasoningLevel = ReasoningLevel.HIGH,
        )

        val codex = withCodexParams(params, codexAccount(), stream = true)

        assertNull(codex.maxTokens)
        assertEquals(params.temperature, codex.temperature)
        assertEquals(params.topP, codex.topP)
    }

    @Test
    fun `codex params keep reasoning body and account header`() {
        val params = TextGenerationParams(
            model = Model(modelId = "gpt-5.2-codex", abilities = listOf(ModelAbility.REASONING)),
            reasoningLevel = ReasoningLevel.HIGH,
        )

        val codex = withCodexParams(params, codexAccount(), stream = true)

        val reasoning = codex.customBody.first { it.key == "reasoning" }
        assertEquals("high", reasoning.value.jsonObject["effort"]?.jsonPrimitive?.content)
        assertTrue(codex.customHeaders.any { it.name == "ChatGPT-Account-Id" && it.value == "account-1" })
        assertTrue(codex.customHeaders.any { it.name == "Accept" && it.value == "text/event-stream" })
    }

    @Test
    fun `codex params skip reasoning for non reasoning models`() {
        val params = TextGenerationParams(
            model = Model(modelId = "gpt-5-nano"),
            reasoningLevel = ReasoningLevel.OFF,
        )

        val codex = withCodexParams(params, codexAccount(), stream = false)

        assertNull(codex.customBody.firstOrNull { it.key == "reasoning" })
        assertTrue(codex.customHeaders.none { it.name == "Accept" })
    }

    private fun codexAccount() = CodexAccount(
        id = "user-1:account-1",
        name = "Test",
        chatgptAccountId = "account-1",
        accessToken = "token",
        refreshToken = "refresh",
        expiresAt = Long.MAX_VALUE,
    )
}
