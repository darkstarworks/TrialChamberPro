package io.github.darkstarworks.trialChamberPro.config

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bukkit.configuration.file.FileConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.logging.Logger

/**
 * Tests for [ConfigValidator]. The validator is a thin wrapper around Bukkit's
 * `FileConfiguration` so the tests use a real `MemoryConfiguration` (or a MockK
 * mock when we want to inspect set-calls). v1.3.0 Phase 5.
 */
class ConfigValidatorTest {

    private fun mockPluginWith(config: FileConfiguration): TrialChamberPro {
        val plugin = mockk<TrialChamberPro>(relaxed = true)
        every { plugin.config } returns config
        every { plugin.logger } returns Logger.getLogger("ConfigValidatorTest")
        return plugin
    }

    @Test
    fun `validate returns 0 for an empty config`() {
        // No keys present → nothing to clamp
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains(any<String>()) } returns false
        val plugin = mockPluginWith(config)

        assertEquals(0, ConfigValidator.validate(plugin))
        verify(exactly = 0) { config.set(any(), any()) }
    }

    @Test
    fun `validate clamps a negative cooldown to its lower bound`() {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains("vaults.normal-cooldown-hours") } returns true
        every { config.contains(not("vaults.normal-cooldown-hours")) } returns false
        every { config.getLong("vaults.normal-cooldown-hours", Long.MIN_VALUE) } returns -5L

        val captured = slot<Any>()
        every { config.set("vaults.normal-cooldown-hours", capture(captured)) } returns Unit

        val plugin = mockPluginWith(config)
        val clamped = ConfigValidator.validate(plugin)

        assertEquals(1, clamped)
        assertEquals(0L, captured.captured, "negative cooldown should clamp to 0")
    }

    @Test
    fun `validate clamps a too-large generation max-volume to the upper bound`() {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains("generation.max-volume") } returns true
        every { config.contains(not("generation.max-volume")) } returns false
        every { config.getLong("generation.max-volume", Long.MIN_VALUE) } returns 999_999_999L

        val captured = slot<Any>()
        every { config.set("generation.max-volume", capture(captured)) } returns Unit

        val plugin = mockPluginWith(config)
        val clamped = ConfigValidator.validate(plugin)

        assertEquals(1, clamped)
        assertEquals(5_000_000L, captured.captured)
    }

    @Test
    fun `validate leaves an in-range value alone`() {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains("performance.blocks-per-tick") } returns true
        every { config.contains(not("performance.blocks-per-tick")) } returns false
        every { config.getLong("performance.blocks-per-tick", Long.MIN_VALUE) } returns 1000L

        val plugin = mockPluginWith(config)
        val clamped = ConfigValidator.validate(plugin)

        assertEquals(0, clamped)
        verify(exactly = 0) { config.set("performance.blocks-per-tick", any()) }
    }

    @Test
    fun `validate accepts the sentinel -1 for spawner-cooldown-minutes`() {
        // The rule has min = 0 but allowSentinel = -1, so -1 must NOT be clamped.
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains("reset.spawner-cooldown-minutes") } returns true
        every { config.contains(not("reset.spawner-cooldown-minutes")) } returns false
        every { config.getLong("reset.spawner-cooldown-minutes", Long.MIN_VALUE) } returns -1L

        val plugin = mockPluginWith(config)
        val clamped = ConfigValidator.validate(plugin)

        assertEquals(0, clamped)
        verify(exactly = 0) { config.set("reset.spawner-cooldown-minutes", any()) }
    }

    @Test
    fun `validate clamps -2 on a sentinel-allowing key (sentinel is exact match only)`() {
        // -1 is allowed via sentinel; -2 should still clamp to 0.
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains("reset.spawner-cooldown-minutes") } returns true
        every { config.contains(not("reset.spawner-cooldown-minutes")) } returns false
        every { config.getLong("reset.spawner-cooldown-minutes", Long.MIN_VALUE) } returns -2L

        val captured = slot<Any>()
        every { config.set("reset.spawner-cooldown-minutes", capture(captured)) } returns Unit

        val plugin = mockPluginWith(config)
        val clamped = ConfigValidator.validate(plugin)

        assertEquals(1, clamped)
        assertEquals(0L, captured.captured)
    }

    @Test
    fun `validate writes the default when getLong returns the unparseable sentinel`() {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains("vaults.normal-cooldown-hours") } returns true
        every { config.contains(not("vaults.normal-cooldown-hours")) } returns false
        every { config.getLong("vaults.normal-cooldown-hours", Long.MIN_VALUE) } returns Long.MIN_VALUE

        val captured = slot<Any>()
        every { config.set("vaults.normal-cooldown-hours", capture(captured)) } returns Unit

        val plugin = mockPluginWith(config)
        val clamped = ConfigValidator.validate(plugin)

        assertEquals(1, clamped)
        assertEquals(24L, captured.captured, "unparseable input falls back to the rule's default")
    }

    @Test
    fun `validate counts multiple clamps`() {
        val config = mockk<FileConfiguration>(relaxed = true)
        every { config.contains(any<String>()) } returns false
        every { config.contains("vaults.normal-cooldown-hours") } returns true
        every { config.contains("vaults.ominous-cooldown-hours") } returns true
        every { config.getLong("vaults.normal-cooldown-hours", Long.MIN_VALUE) } returns -10L
        every { config.getLong("vaults.ominous-cooldown-hours", Long.MIN_VALUE) } returns -20L
        every { config.set(any<String>(), any()) } returns Unit

        val plugin = mockPluginWith(config)
        assertEquals(2, ConfigValidator.validate(plugin))
    }
}
