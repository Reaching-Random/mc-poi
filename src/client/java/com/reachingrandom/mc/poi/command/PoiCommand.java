package com.reachingrandom.mc.poi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.campsite.CampsiteIndex;
import com.reachingrandom.mc.poi.config.PoiConfig;
import com.reachingrandom.mc.poi.storage.LocalPoiStorage;
import com.reachingrandom.mc.poi.storage.PoiStorage;
import com.reachingrandom.mc.poi.storage.PoiStorageProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class PoiCommand {

    private static final String HELP_PATH = "/mc/poi/help";
    private static final int PAGE_SIZE = 8;

    private PoiCommand() {}

    // ── Registration ───────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // --- /world <command> ---
        dispatcher.register(
            literal("world")
                .executes(ctx -> executeWorldHelp(ctx.getSource()))
                .then(literal("help")
                    .executes(ctx -> executeWorldHelp(ctx.getSource())))

                // /world list [page]
                .then(literal("list")
                    .executes(ctx -> executeWorlds(ctx.getSource(), 1))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeWorlds(ctx.getSource(),
                                getInteger(ctx, "page")))))

                // /world add [name]
                .then(literal("add")
                    .executes(ctx -> executeNew(ctx.getSource(), null))
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> executeNew(ctx.getSource(),
                                getString(ctx, "name")))))

                // /world select [number]
                .then(literal("select")
                    .executes(ctx -> executeAutoSelect(ctx.getSource()))
                    .then(argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeSelect(ctx.getSource(),
                                getInteger(ctx, "number")))))

                // /world clear
                .then(literal("clear")
                    .executes(ctx -> executeClear(ctx.getSource())))
        );

        // --- /poi <command> ---
        dispatcher.register(
            literal("poi")
                .executes(ctx -> executePoiHelp(ctx.getSource()))
                .then(literal("help")
                    .executes(ctx -> executePoiHelp(ctx.getSource())))

                // /poi reset
                .then(literal("reset")
                    .executes(ctx -> executeReset(ctx.getSource())))

                // /poi setkey <key>
                .then(literal("setkey")
                    .then(argument("key", StringArgumentType.greedyString())
                        .executes(ctx -> executeSetKey(ctx.getSource(),
                                getString(ctx, "key")))))

                // /poi status
                .then(literal("status")
                    .executes(ctx -> executeStatus(ctx.getSource())))

                // /poi offline
                .then(literal("offline")
                    .executes(ctx -> executeOffline(ctx.getSource())))

                // /poi online
                .then(literal("online")
                    .executes(ctx -> executeOnline(ctx.getSource())))

                // /poi off
                .then(literal("off")
                    .executes(ctx -> executeOff(ctx.getSource())))

                // /poi download <key>
                .then(literal("download")
                    .then(argument("key", StringArgumentType.greedyString())
                        .executes(ctx -> executeDownload(ctx.getSource(),
                                getString(ctx, "key")))))

                // /poi campfires [on|off]
                .then(literal("campfires")
                    .executes(ctx -> executeCampfires(ctx.getSource(), null))
                    .then(literal("on")
                        .executes(ctx -> executeCampfires(ctx.getSource(), true)))
                    .then(literal("off")
                        .executes(ctx -> executeCampfires(ctx.getSource(), false))))

                // /poi groups
                .then(literal("groups")
                    .executes(ctx -> executeGroups(ctx.getSource(), 1)))

                // /poi list [page]         — current dimension only (default)
                // /poi list all [page]     — all dimensions
                // /poi list group <#> [page]
                .then(literal("list")
                    .executes(ctx -> executeList(ctx.getSource(), 0, 1, false))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeList(ctx.getSource(), 0,
                                getInteger(ctx, "page"), false)))
                    .then(literal("all")
                        .executes(ctx -> executeList(ctx.getSource(), 0, 1, true))
                        .then(argument("page", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeList(ctx.getSource(), 0,
                                    getInteger(ctx, "page"), true))))
                    .then(literal("group")
                        .then(argument("group#", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeList(ctx.getSource(),
                                    getInteger(ctx, "group#"), 1, false))
                            .then(argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeList(ctx.getSource(),
                                        getInteger(ctx, "group#"),
                                        getInteger(ctx, "page"), false))))))

                // /poi delete <#>
                .then(literal("delete")
                    .then(argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeDelete(ctx.getSource(),
                                getInteger(ctx, "number")))))

                // /poi add <name> [description]
                .then(literal("add")
                    .then(argument("name", StringArgumentType.string())
                        .executes(ctx -> executeAdd(ctx.getSource(),
                                getString(ctx, "name"), ""))
                        .then(argument("description", StringArgumentType.greedyString())
                            .executes(ctx -> executeAdd(ctx.getSource(),
                                    getString(ctx, "name"),
                                    getString(ctx, "description"))))))

                // /poi track [number(s) | clear]
                .then(literal("track")
                    .executes(ctx -> executeTrackList(ctx.getSource()))
                    .then(literal("clear")
                        .executes(ctx -> executeTrackClear(ctx.getSource())))
                    .then(argument("numbers", StringArgumentType.greedyString())
                        .executes(ctx -> executeTrackNumbers(ctx.getSource(),
                                getString(ctx, "numbers")))))

                // /poi untrack <number(s)>
                .then(literal("untrack")
                    .then(argument("numbers", StringArgumentType.greedyString())
                        .executes(ctx -> executeUntrackNumbers(ctx.getSource(),
                                getString(ctx, "numbers")))))

                // /poi find <text>   — POIs whose name/description contains the text
                .then(literal("find")
                    .then(argument("query", StringArgumentType.greedyString())
                        .executes(ctx -> executeFind(ctx.getSource(),
                                getString(ctx, "query")))))
        );

        // --- /group <command> ---
        dispatcher.register(
            literal("group")
                .executes(ctx -> executeGroupHelp(ctx.getSource()))
                .then(literal("list")
                    .executes(ctx -> executeGroups(ctx.getSource(), 1))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeGroups(ctx.getSource(),
                                getInteger(ctx, "page")))))
                .then(literal("add")
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> executeNewGroup(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("select")
                    .then(argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeSelectGroup(ctx.getSource(), getInteger(ctx, "number")))))
                .then(literal("clear")
                    .executes(ctx -> executeClearGroup(ctx.getSource())))
        );

        // --- /groups [page] ---
        dispatcher.register(
            literal("groups")
                .executes(ctx -> executeGroups(ctx.getSource(), 1))
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeGroups(ctx.getSource(), getInteger(ctx, "page"))))
        );

        // --- Aliases ---
        dispatcher.register(
            literal("worlds")
                .executes(ctx -> executeWorlds(ctx.getSource(), 1))
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeWorlds(ctx.getSource(), getInteger(ctx, "page"))))
        );

        dispatcher.register(
            literal("pois")
                .executes(ctx -> executeList(ctx.getSource(), 0, 1, false))
                .then(literal("all")
                    .executes(ctx -> executeList(ctx.getSource(), 0, 1, true))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeList(ctx.getSource(), 0, getInteger(ctx, "page"), true))))
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes(ctx -> executeList(ctx.getSource(), 0, getInteger(ctx, "page"), false)))
        );

        // /track [number(s)] → /poi track
        dispatcher.register(
            literal("track")
                .executes(ctx -> executeTrackList(ctx.getSource()))
                .then(literal("clear")
                    .executes(ctx -> executeTrackClear(ctx.getSource())))
                .then(argument("numbers", StringArgumentType.greedyString())
                    .executes(ctx -> executeTrackNumbers(ctx.getSource(),
                            getString(ctx, "numbers"))))
        );

        // /find <text> → /poi find
        dispatcher.register(
            literal("find")
                .then(argument("query", StringArgumentType.greedyString())
                    .executes(ctx -> executeFind(ctx.getSource(),
                            getString(ctx, "query"))))
        );

        // /untrack <number(s)> → /poi untrack
        dispatcher.register(
            literal("untrack")
                .then(argument("numbers", StringArgumentType.greedyString())
                    .executes(ctx -> executeUntrackNumbers(ctx.getSource(),
                            getString(ctx, "numbers"))))
        );
    }

    // ── Help ──────────────────────────────────────────────────────────────────

    private static int executeWorldHelp(FabricClientCommandSource source) {
        send(source, header("Points of Interest — World Management"));
        send(source, gray("  /world list [page]    ") + "List your worlds");
        send(source, gray("  /world add [name]      ") + "Create a new world entry");
        send(source, gray("  /world select [number]") + "Select world for this save/server (auto-selects if no #)");
        send(source, gray("  /world clear          ") + "Clear currently selected world");
        send(source, "");
        send(source, gray("Run ") + "/poi help" + gray(" for POI and category commands."));
        return 1;
    }

    private static int executePoiHelp(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        String baseUrl = cfg.getApiBaseUrl();
        String modeTag = cfg.isOnlineMode() ? " §a[ONLINE]§r"
                       : cfg.isOffMode()    ? " §8[OFF]§r"
                       :                     " §e[OFFLINE]§r";
        send(source, header("Points of Interest — POI Management") + modeTag);
        send(source, gray("  /poi status               ") + "Show storage mode and configuration");
        send(source, gray("  /poi offline              ") + "Switch to offline storage");
        send(source, gray("  /poi online               ") + "Switch to online storage (requires API key)");
        send(source, gray("  /poi off                  ") + "Disable Points of Interest (hides arrows, blocks commands)");
        send(source, gray("  /poi setkey <key>         ") + "Save API key and switch to online mode");
        send(source, gray("  /poi reset                ") + "Remove API key and return to offline mode");
        send(source, gray("  /poi download <key>       ") + "Download cloud data to local file");
        send(source, gray("  /poi list [page]          ") + "List POIs in current dimension");
        send(source, gray("  /poi list all [page]      ") + "List POIs in all dimensions");
        send(source, gray("  /poi list group <#> [page]") + "List POIs in a specific group");
        send(source, gray("  /poi add <name> [desc]    ") + "Add POI (to current group if selected)");
        send(source, gray("  /poi delete <#>           ") + "Permanently delete a POI");
        send(source, gray("  /poi find <text>          ") + "List POIs containing text, closest first");
        send(source, gray("  /poi track                ") + "List all currently tracked POIs");
        send(source, gray("  /poi track <#> [# ...]    ") + "Track one or more POIs (from last list)");
        send(source, gray("  /poi untrack <#> [# ...]  ") + "Untrack one or more POIs");
        send(source, gray("  /poi track clear          ") + "Stop tracking all POIs");
        send(source, gray("  /poi campfires [on|off]   ") + "Name campfires to save them as Campsites");
        send(source, "");
        send(source, gray("  /pois             ") + "Shortcut for /poi list (current dimension)");
        send(source, gray("  /pois all         ") + "Shortcut for /poi list all");
        send(source, gray("  /track <#> [...]  ") + "Shortcut for /poi track");
        send(source, gray("  /untrack <#> [...] ") + "Shortcut for /poi untrack");
        send(source, gray("  /find <text>      ") + "Shortcut for /poi find");
        send(source, "");
        send(source, gray("Run ") + "/world help" + gray(" or ") + "/group help" + gray(" for more."));
        MutableComponent website = Component.literal("Website: ").withStyle(ChatFormatting.GRAY)
                .append(urlComponent(baseUrl + HELP_PATH));
        sendComponent(source, website);
        return 1;
    }

    private static int executeGroupHelp(FabricClientCommandSource source) {
        send(source, header("Points of Interest — Group Management"));
        send(source, gray("  /group list [page]    ") + "List groups in current world");
        send(source, gray("  /group add <name>      ") + "Create a new group");
        send(source, gray("  /group select <#>     ") + "Select target group for new POIs");
        send(source, gray("  /group clear          ") + "Deselect group (POIs added at root)");
        send(source, "");
        send(source, gray("Run ") + "/poi help" + gray(" for POI commands."));
        return 1;
    }

    // ── /poi off ───────────────────────────────────────────────────────────────

    private static int executeOff(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        if (cfg.isOffMode()) {
            send(source, gray("Points of Interest is already disabled. Run /poi offline or /poi online to re-enable."));
            return 1;
        }
        cfg.storageMode = "off";
        cfg.save();
        send(source, ok("Points of Interest disabled."));
        send(source, gray("  Direction arrows are hidden. Run /poi offline or /poi online to re-enable."));
        return 1;
    }

    // ── /poi campfires ─────────────────────────────────────────────────────────

    private static int executeCampfires(FabricClientCommandSource source, Boolean enable) {
        PoiConfig cfg = PoiConfig.get();
        if (enable != null) {
            cfg.campfireCampsites = enable;
            cfg.save();
        }
        if (cfg.campfireCampsites) {
            send(source, ok("Campfire campsites: ON"));
            send(source, gray("  Place a campfire, or right-click one with an empty hand, to name it."));
            send(source, gray("  Named campfires are saved in the \"" + CampsiteIndex.GROUP_NAME
                    + "\" group; breaking one removes its POI."));
        } else {
            send(source, ok("Campfire campsites: OFF"));
        }
        return 1;
    }

    // ── /poi status ────────────────────────────────────────────────────────────

    private static int executeStatus(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        send(source, header("Points of Interest — Status"));
        if (cfg.isOffMode()) {
            send(source, "  Mode:    §8OFF§r §7(run /poi offline or /poi online to re-enable)§r");
        } else if (cfg.isOnlineMode()) {
            send(source, "  Mode:    §aONLINE§r");
            send(source, "  API Key: " + (cfg.hasApiKey() ? "§a[set]§r" : "§c[not set — run /poi setkey]§r"));
        } else {
            send(source, "  Mode:    §eOFFLINE§r");
            Path dataFile = PoiStorageProvider.resolveDataFile(cfg);
            MutableComponent fileLine = Component.literal("  File:   ").withStyle(ChatFormatting.GRAY)
                    .append(fileLink(dataFile));
            sendComponent(source, fileLine);
        }
        if (cfg.currentWorldId != null) {
            send(source, gray("  World:  " + cfg.currentWorldId));
        } else {
            send(source, gray("  World:  (none selected — run /world list)"));
        }
        if (cfg.currentGroupId != null) {
            send(source, gray("  Group:  " + (cfg.currentGroupName != null ? cfg.currentGroupName : cfg.currentGroupId)));
        }
        send(source, gray("  Campfires: " + (cfg.campfireCampsites ? "on" : "off") + " (/poi campfires)"));
        return 1;
    }

    // ── /poi offline ───────────────────────────────────────────────────────────

    private static int executeOffline(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        cfg.storageMode    = "offline";
        cfg.save();

        Path dataFile = PoiStorageProvider.resolveDataFile(cfg);
        MutableComponent msg = Component.literal(ok("Switched to OFFLINE mode.  Data file: "))
                .append(fileLink(dataFile));
        sendComponent(source, msg);

        // World IDs are backend-specific — re-establish the world from the offline file
        reselectWorld(m -> send(source, m), "  ", false);
        return 1;
    }

    // ── /poi online ────────────────────────────────────────────────────────────

    private static int executeOnline(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        if (!cfg.hasApiKey()) {
            send(source, err("No API key set. Run /poi setkey <key> first."));
            return 0;
        }
        cfg.storageMode    = "online";
        cfg.save();
        send(source, ok("Switched to ONLINE mode."));

        // World IDs are backend-specific — re-establish the world from the API
        reselectWorld(m -> send(source, m), "  ", false);
        return 1;
    }

    // ── /poi setkey ────────────────────────────────────────────────────────────

    private static int executeSetKey(FabricClientCommandSource source, String key) {
        PoiConfig cfg = PoiConfig.get();
        cfg.apiKey         = key.trim();
        cfg.storageMode    = "online";
        cfg.save();
        send(source, ok("API key saved. Switched to ONLINE mode."));

        // Show offline data file for reference (data is never lost on mode switch)
        Path offlineFile = PoiStorageProvider.resolveDataFile(cfg);
        MutableComponent fileNote = Component.literal(gray("  Offline data: "))
                .append(fileLink(offlineFile));
        sendComponent(source, fileNote);
        send(source, gray("  Run /poi offline to switch back at any time."));

        // World IDs are backend-specific — re-establish the world from the API
        reselectWorld(m -> send(source, m), "  ", false);
        return 1;
    }

    // ── /poi download ──────────────────────────────────────────────────────────

    private static int executeDownload(FabricClientCommandSource source, String key) {
        String trimmedKey = key.trim();
        send(source, gray("Downloading POI data from your account..."));

        async(source, () -> {
            // Always use a one-shot ApiClient with the provided key — never persist the key
            ApiClient api = new ApiClient(trimmedKey);

            // Fetch world list
            List<ApiModels.WorldSummary> worlds;
            try {
                ApiModels.WorldsResponse resp = api.getWorlds();
                worlds = resp.worlds != null ? resp.worlds : List.of();
            } catch (ApiClient.ApiException e) {
                send(source, err("Download failed: " + e.getMessage()));
                return;
            }

            if (worlds.isEmpty()) {
                send(source, gray("No worlds found in this account. Nothing to download."));
                return;
            }

            // Build AppState by fetching items for each world
            ApiModels.AppState appState = new ApiModels.AppState();
            int totalPois = 0;

            for (ApiModels.WorldSummary summary : worlds) {
                ApiModels.WorldDetail detail = new ApiModels.WorldDetail();
                detail.id       = summary.id;
                detail.name     = summary.name;
                detail.seed     = summary.seed;
                detail.created  = summary.created;
                detail.modified = summary.modified;

                try {
                    List<ApiModels.WorldItem> items = api.getItems(summary.id).items;
                    detail.items = items != null ? items : new ArrayList<>();
                    for (ApiModels.WorldItem item : detail.items) {
                        if ("group".equals(item.type) && item.items != null) {
                            totalPois += item.items.size();
                        } else if ("poi".equals(item.type)) {
                            totalPois++;
                        }
                    }
                } catch (ApiClient.ApiException e) {
                    detail.items = new ArrayList<>();
                    send(source, gray("  Warning: failed to load items for \"" + summary.name + "\": " + e.getMessage()));
                }

                appState.worlds.add(detail);
            }

            // Compute a stable profile key from SHA-256 of the provided API key
            String profileKey = hashKey(trimmedKey);

            // Store profileKey in config so that /poi offline points to this file
            PoiConfig cfg = PoiConfig.get();
            cfg.offlineProfileKey = profileKey;
            cfg.save();

            // Write data file
            Path dataFile = PoiStorageProvider.resolveDataFile(cfg);
            LocalPoiStorage storage = new LocalPoiStorage(dataFile);
            try {
                storage.overwrite(appState);
            } catch (ApiClient.ApiException e) {
                send(source, err("Failed to save download: " + e.getMessage()));
                return;
            }

            send(source, ok("Downloaded " + worlds.size() + " world(s) with " + totalPois + " POI(s)."));
            MutableComponent fileMsg = Component.literal(gray("  Saved to: "))
                    .append(fileLink(dataFile));
            sendComponent(source, fileMsg);
            send(source, gray("  Run /poi offline to use this data file."));
        });
        return 1;
    }

    // ── /poi worlds ────────────────────────────────────────────────────────────

    private static int executeWorlds(FabricClientCommandSource source, int page) {
        if (!checkReady(source)) return 0;

        String currentSeed = getCurrentSeed();
        String place = getPlaceKey();
        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            List<ApiModels.WorldSummary> worlds = storage.listWorlds();

            if (worlds.isEmpty()) {
                send(source, gray("No worlds found. Use /world add to create one."));
                return;
            }

            // Check for seed matches
            List<ApiModels.WorldSummary> seedMatches = currentSeed != null
                    ? worlds.stream().filter(w -> currentSeed.equals(w.seed)).toList()
                    : List.of();

            // If multiple entries share the same seed, show only those for selection
            List<ApiModels.WorldSummary> listToShow;
            if (seedMatches.size() > 1) {
                listToShow = seedMatches;
                send(source, header("Worlds — Multiple matches for current seed"));
                send(source, gray("Multiple worlds match the current seed. Pick one:"));
            } else {
                listToShow = worlds;
                // Auto-select the single matching world only if none is selected
                if (seedMatches.size() == 1) {
                    ApiModels.WorldSummary match = seedMatches.get(0);
                    PoiConfig cfg = PoiConfig.get();
                    if (cfg.currentWorldId == null) {
                        cfg.bindWorld(place, match.id);
                        switchWorld(match.id);
                    }
                }
                send(source, header("Worlds"));
            }

            PoiSession.get().setLastWorldsList(listToShow);

            int totalPages = totalPages(listToShow.size(), PAGE_SIZE);
            int clampedPage = Math.max(1, Math.min(page, totalPages));
            List<ApiModels.WorldSummary> pageItems = getPage(listToShow, clampedPage, PAGE_SIZE);
            int globalOffset = (clampedPage - 1) * PAGE_SIZE;

            PoiConfig cfg = PoiConfig.get();
            for (int i = 0; i < pageItems.size(); i++) {
                ApiModels.WorldSummary w = pageItems.get(i);
                boolean isSelected  = w.id.equals(cfg.currentWorldId);
                boolean isSeedMatch = currentSeed != null && currentSeed.equals(w.seed);

                String marker = "";
                if      (isSelected && isSeedMatch) marker = " §a[Current]§r";
                else if (isSelected)                marker = " §c[Selected]§r";
                else if (isSeedMatch)               marker = " §b[Seed Match]§r";

                send(source, "  §e" + (globalOffset + i + 1) + ".§r " + w.name + marker);
            }

            if (totalPages > 1) {
                sendComponent(source, paginationBar("/world list", clampedPage, totalPages));
            }
        });
        return 1;
    }

    // ── /world add ─────────────────────────────────────────────────────────────

    private static int executeNew(FabricClientCommandSource source, String name) {
        if (!checkReady(source)) return 0;

        String defaultName = getWorldName();
        String finalName = (name == null || name.isBlank()) ? defaultName : stripQuotes(name);
        String seed = getCurrentSeed() != null ? getCurrentSeed() : "";
        String place = getPlaceKey();

        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            ApiModels.WorldSummary world = storage.createWorld(finalName, seed);

            PoiConfig.get().bindWorld(place, world.id);
            switchWorld(world.id);

            send(source, ok("World \"" + world.name + "\" created and selected."));
            if (!seed.isBlank()) {
                send(source, gray("  Seed: " + seed));
            }
        });
        return 1;
    }

    // ── World selection ────────────────────────────────────────────────────────

    /** Bumped on every world switch, so a resolve that started earlier can't override a newer selection. */
    private static final AtomicInteger worldEpoch = new AtomicInteger();

    /** Selects the POI world that belongs to the Minecraft world or server just joined. */
    public static void onJoin() {
        if (PoiConfig.get().isOffMode()) return;
        reselectWorld(PoiCommand::chat, "[POI] ", true);
    }

    /** Leaves no world selected, so nothing carries over into the next world joined. */
    public static void onDisconnect() {
        switchWorld(null);
    }

    /**
     * Makes {@code worldId} the selected POI world ({@code null} = none): drops the
     * previous world's lists, tracked POIs and campsites, then loads the new world's.
     */
    private static void switchWorld(String worldId) {
        worldEpoch.incrementAndGet();
        PoiConfig.get().activateWorld(worldId);
        PoiSession.get().clearWorldState();
        CampsiteIndex.clear();
        refreshAndRestoreTrackedAsync(worldId);
    }

    /**
     * Deselects the current world, then selects the one for the place the player is in:
     * the world bound to this place if the active storage has it, otherwise the single
     * world whose seed matches (which is then bound), otherwise none.
     * Must be called on the game thread; the lookup itself runs in the background.
     *
     * @param out    receives chat feedback lines
     * @param prefix put in front of every feedback line
     * @param onJoin true for the automatic run on join, which stays silent unless
     *               there is something for the player to do or know
     */
    private static void reselectWorld(Consumer<String> out, String prefix, boolean onJoin) {
        PoiConfig cfg = PoiConfig.get();
        String legacyId = cfg.takeLegacyWorldId();
        String place = getPlaceKey();
        String seed  = getCurrentSeed();

        switchWorld(null);
        if (cfg.isOffMode() || (cfg.isOnlineMode() && !cfg.hasApiKey())) return;

        int epoch = worldEpoch.get();
        CompletableFuture.runAsync(() -> {
            List<ApiModels.WorldSummary> worlds;
            try {
                worlds = PoiStorageProvider.get().listWorlds();
            } catch (Exception e) {
                out.accept(gray(prefix + "(Could not load worlds: " + e.getMessage() + ")"));
                return;
            }
            if (worldEpoch.get() != epoch) return;

            String boundId = cfg.getBinding(place);
            ApiModels.WorldSummary bound = boundId == null ? null : worlds.stream()
                    .filter(w -> boundId.equals(w.id))
                    .findFirst().orElse(null);
            if (bound != null) {
                switchWorld(bound.id);
                if (!onJoin) out.accept(ok(prefix + "World selected: " + bound.name));
                return;
            }

            List<ApiModels.WorldSummary> matches = seed == null ? List.of() : worlds.stream()
                    .filter(w -> seed.equals(w.seed))
                    .toList();
            ApiModels.WorldSummary match = matches.size() == 1 ? matches.get(0)
                    // Selection from before bindings existed: the seed confirms it is this world
                    : matches.stream().filter(w -> w.id.equals(legacyId)).findFirst().orElse(null);

            if (match != null) {
                cfg.bindWorld(place, match.id);
                switchWorld(match.id);
                out.accept(ok(prefix + "World auto-selected: " + match.name));
            } else if (matches.size() > 1) {
                out.accept(gray(prefix + "Multiple worlds match this seed — run /world list to pick one."));
            } else if (worlds.isEmpty()) {
                if (!onJoin) out.accept(gray(prefix + "No worlds found — run /world add to create one."));
            } else if (seed != null) {
                out.accept(gray(prefix + "No worlds match the current seed — run /world list to select one."));
            } else {
                out.accept(gray(prefix + "No world selected for this server — run /world list to select one."));
            }
        });
    }

    // ── /world select ──────────────────────────────────────────────────────────

    private static int executeSelect(FabricClientCommandSource source, int number) {
        ApiModels.WorldSummary world = PoiSession.get().getWorldByNumber(number);
        if (world == null) {
            int count = PoiSession.get().worldCount();
            if (count == 0) {
                send(source, err("No worlds list loaded yet. Run /world list first."));
            } else {
                send(source, err("Invalid number. Choose 1–" + count + "."));
            }
            return 0;
        }

        PoiConfig.get().bindWorld(getPlaceKey(), world.id);
        switchWorld(world.id);

        send(source, ok("Selected: " + world.name));
        String currentSeed = getCurrentSeed();
        if (currentSeed != null && world.seed != null && !world.seed.isBlank()
                && !currentSeed.equals(world.seed)) {
            send(source, gray("  Note: this world was saved with a different seed than the one you are in."));
        }
        return 1;
    }

    private static int executeAutoSelect(FabricClientCommandSource source) {
        if (!checkReady(source)) return 0;

        reselectWorld(m -> send(source, m), "", false);
        return 1;
    }

    private static int executeClear(FabricClientCommandSource source) {
        PoiConfig.get().bindWorld(getPlaceKey(), null);
        switchWorld(null);
        send(source, ok("World selection cleared."));
        return 1;
    }

    // ── /poi reset ─────────────────────────────────────────────────────────────

    /**
     * Removes the API key and returns to offline mode.
     * <ul>
     *   <li>Online → clears key, switches to offline, auto-selects world by seed</li>
     *   <li>Off    → switches to offline, auto-selects world by seed</li>
     *   <li>Offline → no-op with a confirmation message</li>
     * </ul>
     * Local data (POIs, worlds, groups) is never deleted by this command.
     */
    private static int executeReset(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();

        if (!cfg.isOnlineMode() && !cfg.isOffMode()) {
            send(source, gray("Already in offline mode — nothing to reset."));
            send(source, gray("  Your local data file is unchanged."));
            return 1;
        }

        boolean wasOnline = cfg.isOnlineMode();
        cfg.apiKey         = "";
        cfg.storageMode    = "offline";
        cfg.save();

        if (wasOnline) {
            send(source, ok("API key removed. Switched to OFFLINE mode."));
        } else {
            send(source, ok("Switched to OFFLINE mode."));
        }

        // Re-establish world context from the offline file (online IDs are not valid there)
        reselectWorld(m -> send(source, m), "  ", false);
        return 1;
    }

    // ── /poi groups ────────────────────────────────────────────────────────────

    private static int executeGroups(FabricClientCommandSource source, int page) {
        if (!checkReady(source) || !checkWorldSelected(source)) return 0;

        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            String worldId       = PoiConfig.get().currentWorldId;
            String currentGroupId = PoiConfig.get().currentGroupId;
            List<ApiModels.WorldItem> items  = storage.listItems(worldId);
            List<ApiModels.WorldItem> groups = items.stream()
                    .filter(i -> "group".equals(i.type))
                    .toList();

            PoiSession.get().setGroups(groups);

            if (groups.isEmpty()) {
                send(source, gray("No groups in the current world."));
                return;
            }

            send(source, header("Groups"));

            int totalPages  = totalPages(groups.size(), PAGE_SIZE);
            int clampedPage = Math.max(1, Math.min(page, totalPages));
            List<ApiModels.WorldItem> pageItems = getPage(groups, clampedPage, PAGE_SIZE);
            int globalOffset = (clampedPage - 1) * PAGE_SIZE;

            for (int i = 0; i < pageItems.size(); i++) {
                ApiModels.WorldItem g = pageItems.get(i);
                int globalNum  = globalOffset + i + 1;
                int poiCount   = (g.items != null) ? g.items.size() : 0;
                boolean isSelected = g.id.equals(currentGroupId);
                String toggleCmd   = isSelected ? "/group clear" : "/group select " + globalNum;

                MutableComponent indicator = Component.literal(isSelected ? "[*] " : "[ ] ")
                        .withStyle(s -> s
                                .withColor(isSelected ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                                .withClickEvent(new ClickEvent.RunCommand(toggleCmd)));
                MutableComponent line = Component.literal("  ")
                        .append(indicator)
                        .append(Component.literal(globalNum + ". ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(g.name))
                        .append(Component.literal(" (" + poiCount + " POIs)").withStyle(ChatFormatting.GRAY));
                sendComponent(source, line);
            }

            if (totalPages > 1) {
                sendComponent(source, paginationBar("/group list", clampedPage, totalPages));
            }
        });
        return 1;
    }

    private static int executeNewGroup(FabricClientCommandSource source, String name) {
        if (!checkReady(source) || !checkWorldSelected(source)) return 0;

        String finalName = stripQuotes(name);
        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            String worldId = PoiConfig.get().currentWorldId;
            ApiModels.WorldItem group = storage.createGroup(worldId, finalName);
            PoiConfig cfg = PoiConfig.get();
            cfg.currentGroupId   = group.id;
            cfg.currentGroupName = group.name;
            cfg.save();
            send(source, ok("Group \"" + finalName + "\" created and selected."));
        });
        return 1;
    }

    private static int executeSelectGroup(FabricClientCommandSource source, int number) {
        ApiModels.WorldItem group = PoiSession.get().getGroupByNumber(number);
        if (group == null) {
            int count = PoiSession.get().groupCount();
            if (count == 0) {
                send(source, err("No groups loaded yet. Run /group list first."));
            } else {
                send(source, err("Invalid number. Choose 1–" + count + "."));
            }
            return 0;
        }

        PoiConfig cfg = PoiConfig.get();
        cfg.currentGroupId   = group.id;
        cfg.currentGroupName = group.name;
        cfg.save();

        send(source, ok("Focusing on group: " + group.name));
        send(source, gray("New POIs will be added here."));
        return 1;
    }

    private static int executeClearGroup(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        cfg.currentGroupId   = null;
        cfg.currentGroupName = null;
        cfg.save();
        send(source, ok("Group focus cleared. POIs will be added at the root level."));
        return 1;
    }

    // ── /poi list ──────────────────────────────────────────────────────────────

    /**
     * Lists POIs, optionally filtered to the player's current dimension.
     *
     * @param showAll true  → show all dimensions; false → filter to current dimension.
     *                Numbers are always drawn from the full list so that the same POI
     *                always has the same number regardless of the filter in effect.
     */
    private static int executeList(FabricClientCommandSource source, int groupNumber, int page, boolean showAll) {
        if (!checkReady(source)) return 0;
        String worldId = requireCurrentWorld(source);
        if (worldId == null) return 0;

        // Capture dimension on the game thread before going async
        String currentDimension = showAll ? null : getDimension(Minecraft.getInstance());

        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            List<ApiModels.WorldItem> items = storage.listItems(worldId);

            // Build the full flat POI list — this determines global numbers for /poi track
            List<ApiModels.WorldItem> allPois = new ArrayList<>();

            if (groupNumber > 0) {
                // ── Group-scoped list (no dimension filtering) ──────────────────
                ApiModels.WorldItem group = PoiSession.get().getGroupByNumber(groupNumber);
                if (group == null) {
                    send(source, err("Invalid group number. Run /group list first."));
                    return;
                }
                List<ApiModels.WorldItem> pois = group.items != null ? group.items : List.of();
                allPois.addAll(pois);

                PoiSession.get().setLastPoiList(allPois);
                send(source, header("POIs in: " + group.name));

                if (allPois.isEmpty()) {
                    send(source, gray("  (empty)"));
                    return;
                }

                String pageCmd   = "/poi list group " + groupNumber;
                int totalPages   = totalPages(allPois.size(), PAGE_SIZE);
                int clampedPage  = Math.max(1, Math.min(page, totalPages));
                List<ApiModels.WorldItem> pageItems = getPage(allPois, clampedPage, PAGE_SIZE);
                int globalOffset = (clampedPage - 1) * PAGE_SIZE;

                for (int i = 0; i < pageItems.size(); i++) {
                    sendPoiLineNumbered(source, globalOffset + i + 1, pageItems.get(i));
                }

                if (totalPages > 1) {
                    sendComponent(source, paginationBar(pageCmd, clampedPage, totalPages));
                } else {
                    send(source, gray("Click [ ] to track a POI, or use /poi track <#>."));
                }

            } else {
                // ── World-wide list ─────────────────────────────────────────────
                // Flatten all items into the complete list (establishes global numbers)
                allPois.addAll(flattenPois(items));

                // Always store the FULL list so /poi track numbers are global
                PoiSession.get().setLastPoiList(allPois);

                // Determine the display list and metadata
                final List<ApiModels.WorldItem> displayPois;
                final String pageCmd;
                final String dimLabel;

                if (currentDimension != null) {
                    final String dimFilter = currentDimension;
                    displayPois = allPois.stream()
                            .filter(p -> dimFilter.equals(p.dimension)
                                    || (p.dimension == null && "overworld".equals(dimFilter)))
                            .toList();
                    dimLabel = " (" + dimensionLabel(currentDimension) + ")";
                    pageCmd  = "/poi list";
                } else {
                    displayPois = allPois;
                    dimLabel    = " (All)";
                    pageCmd     = "/poi list all";
                }

                send(source, header("POIs" + dimLabel));

                if (displayPois.isEmpty()) {
                    if (currentDimension != null) {
                        send(source, gray("No POIs in the " + dimensionLabel(currentDimension) + "."));
                        send(source, gray("Use /poi list all to see all dimensions."));
                    } else {
                        send(source, gray("No POIs yet. Use /poi add <name> to add one."));
                    }
                    return;
                }

                int totalPages  = totalPages(displayPois.size(), PAGE_SIZE);
                int clampedPage = Math.max(1, Math.min(page, totalPages));
                List<ApiModels.WorldItem> pageItems = getPage(displayPois, clampedPage, PAGE_SIZE);

                // Display with group headers; numbers come from the GLOBAL allPois index
                String lastGroupName = null;
                for (ApiModels.WorldItem poi : pageItems) {
                    String groupName = findGroupName(items, poi);
                    if (groupName != null && !groupName.equals(lastGroupName)) {
                        send(source, "  §6[" + groupName + "]");
                        lastGroupName = groupName;
                    } else if (groupName == null && lastGroupName != null) {
                        lastGroupName = null;
                    }
                    int globalNum = allPois.indexOf(poi) + 1;
                    sendPoiLineNumbered(source, globalNum, poi);
                }

                if (totalPages > 1) {
                    sendComponent(source, paginationBar(pageCmd, clampedPage, totalPages));
                } else {
                    if (currentDimension != null) {
                        send(source, gray("Click [ ] to track, or use /poi track <#>. Use /poi list all for all dimensions."));
                    } else {
                        send(source, gray("Click [ ] to track a POI, or use /poi track <#>."));
                    }
                }
            }
        });
        return 1;
    }

    // ── POI list refresh helpers ───────────────────────────────────────────────

    /**
     * Flattens a top-level item list (groups + root POIs) into a single ordered
     * list of POIs.  All root (ungrouped) POIs come first, then each group's POIs
     * in group order, matching the website.  Root POIs and groups can be
     * interleaved in storage (new root POIs are appended after existing groups),
     * so a plain in-order walk would scatter ungrouped POIs between groups.
     */
    private static List<ApiModels.WorldItem> flattenPois(List<ApiModels.WorldItem> items) {
        List<ApiModels.WorldItem> result = new ArrayList<>();
        for (ApiModels.WorldItem item : items) {
            if (!"group".equals(item.type)) result.add(item);
        }
        for (ApiModels.WorldItem item : items) {
            if ("group".equals(item.type) && item.items != null) result.addAll(item.items);
        }
        return result;
    }

    /**
     * Fetches the POI list using an already-resolved {@link PoiStorage} (i.e. while
     * already on the async thread) and stores it in {@link PoiSession}.  Errors are
     * swallowed — this is always a best-effort background refresh.
     */
    public static void refreshPoiList(PoiStorage storage, String worldId) {
        try {
            List<ApiModels.WorldItem> items = storage.listItems(worldId);
            // The selection may have changed while the list was loading
            if (!worldId.equals(PoiConfig.get().currentWorldId)) return;
            PoiSession.get().setLastPoiList(flattenPois(items));
            CampsiteIndex.rebuild(worldId, items);
        } catch (Exception ignored) {}
    }

    /**
     * Fires an async POI-list refresh for the given world.  Safe to call from
     * any thread; errors are swallowed.  Intended for automatic background
     * refreshes (e.g. on world join) where no chat source is available.
     */
    public static void refreshPoiListAsync(String worldId) {
        if (worldId == null || worldId.isBlank()) return;
        if (PoiConfig.get().isOffMode()) return;
        CompletableFuture.runAsync(() -> refreshPoiList(PoiStorageProvider.get(), worldId));
    }

    /** Refreshes the POI list and restores any previously tracked POIs from config. */
    public static void refreshAndRestoreTrackedAsync(String worldId) {
        if (worldId == null || worldId.isBlank()) return;
        if (PoiConfig.get().isOffMode()) return;
        CompletableFuture.runAsync(() -> {
            refreshPoiList(PoiStorageProvider.get(), worldId);
            if (worldId.equals(PoiConfig.get().currentWorldId)) restoreTrackedPois();
        });
    }

    private static void restoreTrackedPois() {
        List<String> savedIds = List.copyOf(PoiConfig.get().getTrackedPoiIds());
        if (savedIds.isEmpty()) return;
        List<ApiModels.WorldItem> allPois = PoiSession.get().getLastPoiList();
        for (String id : savedIds) {
            allPois.stream()
                    .filter(p -> id.equals(p.id))
                    .findFirst()
                    .ifPresent(PoiSession.get()::addTrackedPoi);
        }
    }

    /** Returns the group name that contains this POI, or null if it's at root level. */
    private static String findGroupName(List<ApiModels.WorldItem> items, ApiModels.WorldItem target) {
        for (ApiModels.WorldItem item : items) {
            if ("group".equals(item.type) && item.items != null) {
                for (ApiModels.WorldItem child : item.items) {
                    if (child == target) return item.name;
                }
            }
        }
        return null;
    }

    // ── /poi add ───────────────────────────────────────────────────────────────

    private static int executeAdd(FabricClientCommandSource source, String name, String desc) {
        if (!checkReady(source)) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            send(source, err("Cannot determine player position."));
            return 0;
        }

        String worldId = PoiConfig.get().currentWorldId;
        String groupId = PoiConfig.get().currentGroupId;
        if (worldId == null) {
            send(source, err("No world selected. Run /world list or /world add first."));
            return 0;
        }

        double x = Math.floor(mc.player.getX());
        double y = Math.floor(mc.player.getY());
        double z = Math.floor(mc.player.getZ());
        String dimension = getDimension(mc);
        String finalName = stripQuotes(name);

        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            String currentGroupName = PoiConfig.get().currentGroupName;
            storage.createPoi(worldId, groupId, finalName, desc, x, y, z, dimension);
            send(source, ok("Added: " + finalName));
            send(source, gray("  " + formatCoords(x, y, z) + " — " + dimensionLabel(dimension)));
            if (groupId != null && currentGroupName != null) {
                send(source, gray("  (Added to group \"" + currentGroupName + "\")"));
            } else if (groupId != null) {
                send(source, gray("  (Added to current group)"));
            }
            // Silently refresh so /poi track <#> works immediately without a manual /pois call
            refreshPoiList(storage, worldId);
        });
        return 1;
    }

    // ── /poi delete ───────────────────────────────────────────────────────────

    private static int executeDelete(FabricClientCommandSource source, int number) {
        if (!checkReady(source)) return 0;

        String worldId = requireCurrentWorld(source);
        if (worldId == null) return 0;

        ApiModels.WorldItem poi = PoiSession.get().getPoiByNumber(number);
        if (poi == null) {
            int count = PoiSession.get().poiCount();
            if (count == 0) {
                send(source, err("No POIs loaded yet. Run /poi list first."));
            } else {
                send(source, err("Invalid number. Choose 1–" + count + "."));
            }
            return 0;
        }

        String poiId   = poi.id;
        String poiName = poi.name;

        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            storage.deletePoi(worldId, poiId);

            // Remove from tracked set and persisted IDs if present
            if (poiId != null) {
                PoiSession.get().removeTrackedPoiById(poiId);
                PoiConfig cfg = PoiConfig.get();
                cfg.getTrackedPoiIds().remove(poiId);
                cfg.save();
            }

            // Refresh the in-memory list so numbers stay accurate
            refreshPoiList(storage, worldId);

            send(source, ok("Deleted: " + poiName));
        });
        return 1;
    }

    // ── /poi track ────────────────────────────────────────────────────────────

    private static int executeTrackList(FabricClientCommandSource source) {
        List<ApiModels.WorldItem> tracked = PoiSession.get().getTrackedPois();
        if (tracked.isEmpty()) {
            send(source, gray("No POIs currently tracked. Use /poi track <#> after /poi list."));
            return 1;
        }
        send(source, header("Tracked POIs"));
        for (ApiModels.WorldItem poi : tracked) {
            int listNum   = PoiSession.get().getListNumber(poi);
            String numLabel = listNum >= 1 ? String.valueOf(listNum) : "?";
            String coords = poi.coords != null
                    ? gray(" [" + formatCoords(poi.coords) + " — " + dimensionLabel(poi.dimension) + "]")
                    : "";
            send(source, "  §a" + numLabel + ".§r §b" + poi.name + "§r" + coords);
        }
        send(source, gray("Use /poi untrack <#> to stop tracking (numbers match /pois)."));
        return 1;
    }

    private static int executeTrack(FabricClientCommandSource source, int number) {
        ApiModels.WorldItem poi = PoiSession.get().getPoiByNumber(number);
        if (poi == null) {
            int count = PoiSession.get().poiCount();
            if (count == 0) {
                send(source, err("No POIs loaded yet. Run /poi list first."));
            } else {
                send(source, err("Invalid number. Choose 1–" + count + "."));
            }
            return 0;
        }
        if (poi.coords == null || poi.coords.x == null || poi.coords.z == null) {
            send(source, err("POI has no coordinates and cannot be tracked."));
            return 0;
        }
        PoiSession.get().addTrackedPoi(poi);
        if (poi.id != null) {
            PoiConfig cfg = PoiConfig.get();
            if (!cfg.getTrackedPoiIds().contains(poi.id)) {
                cfg.getTrackedPoiIds().add(poi.id);
                cfg.save();
            }
        }
        send(source, ok("Now tracking: " + poi.name));
        send(source, gray("  " + formatCoords(poi.coords) + " — " + dimensionLabel(poi.dimension)));
        return 1;
    }

    private static int executeUntrack(FabricClientCommandSource source, int number) {
        int count = PoiSession.get().poiCount();
        ApiModels.WorldItem poi = PoiSession.get().getPoiByNumber(number);
        if (poi == null) {
            if (count == 0) {
                send(source, err("No POIs loaded yet. Run /poi list first."));
            } else {
                send(source, err("Invalid number. Choose 1–" + count + "."));
            }
            return 0;
        }
        if (!PoiSession.get().isTracked(poi)) {
            send(source, err(poi.name + " is not currently tracked."));
            return 0;
        }
        PoiSession.get().removeTrackedPoi(number);
        if (poi.id != null) {
            PoiConfig cfg = PoiConfig.get();
            cfg.getTrackedPoiIds().remove(poi.id);
            cfg.save();
        }
        send(source, ok("Stopped tracking: " + poi.name));
        return 1;
    }

    private static int executeTrackNumbers(FabricClientCommandSource source, String numbersStr) {
        List<Integer> numbers = parseNumbers(source, numbersStr);
        if (numbers == null) return 0;
        int result = 1;
        for (int number : numbers) result = executeTrack(source, number);
        return result;
    }

    private static int executeUntrackNumbers(FabricClientCommandSource source, String numbersStr) {
        List<Integer> numbers = parseNumbers(source, numbersStr);
        if (numbers == null) return 0;
        int result = 1;
        for (int number : numbers) result = executeUntrack(source, number);
        return result;
    }

    private static List<Integer> parseNumbers(FabricClientCommandSource source, String input) {
        List<Integer> numbers = new ArrayList<>();
        for (String part : input.trim().split("\\s+")) {
            try {
                int n = Integer.parseInt(part);
                if (n < 1) {
                    send(source, err("Numbers must be 1 or greater."));
                    return null;
                }
                numbers.add(n);
            } catch (NumberFormatException e) {
                send(source, err("Invalid number: " + part));
                return null;
            }
        }
        if (numbers.isEmpty()) {
            send(source, err("Please provide at least one number."));
            return null;
        }
        return numbers;
    }

    private static int executeTrackClear(FabricClientCommandSource source) {
        PoiSession.get().clearTrackedPois();
        PoiConfig cfg = PoiConfig.get();
        cfg.getTrackedPoiIds().clear();
        cfg.save();
        send(source, ok("All tracking cleared."));
        return 1;
    }

    // ── /poi find ─────────────────────────────────────────────────────────────

    /**
     * Lists every POI whose name or description contains {@code query}
     * (case-insensitive).  Matches in the player's current dimension come first,
     * nearest first, and the nearest one is marked [Closest]; matches in other
     * dimensions or without coordinates follow in list order.  Numbers match
     * /poi list, so /poi track &lt;#&gt; works on the results.
     */
    private static int executeFind(FabricClientCommandSource source, String query) {
        if (!checkReady(source)) return 0;
        String worldId = requireCurrentWorld(source);
        if (worldId == null) return 0;

        String trimmedQuery = stripQuotes(query);
        if (trimmedQuery.isEmpty()) {
            send(source, err("Please provide a word to search for."));
            return 0;
        }
        String needle = trimmedQuery.toLowerCase(Locale.ROOT);

        // Capture player position and dimension on the game thread before going async
        Minecraft mc = Minecraft.getInstance();
        boolean hasPlayer = mc.player != null;
        double px = hasPlayer ? mc.player.getX() : 0;
        double pz = hasPlayer ? mc.player.getZ() : 0;
        String currentDimension = getDimension(mc);

        async(source, () -> {
            PoiStorage storage = PoiStorageProvider.get();
            List<ApiModels.WorldItem> allPois = flattenPois(storage.listItems(worldId));

            // Store the full list so numbers are global and /poi track <#> works
            PoiSession.get().setLastPoiList(allPois);

            record Match(ApiModels.WorldItem poi, int number, double distance) {}
            List<Match> matches = new ArrayList<>();
            for (int i = 0; i < allPois.size(); i++) {
                ApiModels.WorldItem poi = allPois.get(i);
                if (!containsIgnoreCase(poi.name, needle) && !containsIgnoreCase(poi.description, needle)) continue;
                boolean sameDim = currentDimension.equals(poi.dimension)
                        || (poi.dimension == null && "overworld".equals(currentDimension));
                boolean hasCoords = poi.coords != null && poi.coords.x != null && poi.coords.z != null;
                double distance = hasPlayer && sameDim && hasCoords
                        ? Math.hypot(poi.coords.x - px, poi.coords.z - pz)
                        : Double.NaN;
                matches.add(new Match(poi, i + 1, distance));
            }

            if (matches.isEmpty()) {
                send(source, gray("No POIs match \"" + trimmedQuery + "\"."));
                return;
            }

            // Nearest first; POIs with no distance keep list order at the end (stable sort)
            matches.sort(Comparator.comparingDouble(m -> Double.isNaN(m.distance()) ? Double.MAX_VALUE : m.distance()));

            String resultCount = matches.size() == 1 ? "1 result" : matches.size() + " results";
            send(source, header("Find: " + trimmedQuery + " (" + resultCount + ")"));
            for (int i = 0; i < matches.size(); i++) {
                Match m = matches.get(i);
                MutableComponent suffix = null;
                if (!Double.isNaN(m.distance())) {
                    suffix = Component.literal(" " + Math.round(m.distance()) + "m")
                            .withStyle(ChatFormatting.GRAY);
                    if (i == 0) {
                        suffix = Component.literal(" [Closest]").withStyle(ChatFormatting.GREEN)
                                .append(suffix);
                    }
                }
                sendPoiLineNumbered(source, m.number(), m.poi(), suffix);
            }
            send(source, gray("Numbers match /poi list — click [ ] or use /poi track <#> to track."));
        });
        return 1;
    }

    private static boolean containsIgnoreCase(String text, String lowerNeedle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    // ── Readiness checks ──────────────────────────────────────────────────────

    /**
     * Returns true if the mod is ready to perform data operations.
     * <ul>
     *   <li>Off mode: always blocked — user must run /poi offline or /poi online first</li>
     *   <li>Offline mode: always ready (no API key required)</li>
     *   <li>Online mode: requires an API key to be set</li>
     * </ul>
     */
    private static boolean checkReady(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        if (cfg.isOffMode()) {
            send(source, err("Points of Interest is disabled."));
            send(source, gray("  Run /poi offline to use local storage, or /poi online for cloud sync."));
            return false;
        }
        if (cfg.isOnlineMode() && !cfg.hasApiKey()) {
            String baseUrl = cfg.getApiBaseUrl();
            MutableComponent keyMsg = Component.literal("No API key set. Get one at ").withStyle(ChatFormatting.RED)
                    .append(urlComponent(baseUrl + HELP_PATH));
            sendComponent(source, keyMsg);
            send(source, err("Then run: /poi setkey <your-key>"));
            send(source, gray("Or run /poi offline to use offline storage without a key."));
            return false;
        }
        return true;
    }

    private static boolean checkWorldSelected(FabricClientCommandSource source) {
        if (PoiConfig.get().currentWorldId == null) {
            send(source, err("No current world selected."));
            send(source, gray("  Run /world list to list worlds, then /world select <#>"));
            send(source, gray("  Or use /world add [name] to create a world."));
            return false;
        }
        return true;
    }

    private static String requireCurrentWorld(FabricClientCommandSource source) {
        String worldId = PoiConfig.get().currentWorldId;
        if (worldId == null || worldId.isBlank()) {
            send(source, err("No current world selected."));
            send(source, gray("  Run /world list to list worlds, then /world select <#>"));
            send(source, gray("  Or use /world add [name] to create a world."));
            return null;
        }
        return worldId;
    }

    // ── Minecraft world helpers ───────────────────────────────────────────────

    /**
     * Returns the current level seed as a string, or null if unavailable
     * (e.g. on a remote server that hides the seed).
     */
    private static String getCurrentSeed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            long seed = mc.getSingleplayerServer().overworld().getSeed();
            return String.valueOf(seed);
        }
        return null;
    }

    /**
     * Identifies the Minecraft world or server the player is in, as the key under
     * which its POI world is remembered. Returns null when there is nothing stable
     * to key on.
     */
    private static String getPlaceKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            // The seed is part of the key so that a new world created under a
            // deleted world's folder name doesn't inherit its POIs.
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize();
            return "sp:" + root.getFileName() + ":" + getCurrentSeed();
        }
        ServerData server = mc.getCurrentServer();
        if (server == null) return null;
        // Realms and LAN addresses change between sessions; their names don't
        if (server.isRealm()) return "realm:" + server.name;
        if (server.isLan())   return "lan:" + server.name;
        return server.ip != null ? "mp:" + server.ip.toLowerCase(Locale.ROOT) : null;
    }

    private static String getWorldName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        if (mc.getCurrentServer() != null) {
            return mc.getCurrentServer().name;
        }
        return "New World";
    }

    private static String getDimension(Minecraft mc) {
        if (mc.player == null) return "overworld";
        var dim = mc.player.level().dimension();
        if (dim.equals(Level.NETHER)) return "nether";
        if (dim.equals(Level.END))    return "end";
        return "overworld";
    }

    // ── POI chat rendering ────────────────────────────────────────────────────

    private static void sendPoiLineNumbered(FabricClientCommandSource source, int number, ApiModels.WorldItem poi) {
        sendPoiLineNumbered(source, number, poi, null);
    }

    /** Renders a POI line, with {@code suffix} (if non-null) appended after the coordinates. */
    private static void sendPoiLineNumbered(FabricClientCommandSource source, int number,
                                            ApiModels.WorldItem poi, Component suffix) {
        boolean tracked   = PoiSession.get().isTracked(poi);
        String toggleCmd  = tracked ? "/poi untrack " + number : "/poi track " + number;

        MutableComponent indicator = Component.literal(tracked ? "[*] " : "[ ] ")
                .withStyle(s -> s
                        .withColor(tracked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                        .withClickEvent(new ClickEvent.RunCommand(toggleCmd)));
        MutableComponent name = Component.literal(poi.name)
                .withStyle(s -> s
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand(toggleCmd)));
        MutableComponent line = Component.literal("  ")
                .append(indicator)
                .append(Component.literal(number + ". ").withStyle(ChatFormatting.YELLOW))
                .append(name);

        if (poi.coords != null) {
            line.append(Component.literal(
                    " [" + formatCoords(poi.coords) + " — " + dimensionLabel(poi.dimension) + "]"
            ).withStyle(ChatFormatting.GRAY));
        }
        if (suffix != null) {
            line.append(suffix);
        }

        sendComponent(source, line);

        if (poi.description != null && !poi.description.isBlank()) {
            send(source, gray("    " + poi.description));
        }
    }

    // ── Formatting helpers ─────────────────────────────────────────────────────

    private static String formatCoords(ApiModels.Coords c) {
        List<String> parts = new ArrayList<>();
        if (c.x != null) parts.add("x:" + Math.round(c.x));
        if (c.y != null) parts.add("y:" + Math.round(c.y));
        if (c.z != null) parts.add("z:" + Math.round(c.z));
        return String.join(" ", parts);
    }

    private static String formatCoords(double x, double y, double z) {
        return "x:" + (int)x + " y:" + (int)y + " z:" + (int)z;
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String dimensionLabel(String dim) {
        if (dim == null) return "Overworld";
        return switch (dim) {
            case "nether" -> "Nether";
            case "end"    -> "End";
            default       -> "Overworld";
        };
    }

    /**
     * Returns a 12-character lowercase hex prefix of the SHA-256 hash of the
     * given string.  Used to derive stable, filesystem-safe profile keys from
     * API keys without storing the raw key on disk.
     */
    private static String hashKey(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in Java SE — this branch should never execute
            return key.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(12, key.length()));
        }
    }

    // ── Pagination helpers ─────────────────────────────────────────────────────

    private static <T> List<T> getPage(List<T> items, int page, int pageSize) {
        int start = (page - 1) * pageSize;
        if (start >= items.size()) return List.of();
        return items.subList(start, Math.min(start + pageSize, items.size()));
    }

    private static int totalPages(int totalItems, int pageSize) {
        return Math.max(1, (totalItems + pageSize - 1) / pageSize);
    }

    /**
     * Builds a clickable [◀ Prev]  Page X/Y  [Next ▶] bar.
     * Inactive buttons are shown in dark gray; active ones are aqua and clickable.
     */
    private static Component paginationBar(String baseCommand, int page, int totalPages) {
        MutableComponent bar = Component.empty();

        if (page > 1) {
            bar.append(Component.literal("[◀ Prev]").withStyle(s -> s
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand(baseCommand + " " + (page - 1)))));
        } else {
            bar.append(Component.literal("[◀ Prev]").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY)));
        }

        bar.append(Component.literal("  Page " + page + "/" + totalPages + "  ")
                .withStyle(ChatFormatting.GRAY));

        if (page < totalPages) {
            bar.append(Component.literal("[Next ▶]").withStyle(s -> s
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.RunCommand(baseCommand + " " + (page + 1)))));
        } else {
            bar.append(Component.literal("[Next ▶]").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY)));
        }

        return bar;
    }

    // ── Chat helpers ───────────────────────────────────────────────────────────

    private static MutableComponent urlComponent(String url) {
        return Component.literal(url)
                .withStyle(s -> s
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/poi copyurl " + url)));
    }

    /**
     * Returns a clickable chat component that opens the given file in the OS's
     * default application (e.g. a text editor or file manager).
     */
    private static MutableComponent fileLink(Path path) {
        String pathStr = path.toAbsolutePath().toString();
        return Component.literal(pathStr)
                .withStyle(s -> s
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenFile(pathStr)));
    }

    private static String header(String text) {
        return "§7§m-----§r §f§l" + text + "§r §7§m-----§r";
    }

    private static String ok(String text)   { return "§a" + text + "§r"; }
    private static String err(String text)  { return "§c" + text + "§r"; }
    private static String gray(String text) { return "§7" + text + "§r"; }

    /** Chat message outside of a command (no command source available). */
    private static void chat(String legacyText) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal(legacyText), false);
        });
    }

    private static void send(FabricClientCommandSource source, String legacyText) {
        Minecraft.getInstance().execute(() ->
                source.sendFeedback(Component.literal(legacyText)));
    }

    private static void sendComponent(FabricClientCommandSource source, Component component) {
        Minecraft.getInstance().execute(() -> source.sendFeedback(component));
    }

    /**
     * Runs a storage operation off the main thread, then dispatches chat feedback
     * back onto the render thread.  Errors are shown in chat automatically.
     */
    private static void async(FabricClientCommandSource source, ApiCallable call) {
        CompletableFuture.runAsync(() -> {
            try {
                call.run();
            } catch (ApiClient.ApiException e) {
                Minecraft.getInstance().execute(() ->
                        send(source, err("[POI] " + e.getMessage())));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() ->
                        send(source, err("[POI] Unexpected error: " + e.getMessage())));
            }
        });
    }

    @FunctionalInterface
    private interface ApiCallable {
        void run() throws Exception;
    }
}
