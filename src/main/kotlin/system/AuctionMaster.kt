package system

import de.fruxz.sparkle.framework.infrastructure.app.App
import de.fruxz.sparkle.framework.infrastructure.app.AppCache
import de.fruxz.sparkle.framework.infrastructure.app.AppCompanion
import de.fruxz.sparkle.framework.infrastructure.app.update.AppUpdater

class AuctionMaster : App() {


    override val appIdentity = "am"
    override val label: String = "AuctionMaster"
    override val updater: AppUpdater = AppUpdater.none()
    override val companion: AppCompanion<out App> = Companion
    override val appCache: AppCache = AppCache

    override suspend fun hello() {
        println("Hello from AuctionMaster")
    }

    companion object : AppCompanion<AuctionMaster>() {
        override val predictedIdentity = "am"
    }
}