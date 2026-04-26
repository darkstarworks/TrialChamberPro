package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultData
import io.github.darkstarworks.trialChamberPro.models.VaultType
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired immediately before a vault open begins resolving its loot table —
 * after the spam-click / cooldown / key-validation gates in
 * `VaultInteractListener` have all passed, but BEFORE the loot table is
 * resolved or any reward is granted.
 *
 * Cancellable. If a listener cancels the event, the open is aborted in the
 * same way a missing loot table would abort: no key consumed, no loot
 * generated, no reward marked, no statistics incremented. The player
 * receives no plugin message — listeners that cancel are responsible for
 * their own user-facing feedback.
 *
 * Listeners may also override the loot table that will be used for this
 * specific open by setting [lootTableOverride] to a non-null table id. The
 * override takes priority over both the chamber's per-type loot override and
 * the vault's own default. If the override resolves to a missing table, the
 * vault open will fail the same way an invalid default would (no key
 * consumed). Setting [lootTableOverride] to `null` (the default) means
 * "use normal resolution".
 *
 * Fires on whichever thread the vault open coroutine is running on — almost
 * always an IO-dispatcher coroutine, never the primary thread. The event is
 * therefore delivered as asynchronous; listeners that need Bukkit-API access
 * should schedule onto the appropriate region thread themselves.
 *
 * Primary intended consumer: a premium "Vault Crate" / custom-keys module
 * that needs to (a) accept non-vanilla key items as valid and (b) substitute
 * a tier-specific loot table based on which key was consumed.
 *
 * @property player    The player who triggered the open.
 * @property vault     Database row for the vault being opened.
 * @property chamber   The chamber the vault belongs to. May be null in the
 *                     pathological case of a vault whose chamber row was
 *                     deleted while the vault was open.
 * @property vaultType `NORMAL` or `OMINOUS`.
 * @property lootTableOverride Mutable. If set non-null by a listener, the
 *                     specified loot table id is used instead of the
 *                     normally-resolved one (chamber override > vault
 *                     default). Defaults to `null`.
 *
 * @since v1.3.3
 */
class PreVaultOpenEvent(
    val player: Player,
    val vault: VaultData,
    val chamber: Chamber?,
    val vaultType: VaultType,
    var lootTableOverride: String? = null
) : Event(!Bukkit.isPrimaryThread()), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
