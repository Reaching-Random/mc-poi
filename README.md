# Points of Interest — Minecraft Fabric Mod

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.1-blue.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric-0.18.5-green.svg)](https://fabricmc.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Points of Interest** is a Minecraft mod built with the Fabric API that lets you save, organize, and navigate to Points of Interest (POIs) directly in-game. It works **completely offline** out of the box and can optionally sync with your [Reaching Random](https://reachingrandom.com/mc/poi) account.

---

## Features

- **Offline by default** — No account required. POIs are stored locally in a JSON file compatible with the Reaching Random website's import/export format.
- **Cloud sync** — Connect your Reaching Random account to sync POIs across devices and view them on the website.
- **Shared worlds** — Share a world with everyone on your server, each with a role (read-only, contribute, admin, owner), from the [POI Tracker](https://reachingrandom.com/mc/poi) or with `/world share`. Changes from other players show up within 30 seconds.
- **Download snapshot** — Fetch your cloud data to a local file with `/poi download` for offline access or backup.
- **Multi-World & Groups** — Organize POIs by world and nested groups (categories).
- **Per-World Selection** — Each singleplayer world and each server remembers its own POI world, tracked POIs and group. Singleplayer worlds are matched by seed automatically; on a server (where the seed is hidden) you pick the world once and it sticks.
- **Dimension Support** — Tracks whether a POI is in the Overworld, Nether, or The End.
- **Multi-POI Tracking** — Track multiple POIs simultaneously, each with its own 3D direction indicator.
- **Campsites** — Name a campfire when you place it (or right-click it with an empty hand) and it becomes a POI in the "Campsites" group. Breaking the campfire removes the POI.
- **Find** — `/poi find <text>` lists every POI whose name or description contains the text, nearest first, with the closest one marked.
- **Schema-validated storage** — Local data files are validated against a versioned JSON schema on load to catch corruption early.

---

## Installation

1. Ensure you have **Fabric Loader** installed for Minecraft **26.1**.
2. Download the latest `points-of-interest-*.jar` from the [Releases](https://github.com/Reaching-Random/mc-poi/releases) page.
3. Place the JAR file in your Minecraft `mods/` folder.
4. Install the **Fabric API** mod. It is required; the mod will not load without it.

The mod works immediately after installation — no configuration needed for offline use.

---

## Storage Modes

The mod has two storage modes, selectable at any time:

| Mode | Command | Description |
|------|---------|-------------|
| **Offline** (default) | `/poi offline` | POIs saved to a local JSON file. No internet connection or account needed. |
| **Online** | `/poi online` | POIs synced to your Reaching Random account via the API. Requires an API key. |

Switching modes is instant and non-destructive — your offline data is never modified when switching to online mode, and vice versa.

### Offline data files

Local data lives at:
```
{minecraft-config}/poi/data/{playerUUID}-{profileKey}.json
```

The `profileKey` is:
- `offline` for pure local use (the default)
- A 12-character hash of your API key for data downloaded with `/poi download`

Files use the same JSON format as the Reaching Random website's import/export feature, so they're fully interchangeable.

---

## Quick Start (Offline)

```mcfunction
/world add                    # Create a world entry (uses current world name + seed)
/poi add Diamond Vein         # Save your current position as a POI
/poi list                     # List POIs in the current dimension
/poi track 1                  # Show a 3D direction arrow to POI #1
```

---

## Quick Start (Online / Cloud Sync)

1. Log in to [Reaching Random](https://reachingrandom.com), open the [POI Tracker](https://reachingrandom.com/mc/poi), click **Connect mod** and copy your API key (it starts with `mcpoi_`).
2. In Minecraft:
```mcfunction
/poi setkey <your-api-key>    # Saves key, links this Minecraft account, switches to online mode
/world list                   # Lists your cloud worlds (auto-selects if seed matches)
/poi add "My Base"            # Saves to the cloud immediately
```

### Download a cloud snapshot

To take your cloud data offline (backup, or offline play):
```mcfunction
/poi download <your-api-key>  # Downloads all worlds + POIs to a local file
/poi offline                  # Switch to the downloaded file
```

The downloaded file is tied to your API key via a hash — you can switch between multiple accounts' snapshots independently.

---

## Commands

### Storage & Setup

| Command | Description |
|---------|-------------|
| `/poi status` | Show current storage mode, data file, and world selection |
| `/poi offline` | Switch to offline storage (no API key needed) |
| `/poi online` | Switch to online storage (requires API key) |
| `/poi setkey <key>` | Save API key, link this Minecraft account, and switch to online mode |
| `/poi download <key>` | Download cloud data to a local file (key not persisted) |
| `/poi reset` | Clear all config and switch back to offline mode |

### World Management

| Command | Description |
|---------|-------------|
| `/world list [page]` | List your saved worlds |
| `/world add [name]` | Create a new world entry (auto-fills name and seed) |
| `/world select [#]` | Select a world for the Minecraft world or server you are in (with no number: the remembered world, or the one matching the seed) |
| `/world clear` | Deselect the current world and forget it for this Minecraft world or server |
| `/worlds [page]` | Alias for `/world list` |

### Shared Worlds (online mode)

Roles: **read-only** sees and tracks POIs and names campfires; **contribute** also adds POIs and groups and changes its own; **admin** changes anything and invites; **owner** also deletes the world and manages admins (on the website).

| Command | Description |
|---------|-------------|
| `/world share <role> <player...>` | Invite players who are on this server (admin). Linked players get the invite in game; for others you get a link to pass on |
| `/world invites` | List pending invites for the selected world, with **[Revoke]** (admin) |
| `/world members` | List the selected world's members and roles |
| `/world leave` | Leave a world someone shared with you |
| `/poi link` | Link this Minecraft account now and show any error. `/poi setkey` and joining a world already try this quietly |
| `/poi unlink` | Unlink this Minecraft account |

Invites sent to a linked account show up in chat with **[Accept]** and **[Decline]**. When an admin selects a shared world on a server, other members' mods select it automatically when they join that server (`autoSelectSharedWorlds` in the config, on by default).

### Group Management

| Command | Description |
|---------|-------------|
| `/group list [page]` | List groups in the current world |
| `/group add <name>` | Create a new group and select it |
| `/group select <#>` | Select a group (new POIs go here) |
| `/group clear` | Deselect group (POIs added at root level) |
| `/groups [page]` | Alias for `/group list` |

### POI Management

| Command | Description |
|---------|-------------|
| `/poi add <name> [desc]` | Add a POI at your current location |
| `/poi list [page]` | List POIs in your current dimension |
| `/poi list all [page]` | List POIs across all dimensions |
| `/poi list group <#> [page]` | List POIs in a specific group |
| `/poi find <text>` | List POIs whose name or description contains the text, nearest first; the closest in your dimension is marked `[Closest]` |
| `/poi track` | Show all tracked POIs |
| `/poi track <#> [# ...]` | Start tracking one or more POIs |
| `/poi untrack <#> [# ...]` | Stop tracking one or more POIs |
| `/poi track clear` | Stop tracking all POIs |
| `/poi campfires [on\|off]` | Turn campfire campsites on or off (on by default) |
| `/poi help` | Show command help (includes current storage mode) |
| `/pois [page]` | Alias for `/poi list` |
| `/pois all [page]` | Alias for `/poi list all` |
| `/track <#> [...]` | Alias for `/poi track` |
| `/untrack <#> [...]` | Alias for `/poi untrack` |
| `/find <text>` | Alias for `/poi find` |

---

## Campsites

Campfires can be saved as POIs, named much like a sign:

- **Place** a campfire and a name prompt opens. Enter a name to save it as a POI in the **Campsites** group (created automatically). Leave it blank, or press Esc, and nothing is saved.
- **Right-click** any campfire with an empty main hand to name or rename it. This also works on campfires placed before you installed the mod. Clearing the name removes the campsite.
- **Break** the campfire and its POI is deleted and untracked.

A campsite is linked to its campfire by position: any POI in the Campsites group at the campfire's exact block coordinates (same dimension) belongs to it. Everything runs on your client, so it works on any server, and campsites land in the selected POI world.

In a shared world the Campsites group is locked: POIs can't be added to it, moved in or out, or edited from the website or commands. Every member, read-only included, can name a campfire and remove a campsite by breaking it. Only the person who named a campsite, or an admin, can rename it or clear its name. A removed campsite can be restored by an admin on the website for 30 days.

A POI is only removed automatically if the mod saw the campfire earlier in the same session and then saw it disappear. If a campfire is broken while you're logged out, delete its POI with `/poi delete`. Turn the feature off with `/poi campfires off`.

---

## List Pagination

Commands that return lists support pagination (8 items per page). When a list spans multiple pages, a navigation bar appears at the bottom:

```
[◀ Prev]  Page 1/2  [Next ▶]
```

- **Click to navigate**: Click **[◀ Prev]** or **[Next ▶]** directly in chat — no typing needed.
- **Jump to page**: Append a page number (e.g. `/poi list 3`, `/worlds 2`).
- POI numbers are **global** — the same POI always has the same number regardless of whether you're filtering by dimension, so `/poi track 12` always refers to the same POI.
- **Clickable track toggles**: Each row shows `[ ]` (untracked) or `[*]` (tracked). Click either the indicator or the POI name to toggle tracking.
- **Clickable group toggles**: In `/group list`, click `[ ]` to select a group or `[*]` to deselect it.
- **Clickable file paths**: In `/poi status` and `/poi offline`, the data file path is a clickable link that opens the file in your OS default app.

---

## Data File Format

Offline data files use the same JSON schema as the Reaching Random website's import/export feature (`poi-state-v1.json`), making them fully interchangeable:

```json
{
  "version": 1,
  "worlds": [
    {
      "id": "abc123...",
      "name": "My Survival World",
      "seed": "1234567890",
      "created": "2024-01-01T00:00:00Z",
      "modified": "2024-01-15T12:34:56Z",
      "items": [
        {
          "type": "group",
          "id": "grp001...",
          "name": "Bases",
          "items": [
            {
              "type": "poi",
              "id": "poi001...",
              "name": "Main Base",
              "description": "Starter base with storage",
              "dimension": "overworld",
              "coords": { "x": 100, "y": 64, "z": -200 }
            }
          ]
        }
      ]
    }
  ]
}
```

The schema is validated on every file load. If the file fails validation, an error is shown in chat with instructions to restore from the website's export.

---

## Development

This repository contains the mod only. The Reaching Random website and the POI Tracker API that online mode syncs with are a separate, closed-source service. Offline mode needs neither.

Contributions are welcome. See [CONTRIBUTING.md](https://github.com/Reaching-Random/mc-poi/blob/HEAD/CONTRIBUTING.md) for how the branches are organized, and [SECURITY.md](https://github.com/Reaching-Random/mc-poi/blob/HEAD/SECURITY.md) to report a vulnerability.

### Prerequisites
- JDK 25+
- Gradle (provided via `./gradlew`)

### Build
```bash
./gradlew build
```

The build downloads the latest `poi-state-v1.json` schema from `reachingrandom.com` and bundles it in the JAR. If the server is unreachable, the committed fallback schema in `src/main/resources/schemas/` is used instead.

The resulting JAR will be in `build/libs/`.

### Run Locally
```bash
./gradlew runClient
```

### Local API Override
To develop against a local backend, set the JVM property:
```
-Dreaching.random.api.root=http://localhost:3000
```

Or add it to your run configuration.

---

## License

This project is licensed under the [MIT License](LICENSE).
