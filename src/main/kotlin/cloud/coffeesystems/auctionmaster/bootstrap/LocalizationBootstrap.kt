package cloud.coffeesystems.auctionmaster.bootstrap

import cloud.coffeesystems.auctionmaster.AuctionMaster
import java.io.File

class LocalizationBootstrap(private val plugin: AuctionMaster) {

    fun ensureLanguageFiles(languages: List<String> = listOf("en", "de")) {
        languages.forEach { language ->
            val fileName = "messages_${language}.yml"
            val file = File(plugin.dataFolder, fileName)

            if (!file.exists()) {
                plugin.saveResource(fileName, false)
                plugin.logger.info("Created language file: $fileName")
            }
        }
    }
}
