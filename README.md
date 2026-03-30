# POI Tracker — Minecraft Fabric Mod

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1-blue.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric-0.18.5-green.svg)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**POI Tracker** is a powerful, cloud-synced Minecraft mod built with the Fabric API. It allows you to save, categorize, and manage your Points of Interest (POIs) directly in-game and synchronize them with the [Reaching Random](https://reachingrandom.com/mc/poi) website.

---

## 🚀 Features

- **🌐 Cloud Sync**: Your POIs are saved to your Reaching Random account, allowing you to view and share them from anywhere.
- **📁 Multi-World & Groups**: Organize your POIs by world entry and nested groups (categories).
- **🌱 Seed Detection**: Automatically identifies singleplayer worlds by their seed for seamless selection.
- **🌌 Dimension Support**: Tracks whether a POI is in the Overworld, Nether, or The End.
- **📍 Live Tracking**: Show a 3D direction indicator towards a selected POI.
- **💻 Clean CLI**: A structured and intuitive command interface.

---

## 🛠️ Installation

1.  Ensure you have **Fabric Loader** installed for Minecraft **26.1**.
2.  Download the latest `points-of-interest-*.jar` from the [Releases](https://github.com/Reaching-Random/mc-poi/releases) page.
3.  Place the JAR file in your Minecraft `mods/` folder.
4.  (Optional but Recommended) Install the **Fabric API** mod.

---

## ⚙️ Setup

Before you can sync POIs, you need an API key:

1.  Log in to [Reaching Random](https://reachingrandom.com).
2.  Go to your **Settings** and copy your **POI Tracker API Key**.
3.  In Minecraft, run:
    ```mcfunction
    /poi setkey <your-api-key>
    ```

---

## 🎮 Basic Commands

### World Management
- `/world list`: Show all your saved worlds.
- `/world add [name]`: Create a new world record.
- `/world select <#>`: Select a world from the list.
- `/world select`: Auto-select based on the current world seed.
- `/world help`: Show all world management commands.

### Group Management
- `/group list`: List categories/groups in the current world.
- `/group add <name>`: Create a new group.
- `/group select <#>`: Focus on a group (new POIs will be added here).
- `/group clear`: Deselect the current group.

### POI Management
- `/poi add <name> [desc]`: Add a POI at your current location.
- `/poi list [group#]`: List POIs in the current world or a specific group.
- `/poi track <#>`: Track a POI from the last `/poi list` (shows direction).
- `/poi track clear`: Stop tracking the current POI.
- `/poi help`: Show general help.
- `/poi reset`: Wipe all local configs and API key.

---

## 👩‍💻 Development

### Prerequisites
- JDK 21+
- Gradle (provided via `./gradlew`)

### Build
To compile the mod and generate a JAR:
```bash
./gradlew build
```
The resulting JAR will be in `build/libs/`.

### Run Locally
To launch a development instance of Minecraft:
```bash
./gradlew runClient
```

### Local API Override
For development against a local backend, create a `.env.development` file in the root:
```env
REACHING_RANDOM_API_ROOT=http://localhost:3000
```

---

## 📜 License

This project is licensed under the [MIT License](LICENSE).
