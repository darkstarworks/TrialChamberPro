package io.github.darkstarworks.trialChamberPro.commands.handlers

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [CoordinateParser]. Covers the two argument shapes the
 * `/tcp generate coords` command accepts (comma-triple and hyphen-pair) plus
 * the malformed-input rejection paths. v1.3.0 Phase 5.
 */
class CoordinateParserTest {

    // ==================== parseTriple ====================

    @Test
    fun `parseTriple accepts plain integers`() {
        val r = CoordinateParser.parseTriple("100,64,-200")
        assertEquals(Triple(100, 64, -200), r)
    }

    @Test
    fun `parseTriple tolerates whitespace around tokens`() {
        val r = CoordinateParser.parseTriple("  10 , 64 ,  -5  ")
        assertEquals(Triple(10, 64, -5), r)
    }

    @Test
    fun `parseTriple handles all-negative coordinates`() {
        val r = CoordinateParser.parseTriple("-1000,-50,-2000")
        assertEquals(Triple(-1000, -50, -2000), r)
    }

    @Test
    fun `parseTriple rejects floats`() {
        assertNull(CoordinateParser.parseTriple("1.5,2,3"))
        assertNull(CoordinateParser.parseTriple("1,2.0,3"))
    }

    @Test
    fun `parseTriple rejects non-numeric tokens`() {
        assertNull(CoordinateParser.parseTriple("abc,2,3"))
        assertNull(CoordinateParser.parseTriple("1,~,3"))
    }

    @Test
    fun `parseTriple rejects wrong arity`() {
        assertNull(CoordinateParser.parseTriple("1,2"))
        assertNull(CoordinateParser.parseTriple("1,2,3,4"))
        assertNull(CoordinateParser.parseTriple(""))
    }

    @Test
    fun `parseTriple rejects bare numbers`() {
        assertNull(CoordinateParser.parseTriple("1234"))
    }

    // ==================== isTripleString ====================

    @Test
    fun `isTripleString returns false for null and blank`() {
        assertFalse(CoordinateParser.isTripleString(null))
        assertFalse(CoordinateParser.isTripleString(""))
        assertFalse(CoordinateParser.isTripleString("   "))
    }

    @Test
    fun `isTripleString returns true for valid triples`() {
        assertTrue(CoordinateParser.isTripleString("0,0,0"))
        assertTrue(CoordinateParser.isTripleString("-1,2,-3"))
    }

    @Test
    fun `isTripleString returns false for valid-arity but non-numeric input`() {
        assertFalse(CoordinateParser.isTripleString("a,b,c"))
    }

    // ==================== parseHyphenated ====================

    @Test
    fun `parseHyphenated parses a clean pair`() {
        val r = CoordinateParser.parseHyphenated("10,64,10-40,80,40")
        assertEquals(
            Pair(Triple(10, 64, 10), Triple(40, 80, 40)),
            r
        )
    }

    @Test
    fun `parseHyphenated handles negative coordinates on both ends`() {
        val r = CoordinateParser.parseHyphenated("-30,-10,-30--5,5,-5")
        assertEquals(
            Pair(Triple(-30, -10, -30), Triple(-5, 5, -5)),
            r
        )
    }

    @Test
    fun `parseHyphenated tolerates whitespace around components`() {
        val r = CoordinateParser.parseHyphenated("  10 , 64 , 10  -  40 , 80 , 40  ")
        assertEquals(
            Pair(Triple(10, 64, 10), Triple(40, 80, 40)),
            r
        )
    }

    @Test
    fun `parseHyphenated rejects missing hyphen`() {
        assertNull(CoordinateParser.parseHyphenated("10,64,10,40,80,40"))
    }

    @Test
    fun `parseHyphenated rejects missing axis`() {
        assertNull(CoordinateParser.parseHyphenated("10,64-40,80,40"))
        assertNull(CoordinateParser.parseHyphenated("10,64,10-40,80"))
    }

    @Test
    fun `parseHyphenated rejects floats`() {
        assertNull(CoordinateParser.parseHyphenated("1.5,2,3-4,5,6"))
    }

    @Test
    fun `parseHyphenated rejects garbage input`() {
        assertNull(CoordinateParser.parseHyphenated("not-coordinates"))
        assertNull(CoordinateParser.parseHyphenated(""))
        assertNull(CoordinateParser.parseHyphenated("---"))
    }
}
