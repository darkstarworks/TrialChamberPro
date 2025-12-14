package io.github.darkstarworks.trialChamberPro.scheduler

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * Abstraction layer for scheduling tasks that works on both Paper and Folia.
 *
 * Folia uses regionized multithreading, meaning tasks must be scheduled
 * for specific regions (locations) or entities rather than globally.
 * This adapter provides a unified API that works on both platforms.
 */
interface SchedulerAdapter {

    /**
     * Whether this adapter is running on Folia.
     */
    val isFolia: Boolean

    /**
     * Run a task on the main thread (Paper) or global region (Folia).
     * Use this for tasks that don't interact with specific world locations.
     */
    fun runTask(task: Runnable)

    /**
     * Run a task asynchronously (off the main/region thread).
     * Safe to use for I/O, database operations, etc.
     */
    fun runTaskAsync(task: Runnable)

    /**
     * Run a task after a delay on the main thread (Paper) or global region (Folia).
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     * @return A task handle that can be cancelled
     */
    fun runTaskLater(task: Runnable, delayTicks: Long): ScheduledTask

    /**
     * Run a task asynchronously after a delay.
     * @param delayTicks Delay in ticks (20 ticks = 1 second)
     * @return A task handle that can be cancelled
     */
    fun runTaskLaterAsync(task: Runnable, delayTicks: Long): ScheduledTask

    /**
     * Run a repeating task on the main thread (Paper) or global region (Folia).
     * @param delayTicks Initial delay in ticks
     * @param periodTicks Period between executions in ticks
     * @return A task handle that can be cancelled
     */
    fun runTaskTimer(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask

    /**
     * Run a repeating task asynchronously.
     * @param delayTicks Initial delay in ticks
     * @param periodTicks Period between executions in ticks
     * @return A task handle that can be cancelled
     */
    fun runTaskTimerAsync(task: Runnable, delayTicks: Long, periodTicks: Long): ScheduledTask

    /**
     * Run a task at a specific location's region.
     * On Paper, this runs on the main thread.
     * On Folia, this runs on the region thread owning that location.
     *
     * IMPORTANT: Use this when modifying blocks or accessing location-specific data.
     */
    fun runAtLocation(location: Location, task: Runnable)

    /**
     * Run a task at a specific location's region after a delay.
     * @param delayTicks Delay in ticks
     */
    fun runAtLocationLater(location: Location, task: Runnable, delayTicks: Long)

    /**
     * Run a task for a specific entity.
     * On Paper, this runs on the main thread.
     * On Folia, this runs on the region thread owning that entity.
     *
     * IMPORTANT: Use this when modifying entity state, inventory, etc.
     *
     * @param retired Callback if the entity is removed before execution (Folia only)
     */
    fun runAtEntity(entity: Entity, task: Runnable, retired: Runnable? = null)

    /**
     * Run a task for a specific entity after a delay.
     * @param delayTicks Delay in ticks
     * @param retired Callback if the entity is removed before execution (Folia only)
     */
    fun runAtEntityLater(entity: Entity, task: Runnable, delayTicks: Long, retired: Runnable? = null)

    /**
     * Cancel all tasks scheduled by this plugin.
     */
    fun cancelAllTasks()

    companion object {
        /**
         * Creates the appropriate scheduler adapter based on the server platform.
         */
        fun create(plugin: Plugin): SchedulerAdapter {
            return if (isFoliaServer()) {
                FoliaSchedulerAdapter(plugin)
            } else {
                BukkitSchedulerAdapter(plugin)
            }
        }

        /**
         * Detects if the server is running Folia.
         */
        private fun isFoliaServer(): Boolean {
            return try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }
}

/**
 * Handle for a scheduled task that can be cancelled.
 */
interface ScheduledTask {
    fun cancel()
    val isCancelled: Boolean
}
