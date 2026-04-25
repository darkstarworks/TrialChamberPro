package io.github.darkstarworks.trialChamberPro.commands.handlers

import org.bukkit.command.CommandSender

/**
 * Contract for an extracted `/tcp <subcommand>` handler. Introduced in v1.3.0
 * Phase 3 to factor the largest branches of the [TCPCommand] dispatcher into
 * standalone classes — see `commands/handlers/` for concrete instances.
 *
 * Handlers are constructed once at plugin enable, take any dependencies (the
 * plugin itself, managers) via constructor, and are invoked by `TCPCommand`'s
 * `when` chain. Small handlers (~ <50 lines) remain inline in `TCPCommand` —
 * extraction is reserved for branches large or complex enough that the
 * separation pays for itself.
 *
 * Permission checking is the handler's responsibility: each `execute`
 * implementation should fail fast with `plugin.getMessage("no-permission")` if
 * the sender lacks the required node, rather than relying on the dispatcher.
 */
interface SubcommandHandler {
    /**
     * Execute the subcommand. The full original argv is passed unchanged
     * (including `args[0]` which is the subcommand label itself), so handlers
     * can use the same indexing scheme they had as private methods on
     * `TCPCommand` and don't need to re-derive position.
     */
    fun execute(sender: CommandSender, args: Array<out String>)
}
