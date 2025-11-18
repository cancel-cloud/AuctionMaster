package cloud.coffeesystems.auctionmaster.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File

/**
 * Manages localized messages for the plugin
 */
class MessageManager(private val plugin: Plugin) {

    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private var messages: YamlConfiguration = YamlConfiguration()
    private var language: String = "en"

    init {
        loadMessages()
    }

    /**
     * Load messages based on the configured language
     */
    fun loadMessages() {
        language = plugin.config.getString("language", "en") ?: "en"

        val messagesFile = File(plugin.dataFolder, "messages_$language.yml")

        // If custom messages file doesn't exist, copy from resources
        if (!messagesFile.exists()) {
            plugin.saveResource("messages_$language.yml", false)
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile)
    }

    /**
     * Get a raw message string without formatting
     */
    fun getRaw(key: String, vararg placeholders: Any): String {
        var message = messages.getString(key, "&cMessage not found: $key") ?: return "&cMessage not found: $key"

        // Replace placeholders
        placeholders.forEachIndexed { index, value ->
            message = message.replace("{$index}", value.toString())
        }

        return message
    }

    /**
     * Get a formatted message as a Component
     */
    fun get(key: String, vararg placeholders: Any): Component {
        val raw = getRaw(key, *placeholders)
        return legacySerializer.deserialize(raw)
    }

    /**
     * Get a message with prefix
     */
    fun getWithPrefix(key: String, vararg placeholders: Any): Component {
        val prefix = get("prefix")
        val message = get(key, *placeholders)
        return prefix.append(Component.text(" ")).append(message)
    }

    /**
     * Send a message to a command sender
     */
    fun send(sender: CommandSender, key: String, vararg placeholders: Any) {
        sender.sendMessage(getWithPrefix(key, *placeholders))
    }

    /**
     * Send a message without prefix
     */
    fun sendRaw(sender: CommandSender, key: String, vararg placeholders: Any) {
        sender.sendMessage(get(key, *placeholders))
    }

    /**
     * Get multiple messages as a list
     */
    fun getList(key: String): List<Component> {
        return messages.getStringList(key).map { legacySerializer.deserialize(it) }
    }

    /**
     * Check if a message key exists
     */
    fun has(key: String): Boolean {
        return messages.contains(key)
    }

    /**
     * Get current language
     */
    fun getLanguage(): String = language
}
