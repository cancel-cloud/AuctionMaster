![AuctionMaster](https://cdn.upload.systems/uploads/vsr7ycbh.jpg)
<br><br>

---

# AuctionMaster 🏦

AuctionMaster is a comprehensive auction plugin for Minecraft Paper servers that allows players to create, bid on, and manage auctions in a user-to-user marketplace.

<br><br>

---

# Version Information

- **Minecraft Version**: 1.21+
- **Paper API**: 1.21-R0.1-SNAPSHOT
- **Kotlin**: 2.2.21
- **Sparkle Framework**: 1.0.0-PRE-22
- **Java**: 17+

<br>

---

# Current State

✅ **Version 1.0 - Fully Implemented!**

The plugin has been completely rebuilt with a modern architecture and full auction system functionality. All core features are implemented and ready for testing.

<br>

---

# Features

## Core Auction System
- ✅ **Create Auctions**: Players can auction items with configurable start prices, durations, and optional buy-now prices
- ✅ **Bidding System**: Real-time bidding with automatic outbid refunds
- ✅ **Buy Now**: Optional instant-purchase option for auctions
- ✅ **Auto-Expiration**: Automatic processing of expired auctions with winner/seller notifications
- ✅ **Claims System**: Safe item and money collection system for auction results

## Data Management
- ✅ **Three-Tier Storage**: In-memory cache + H2 database + JSON backups
- ✅ **Automatic Backups**: Hourly database backups with configurable retention
- ✅ **Transaction Safety**: Atomic operations ensure no item/money loss
- ✅ **Audit Trail**: Complete bid history and transaction logging

## User Interface
- ✅ **Interactive GUIs**: Browse auctions, create listings, view claims
- ✅ **Filter & Sort**: Search by category, price, seller, and more
- ✅ **Real-time Updates**: Dynamic auction information display
- ✅ **Intuitive Navigation**: Easy-to-use menu system

## Administration
- ✅ **Configurable Limits**: Max auctions per player, duration limits, price ranges
- ✅ **Item Blacklist**: Prevent certain items from being auctioned
- ✅ **Fee System**: Configurable listing and cancellation fees
- ✅ **Cleanup Tasks**: Automatic removal of old expired auctions

<br>

---

# Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/auction` | `/ah`, `/auctionhouse` | Open the auction house GUI |
| `/auction list` | `/ah list` | Browse all active auctions |
| `/auction create <price> [duration] [buyNow]` | `/ah create` | Create a new auction |
| `/auction bid <id> <amount>` | `/ah bid` | Place a bid on an auction |
| `/auction claims` | `/ah claims` | View and claim your items/money |
| `/auction myauctions` | `/ah myauctions` | View your active auctions |
| `/auction mybids` | `/ah mybids` | View auctions you've bid on |

## Command Examples

```bash
# Create auction with $100 start price, 24h duration
/ah create 100

# Create auction with custom duration (48 hours)
/ah create 100 48h

# Create auction with buy-now price
/ah create 100 24h 500

# Place a bid
/ah bid <auction-id> 150

# View your claims
/ah claims
```

<br>

---

# Architecture

## Technology Stack
- **Language**: Kotlin 2.2.21
- **Framework**: Sparkle (Paper plugin framework)
- **Database**: H2 Embedded Database with Exposed ORM
- **Concurrency**: Kotlin Coroutines for async operations
- **Serialization**: kotlinx.serialization for data persistence

## Project Structure
```
src/main/kotlin/
├── AuctionMaster.kt              # Main plugin class
├── config/                        # Configuration & messages
├── database/                      # Data layer with CRUD operations
├── model/                         # Data models
├── service/                       # Business logic & validation
├── scheduler/                     # Background tasks
├── gui/                          # User interfaces
├── interchange/                   # Commands
├── util/                         # Utilities
└── system/                       # Caching system
```

<br>

---

# Future Enhancements

- [ ] Economy integration (Vault API)
- [ ] Permission system (LuckPerms)
- [ ] Advanced filtering and categories
- [ ] Auction statistics and leaderboards
- [ ] Watchlist and notifications
- [ ] Admin GUI for management

<br>

---

# Credits

**Developed by**: [@cancel-cloud](https://github.com/cancel-cloud)

**Built with**: [Sparkle Framework](https://github.com/TheFruxz/Sparkle) by TheFruxz

<br>

---

# Support

For issues or questions, visit the [GitHub Board](https://github.com/cancel-cloud/AuctionMaster/projects?query=is%3Aopen)
