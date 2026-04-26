package io.github.darkstarworks.trialChamberPro.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/**
 * Unified text parser for v1.4.0+.
 *
 * Translates a raw message string into an Adventure [Component] using
 * **MiniMessage** as the primary syntax, while remaining fully backwards-
 * compatible with legacy `&` colour/format codes (and `&#RRGGBB` hex codes).
 * Existing `messages.yml` entries continue to render unchanged; users can
 * start adopting MiniMessage tags entry-by-entry without breakage.
 *
 * **How the conversion works** (cheap, no Component round-trip):
 * 1. Pre-convert legacy `&[0-9a-fk-or]` codes to their MiniMessage tag
 *    equivalents (`&a` → `<green>`, `&l` → `<bold>`, `&r` → `<reset>`).
 * 2. Pre-convert legacy `&#RRGGBB` hex codes to MiniMessage hex tags
 *    (`<#RRGGBB>`).
 * 3. Run the resulting string through [MiniMessage.deserialize].
 *
 * The pre-conversion uses unclosed MM tags (`<green>` rather than
 * `<green>...</green>`), which MiniMessage handles by applying the style
 * forward until reset — matching legacy `&`-code semantics exactly.
 *
 * **Mixed input is fully supported:** `&aHello <gradient:red:gold>world</gradient>`
 * produces green "Hello " followed by a red→gold gradient "world".
 *
 * **Section codes (`§`) are not pre-converted.** Bukkit's section character
 * is a runtime artefact (the result of legacy parsing) and should never
 * appear in source `messages.yml` text.
 */
object MessageParser {

    private val mm: MiniMessage = MiniMessage.miniMessage()
    private val legacySection: LegacyComponentSerializer = LegacyComponentSerializer.legacySection()

    private val LEGACY_HEX_REGEX = Regex("&#([0-9a-fA-F]{6})")
    private val LEGACY_CODE_REGEX = Regex("&([0-9a-fk-orA-FK-OR])")

    private val LEGACY_TO_MM = mapOf(
        '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua",
        '4' to "dark_red", '5' to "dark_purple", '6' to "gold", '7' to "gray",
        '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua",
        'c' to "red", 'd' to "light_purple", 'e' to "yellow", 'f' to "white",
        'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough",
        'n' to "underlined", 'o' to "italic", 'r' to "reset"
    )

    /**
     * Parse a raw string (MiniMessage, legacy `&` codes, or any mix) into a
     * fully-styled [Component]. Use this whenever the consumer accepts a
     * Component (modern Bukkit/Paper APIs: `Player.sendMessage(Component)`,
     * `BossBar.name(Component)`, `ItemMeta.displayName(Component)`, etc.).
     */
    fun parse(input: String): Component {
        if (input.isEmpty()) return Component.empty()
        val mmified = legacyToMiniMessage(input)
        return try {
            mm.deserialize(mmified)
        } catch (e: Exception) {
            // Malformed MM tag in user-supplied text shouldn't crash the
            // server. Fall back to raw text so the admin can see the bad
            // entry rendered verbatim and fix it.
            Component.text(input)
        }
    }

    /**
     * Parse a raw string and serialize the result back to a legacy
     * **section-coded** String (`§a...`). Used by the deprecated `String`
     * variant of [io.github.darkstarworks.trialChamberPro.TrialChamberPro.getMessage]
     * for backwards compatibility with code paths that haven't migrated to
     * Component-based output yet.
     *
     * MiniMessage features that have no legacy equivalent — gradients,
     * click events, hover events, fonts — degrade to plain text or a single
     * representative colour. For full fidelity use [parse] (returns a
     * Component) instead.
     */
    fun parseToLegacy(input: String): String {
        if (input.isEmpty()) return ""
        return legacySection.serialize(parse(input))
    }

    /**
     * Convert legacy `&` codes (including `&#RRGGBB` hex) to their
     * MiniMessage tag equivalents textually, leaving any existing MM tags
     * untouched. Public so callers that want to inspect the converted form
     * (e.g. for debugging) can do so without round-tripping through a
     * Component.
     */
    fun legacyToMiniMessage(input: String): String {
        // Hex first so the broader code regex doesn't gobble the leading `&`
        // of a `&#RRGGBB` sequence.
        val withHex = LEGACY_HEX_REGEX.replace(input) { match ->
            "<#${match.groupValues[1]}>"
        }
        return LEGACY_CODE_REGEX.replace(withHex) { match ->
            val ch = match.groupValues[1].lowercase().first()
            val tagName = LEGACY_TO_MM[ch] ?: return@replace match.value
            "<$tagName>"
        }
    }
}
