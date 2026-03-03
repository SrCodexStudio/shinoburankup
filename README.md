<div align="center">

# ❄️ ShinobuRankup

### The Ultimate Rankup Experience for Modern Minecraft Servers

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20--1.21.x-green?style=for-the-badge&logo=minecraft)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple?style=for-the-badge&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-Proprietary-red?style=for-the-badge)](LICENSE)

<img width="1024" height="1024" alt="ShinobuRankupv2" src="https://github.com/user-attachments/assets/392730ad-8564-4e03-8db4-8a56b9c21268" />


**Beautiful GUIs • Async Database • Leaderboards • Fully Customizable**

[Features](#-features) •
[Installation](#-installation) •
[Commands](#-commands) •
[Configuration](#%EF%B8%8F-configuration) •
[PlaceholderAPI](#-placeholderapi) •
[Support](#-support)

</div>

---

## 📋 Overview

**ShinobuRankup** is a premium, high-performance rankup plugin built with **Kotlin** for modern Paper servers. It provides an elegant and smooth ranking experience with stunning animated GUIs, async database operations, and complete customization.

Whether you're running a survival server, prison, skyblock, or any economy-based gamemode, ShinobuRankup delivers everything you need for an engaging progression system.

---

## ✨ Features

<table>
<tr>
<td width="50%">

### 🎮 Core Features
- ✅ Unlimited custom ranks
- ✅ Beautiful animated GUIs
- ✅ Prestige system
- ✅ Leaderboard with pagination
- ✅ RankupMax command
- ✅ Confirmation GUI

</td>
<td width="50%">

### ⚡ Performance
- ✅ Kotlin coroutines (non-blocking)
- ✅ HikariCP connection pooling
- ✅ Smart caching system
- ✅ Async database operations
- ✅ Optimized queries

</td>
</tr>
<tr>
<td width="50%">

### 🎨 Customization
- ✅ 100% configurable messages
- ✅ Custom GUI layouts
- ✅ Particles & sound effects
- ✅ Titles & action bars
- ✅ Broadcast messages
- ✅ Multi-language support

</td>
<td width="50%">

### 💾 Storage
- ✅ SQLite (zero config)
- ✅ MySQL/MariaDB support
- ✅ BungeeCord/Velocity ready
- ✅ Auto-migration

</td>
</tr>
</table>

---

## 📥 Installation

1. Download the latest release from [Releases](../../releases)
2. Place `ShinobuRankup.jar` in your `plugins/` folder
3. Install required dependencies:
   - [Vault](https://www.spigotmc.org/resources/vault.34315/)
   - Any economy plugin (EssentialsX, CMI, etc.)
4. Restart your server
5. Configure in `plugins/ShinobuRankup/`

### Requirements

| Requirement | Version |
|-------------|---------|
| Server | Paper, Purpur, or Folia |
| Minecraft | 1.20 - 1.21.x |
| Java | 17 or higher |
| Vault | Latest |

### Optional Dependencies

- **PlaceholderAPI** - For placeholders support
- **LuckPerms** - For permission rewards

---

## 📝 Commands

### Player Commands
> No permissions required - Available to all players!

| Command | Aliases | Description |
|---------|---------|-------------|
| `/rankup` | `/ru`, `/upgrade` | Rank up to the next level |
| `/rankupmax` | `/rumax`, `/maxrankup` | Rank up as many times as you can afford |
| `/ranks` | `/rank`, `/progression` | Open the ranks GUI |
| `/rankuptop` | `/ranktop`, `/leaderboard` | View the top players leaderboard |

### Admin Commands
> Requires `shinoburankup.admin` permission (OP by default)

| Command | Description |
|---------|-------------|
| `/shinobu reload` | Reload all configurations |
| `/shinobu setrank <player> <rank>` | Set a player's rank |
| `/shinobu reset <player>` | Reset a player's progression |
| `/shinobu info <player>` | View player information |
| `/shinobu give <player> [amount]` | Give free rankups |

---

## 🔐 Permissions

### Player Permissions
All enabled by default for every player:

| Permission | Description | Default |
|------------|-------------|---------|
| `shinoburankup.rankup` | Use /rankup | `true` |
| `shinoburankup.rankupmax` | Use /rankupmax | `true` |
| `shinoburankup.rank` | Use /ranks GUI | `true` |
| `shinoburankup.top` | View leaderboard | `true` |

### Admin Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `shinoburankup.admin` | All admin commands | `op` |
| `shinoburankup.admin.reload` | Reload config | `op` |
| `shinoburankup.admin.setrank` | Set player ranks | `op` |
| `shinoburankup.admin.reset` | Reset players | `op` |
| `shinoburankup.admin.give` | Give free rankups | `op` |

### Bypass Permissions

| Permission | Description |
|------------|-------------|
| `shinoburankup.bypass.cooldown` | Bypass rankup cooldown |
| `shinoburankup.bypass.cost` | Free rankups |
| `shinoburankup.bypass.requirements` | Skip requirements |

---

## ⚙️ Configuration

```
plugins/ShinobuRankup/
├── config.yml          # Main settings
├── ranks.yml           # Rank definitions
├── messages.yml        # Broadcast & effects
├── gui.yml             # GUI customization
├── data.db             # SQLite database
└── lang/
    ├── en.yml          # English
    └── es.yml          # Spanish
```

### Example Rank Configuration

```yaml
ranks:
  A:
    display-name: "<gradient:#00ff00:#00aa00>Rank A</gradient>"
    cost: 1000
    order: 1
    material: COAL
    lore:
      - "&7The beginning of your journey"
      - "&7Cost: &a$1,000"
    commands:
      - "lp user %player% parent set rankA"
    broadcast: true

  B:
    display-name: "<gradient:#55ff55:#00ff00>Rank B</gradient>"
    cost: 5000
    order: 2
    material: IRON_INGOT
    lore:
      - "&7Moving up!"
      - "&7Cost: &a$5,000"
    commands:
      - "lp user %player% parent set rankB"
```

---

## 🔗 PlaceholderAPI

Integrate with any plugin using these placeholders:

| Placeholder | Description |
|-------------|-------------|
| `%shinobu_rank%` | Current rank display name |
| `%shinobu_rank_id%` | Current rank ID |
| `%shinobu_rank_order%` | Current rank position |
| `%shinobu_next_rank%` | Next rank display name |
| `%shinobu_next_rank_cost%` | Cost to next rank |
| `%shinobu_progress%` | Progress percentage |
| `%shinobu_progress_bar%` | Visual progress bar |
| `%shinobu_total_ranks%` | Total number of ranks |
| `%shinobu_is_max_rank%` | true/false if max rank |
| `%shinobu_rankups_count%` | Total rankups completed |
| `%shinobu_total_spent%` | Total money spent |
| `%shinobu_leaderboard_pos%` | Leaderboard position |

---

## 🖼️ Screenshots

<div align="center">

<img width="545" height="443" alt="Rank" src="https://github.com/user-attachments/assets/3cbe9462-84d3-4be0-b110-9fbf7f2d23e4" />
<img width="382" height="263" alt="RankupTOP" src="https://github.com/user-attachments/assets/5b1aec31-e3b9-4a48-8e70-17de323df1e2" />
<img width="458" height="249" alt="TAB-SUPPORT" src="https://github.com/user-attachments/assets/b46a2dbf-a3e1-4b2f-895d-6f33853b0e28" />


</div>

---

## 🛠️ Building from Source

```bash
NO OPEN SOURCE
```

---

## 📞 Support

Need help? Found a bug? Have a suggestion?
- 🐛 [Issue Tracker](../../issues)
- 💬 [Discord Server](https://discord.com/invite/4crMdptpP6)

---

## 📄 License

This project is proprietary software. See [LICENSE](LICENSE) for details.

- ❌ Do not redistribute or resell
- ❌ Do not decompile or modify
- ❌ Do not claim as your own

---

<div align="center">

### ❄️ Thank you for choosing ShinobuRankup!

**Made with ❤️ and Kotlin**

<sub>Copyright © 2026 SrCodexStudio. All rights reserved.</sub>

</div>
