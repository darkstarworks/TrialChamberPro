package io.github.darkstarworks.trialChamberPro.models

import io.mockk.every
import io.mockk.mockk
import org.bukkit.Location
import org.bukkit.World
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for the [Chamber] model — bounds math, lookup helpers, and custom-mob
 * provider behavior. Bukkit `Location` and `World` references are mocked via
 * MockK so the tests run without a server. Added in v1.3.0 Phase 5.
 */
class ChamberTest {

    private fun chamber(
        minX: Int = 0, minY: Int = 64, minZ: Int = 0,
        maxX: Int = 30, maxY: Int = 78, maxZ: Int = 30,
        worldName: String = "world",
        normalIds: List<String> = emptyList(),
        ominousIds: List<String> = emptyList(),
        provider: String? = null,
        normalLootTable: String? = null,
        ominousLootTable: String? = null
    ): Chamber = Chamber(
        id = 1, name = "test", world = worldName,
        minX = minX, minY = minY, minZ = minZ,
        maxX = maxX, maxY = maxY, maxZ = maxZ,
        resetInterval = 3600L,
        createdAt = 0L,
        normalLootTable = normalLootTable,
        ominousLootTable = ominousLootTable,
        customMobProvider = provider,
        customMobIdsNormal = normalIds,
        customMobIdsOminous = ominousIds
    )

    private fun loc(x: Int, y: Int, z: Int, worldName: String = "world"): Location {
        val w = mockk<World>()
        every { w.name } returns worldName
        val l = mockk<Location>()
        every { l.world } returns w
        every { l.blockX } returns x
        every { l.blockY } returns y
        every { l.blockZ } returns z
        return l
    }

    // ==================== getVolume ====================

    @Test
    fun `getVolume includes both endpoints`() {
        // 31 x 15 x 31 = 14415 — vanilla minimum chamber size
        val c = chamber(0, 64, 0, 30, 78, 30)
        assertEquals(31 * 15 * 31, c.getVolume())
    }

    @Test
    fun `getVolume on a single-block chamber returns 1`() {
        val c = chamber(0, 64, 0, 0, 64, 0)
        assertEquals(1, c.getVolume())
    }

    @Test
    fun `getVolume tolerates negative coordinates`() {
        val c = chamber(-100, -10, -50, -70, 0, -20)
        assertEquals(31 * 11 * 31, c.getVolume())
    }

    // ==================== contains ====================

    @Test
    fun `contains returns true for points strictly inside`() {
        val c = chamber()
        assertTrue(c.contains(loc(15, 70, 15)))
    }

    @Test
    fun `contains is inclusive at the min corner`() {
        val c = chamber(0, 64, 0, 30, 78, 30)
        assertTrue(c.contains(loc(0, 64, 0)))
    }

    @Test
    fun `contains is inclusive at the max corner`() {
        val c = chamber(0, 64, 0, 30, 78, 30)
        assertTrue(c.contains(loc(30, 78, 30)))
    }

    @Test
    fun `contains rejects points one block past max in any axis`() {
        val c = chamber(0, 64, 0, 30, 78, 30)
        assertFalse(c.contains(loc(31, 78, 30)), "x past max")
        assertFalse(c.contains(loc(30, 79, 30)), "y past max")
        assertFalse(c.contains(loc(30, 78, 31)), "z past max")
    }

    @Test
    fun `contains rejects points one block before min in any axis`() {
        val c = chamber(0, 64, 0, 30, 78, 30)
        assertFalse(c.contains(loc(-1, 70, 15)), "x before min")
        assertFalse(c.contains(loc(15, 63, 15)), "y before min")
        assertFalse(c.contains(loc(15, 70, -1)), "z before min")
    }

    @Test
    fun `contains rejects locations in a different world`() {
        val c = chamber(worldName = "world")
        assertFalse(c.contains(loc(15, 70, 15, worldName = "world_nether")))
    }

    @Test
    fun `contains works with negative coordinates`() {
        val c = chamber(-100, -10, -50, -70, 0, -20)
        assertTrue(c.contains(loc(-85, -5, -35)))
        assertFalse(c.contains(loc(-101, -5, -35)))
    }

    // ==================== getLootTable ====================

    @Test
    fun `getLootTable returns null when no override is set`() {
        val c = chamber()
        assertNull(c.getLootTable(VaultType.NORMAL))
        assertNull(c.getLootTable(VaultType.OMINOUS))
    }

    @Test
    fun `getLootTable returns the configured override per vault type`() {
        val c = chamber(normalLootTable = "rich-normal", ominousLootTable = "rich-ominous")
        assertEquals("rich-normal", c.getLootTable(VaultType.NORMAL))
        assertEquals("rich-ominous", c.getLootTable(VaultType.OMINOUS))
    }

    // ==================== hasCustomMobProvider ====================

    @Test
    fun `hasCustomMobProvider is false when provider is null`() {
        val c = chamber(provider = null, normalIds = listOf("ZOMBIE"))
        assertFalse(c.hasCustomMobProvider(false))
    }

    @Test
    fun `hasCustomMobProvider is false when provider is the literal vanilla`() {
        val c = chamber(provider = "vanilla", normalIds = listOf("ZOMBIE"))
        assertFalse(c.hasCustomMobProvider(false))
    }

    @Test
    fun `hasCustomMobProvider is case-insensitive on the vanilla check`() {
        val c = chamber(provider = "VANILLA", normalIds = listOf("ZOMBIE"))
        assertFalse(c.hasCustomMobProvider(false))
    }

    @Test
    fun `hasCustomMobProvider needs at least one mob id`() {
        val c = chamber(provider = "mythicmobs", normalIds = emptyList(), ominousIds = emptyList())
        assertFalse(c.hasCustomMobProvider(false))
    }

    @Test
    fun `hasCustomMobProvider is true with provider plus normal ids`() {
        val c = chamber(provider = "mythicmobs", normalIds = listOf("SkeletonKing"))
        assertTrue(c.hasCustomMobProvider(false))
    }

    @Test
    fun `hasCustomMobProvider for ominous falls back to normal ids`() {
        val c = chamber(
            provider = "mythicmobs",
            normalIds = listOf("SkeletonKing"),
            ominousIds = emptyList()
        )
        assertTrue(c.hasCustomMobProvider(ominous = true))
    }

    // ==================== pickMobId ====================

    @Test
    fun `pickMobId returns null when both lists are empty`() {
        val c = chamber()
        assertNull(c.pickMobId(false))
        assertNull(c.pickMobId(true))
    }

    @Test
    fun `pickMobId picks from the ominous list when ominous and ominous list is non-empty`() {
        val c = chamber(
            normalIds = listOf("normal-mob"),
            ominousIds = listOf("ominous-mob")
        )
        assertEquals("ominous-mob", c.pickMobId(true))
    }

    @Test
    fun `pickMobId falls back to normal list when ominous list is empty`() {
        val c = chamber(
            normalIds = listOf("only-normal"),
            ominousIds = emptyList()
        )
        assertEquals("only-normal", c.pickMobId(true))
    }

    @Test
    fun `pickMobId returns the only id deterministically when list size is 1`() {
        val c = chamber(normalIds = listOf("single"))
        repeat(10) { assertEquals("single", c.pickMobId(false)) }
    }
}
