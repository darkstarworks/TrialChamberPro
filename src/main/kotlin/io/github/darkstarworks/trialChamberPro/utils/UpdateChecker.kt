package io.github.darkstarworks.trialChamberPro.utils

import com.google.gson.JsonParser
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.net.URI

/**
 * Checks for plugin updates from GitHub releases.
 */
class UpdateChecker(
    private val plugin: JavaPlugin,
    private val githubRepo: String,
    private val updateDescriptionUrl: String? = null
) {
    private val currentVersion = plugin.pluginMeta.version

    fun checkForUpdates(notifyConsole: Boolean = true) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val latestVersion = fetchLatestVersion()
                if (isNewerVersion(latestVersion)) {
                    val updateInfo = updateDescriptionUrl?.let { fetchUpdateDescription(it) } 
                        ?: "Check GitHub for details"
                    val message = "[${plugin.name}] Update available: $latestVersion (current: $currentVersion) - $updateInfo"
                    if (notifyConsole) plugin.logger.info(message)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to check for updates: ${e.message}")
            }
        })
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
