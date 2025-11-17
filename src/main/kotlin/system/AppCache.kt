package system

import de.fruxz.sparkle.framework.infrastructure.app.AppCache
import de.fruxz.sparkle.framework.infrastructure.app.cache.CacheDepthLevel
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced AppCache implementation for AuctionMaster
 */
object AppCache : AppCache {

    // Cache for player-specific data (future use)
    private val playerCache = ConcurrentHashMap<UUID, MutableMap<String, Any>>()

    /**
     * Drop all cached data for a specific entity (player)
     */
    override fun dropEntityData(entityIdentity: UUID, dropDepth: CacheDepthLevel) {
        when (dropDepth) {
            CacheDepthLevel.ALL -> {
                // Remove all data for this player
                playerCache.remove(entityIdentity)
            }
            CacheDepthLevel.TEMPORARY -> {
                // Remove only temporary data (keep permanent settings)
                playerCache[entityIdentity]?.let { cache ->
                    cache.keys.filter { it.startsWith("temp_") }.forEach { key ->
                        cache.remove(key)
                    }
                }
            }
            else -> {
                // Minimal cleanup
                playerCache[entityIdentity]?.let { cache ->
                    cache.keys.filter { it.startsWith("session_") }.forEach { key ->
                        cache.remove(key)
                    }
                }
            }
        }
    }

    /**
     * Drop all cached data
     */
    override fun dropEverything(dropDepth: CacheDepthLevel) {
        when (dropDepth) {
            CacheDepthLevel.ALL -> {
                // Clear everything
                playerCache.clear()
                database.AuctionManager.invalidateCache()
            }
            CacheDepthLevel.TEMPORARY -> {
                // Clear temporary data only
                playerCache.forEach { (_, cache) ->
                    cache.keys.filter { it.startsWith("temp_") }.forEach { key ->
                        cache.remove(key)
                    }
                }
            }
            else -> {
                // Minimal cleanup
                playerCache.forEach { (_, cache) ->
                    cache.keys.filter { it.startsWith("session_") }.forEach { key ->
                        cache.remove(key)
                    }
                }
            }
        }
    }

    /**
     * Get cached data for a player
     */
    fun getPlayerData(playerId: UUID, key: String): Any? {
        return playerCache[playerId]?.get(key)
    }

    /**
     * Set cached data for a player
     */
    fun setPlayerData(playerId: UUID, key: String, value: Any) {
        playerCache.computeIfAbsent(playerId) { ConcurrentHashMap() }[key] = value
    }

    /**
     * Remove cached data for a player
     */
    fun removePlayerData(playerId: UUID, key: String) {
        playerCache[playerId]?.remove(key)
    }
}
