package cloud.coffeesystems.auctionmaster.util

import java.io.File
import java.text.MessageFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

class MessageManager(private val plugin: JavaPlugin) {

        private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
        private val plainSerializer = PlainTextComponentSerializer.plainText()

        private var messages = YamlConfiguration()
        private var fallbackMessages = YamlConfiguration()
        private var language: String = "en"

        init {
                loadMessages()
        }

        fun loadMessages() {
                language = plugin.config.getString("language", "en") ?: "en"
                messages = loadLanguageFile(language)
                fallbackMessages = loadLanguageFile("en")
        }

        private fun loadLanguageFile(lang: String): YamlConfiguration {
                val fileName = "messages_${lang}.yml"
                val file = File(plugin.dataFolder, fileName)
                if (!file.exists()) {
                        plugin.saveResource(fileName, false)
                }
                return YamlConfiguration.loadConfiguration(file)
        }

        fun getLanguage(): String = language

        fun getRaw(key: String, vararg placeholders: Any?): String {
                val template = readMessage(key) ?: "&cMessage not found: $key"
                return formatString(template, placeholders)
        }

        fun get(key: String, vararg placeholders: Any?): Component {
                val raw = getRaw(key, *placeholders)
                return legacySerializer.deserialize(raw)
        }

        fun send(sender: CommandSender, key: String, vararg placeholders: Any?) {
                sender.sendMessage(prefixed(get(key, *placeholders)))
        }

        fun sendRaw(sender: CommandSender, key: String, vararg placeholders: Any?) {
                send(sender, key, *placeholders)
        }

        fun sendComponent(sender: CommandSender, component: Component) {
                sender.sendMessage(prefixed(component))
        }

        fun getList(key: String, vararg placeholders: Any?): List<Component> {
                val values =
                        if (messages.contains(key)) {
                                messages.getStringList(key)
                        } else {
                                fallbackMessages.getStringList(key)
                        }
                if (values.isEmpty()) return emptyList()
                return values.map { line ->
                        val formatted = formatString(line, placeholders)
                        legacySerializer.deserialize(formatted)
                }
        }

        private fun readMessage(key: String): String? {
                return messages.getString(key) ?: fallbackMessages.getString(key)
        }

        private fun formatString(template: String, args: Array<out Any?>): String {
                if (args.isEmpty()) return template
                val converted =
                        args.map { arg ->
                                when (arg) {
                                        is ComponentLike -> legacySerializer.serialize(arg.asComponent())
                                        is Component -> legacySerializer.serialize(arg)
                                        else -> arg?.toString() ?: "null"
                                }
                        }
                return MessageFormat.format(template, *converted.toTypedArray())
        }

        private fun prefixed(component: Component): Component {
                return get("prefix").append(Component.text(" ")).append(component)
        }
}
