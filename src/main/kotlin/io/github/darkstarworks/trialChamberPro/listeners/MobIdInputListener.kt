package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * One-shot chat input collector used by [io.github.darkstarworks.trialChamberPro.gui.CustomMobProviderView]
 * to add mob ids to a chamber's provider pool without a full anvil UI.
 *
 * The flow:
 * 1. GUI calls [awaitInput] with the chamber id and section (normal/ominous)
 * 2. GUI tells the player "type the mob id in chat, or 'cancel'"
 * 3. Next chat message from that player is cancelled and routed to [handleInput]
 * 4. [handleInput] appends the id via `ChamberManager.updateCustomMobProvider`
 *    and reopens the GUI on the main thread
 */
class MobIdInputListener(private val plugin: TrialChamberPro) : Listener {

    data class Pending(
        val chamberId: Int,
        val section: Section,
        val expiresAt: Long
    )

    enum class Section { NORMAL, OMINOUS }

    companion object {
        private val pending = ConcurrentHashMap<UUID, Pending>()
        private const val TIMEOUT_MS = 30_000L

        /** Registers a pending input for [playerId]. Overwrites any previous pending entry. */
        fun awaitInput(playerId: UUID, chamberId: Int, section: Section) {
            pending[playerId] = Pending(chamberId, section, System.currentTimeMillis() + TIMEOUT_MS)
        }

        fun cancel(playerId: UUID) {
            pending.remove(playerId)
        }

        fun hasPending(playerId: UUID): Boolean {
            val p = pending[playerId] ?: return false
            if (p.expiresAt < System.currentTimeMillis()) {
                pending.remove(playerId)
                return false
            }
            return true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val p = pending[player.uniqueId] ?: return

        if (p.expiresAt < System.currentTimeMillis()) {
            pending.remove(player.uniqueId)
            return
        }

        event.isCancelled = true
        pending.remove(player.uniqueId)

        val raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim()
        if (raw.isEmpty() || raw.equals("cancel", ignoreCase = true)) {
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessage("gui-mob-input-cancelled"))
                reopen(player.uniqueId, p.chamberId)
            })
            return
        }

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getCachedChamberById(p.chamberId)
            if (chamber == null) {
                plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendMessage(plugin.getMessage("gui-mob-input-no-chamber"))
                })
                return@launchAsync
            }

            val normal = chamber.customMobIdsNormal.toMutableList()
            val ominous = chamber.customMobIdsOminous.toMutableList()
            val targetList = if (p.section == Section.NORMAL) normal else ominous

            if (targetList.any { it.equals(raw, ignoreCase = true) }) {
                plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendMessage(plugin.getMessage("gui-mob-input-duplicate", "id" to raw))
                    reopen(player.uniqueId, p.chamberId)
                })
                return@launchAsync
            }
            targetList += raw

            val ok = plugin.chamberManager.updateCustomMobProvider(
                chamber.id,
                chamber.customMobProvider,
                normal,
                ominous
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                if (ok) {
                    player.sendMessage(plugin.getMessage("gui-mob-input-added",
                        "id" to raw,
                        "section" to p.section.name.lowercase()))
                } else {
                    player.sendMessage(plugin.getMessage("gui-mob-input-failed"))
                }
                reopen(player.uniqueId, p.chamberId)
            })
        }
    }

    private fun reopen(playerId: UUID, chamberId: Int) {
        val player = plugin.server.getPlayer(playerId) ?: return
        val refreshed = plugin.chamberManager.getCachedChamberById(chamberId) ?: return
        plugin.menuService.openCustomMobProvider(player, refreshed)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)
    }
}
