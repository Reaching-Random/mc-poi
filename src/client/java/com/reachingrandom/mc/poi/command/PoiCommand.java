package com.reachingrandom.mc.poi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

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

                // /poi add <name> [description]
                .then(literal("add")
                    .then(argument("name", StringArgumentType.string())
                        .executes(ctx -> executeAdd(ctx.getSource(),
                                getString(ctx, "name"), ""))
                        .then(argument("description", StringArgumentType.greedyString())
                            .executes(ctx -> executeAdd(ctx.getSource(),
                                    getString(ctx, "name"),
                                    getString(ctx, "description"))))))

                // /poi track [number | clear]
                .then(literal("track")
                    .executes(ctx -> executeTrackList(ctx.getSource()))
                    .then(literal("clear")
                        .executes(ctx -> executeTrackClear(ctx.getSource())))
                    .then(argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeTrack(ctx.getSource(),
                                getInteger(ctx, "number")))))

                // /poi untrack <number>
                .then(literal("untrack")
                    .then(argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeUntrack(ctx.getSource(),
                                getInteger(ctx, "number")))))

                // /poi copyurl <url>  — internal: copies URL and confirms in chat
                .then(literal("copyurl")
                    .then(argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> executeCopyUrl(ctx.getSource(),
                                getString(ctx, "url")))))
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
    }

    // ── /poi help ──────────────────────────────────────────────────────────────

    // ── Help ──────────────────────────────────────────────────────────────────

    private static int executeWorldHelp(FabricClientCommandSource source) {
        send(source, header("POI Tracker — World Management"));
        send(source, gray("  /world list [page]    ") + "List your worlds");
        send(source, gray("  /world add [name]      ") + "Create a new world entry");
        send(source, gray("  /world select [number]") + "Select world (auto-selects by seed if no #)");
        send(source, gray("  /world clear          ") + "Clear currently selected world");
        send(source, "");
        send(source, gray("Run ") + "/poi help" + gray(" for POI and category commands."));
        return 1;
    }

    private static int executePoiHelp(FabricClientCommandSource source) {
        String baseUrl = PoiConfig.get().getApiBaseUrl();
        send(source, header("POI Tracker — POI Management"));
        send(source, gray("  /poi setkey <key>         ") + "Save your API key");
        send(source, gray("  /poi list [page]          ") + "List POIs in current dimension");
        send(source, gray("  /poi list all [page]      ") + "List POIs in all dimensions");
        send(source, gray("  /poi list group <#> [page]") + "List POIs in a specific group");
        send(source, gray("  /poi add <name> [desc]    ") + "Add POI (to current group if selected)");
        send(source, gray("  /poi track                ") + "List all currently tracked POIs");
        send(source, gray("  /poi track <#>            ") + "Add a POI to tracked (from last list)");
        send(source, gray("  /poi untrack <#>          ") + "Remove a POI from tracked");
        send(source, gray("  /poi track clear          ") + "Stop tracking all POIs");
        send(source, gray("  /poi reset                ") + "Clear ALL data and API key");
        send(source, "");
        send(source, gray("  /pois         ") + "Shortcut for /poi list (current dimension)");
        send(source, gray("  /pois all     ") + "Shortcut for /poi list all");
        send(source, "");
        send(source, gray("Run ") + "/world help" + gray(" or ") + "/group help" + gray(" for more."));
        MutableComponent website = Component.literal("Website: ").withStyle(ChatFormatting.GRAY)
                .append(urlComponent(baseUrl + HELP_PATH));
        sendComponent(source, website);
        return 1;
    }

    private static int executeGroupHelp(FabricClientCommandSource source) {
        send(source, header("POI Tracker — Group Management"));
        send(source, gray("  /group list [page]    ") + "List groups in current world");
        send(source, gray("  /group add <name>      ") + "Create a new group");
        send(source, gray("  /group select <#>     ") + "Select target group for new POIs");
        send(source, gray("  /group clear          ") + "Deselect group (POIs added at root)");
        send(source, "");
        send(source, gray("Run ") + "/poi help" + gray(" for POI commands."));
        return 1;
    }

    // ── /poi setkey ────────────────────────────────────────────────────────────

    private static int executeSetKey(FabricClientCommandSource source, String key) {
        PoiConfig cfg = PoiConfig.get();
        cfg.apiKey = key.trim();
        cfg.save();
        send(source, ok("API key saved."));

        // Capture seed now — getCurrentSeed() touches the server and must run on the game thread
        String currentSeed = getCurrentSeed();

        async(source, () -> {
            ApiClient api = new ApiClient();
            List<ApiModels.WorldSummary> worlds;
            try {
                ApiModels.WorldsResponse resp = api.getWorlds();
                worlds = resp.worlds != null ? resp.worlds : List.of();
            } catch (Exception e) {
                send(source, gray("(Could not load worlds: " + e.getMessage() + ")"));
                return;
            }

            if (worlds.isEmpty()) {
                send(source, gray("No worlds found. Use /world add to create one."));
                return;
            }

            if (currentSeed == null) {
                // Remote server — seed unavailable, can't auto-select
                send(source, gray("Use /world list to select your world."));
                return;
            }

            List<ApiModels.WorldSummary> matches = worlds.stream()
                    .filter(w -> currentSeed.equals(w.seed))
                    .toList();

            if (matches.isEmpty()) {
                send(source, gray("No saved worlds match this seed. Use /world add or /world select."));
            } else if (matches.size() > 1) {
                send(source, gray("Multiple worlds match this seed. Use /world list to pick one."));
            } else {
                ApiModels.WorldSummary match = matches.get(0);
                PoiConfig poiCfg = PoiConfig.get();
                poiCfg.currentWorldId = match.id;
                poiCfg.save();
                send(source, ok("World auto-selected: " + match.name));

                refreshPoiList(api, match.id);
                send(source, gray("POIs loaded — use /pois to browse or /poi track <#> to track."));
            }
        });
        return 1;
    }

    // ── /poi worlds ────────────────────────────────────────────────────────────

    private static int executeWorlds(FabricClientCommandSource source, int page) {
        if (!checkApiKey(source)) return 0;

        String currentSeed = getCurrentSeed();
        async(source, () -> {
            ApiClient api = new ApiClient();
            ApiModels.WorldsResponse resp = api.getWorlds();
            List<ApiModels.WorldSummary> worlds = resp.worlds != null ? resp.worlds : List.of();

            if (worlds.isEmpty()) {
                send(source, gray("No worlds found. Use /poi new <name> to create one."));
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
                        cfg.currentWorldId = match.id;
                        cfg.save();
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
                boolean isSelected = w.id.equals(cfg.currentWorldId);
                boolean isSeedMatch = currentSeed != null && currentSeed.equals(w.seed);

                String marker = "";
                if (isSelected && isSeedMatch) marker = " §a[Current]§r";
                else if (isSelected) marker = " §c[Selected]§r";
                else if (isSeedMatch) marker = " §b[Seed Match]§r";

                send(source, "  §e" + (globalOffset + i + 1) + ".§r " + w.name + marker);
            }

            if (totalPages > 1) {
                sendComponent(source, paginationBar("/world list", clampedPage, totalPages));
            }
        });
        return 1;
    }

    // ── /poi new ───────────────────────────────────────────────────────────────

    private static int executeNew(FabricClientCommandSource source, String name) {
        if (!checkApiKey(source)) return 0;

        String defaultName = getWorldName();
        String finalName = (name == null || name.isBlank()) ? defaultName : stripQuotes(name);
        String seed = getCurrentSeed() != null ? getCurrentSeed() : "";

        async(source, () -> {
            ApiClient api = new ApiClient();
            ApiModels.CreateWorldResponse resp = api.createWorld(finalName, seed);
            ApiModels.WorldSummary world = resp.world;

            PoiConfig cfg = PoiConfig.get();
            cfg.currentWorldId = world.id;
            cfg.save();

            send(source, ok("World \"" + world.name + "\" created and selected."));
            if (!seed.isBlank()) {
                send(source, gray("  Seed: " + seed));
            }
        });
        return 1;
    }

    // ── /poi select ────────────────────────────────────────────────────────────

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

        PoiConfig cfg = PoiConfig.get();
        cfg.currentWorldId = world.id;
        cfg.save();

        send(source, ok("Selected: " + world.name));
        return 1;
    }

    private static int executeAutoSelect(FabricClientCommandSource source) {
        if (!checkApiKey(source)) return 0;

        String currentSeed = getCurrentSeed();
        if (currentSeed == null) {
            send(source, err("Current world seed unknown (remote server?)."));
            return 0;
        }

        async(source, () -> {
            ApiClient api = new ApiClient();
            List<ApiModels.WorldSummary> worlds = api.getWorlds().worlds;
            List<ApiModels.WorldSummary> matches = worlds.stream()
                    .filter(w -> currentSeed.equals(w.seed))
                    .toList();

            if (matches.isEmpty()) {
                send(source, err("No saved worlds match your current seed."));
            } else if (matches.size() > 1) {
                send(source, err("Multiple worlds match your current seed. Use /world list to choose manually."));
            } else {
                ApiModels.WorldSummary match = matches.get(0);
                PoiConfig cfg = PoiConfig.get();
                cfg.currentWorldId = match.id;
                cfg.save();
                send(source, ok("Auto-selected: " + match.name));
            }
        });
        return 1;
    }

    private static int executeClear(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        cfg.currentWorldId = null;
        cfg.save();
        send(source, ok("World selection cleared."));
        return 1;
    }

    private static int executeReset(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        cfg.apiKey = "";
        cfg.currentWorldId = null;
        cfg.save();
        send(source, ok("ALL data cleared. API key and world selection reset."));
        return 1;
    }

    // ── /poi groups ────────────────────────────────────────────────────────────

    private static int executeGroups(FabricClientCommandSource source, int page) {
        if (!checkApiKey(source) || !checkWorldSelected(source)) return 0;

        async(source, () -> {
            ApiClient api = new ApiClient();
            String worldId = PoiConfig.get().currentWorldId;
            String currentGroupId = PoiConfig.get().currentGroupId;
            List<ApiModels.WorldItem> items = api.getItems(worldId).items;
            List<ApiModels.WorldItem> groups = items.stream()
                    .filter(i -> "group".equals(i.type))
                    .toList();

            PoiSession.get().setGroups(groups);

            if (groups.isEmpty()) {
                send(source, gray("No groups in the current world."));
                return;
            }

            send(source, header("Groups"));

            int totalPages = totalPages(groups.size(), PAGE_SIZE);
            int clampedPage = Math.max(1, Math.min(page, totalPages));
            List<ApiModels.WorldItem> pageItems = getPage(groups, clampedPage, PAGE_SIZE);
            int globalOffset = (clampedPage - 1) * PAGE_SIZE;

            for (int i = 0; i < pageItems.size(); i++) {
                ApiModels.WorldItem g = pageItems.get(i);
                int globalNum = globalOffset + i + 1;
                int poiCount = (g.items != null) ? g.items.size() : 0;
                boolean isSelected = g.id.equals(currentGroupId);
                String toggleCmd = isSelected ? "/group clear" : "/group select " + globalNum;

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
        if (!checkApiKey(source) || !checkWorldSelected(source)) return 0;

        String finalName = stripQuotes(name);
        async(source, () -> {
            ApiClient api = new ApiClient();
            String worldId = PoiConfig.get().currentWorldId;
            ApiModels.CreateGroupResponse resp = api.createGroup(worldId, finalName);
            PoiConfig cfg = PoiConfig.get();
            cfg.currentGroupId = resp.group.id;
            cfg.currentGroupName = resp.group.name;
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
        cfg.currentGroupId = group.id;
        cfg.currentGroupName = group.name;
        cfg.save();

        send(source, ok("Focusing on group: " + group.name));
        send(source, gray("New POIs will be added here."));
        return 1;
    }

    private static int executeClearGroup(FabricClientCommandSource source) {
        PoiConfig cfg = PoiConfig.get();
        cfg.currentGroupId = null;
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
        if (!checkApiKey(source)) return 0;
        String worldId = requireCurrentWorld(source);
        if (worldId == null) return 0;

        // Capture dimension on the game thread before going async
        String currentDimension = showAll ? null : getDimension(Minecraft.getInstance());

        async(source, () -> {
            ApiClient api = new ApiClient();
            ApiModels.ItemsResponse resp = api.getItems(worldId);
            List<ApiModels.WorldItem> items = resp.items != null ? resp.items : List.of();

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

                String pageCmd = "/poi list group " + groupNumber;
                int totalPages = totalPages(allPois.size(), PAGE_SIZE);
                int clampedPage = Math.max(1, Math.min(page, totalPages));
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
                    pageCmd = "/poi list";
                } else {
                    displayPois = allPois;
                    dimLabel = " (All)";
                    pageCmd = "/poi list all";
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

                int totalPages = totalPages(displayPois.size(), PAGE_SIZE);
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
     * list of POIs.  Groups are expanded in-order; root POIs are included as-is.
     */
    private static List<ApiModels.WorldItem> flattenPois(List<ApiModels.WorldItem> items) {
        List<ApiModels.WorldItem> result = new ArrayList<>();
        for (ApiModels.WorldItem item : items) {
            if ("group".equals(item.type)) {
                if (item.items != null) result.addAll(item.items);
            } else {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Fetches the POI list using an already-created {@link ApiClient} (i.e. while
     * already on the async thread) and stores it in {@link PoiSession}.  Errors are
     * swallowed — this is always a best-effort background refresh.
     */
    private static void refreshPoiList(ApiClient api, String worldId) {
        try {
            ApiModels.ItemsResponse resp = api.getItems(worldId);
            List<ApiModels.WorldItem> items = resp.items != null ? resp.items : List.of();
            PoiSession.get().setLastPoiList(flattenPois(items));
        } catch (Exception ignored) {}
    }

    /**
     * Fires an async POI-list refresh for the given world.  Safe to call from
     * any thread; errors are swallowed.  Intended for automatic background
     * refreshes (e.g. on world join) where no chat source is available.
     */
    public static void refreshPoiListAsync(String worldId) {
        if (worldId == null || worldId.isBlank()) return;
        CompletableFuture.runAsync(() -> refreshPoiList(new ApiClient(), worldId));
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
        if (!checkApiKey(source)) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            send(source, err("Cannot determine player position."));
            return 0;
        }

        String worldId = PoiConfig.get().currentWorldId;
        String groupId = PoiConfig.get().currentGroupId;
        if (worldId == null) {
            send(source, err("No world selected. Run /world list or /world new first."));
            return 0;
        }

        double x = Math.floor(mc.player.getX());
        double y = Math.floor(mc.player.getY());
        double z = Math.floor(mc.player.getZ());
        String dimension = getDimension(mc);
        String finalName = stripQuotes(name);

        async(source, () -> {
            ApiClient api = new ApiClient();
            String currentGroupName = PoiConfig.get().currentGroupName;
            api.createPoi(worldId, groupId, finalName, desc, x, y, z, dimension);
            send(source, ok("Added: " + finalName));
            send(source, gray("  " + formatCoords(x, y, z) + " — " + dimensionLabel(dimension)));
            if (groupId != null && currentGroupName != null) {
                send(source, gray("  (Added to group \"" + currentGroupName + "\")"));
            } else if (groupId != null) {
                send(source, gray("  (Added to current group)"));
            }
            // Silently refresh so /poi track <#> works immediately without a manual /pois call
            refreshPoiList(api, worldId);
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
            int listNum = PoiSession.get().getListNumber(poi);
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
        send(source, ok("Stopped tracking: " + poi.name));
        return 1;
    }

    private static int executeTrackClear(FabricClientCommandSource source) {
        PoiSession.get().clearTrackedPois();
        send(source, ok("All tracking cleared."));
        return 1;
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static boolean checkApiKey(FabricClientCommandSource source) {
        if (!PoiConfig.get().hasApiKey()) {
            String baseUrl = PoiConfig.get().getApiBaseUrl();
            MutableComponent keyMsg = Component.literal("No API key set. Get one at ").withStyle(ChatFormatting.RED)
                    .append(urlComponent(baseUrl + HELP_PATH));
            Minecraft.getInstance().execute(() -> source.sendFeedback(keyMsg));
            send(source, err("Then run: /poi setkey <your-key>"));
            return false;
        }
        return true;
    }

    private static boolean checkWorldSelected(FabricClientCommandSource source) {
        if (PoiConfig.get().currentWorldId == null) {
            send(source, err("No current world selected."));
            send(source, gray("  Run /world list to list worlds, then /world select <#>"));
            send(source, gray("  Or use /world new [name] to create a world."));
            return false;
        }
        return true;
    }

    private static String requireCurrentWorld(FabricClientCommandSource source) {
        String worldId = PoiConfig.get().currentWorldId;
        if (worldId == null || worldId.isBlank()) {
            send(source, err("No current world selected."));
            send(source, gray("  Run /world list to list worlds, then /world select <#>"));
            send(source, gray("  Or use /world new [name] to create a world."));
            return null;
        }
        return worldId;
    }

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
        if (dim.equals(Level.END)) return "end";
        return "overworld";
    }

    private static void printPois(FabricClientCommandSource source,
                                   List<ApiModels.WorldItem> pois) {
        for (ApiModels.WorldItem poi : pois) {
            printPoiLine(source, poi);
        }
    }

    private static void sendPoiLineNumbered(FabricClientCommandSource source, int number, ApiModels.WorldItem poi) {
        boolean tracked = PoiSession.get().isTracked(poi);
        String toggleCmd = tracked ? "/poi untrack " + number : "/poi track " + number;

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

        sendComponent(source, line);

        if (poi.description != null && !poi.description.isBlank()) {
            send(source, gray("    " + poi.description));
        }
    }

    private static void printPoiLine(FabricClientCommandSource source, ApiModels.WorldItem poi) {
        String coords = poi.coords != null
                ? gray(" [" + formatCoords(poi.coords) + " — " + dimensionLabel(poi.dimension) + "]")
                : "";
        String desc = poi.description != null && !poi.description.isBlank()
                ? gray("    " + poi.description)
                : "";
        send(source, "  §b" + poi.name + "§r" + coords);
        if (!desc.isEmpty()) send(source, desc);
    }

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
            case "end" -> "End";
            default -> "Overworld";
        };
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

    private static int executeCopyUrl(FabricClientCommandSource source, String url) {
        Minecraft.getInstance().keyboardHandler.setClipboard(url);
        send(source, ok("Copied to clipboard: ") + gray(url));
        return 1;
    }

    private static MutableComponent urlComponent(String url) {
        return Component.literal(url)
                .withStyle(s -> s
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/poi copyurl " + url)));
    }

    private static String header(String text) {
        return "§7§m-----§r §f§l" + text + "§r §7§m-----§r";
    }

    private static String ok(String text) {
        return "§a" + text + "§r";
    }

    private static String err(String text) {
        return "§c" + text + "§r";
    }

    private static String gray(String text) {
        return "§7" + text + "§r";
    }

    private static void send(FabricClientCommandSource source, String legacyText) {
        Minecraft.getInstance().execute(() ->
                source.sendFeedback(Component.literal(legacyText)));
    }

    private static void sendComponent(FabricClientCommandSource source, Component component) {
        Minecraft.getInstance().execute(() -> source.sendFeedback(component));
    }

    /**
     * Runs an API call off the main thread, then dispatches chat feedback
     * back onto the render thread. Errors are shown in chat automatically.
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
