package cloud.coffeesystems.auctionmaster.notifications

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.database.NotificationSettings
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

enum class NotificationSoundType {
        AUCTION_CREATE,
        AUCTION_BUY,
        AUCTION_SELL,
        LOGIN_PAYOUT
}

class NotificationSettingsService(private val plugin: AuctionMaster) {

        data class Settings(
                val auctionCreate: Boolean,
                val auctionBuy: Boolean,
                val auctionSell: Boolean,
                val loginPayout: Boolean
        ) {
                fun isEnabled(type: NotificationSoundType): Boolean =
                        when (type) {
                                NotificationSoundType.AUCTION_CREATE -> auctionCreate
                                NotificationSoundType.AUCTION_BUY -> auctionBuy
                                NotificationSoundType.AUCTION_SELL -> auctionSell
                                NotificationSoundType.LOGIN_PAYOUT -> loginPayout
                        }

                fun with(type: NotificationSoundType, value: Boolean): Settings =
                        when (type) {
                                NotificationSoundType.AUCTION_CREATE -> copy(auctionCreate = value)
                                NotificationSoundType.AUCTION_BUY -> copy(auctionBuy = value)
                                NotificationSoundType.AUCTION_SELL -> copy(auctionSell = value)
                                NotificationSoundType.LOGIN_PAYOUT -> copy(loginPayout = value)
                        }
        }

        private val settingsCache = ConcurrentHashMap<UUID, Settings>()
        private val defaultSettings = Settings(true, true, true, true)
        private val loginPayoutSound: Sound =
                try {
                        Sound.valueOf("UI_TOAST_CHALLENGE_COMPLETE")
                } catch (_: IllegalArgumentException) {
                        Sound.ENTITY_PLAYER_LEVELUP
                }

        fun getSettings(playerUuid: UUID): Settings {
                return settingsCache[playerUuid] ?: loadSettings(playerUuid)
        }

        private fun loadSettings(playerUuid: UUID): Settings {
                val uuidString = playerUuid.toString()
                val settings =
                        transaction(plugin.databaseManager.getDatabase()) {
                                NotificationSettings.selectAll()
                                        .where { NotificationSettings.playerUuid eq uuidString }
                                        .singleOrNull()
                                        ?.let { row ->
                                                Settings(
                                                        row[
                                                                NotificationSettings
                                                                        .soundOnAuctionCreate],
                                                        row[NotificationSettings.soundOnAuctionBuy],
                                                        row[
                                                                NotificationSettings
                                                                        .soundOnAuctionSell],
                                                        row[NotificationSettings.soundOnLoginPayout]
                                                )
                                        }
                                        ?: run {
                                                NotificationSettings.insert {
                                                        it[NotificationSettings.playerUuid] =
                                                                uuidString
                                                        it[
                                                                NotificationSettings
                                                                        .soundOnAuctionCreate] =
                                                                defaultSettings.auctionCreate
                                                        it[NotificationSettings.soundOnAuctionBuy] =
                                                                defaultSettings.auctionBuy
                                                        it[
                                                                NotificationSettings
                                                                        .soundOnAuctionSell] =
                                                                defaultSettings.auctionSell
                                                        it[
                                                                NotificationSettings
                                                                        .soundOnLoginPayout] =
                                                                defaultSettings.loginPayout
                                                }
                                                defaultSettings
                                        }
                        }
                settingsCache[playerUuid] = settings
                return settings
        }

        fun isEnabled(playerUuid: UUID, type: NotificationSoundType): Boolean {
                return getSettings(playerUuid).isEnabled(type)
        }

        fun toggle(playerUuid: UUID, type: NotificationSoundType): Settings {
                val current = getSettings(playerUuid)
                val newSettings = current.with(type, !current.isEnabled(type))
                val uuidString = playerUuid.toString()

                transaction(plugin.databaseManager.getDatabase()) {
                        NotificationSettings.update({
                                NotificationSettings.playerUuid eq uuidString
                        }) {
                                it[NotificationSettings.soundOnAuctionCreate] =
                                        newSettings.auctionCreate
                                it[NotificationSettings.soundOnAuctionBuy] = newSettings.auctionBuy
                                it[NotificationSettings.soundOnAuctionSell] =
                                        newSettings.auctionSell
                                it[NotificationSettings.soundOnLoginPayout] =
                                        newSettings.loginPayout
                        }
                }

                settingsCache[playerUuid] = newSettings
                return newSettings
        }

        fun playSoundIfEnabled(
                player: Player,
                type: NotificationSoundType,
                sound: Sound,
                volume: Float = 0.85f,
                pitch: Float = 1.0f
        ) {
                if (!player.isOnline) return
                if (!isEnabled(player.uniqueId, type)) return
                player.playSound(player.location, sound, volume, pitch)
        }

        fun playLoginPayoutSound(player: Player) {
                playSoundIfEnabled(player, NotificationSoundType.LOGIN_PAYOUT, loginPayoutSound)
        }

        fun clearCache(playerUuid: UUID) {
                settingsCache.remove(playerUuid)
        }
}
