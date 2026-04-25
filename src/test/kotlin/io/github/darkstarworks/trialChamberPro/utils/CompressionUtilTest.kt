package io.github.darkstarworks.trialChamberPro.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.Serializable
import kotlin.random.Random

/**
 * Pure-logic tests for [CompressionUtil] — no Bukkit, no mocks. Exercises gzip
 * roundtripping at sizes that exposed bugs in earlier snapshot work (very small
 * payloads where the gzip header dominates, and the 1MB band that's typical of
 * a small chamber's block array).
 *
 * Added in v1.3.0 Phase 5 alongside the rest of the test foundation.
 */
class CompressionUtilTest {

    @Test
    fun `roundtrip empty byte array`() {
        val compressed = CompressionUtil.compress(ByteArray(0))
        val decompressed = CompressionUtil.decompress(compressed)
        assertEquals(0, decompressed.size)
    }

    @Test
    fun `roundtrip single byte`() {
        val original = byteArrayOf(0x42)
        val decompressed = CompressionUtil.decompress(CompressionUtil.compress(original))
        assertArrayEquals(original, decompressed)
    }

    @Test
    fun `roundtrip ASCII text preserves content`() {
        val original = "TrialChamberPro v1.3.0 — gzip roundtrip canary".toByteArray(Charsets.UTF_8)
        val decompressed = CompressionUtil.decompress(CompressionUtil.compress(original))
        assertArrayEquals(original, decompressed)
    }

    @Test
    fun `roundtrip 1MB random buffer preserves content`() {
        val original = ByteArray(1024 * 1024).also { Random(0xC0FFEE).nextBytes(it) }
        val decompressed = CompressionUtil.decompress(CompressionUtil.compress(original))
        assertArrayEquals(original, decompressed)
    }

    @Test
    fun `compression of highly-redundant data is much smaller than input`() {
        val original = ByteArray(64 * 1024) { 0x00 } // 64 KiB of zeros
        val compressed = CompressionUtil.compress(original)
        assertTrue(
            compressed.size < original.size / 10,
            "expected >10x compression on all-zeros input, got ${compressed.size} bytes from ${original.size}"
        )
    }

    @Test
    fun `decompress rejects invalid gzip data`() {
        val notGzip = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertThrows(Exception::class.java) {
            CompressionUtil.decompress(notGzip)
        }
    }

    @Test
    fun `compressObject roundtrip preserves serializable payload`() {
        val original = TestPayload("hello", 42, listOf("a", "b", "c"))
        val compressed = CompressionUtil.compressObject(original)
        val restored: TestPayload = CompressionUtil.decompressObject(compressed)
        assertEquals(original, restored)
    }

    @Test
    fun `formatSize reports bytes for small values`() {
        assertEquals("0 bytes", CompressionUtil.formatSize(0))
        assertEquals("512 bytes", CompressionUtil.formatSize(512))
        assertEquals("1023 bytes", CompressionUtil.formatSize(1023))
    }

    @Test
    fun `formatSize switches to KB at 1024 bytes`() {
        assertTrue(CompressionUtil.formatSize(1024).endsWith("KB"))
        assertTrue(CompressionUtil.formatSize(1024 * 100).endsWith("KB"))
    }

    @Test
    fun `formatSize switches to MB at 1MB`() {
        assertTrue(CompressionUtil.formatSize(1024L * 1024).endsWith("MB"))
        assertTrue(CompressionUtil.formatSize(1024L * 1024 * 500).endsWith("MB"))
    }

    @Test
    fun `formatSize switches to GB at 1GB`() {
        assertTrue(CompressionUtil.formatSize(1024L * 1024 * 1024).endsWith("GB"))
    }

    private data class TestPayload(
        val text: String,
        val number: Int,
        val items: List<String>
    ) : Serializable
}
