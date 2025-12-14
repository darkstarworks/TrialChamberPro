package io.github.darkstarworks.trialChamberPro.scheduler

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.concurrent.TimeUnit

/**
 * Folia scheduler implementation using regionized scheduling.
 *
 * Folia splits the world into independent regions that tick in parallel.
 * Tasks must be scheduled to specific regions (by location or entity)
 * to ensure thread safety.
 *
 * This implementation uses Paper's Folia-compatible APIs which are
 * available in Paper 1.20+ when running on Folia.
 */
class FoliaSchedulerAdapter(private val plugin: Plugin) : SchedulerAdapter {

    override val isFolia: Boolean = true

    override fun runTask(task: Runnable) {
        // Use global region scheduler for non-location-specific tasks
        Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
    }

    override fun runTaskAsync(task: Runnable) {
        Bukkit.getAsyncScheduler().runNow(plugin) { task.run() }
    }

    override fun runTaskLater(task: Runnable, delayTicks: Long): ScheduledTask {
        return if (delayTicks <= 0) {
            // For immediate execution, we need to wrap in a cancellable task
            val foliaTask = Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
            FoliaScheduledTask(foliaTask)
        } else {
            val foliaTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delayTicks)
            FoliaScheduledTask(foliaTask)
        }
    }

    override fun runTaskLaterAsync(task: Runnable, delayTicks: Long): ScheduledTask {
        val delayMs = delayTicks * 50 // 1 tick = 50ms
        val foliaTask = Bukkit.getAsyncScheduler().runDelayed(plugin, { task.run() }, maxOf(1, delayMs), TimeUnit.MILLISECONDS)
        return FoliaScheduledTask(foliaTask)
    }

    override fun runTaskTimer(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask {
        val foliaTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin,
            { task.run() },
            maxOf(1, delayTicks), // Folia requires delay >= 1
            periodTicks
        )
        return FoliaScheduledTask(foliaTask)
    }

    override fun runTaskTimerAsync(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask {
        val delayMs = delayTicks * 50
        val periodMs = periodTicks * 50
        val foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(
            plugin,
            { task.run() },
            maxOf(1, delayMs), // Minimum 1ms
            periodMs,
            TimeUnit.MILLISECONDS
        )
        return FoliaScheduledTask(foliaTask)
    }

    override fun runAtLocation(location: Location, task: Runnable) {
        Bukkit.getRegionScheduler().run(plugin, location) { task.run() }
    }

    override fun runAtLocationLater(location: Location, task: Runnable, delayTicks: Long) {
        if (delayTicks <= 0) {
            runAtLocation(location, task)
        } else {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, { task.run() }, delayTicks)
        }
    }

    override fun runAtEntity(entity: Entity, task: Runnable, retired: Runnable?) {
        entity.scheduler.run(plugin, { task.run() }, retired)
    }

    override fun runAtEntityLater(entity: Entity, task: Runnable, delayTicks: Long, retired: Runnable?) {
        if (delayTicks <= 0) {
            runAtEntity(entity, task, retired)
        } else {
            entity.scheduler.runDelayed(plugin, { task.run() }, retired, delayTicks)
        }
    }

    override fun cancelAllTasks() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin)
        Bukkit.getAsyncScheduler().cancelTasks(plugin)
        // Note: Region and entity tasks are cancelled when the plugin is disabled
    }

    /**
     * Wrapper for Folia's ScheduledTask.
     */
    private class FoliaScheduledTask(private val task: io.papermc.paper.threadedregions.scheduler.ScheduledTask) : ScheduledTask {
        override fun cancel() {
            task.cancel()
        }

        override val isCancelled: Boolean
            get() = task.isCancelled
    }
}
