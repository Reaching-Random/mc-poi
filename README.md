# Points of Interest — Minecraft Fabric Mod

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11-blue.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric-0.18.4-green.svg)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Points of Interest** is a powerful, cloud-synced Minecraft mod built with the Fabric API. It allows you to save, categorize, and manage your Points of Interest (POIs) directly in-game and synchronize them with the [Reaching Random](https://reachingrandom.com/mc/poi) website.

---

## 🚀 Features

- **🌐 Cloud Sync**: Your POIs are saved to your Reaching Random account, allowing you to view and share them from anywhere.
- **📁 Multi-World & Groups**: Organize your POIs by world entry and nested groups (categories).
- **🌱 Per-World Selection**: Each singleplayer world and each server remembers its own POI world, tracked POIs and group. Singleplayer worlds are matched by seed automatically; on a server (where the seed is hidden) you pick the world once and it sticks.
- **🌌 Dimension Support**: Tracks whether a POI is in the Overworld, Nether, or The End.
- **📍 Multi-POI Tracking**: Track multiple POIs simultaneously, each with its own 3D direction indicator.
- **🔎 Find**: `/poi find <text>` lists every POI whose name or description contains the text, nearest first, with the closest one marked.
- **🏕️ Campsites**: Name a campfire when you place it (or right-click it with an empty hand) and it becomes a POI in the "Campsites" group. Breaking the campfire removes the POI.
- **💻 Clean CLI**: A structured and intuitive command interface.

---

## 🛠️ Installation

1.  Ensure you have **Fabric Loader** installed for Minecraft **1.21.11**.
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
- `/world list [page]`: Show your saved worlds (paginated).
- `/world add [name]`: Create a new world record.
- `/world select <#>`: Select a world from the list for the Minecraft world or server you are in.
- `/world select`: Auto-select the remembered world, or the one matching the current world seed.
- `/world clear`: Deselect the current world and forget it for this Minecraft world or server.
- `/world help`: Show all world management commands.

### Group Management
- `/group list [page]`: List categories/groups in the current world (paginated). Click `[ ]` next to a group to select it; click `[*]` to deselect.
- `/group add <name>`: Create a new group.
- `/group select <#>`: Focus on a group (new POIs will be added here).
- `/group clear`: Deselect the current group.

### POI Management
- `/poi add <name> [desc]`: Add a POI at your current location.
- `/poi list [page]`: List all POIs in the current world (paginated). Click `[ ]` next to any POI to start tracking it; click `[*]` to stop.
- `/poi list group <#> [page]`: List POIs within a specific group.
- `/poi track`: List all currently tracked POIs (numbers match `/pois` for easy reference).
- `/poi track <#>`: Add a POI from the last list to tracked POIs (multiple allowed).
- `/poi untrack <#>`: Remove a POI from tracked (use the number shown in `/poi list`).
- `/poi find <text>` (or `/find <text>`): List POIs whose name or description contains the text, nearest first. The closest one in your dimension is marked `[Closest]`.
- `/poi track clear`: Stop tracking all POIs.
- `/poi campfires [on|off]`: Turn campfire campsites on or off (on by default).
- `/poi help`: Show general help.
- `/poi reset`: Wipe all local configs and API key.

### Aliases
- `/worlds [page]` → `/world list [page]`
- `/groups [page]` → `/group list [page]`
- `/pois [page]` → `/poi list [page]`

---

## 🏕️ Campsites

Campfires can be saved as POIs, named much like a sign:

- **Place** a campfire and a name prompt opens. Enter a name to save it as a POI in the **Campsites** group (created automatically). Leave it blank, or press Esc, and nothing is saved.
- **Right-click** any campfire with an empty main hand to name or rename it. This also works on campfires placed before you installed the mod. Clearing the name removes the campsite.
- **Break** the campfire and its POI is deleted and untracked.

A campsite is linked to its campfire by position: any POI in the Campsites group at the campfire's exact block coordinates (same dimension) belongs to it. Everything runs on your client, so it works on any server, and campsites land in *your* POI list only.

A POI is only removed automatically if the mod saw the campfire earlier in the same session and then saw it disappear. If a campfire is broken while you're logged out, delete its POI with `/poi delete`. Turn the feature off with `/poi campfires off`.

---

## 📖 List Pagination

Commands that return lists support pagination (8 items per page). When a list spans multiple pages, a navigation bar appears at the bottom:

`[◀ Prev]  Page 1/2  [Next ▶]`

- **Click to navigate**: Click **[◀ Prev]** or **[Next ▶]** directly in chat — no typing needed.
- **Jump to page**: Append a page number to any list command (e.g. `/poi list 3`, `/worlds 2`, `/pois 2`).
- POI numbers are **global** across pages, so `/poi track 12` always refers to the same POI regardless of which page it's on.
- **Clickable track toggles**: In `/poi list` and `/pois`, each row shows `[ ]` (untracked) or `[*]` (tracked). Click either the indicator or the POI name to toggle tracking without typing a command. The same numbers are used by `/poi track` and `/poi untrack`.
- **Clickable group toggles**: In `/group list` and `/groups`, click `[ ]` to select a group or `[*]` to deselect it.
- **Clickable URLs**: URLs shown in chat (e.g. in `/poi help`) can be clicked to copy them to your clipboard.

---

## 👩‍💻 Development

This repository contains the mod only. The Reaching Random website and the POI Tracker API that online mode syncs with are a separate, closed-source service. Offline mode needs neither.

Contributions are welcome. See [CONTRIBUTING.md](https://github.com/Reaching-Random/mc-poi/blob/HEAD/CONTRIBUTING.md) for how the branches are organized, and [SECURITY.md](https://github.com/Reaching-Random/mc-poi/blob/HEAD/SECURITY.md) to report a vulnerability.

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
