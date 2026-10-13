# Modrinth listing

The source of truth for the mod's Modrinth page. Edit these files, then paste them into Modrinth by hand. Modrinth doesn't read them.

| File | Modrinth field | Format |
|---|---|---|
| `description.md` | Description | Markdown |
| `disclosure.txt` | Data disclosure | Plain text |

Update them in the same change as any feature that players would notice (`feat:`), and any change to what the mod sends over the network, which needs `disclosure.txt` and the Privacy section of `description.md` updated together.

Describe tracking as a floating label with the POI's name and distance, placed in its direction. Never call it an arrow.

## Gallery

Upload these to the Modrinth gallery, with the titles and descriptions below. The first is the featured image. Captured at 2560×1440 with GUI scale 4, using the local dev site's seeded data (`pnpm dev:local` in `reaching-random`), not a real account.

| File | Title | Description |
|---|---|---|
| `screenshots/tracking-labels.jpg` | Tracked POIs | Each tracked POI shows a floating label with its name and distance, placed in its direction. The HUD in the corner shows your position and the current group. |
| `screenshots/pois-tab.png` | Your POIs | Press I to browse POIs by group or dimension, search them, and tick a checkbox to track one. |
| `screenshots/edit-poi.png` | Edit a POI | Click a POI to rename it, change its group, or move it to where you're standing. |
| `screenshots/world-tab.png` | Shared worlds | See who's in a shared world, invite players on your server, and answer invites, all in game. |
| `screenshots/settings-tab.png` | Settings | Switch between offline and online storage, add your API key, and turn on the HUD. |

Retake them when a screen changes enough that they'd mislead.
