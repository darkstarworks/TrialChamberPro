package io.github.darkstarworks.trialChamberPro.commands.handlers

/**
 * Pure-logic coordinate parsing for `/tcp generate` argument shapes. Extracted
 * from [GenerateCommand] in v1.3.0 Phase 5 so the parsers can be unit-tested
 * without mocking Bukkit's `World` registry. The world-aware composite parsing
 * (resolving world names, falling back to the sender's world, etc.) stays in
 * `GenerateCommand.parseCoordsArgs` because it needs the live Bukkit `World`
 * lookup.
 */
object CoordinateParser {

    /**
     * Parses a single hyphen-separated coordinate pair of the form
     * `x1,y1,z1-x2,y2,z2`. Whitespace around tokens is tolerated; negative
     * values are supported on every axis. Returns `null` for any malformed
     * input rather than throwing.
     */
    fun parseHyphenated(input: String): Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>? {
        val regex = Regex(
            "^\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*$"
        )
        val m = regex.matchEntire(input) ?: return null
        return Pair(
            Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()),
            Triple(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].toInt())
        )
    }

    /**
     * Parses a comma-separated triple `x,y,z`. Returns `null` for malformed
     * input. Tolerates whitespace around each token; rejects floats.
     */
    fun parseTriple(input: String): Triple<Int, Int, Int>? {
        val parts = input.split(",")
        if (parts.size != 3) return null
        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null
        val z = parts[2].trim().toIntOrNull() ?: return null
        return Triple(x, y, z)
    }

    /** True iff [input] is a syntactically valid coordinate triple. */
    fun isTripleString(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        return parseTriple(input) != null
    }
}
