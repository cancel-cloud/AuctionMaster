# AuctionMaster - Developer Onboarding Guide

This document provides a comprehensive guide for developers (and Copilot!) to quickly understand and contribute to the AuctionMaster project.

## Project Purpose

AuctionMaster is a modern Minecraft auction house plugin for Paper/Purpur servers (1.21+). It enables players to:
- List items for sale in a global auction system
- Browse and purchase items from other players
- Manage their active listings and transaction history
- Receive payments even when offline

## Architecture Overview

```
src/main/kotlin/cloud/coffeesystems/auctionmaster/
├── AuctionMaster.kt          # Main plugin entry point
├── bootstrap/                 # Initialization helpers
│   ├── ListenerRegistrar.kt   # Registers Bukkit event listeners
│   └── LocalizationBootstrap.kt # Ensures language files exist
├── commands/                  # Command handlers
│   ├── AuctionCommand.kt      # Main /auction command executor
│   └── PaperCommandRegistrar.kt # Paper command registration
├── database/                  # Data persistence layer
│   ├── AuctionManager.kt      # Business logic for auctions
│   ├── DatabaseManager.kt     # Connection management (SQLite/MySQL/PostgreSQL)
│   └── Tables.kt              # Exposed ORM table definitions
├── hooks/                     # External plugin integrations
│   └── EconomyHook.kt         # Vault economy integration
├── listeners/                 # Bukkit event listeners
│   ├── DatabaseWarningListener.kt
│   ├── PendingPaymentListener.kt
│   └── PlayerJoinExpiryListener.kt
├── migration/                 # Database migration tools
│   └── DatabaseMigrationService.kt
├── model/                     # Data models
│   ├── Auction.kt             # Core auction data class
│   ├── AuctionDuration.kt     # Duration options with fee calculations
│   ├── AuctionHistoryItem.kt  # Transaction history record
│   └── AuctionStatus.kt       # Auction state enum
├── ui/                        # GUI components
│   ├── AuctionGUI.kt          # Browse auctions view
│   ├── ConfirmationGUI.kt     # Purchase confirmation dialog
│   ├── CreateAuctionGUI.kt    # Auction creation wizard
│   ├── HistoryGUI.kt          # Transaction history view
│   ├── MainMenuGUI.kt         # Main hub menu
│   ├── MyListingsGUI.kt       # Player's active listings
│   └── PastItemsGUI.kt        # Expired/sold items view
└── util/                      # Utilities
    ├── MessageManager.kt      # Localization/messaging
    └── TimeUtil.kt            # Time formatting helpers
```

## Key Components

### Main Plugin Class (`AuctionMaster.kt`)
- Initializes all managers and hooks on enable
- Provides singleton access via `AuctionMaster.instance`
- Manages lockdown mode and plugin reload
- Exposes `databaseManager`, `auctionManager`, `messageManager`, and `economyHook`

### Database Layer
- **Exposed ORM Framework**: Uses JetBrains Exposed for type-safe SQL
- **Tables**: `Auctions`, `Transactions`, `AuctionHistory`, `PendingPayments`, `PendingExpiredItems`
- **Supported backends**: SQLite (default), MySQL, PostgreSQL
- **Item serialization**: Items stored as Base64-encoded byte arrays

### GUI System
- Uses Bukkit's inventory API with Adventure text components
- Each GUI registers/unregisters itself as a listener
- Text styling uses `decoration(TextDecoration.ITALIC, false)` to remove default italic
- Localized via `messageManager.get()` and `messageManager.getList()`

### Economy Integration
- Requires Vault plugin for economy operations
- `EconomyHook` wraps Vault API for balance/withdraw/deposit operations
- Supports offline player deposits for when sellers are offline

### Localization
- Language files: `messages_en.yml`, `messages_de.yml` in `src/main/resources`
- Uses legacy color codes (`&a`, `&6`, etc.) with Adventure's `LegacyComponentSerializer`
- Placeholders use `{0}`, `{1}` format with `MessageFormat.format()`

## Getting Started

### Prerequisites
- JDK 21 (required by Kotlin toolchain)
- Gradle 9.x (wrapper included)
- A Purpur/Paper 1.21+ server for testing

### Build Commands
```bash
# Full rebuild (runs tests, creates shaded JAR, copies to local server)
./gradlew clean build

# Run tests only
./gradlew test

# Create shaded JAR only (no copy)
./gradlew shadowJar

# Copy JAR to local dev server (update path in build.gradle.kts)
./gradlew copyJar
```

The shaded JAR is output to `build/libs/AuctionMaster-<version>.jar`.

### Configuration Files
- `src/main/resources/config.yml` - Main plugin configuration
- `src/main/resources/paper-plugin.yml` - Paper plugin descriptor
- `src/main/resources/messages_*.yml` - Localization files

## Development Conventions

### Code Style
- **Kotlin style**: 4-space indentation, trailing commas, prefer `val` over `var`
- **Package structure**: `cloud.coffeesystems.auctionmaster.<module>`
- **File naming**: Classes match file names

### GUI Development
- Extend the pattern in `ui/` package
- Always disable italic on text: `.decoration(TextDecoration.ITALIC, false)`
- Use `messageManager` for all player-visible text
- Register listeners on open, unregister on close

### Database Operations
- Wrap all database calls in `transaction(databaseManager.getDatabase()) { }`
- Handle exceptions and return null/empty on failure
- Use `Auctions`, `Transactions`, etc. table objects from `Tables.kt`

### Configuration Keys
- Use snake-case in YAML: `gui.main-menu.title`
- Add corresponding entries to ALL locale files
- Access via `plugin.config.getString/getInt/getDouble/etc.`

### Localization
- Add new messages to both `messages_en.yml` and `messages_de.yml`
- Use `{0}`, `{1}` for placeholders
- Legacy color codes: `&a` (green), `&c` (red), `&6` (gold), `&7` (gray), `&f` (white), etc.

## Testing

- Test framework: JUnit Platform via `kotlin("test")`
- Test class naming: `<Feature>Test`
- Location: `src/test/kotlin/` (to be created)
- Mock Bukkit objects for off-server testing

### Manual Testing Checklist
1. Create auctions with various prices/durations
2. Browse and purchase items as different players
3. Verify offline payment delivery
4. Test expired auction item recovery
5. Confirm GUI navigation and localization
6. Test lockdown mode and admin commands

## Common Tasks

### Adding a New Command Subcommand
1. Add handler method in `AuctionCommand.kt`
2. Add case in `onCommand()` switch
3. Add tab completion in `onTabComplete()`
4. Add localization keys for messages

### Adding a New GUI
1. Create class in `ui/` extending the existing pattern
2. Implement `Listener` interface
3. Register/unregister events properly
4. Use `messageManager` for all text
5. Add localization keys to message files

### Adding a Database Table
1. Define table object in `Tables.kt`
2. Add to `SchemaUtils.create()` in `DatabaseManager.connect()`
3. Add CRUD methods in `AuctionManager.kt` or new manager class

### Adding a Configuration Option
1. Add to `config.yml` with default value
2. Access via `plugin.config.get*()` methods
3. Document in README if user-facing

## Dependencies

| Dependency | Purpose |
|------------|---------|
| Purpur API | Server API (Paper-compatible) |
| Vault API | Economy integration |
| Exposed | Database ORM framework |
| HikariCP | Connection pooling |
| SQLite/PostgreSQL drivers | Database connectivity |
| Fruxz Stacked/Ascend | Kotlin utility libraries |
| Adventure | Text component API |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `auctionmaster.use` | Access auction house | true |
| `auctionmaster.create` | Create auctions | true |
| `auctionmaster.buy` | Purchase items | true |
| `auctionmaster.history.others` | View others' history | op |
| `auctionmaster.admin` | Admin commands | op |
| `auctionmaster.notify` | Database issue notifications | op |

## Useful Links

- [GitHub Repository](https://github.com/cancel-cloud/AuctionMaster)
- [Purpur API Docs](https://purpurmc.org/docs)
- [Vault API](https://github.com/MilkBowl/VaultAPI)
- [Exposed ORM](https://github.com/JetBrains/Exposed)
- [Adventure Text](https://docs.adventure.kyori.net/)
- [Paper Plugin Docs](https://docs.papermc.io/paper/dev/getting-started)

## License

This project is licensed under the GNU Lesser General Public License v3 (LGPL-3.0).
