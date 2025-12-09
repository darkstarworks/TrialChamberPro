package io.github.darkstarworks.trialChamberPro.utils

import com.google.gson.JsonParser
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import java.net.URI

/**
 * Checks for plugin updates from GitHub releases.
 * Folia compatible: Uses scheduler adapter for async operations.
 */
class UpdateChecker(
    private val plugin: TrialChamberPro,
    private val githubRepo: String,
    private val updateDescriptionUrl: String? = null
) {
    private val currentVersion = plugin.pluginMeta.version

    fun checkForUpdates(notifyConsole: Boolean = true) {
        plugin.scheduler.runTaskAsync(Runnable {
            try {
                val latestVersion = fetchLatestVersion()
                if (isNewerVersion(latestVersion)) {
                    val updateInfo = updateDescriptionUrl?.let { fetchUpdateDescription(it) }
                        ?: "Check GitHub for details"
                    if (notifyConsole) {
                        val header = "<gold>[${plugin.name}]</gold> <green>Update available:</green> <yellow>$latestVersion</yellow> <gray>(current: $currentVersion)</gray>"
                        sendColoredConsoleMessage(header)
                        // Send each line of the update info separately for proper formatting
                        updateInfo.lines().forEach { line ->
                            if (line.isNotBlank()) sendColoredConsoleMessage(line)
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to check for updates: ${e.message}")
            }
        })
    }

    private fun sendColoredConsoleMessage(message: String) {
        val component = MiniMessage.miniMessage().deserialize(message)
        Bukkit.getConsoleSender().sendMessage(component)
    }

    private fun fetchUpdateDescription(url: String): String =
        URI(url).toURL().readText().trim()

    private fun fetchLatestVersion(): String {
        val url = "https://api.github.com/repos/$githubRepo/releases/latest"
        val json = URI(url).toURL().readText()
        return JsonParser.parseString(json).asJsonObject["tag_name"].asString.removePrefix("v")
    }

    private fun isNewerVersion(latest: String): Boolean {
        val current = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val remote = latest.split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(current.size, remote.size)) {
            val c = current.getOrNull(i) ?: 0
            val r = remote.getOrNull(i) ?: 0
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
