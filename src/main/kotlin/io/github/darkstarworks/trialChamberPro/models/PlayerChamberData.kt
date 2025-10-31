package io.github.darkstarworks.trialChamberPro.models

import java.util.UUID

/**
 * Represents player statistics for Trial Chambers.
 *
 * @property playerUuid Player's unique ID
 * @property chambersCompleted Number of chambers completed
 * @property normalVaultsOpened Number of normal vaults opened
 * @property ominousVaultsOpened Number of ominous vaults opened
 * @property mobsKilled Number of mobs killed in chambers
 * @property deaths Number of deaths in chambers
 * @property timeSpent Total time spent in chambers (seconds)
 * @property lastUpdated Last update timestamp
 */
data class PlayerChamberData(
    val playerUuid: UUID,
    val chambersCompleted: Int = 0,
    val normalVaultsOpened: Int = 0,
    val ominousVaultsOpened: Int = 0,
    val mobsKilled: Int = 0,
    val deaths: Int = 0,
    val timeSpent: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
