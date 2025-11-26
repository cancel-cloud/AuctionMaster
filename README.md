![AuctionMaster](https://cdn.upload.systems/uploads/vsr7ycbh.jpg)

# AuctionMaster 🏦

**The new Way to Auction Stuff - User to User, directly.**

AuctionMaster is a modern, feature-rich auction house plugin for Minecraft servers. It allows players to safely trade items with each other through a global auction system, featuring a beautiful GUI, offline transaction support, and robust database storage.

---

## ✨ Features

- **Global Auction House**: Players can list items for sale and browse listings from others.
- **Modern GUI**: Intuitive and beautiful inventory interfaces for browsing, buying, and managing auctions.
- **Offline Support**:
  - Sellers receive payments even if they are offline when their item sells.
  - Items from expired auctions are safely stored until the player returns.
- **Robust Storage**:
  - Supports **SQLite** (default, zero-setup) and **MySQL/PostgreSQL** for larger networks.
  - Uses the **Exposed** framework for reliable database interactions.
- **Configurable**:
  - Customizable auction duration and fees.
  - Blacklist items that cannot be auctioned.
  - Adjustable minimum and maximum prices.
- **Safe & Secure**:
  - Transactional integrity prevents item/money loss.
  - **Lockdown Mode**: Admins can instantly freeze all auction activity in emergencies.
- **Multi-Currency**: Built on **Vault** to support any economy plugin.

---

## 📥 Installation

1.  Download the `AuctionMaster-1.0.0.jar`.
2.  Ensure you have **Vault** and a compatible economy plugin (e.g., EssentialsX) installed.
3.  Place the jar in your server's `plugins` folder.
4.  Restart your server.
5.  (Optional) Configure `config.yml` to switch to MySQL/PostgreSQL if desired.

---

## 🎮 Commands

| Command                              | Permission             | Description                                                                |
| :----------------------------------- | :--------------------- | :------------------------------------------------------------------------- |
| `/auction`                           | `auctionmaster.use`    | Open the main auction house menu.                                          |
| `/auction create <price> [duration]` | `auctionmaster.create` | List the item in your hand for sale. Duration examples: `1h`, `12h`, `2d`. |
| `/auction list`                      | `auctionmaster.use`    | Browse active auctions.                                                    |
| `/auction history [player]`          | `auctionmaster.use`    | View your purchase history (or others with permission).                    |
| `/auction view <player>`             | `auctionmaster.use`    | View all active auctions from a specific player.                           |
| `/auction cancel`                    | `auctionmaster.create` | Cancel your active auctions and retrieve items.                            |
| `/auction info <id>`                 | `auctionmaster.use`    | View detailed info about a specific auction.                               |
| `/auction help`                      | `auctionmaster.use`    | Show the help menu.                                                        |

### Admin Commands

| Command                      | Permission            | Description                                                        |
| :--------------------------- | :-------------------- | :----------------------------------------------------------------- |
| `/auction reload`            | `auctionmaster.admin` | Reload the plugin configuration and language files.                |
| `/auction lockdown <on/off>` | `auctionmaster.admin` | Enable or disable lockdown mode (prevents new auctions/purchases). |
| `/auction migrate`           | `auctionmaster.admin` | Migrate database from SQLite to PostgreSQL.                        |

---

## 👮 Permissions

| Permission Node                | Description                                                | Default |
| :----------------------------- | :--------------------------------------------------------- | :------ |
| `auctionmaster.use`            | Allows access to the auction house and basic commands.     | `true`  |
| `auctionmaster.create`         | Allows listing items for auction.                          | `true`  |
| `auctionmaster.buy`            | Allows purchasing items.                                   | `true`  |
| `auctionmaster.history.others` | Allows viewing other players' transaction history.         | `op`    |
| `auctionmaster.admin`          | Grants full administrative access (reload, lockdown, etc). | `op`    |
| `auctionmaster.notify`         | Receives login notifications about database issues.        | `op`    |

---

## 🔧 Configuration

The `config.yml` allows you to tweak almost every aspect of the plugin:

- **Database**: Switch between SQLite, MySQL, and PostgreSQL.
- **Auctions**: Set max auctions per player, default duration, and price limits.
- **Fees**: Enable listing fees based on auction duration (e.g., higher fee for 48h listing).
- **Blacklist**: Prevent specific items (like Bedrock or Command Blocks) from being auctioned.
- **GUI**: Customize the number of items per page and refresh rates.

---

## 🆘 Support

If you encounter any issues or have feature requests, please open an issue on our [GitHub Repository](https://github.com/cancel-cloud/AuctionMaster/issues).

---

_Made with ❤️ by Cancelcloud_
