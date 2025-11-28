# GitHub Copilot Instructions for AuctionMaster

This document provides repository-specific guidance for GitHub Copilot when working with the AuctionMaster Minecraft plugin.

## Project Overview

AuctionMaster is a Minecraft Paper/Purpur plugin written in Kotlin that provides auction functionality. The plugin uses:
- **Kotlin** as the primary language (JVM 21)
- **Gradle** with Shadow plugin for building
- **Exposed ORM** for database access (SQLite/PostgreSQL)
- **Paper/Purpur API** for Minecraft server integration
- **Adventure API** for text components

## Project Structure

```
src/main/kotlin/cloud/coffeesystems/auctionmaster/
├── ui/          # GUI menus and user interface
├── database/    # Database managers and models
└── ...          # Other modules grouped by concern

src/main/resources/
├── paper-plugin.yml    # Plugin descriptor
├── config.yml          # Configuration file
├── messages_en.yml     # English locale
└── messages_de.yml     # German locale

src/test/kotlin/        # Test files
src/test/resources/     # Test fixtures
```

## Build Commands

- **Full build**: `./gradlew clean build` - Runs tests, creates shaded JAR
- **Run tests**: `./gradlew test` - Executes Kotlin/JUnit test suite
- **Create JAR only**: `./gradlew shadowJar` - Creates `build/libs/AuctionMaster-<version>.jar`

## Coding Conventions

### Kotlin Style
- Use 4-space indentation
- Prefer `val` over `var` when possible
- Use expression functions where appropriate
- Add trailing commas where helpful
- Keep files under `cloud.coffeesystems.auctionmaster.<module>` packages

### GUI Development
- Follow Adventure text patterns used in `ui/` directory
- No italics by default for text components
- Use `messageManager` for localization
- Register/unregister Bukkit listeners properly

### Configuration
- Use snake-case for YAML keys (e.g., `gui.main-menu.title`)
- Add matching entries in all locale files (`messages_en.yml`, `messages_de.yml`)
- Never commit database credentials

### Testing
- Name test classes `<Feature>Test`
- Place shared fakes in `src/test/kotlin/support`
- Mock Bukkit objects for off-server testing
- Target behavioral coverage for `AuctionManager`, database adapters, and utilities

## Commit Message Format

Use the format: `type: summary`

Examples:
- `feature: Add new auction listing GUI`
- `fix: Resolve database connection timeout`
- `refactor: Simplify message handling`
- `docs: Update configuration documentation`

Keep subjects in sentence case and under 60 characters.

## Important Notes

1. **Dependencies**: When adding dependencies that bundle shaded libraries, update `shadowJar` relocation/exclusion rules in `build.gradle.kts`
2. **Locale Sync**: Keep all locale files synchronized (`messages_en.yml`, `messages_de.yml`)
3. **Plugin Metadata**: Validate `paper-plugin.yml` when modifying metadata or permissions
4. **JDBC Drivers**: Do not relocate Exposed framework or JDBC drivers as they use reflection
5. **MCKotlin-Paper**: Kotlin stdlib is provided by MCKotlin-Paper at runtime, so it's excluded from the shadow JAR
