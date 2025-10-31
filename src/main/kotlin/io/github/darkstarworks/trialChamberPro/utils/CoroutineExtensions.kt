package io.github.darkstarworks.trialChamberPro.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatcher that runs on Bukkit's main thread.
 * Required for WorldEdit operations that must execute on the main thread.
 */
val Plugin.minecraftDispatcher: CoroutineDispatcher
    get() = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (server.isPrimaryThread) {
                block.run()
            } else {
                server.scheduler.runTask(this@minecraftDispatcher, block)
            }
        }
    }
