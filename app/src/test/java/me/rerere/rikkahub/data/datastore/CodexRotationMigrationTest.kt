package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.CodexRotationMode
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CodexRotationMigrationTest {
    @Test
    fun `legacy enabled rotation becomes round robin`() {
        val provider = ProviderSetting.Codex(rotation = CodexRotationMode.OFF, poolEnabled = true)

        val migrated = listOf(provider).applyCodexRotationMigration().single()

        assertEquals(provider.copy(rotation = CodexRotationMode.ROUND_ROBIN, poolEnabled = false), migrated)
    }

    @Test
    fun `fill first rotation is preserved and legacy flag cleared`() {
        val provider = ProviderSetting.Codex(rotation = CodexRotationMode.FILL_FIRST, poolEnabled = true)

        val migrated = listOf(provider).applyCodexRotationMigration().single()

        assertEquals(provider.copy(poolEnabled = false), migrated)
    }

    @Test
    fun `disabled rotation stays off`() {
        val provider = ProviderSetting.Codex(rotation = CodexRotationMode.OFF, poolEnabled = false)

        val migrated = listOf(provider).applyCodexRotationMigration().single()

        assertSame(provider, migrated)
    }

    @Test
    fun `reapplying migration leaves result unchanged`() {
        val provider = ProviderSetting.Codex(rotation = CodexRotationMode.OFF, poolEnabled = true)
        val migrated = listOf(provider).applyCodexRotationMigration()

        assertEquals(migrated, migrated.applyCodexRotationMigration())
    }

    @Test
    fun `non-Codex provider passes through unchanged`() {
        val provider = ProviderSetting.OpenAI(poolEnabled = true)

        val migrated = listOf(provider).applyCodexRotationMigration().single()

        assertSame(provider, migrated)
    }
}
