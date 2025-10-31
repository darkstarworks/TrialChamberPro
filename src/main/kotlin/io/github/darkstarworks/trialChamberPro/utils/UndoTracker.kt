package io.github.darkstarworks.trialChamberPro.utils

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks last generated chambers per player and manages confirmation flow
 * for undoing (deleting) the generated chamber via WorldEdit //undo.
 */
object UndoTracker {
    data class LastGenerated(val chamberName: String, val timestamp: Long)
    data class Pending(val chamberName: String, val expiresAt: Long)

    private val lastMap = ConcurrentHashMap<UUID, LastGenerated>()
    private val pendingMap = ConcurrentHashMap<UUID, Pending>()

    fun setLast(playerId: UUID, chamberName: String) {
        lastMap[playerId] = LastGenerated(chamberName, System.currentTimeMillis())
    }

    fun getLast(playerId: UUID): LastGenerated? = lastMap[playerId]

    fun clearLast(playerId: UUID) {
        lastMap.remove(playerId)
    }

    fun setPending(playerId: UUID, chamberName: String, ttlSeconds: Int) {
        val expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L)
        pendingMap[playerId] = Pending(chamberName, expiresAt)
    }

    fun getPending(playerId: UUID): Pending? {
        val p = pendingMap[playerId] ?: return null
        if (System.currentTimeMillis() > p.expiresAt) {
            pendingMap.remove(playerId)
            return null
        }
        return p
    }

    fun clearPending(playerId: UUID) {
        pendingMap.remove(playerId)
    }
}
