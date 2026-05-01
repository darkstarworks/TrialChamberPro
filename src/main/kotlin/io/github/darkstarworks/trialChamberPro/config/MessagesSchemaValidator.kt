package io.github.darkstarworks.trialChamberPro.config

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader

/**
 * Startup schema check for `messages.yml` (added in v1.4.1).
 *
 * Compares the leaf keys present in the user's deployed `messages.yml` against
 * the leaf keys present in the JAR-bundled defaults. Any key the bundle defines
 * but the user's file lacks is reported as missing, because at runtime
 * [TrialChamberPro.getGuiText] / [TrialChamberPro.getMessage] fall back to the
 * literal string `<missing: <key>>`, which surfaces in chat and GUI tooltips.
 *
 * The check is purely informative — it only logs. It never modifies the user's
 * file (we don't want to clobber translations) and never blocks startup.
 *
 * Triggered once during [TrialChamberPro.onEnable] right after
 * `saveResource("messages.yml", false)`. Bypass with the config flag
 * `debug.skip-messages-schema-check: true` (false by default).
 */
object MessagesSchemaValidator {

    /**
     * Maximum number of missing keys to enumerate in the log. Beyond this we
     * truncate and just print a count, so a wildly out-of-date `messages.yml`
     * doesn't flood the console.
     */
    private const val MAX_KEYS_LOGGED = 25

    fun validate(plugin: TrialChamberPro) {
        if (plugin.config.getBoolean("debug.skip-messages-schema-check", false)) return

        val userFile = File(plugin.dataFolder, "messages.yml")
        if (!userFile.exists()) {
            // saveResource just ran — if this is missing the JAR doesn't ship it.
            // Either way, nothing for this check to compare.
            return
        }

        val bundledKeys: Set<String> = try {
            val resource = plugin.getResource("messages.yml") ?: return
            val bundled = InputStreamReader(resource, Charsets.UTF_8).use { reader ->
                YamlConfiguration.loadConfiguration(reader)
            }
            collectLeafKeys(bundled)
        } catch (e: Exception) {
            plugin.logger.warning("[messages.yml] Could not read bundled defaults for schema check: ${e.message}")
            return
        }

        val userConfig = try {
            YamlConfiguration.loadConfiguration(userFile)
        } catch (e: Exception) {
            plugin.logger.warning("[messages.yml] Could not parse your messages.yml for schema check: ${e.message}")
            return
        }

        // A user key is "present" if it exists *and* is not itself a section
        // (otherwise a parent path like `gui.help-menu` would mask a missing
        // leaf `gui.help-menu.title`). The bundle contributes leaves only.
        val missing = bundledKeys.filter { key ->
            !userConfig.contains(key) || userConfig.isConfigurationSection(key)
        }

        if (missing.isEmpty()) return

        plugin.logger.warning("=".repeat(72))
        plugin.logger.warning("[messages.yml] Your file is missing ${missing.size} key(s) that this")
        plugin.logger.warning("[messages.yml] version of TrialChamberPro expects. The plugin will fall")
        plugin.logger.warning("[messages.yml] back to literal '<missing: <key>>' text wherever those keys")
        plugin.logger.warning("[messages.yml] are referenced (chat output, item names, item lores, GUI titles).")
        plugin.logger.warning("")

        val shown = missing.take(MAX_KEYS_LOGGED)
        for (key in shown) {
            plugin.logger.warning("[messages.yml]   - $key")
        }
        if (missing.size > MAX_KEYS_LOGGED) {
            plugin.logger.warning("[messages.yml]   ... and ${missing.size - MAX_KEYS_LOGGED} more")
        }

        plugin.logger.warning("")
        plugin.logger.warning("[messages.yml] To fix:")
        plugin.logger.warning("[messages.yml]   1. Stop the server.")
        plugin.logger.warning("[messages.yml]   2. Rename plugins/TrialChamberPro/messages.yml to")
        plugin.logger.warning("[messages.yml]      messages.yml.bak so the plugin regenerates a fresh one.")
        plugin.logger.warning("[messages.yml]   3. Start the server.")
        plugin.logger.warning("[messages.yml]   4. If you had translations, port them over from the .bak file.")
        plugin.logger.warning("")
        plugin.logger.warning("[messages.yml] To silence this check, set `debug.skip-messages-schema-check: true`")
        plugin.logger.warning("[messages.yml] in config.yml.")
        plugin.logger.warning("=".repeat(72))
    }

    /**
     * Walks the YAML tree and returns every leaf path — i.e. every key whose
     * value is a String, list, number, etc. but NOT a [ConfigurationSection].
     * Section paths are not interesting because they don't carry text.
     */
    private fun collectLeafKeys(config: org.bukkit.configuration.file.FileConfiguration): Set<String> {
        val out = LinkedHashSet<String>()
        for (key in config.getKeys(true)) {
            val value = config.get(key)
            if (value !is ConfigurationSection) {
                out.add(key)
            }
        }
        return out
    }
}
