package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * `/tcp give <preset> [player] [amount]` — hand the sender (or a target
 * player) a `minecraft:trial_spawner` item preconfigured from a named entry
 * in `spawner_presets.yml`. Introduced in v1.3.1.
 *
 * - `<preset>` is required and must match a loaded preset id.
 * - `[player]` defaults to the sender (must be a player when omitted).
 * - `[amount]` defaults to 1; clamped to the item's max stack size.
 *
 * Reload the preset file with `/tcp reload`.
 */
class GiveCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.give")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("give-usage"))
            val available = plugin.spawnerPresetManager.getNames()
            if (available.isNotEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("give-available", "presets" to available.joinToString(", ")))
            } else {
                sender.sendMessage(plugin.getMessageComponent("give-no-presets"))
            }
            return
        }

        val presetId = args[1]
        val preset = plugin.spawnerPresetManager.get(presetId)
        if (preset == null) {
            sender.sendMessage(plugin.getMessageComponent("give-unknown-preset", "preset" to presetId))
            val available = plugin.spawnerPresetManager.getNames()
            if (available.isNotEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("give-available", "presets" to available.joinToString(", ")))
            }
            return
        }

        // Resolve target player: explicit arg, else the sender if they're a player.
        val target: Player = if (args.size >= 3) {
            val targetName = args[2]
            val resolved = plugin.server.getPlayerExact(targetName)
            if (resolved == null) {
                sender.sendMessage(plugin.getMessageComponent("player-not-found", "player" to targetName))
                return
            }
            resolved
        } else {
            if (sender !is Player) {
                sender.sendMessage(plugin.getMessageComponent("give-needs-target-from-console"))
                return
            }
            sender
        }

        val amount = if (args.size >= 4) {
            val parsed = args[3].toIntOrNull()
            if (parsed == null || parsed < 1) {
                sender.sendMessage(plugin.getMessageComponent("give-bad-amount", "value" to args[3]))
                return
            }
            parsed
        } else 1

        val item = try {
            plugin.spawnerPresetManager.getItem(preset, amount)
        } catch (e: IllegalArgumentException) {
            // Bad NBT in the preset — surface to the admin running the command.
            sender.sendMessage(plugin.getMessageComponent("give-build-failed", "preset" to preset.id, "error" to (e.message ?: "unknown")))
            plugin.logger.warning("Failed to build trial_spawner item for preset '${preset.id}': ${e.message}")
            return
        }

        // Inventory mutation must run on the target's region thread (Folia).
        plugin.scheduler.runAtEntity(target, Runnable {
            val leftover = target.inventory.addItem(item)
            if (leftover.isNotEmpty()) {
                // Inventory full — drop the overflow at the player's feet so we don't silently lose items.
                leftover.values.forEach { stack ->
                    target.world.dropItemNaturally(target.location, stack)
                }
                target.sendMessage(plugin.getMessageComponent("give-inventory-full"))
            }

            target.sendMessage(plugin.getMessageComponent(
                "give-received",
                "amount" to item.amount,
                "preset" to preset.id
            ))
            if (sender !== target) {
                sender.sendMessage(plugin.getMessageComponent(
                    "give-sent",
                    "amount" to item.amount,
                    "preset" to preset.id,
                    "player" to target.name
                ))
            }
        })
    }
}
