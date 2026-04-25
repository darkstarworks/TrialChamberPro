package io.github.darkstarworks.trialChamberPro.models

import io.mockk.mockk
import org.bukkit.Material
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Smoke tests for [LootEditorDraft]. The draft is a passive mutable container,
 * but the dirty-flag plus the detail that `guaranteed` and `weighted` are
 * mutable lists drives a lot of the GUI's copy/preserve behavior — these tests
 * pin down the contract. v1.3.0 Phase 5.
 */
class LootEditorDraftTest {

    private fun item(): LootItem = LootItem(
        type = Material.DIAMOND,
        amountMin = 1,
        amountMax = 3,
        weight = 1.0,
        name = null,
        lore = null,
        enchantments = null,
        enabled = true
    )

    @Test
    fun `new draft starts not dirty`() {
        val d = LootEditorDraft("table", mutableListOf(), mutableListOf(), 1, 3)
        assertFalse(d.dirty)
    }

    @Test
    fun `dirty flag is settable`() {
        val d = LootEditorDraft("table", mutableListOf(), mutableListOf(), 1, 3)
        d.dirty = true
        assertTrue(d.dirty)
    }

    @Test
    fun `min and max rolls are settable`() {
        val d = LootEditorDraft("t", mutableListOf(), mutableListOf(), 1, 3)
        d.minRolls = 2
        d.maxRolls = 5
        assertEquals(2, d.minRolls)
        assertEquals(5, d.maxRolls)
    }

    @Test
    fun `tableName is settable`() {
        val d = LootEditorDraft("old-name", mutableListOf(), mutableListOf(), 1, 3)
        d.tableName = "new-name"
        assertEquals("new-name", d.tableName)
    }

    @Test
    fun `weighted list is mutable in place`() {
        val d = LootEditorDraft("t", mutableListOf(), mutableListOf(), 1, 3)
        d.weighted.add(item())
        assertEquals(1, d.weighted.size)
        d.weighted.removeAt(0)
        assertEquals(0, d.weighted.size)
    }

    @Test
    fun `guaranteed list is mutable in place`() {
        val d = LootEditorDraft("t", mutableListOf(), mutableListOf(), 1, 3)
        d.guaranteed.add(item())
        assertEquals(1, d.guaranteed.size)
    }

    @Test
    fun `data class equality compares by value`() {
        val a = LootEditorDraft("t", mutableListOf(), mutableListOf(), 1, 3)
        val b = LootEditorDraft("t", mutableListOf(), mutableListOf(), 1, 3)
        assertEquals(a, b)
    }

    @Test
    fun `data class equality distinguishes by tableName`() {
        val a = LootEditorDraft("a", mutableListOf(), mutableListOf(), 1, 3)
        val b = LootEditorDraft("b", mutableListOf(), mutableListOf(), 1, 3)
        assertNotEquals(a, b)
    }

    @Test
    fun `mockk reference compiles - sanity check for test-classpath setup`() {
        // Ensures Material from Paper API is available on the test classpath.
        val m = mockk<Material>(relaxed = true)
        assertNotNull(m)
    }
}
