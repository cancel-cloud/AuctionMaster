# AuctionMaster - Copilot Instructions

This is a Minecraft Paper/Spigot plugin that implements an auction house system for Minecraft servers. It allows players to create auctions, browse listings, purchase items, and manage their sales.

## Code Standards

- **Language**: Kotlin
- **Style**: Follow Kotlin coding conventions (4-space indentation, trailing commas where helpful, prefer `val` over `var`, expression functions)
- **Package Structure**: Keep files under `cloud.coffeesystems.auctionmaster.<module>` packages
- **GUI Patterns**: Use Adventure text components (no italics by default, localized via `messageManager`)
- **Configuration**: New config keys go in snake-case YAML (`gui.main-menu.title`) with matching entries in each locale file
- **Listeners**: Register and unregister Bukkit listeners properly like existing GUIs

## Development Flow

### Building the Plugin

The project is built with Gradle. The main build command is:

```bash
./gradlew build shadowJar
```

**Copilot should run `./gradlew build shadowJar` in its environment after making changes, and only consider the task successful if this command completes without errors.**

Alternative commands:
- `./gradlew clean build` - Full rebuild with tests
- `./gradlew test` - Run unit tests only
- `./gradlew shadowJar` - Generate shaded JAR only

### Testing

- Test classes should be named `<Feature>Test`
- Place shared test fixtures in `src/test/kotlin/support`
- Target behavioral coverage for `AuctionManager`, database adapters, and utility extensions

### Directory Structure

- `src/main/kotlin/cloud/coffeesystems/auctionmaster/` - Main source code
  - `ui/` - GUI menus
  - `database/` - Database managers
  - `listeners/` - Event listeners
  - `model/` - Data models
  - `commands/` - Command handlers
  - `util/` - Utility classes
- `src/main/resources/` - Plugin descriptors, configs, locale bundles
- `src/test/kotlin/` - Test files

## Special Notes

### Sounds in Minecraft

When implementing sound effects:
- Use valid, modern `org.bukkit.Sound` enum values available in current Minecraft versions
- Don't spam sounds: each event should play at most once per action
- Always check for null/offline players before playing sounds
- Use consistent volume (1.0) and reasonable pitch (0.8–1.2)
- Play sounds only to directly involved players

### Minecraft Version Compatibility

This plugin targets Paper/Purpur API for Minecraft 1.21.1+. Ensure all APIs used are available in this version.
