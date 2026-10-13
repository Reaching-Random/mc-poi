# Points of Interest

Save, organize and find your way back to the places that matter: your base, a diamond vein, a woodland mansion, that one village with the good trades. Press **I** for a screen to manage it all, or use chat commands. It works on any server, and **completely offline** out of the box.

Want your POIs everywhere? Connect a free [Reaching Random](https://reachingrandom.com/mc/poi) account to sync them to the web, and **share a world** with everyone you play with.

## Features

- **In-game screen**: press **I** to browse your POIs by group, tick a checkbox to track one, and click a POI to edit or move it. Manage groups, pick and share worlds, answer invites and change settings from the same screen.
- **POIs with positional labels**: save your spot with `/poi add`, then track one or more POIs. Each shows a floating label with its name and distance, placed in its direction.
- **Worlds and groups**: each singleplayer world and server remembers its own POI world. Organize POIs into groups like Farms or Portals. Deleting a group never deletes its POIs.
- **Move POIs**: `/poi move <#>`, or "Use current position" in the editor, puts a POI where you're standing.
- **HUD** (optional): your position and current group in a corner of the screen.
- **Every dimension**: POIs know whether they're in the Overworld, Nether or End, and lists show your current dimension first.
- **Find**: `/poi find <text>` lists matching POIs, nearest first.
- **Campsites**: name a campfire when you place it and it becomes a POI. Break the campfire and it's gone.
- **Offline by default**: POIs live in a local file. No account needed.
- **Cloud sync** (optional): view and edit your POIs on the [POI Tracker](https://reachingrandom.com/mc/poi) website, from any device.
- **Shared worlds** (optional): one POI list for your whole server, with roles.

## Shared worlds

Share a world from the website, or in game from the World tab or with `/world share <role> <player...>`. Each member gets a role:

| Role | Can |
|---|---|
| Read-only | See and track every POI, name campfires |
| Contribute | Also add POIs and groups, and change their own |
| Admin | Change anything, invite others |
| Owner | Everything, including deleting the world |

Players who've connected their account get invites right in game, in chat and on the screen's World tab, with **Accept** and **Decline**. Others get a link you can pass on. Changes from other players show up in your game automatically, and when an admin picks the shared world on a server, everyone else's mod selects it when they join.

## Getting started

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop Points of Interest into your `mods` folder.
3. In game, press **I**, or run `/poi help` for the commands.

```
/world add              Create a POI world for this world or server
/poi add "Home Base"    Save where you're standing
/pois                   List POIs in this dimension
/track 1                Show a floating label for POI #1
```

**To sync online:** sign in at [reachingrandom.com](https://reachingrandom.com/mc/poi), click **Connect mod**, copy your key, then paste it into the screen's Settings tab, or run `/poi setkey <key>`. That also links your Minecraft account, so shared-world members see your name.

Full guide: [reachingrandom.com/mc/poi/help](https://reachingrandom.com/mc/poi/help)

## Privacy

Points of Interest is a client-side mod and works on any server, including vanilla servers. In offline mode, the default, it never connects to the internet.

In online mode it talks only to reachingrandom.com, and to Mojang's session server to confirm your Minecraft account when linking. It sends your API key and the POI data you create: world names and seeds, POIs, groups and campsites. For shared worlds it also sends the server address you play on, the names of players you invite, and your Minecraft name and UUID. Your Minecraft password and access token never leave your game. No ads, no analytics, no tracking.

Details: [Privacy policy](https://reachingrandom.com/privacy)
