import de.fruxz.sparkle.framework.infrastructure.app.App
import de.fruxz.sparkle.framework.infrastructure.app.AppCache
import de.fruxz.sparkle.framework.infrastructure.app.AppCompanion
import de.fruxz.sparkle.framework.infrastructure.app.update.AppUpdater
import interchange.AuctionInterchange

class AuctionMaster : App() {


    override val appIdentity = "AuctionMaster"
    override val label: String = "AuctionMaster"
    override val updater: AppUpdater = AppUpdater.none()
    override val companion: AppCompanion<out App> = Companion
    override val appCache: AppCache = system.AppCache

    override suspend fun hello() {
        add(AuctionInterchange())

        println("Hello from AuctionMaster")
    }

    companion object : AppCompanion<AuctionMaster>() {
        override val predictedIdentity = "auctionmaster"
    }
}