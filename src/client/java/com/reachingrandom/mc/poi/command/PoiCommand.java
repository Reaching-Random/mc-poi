package com.reachingrandom.mc.poi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.reachingrandom.mc.poi.api.ApiClient;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class PoiCommand {

    private static final String HELP_PATH = "/mc/poi/help";

    private PoiCommand() {}

    // ── Registration ───────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        // --- /world <command> ---
        dispatcher.register(
            literal("world")
                .executes(ctx -> executeWorldHelp(ctx.getSource()))
                .then(literal("help")
                    .executes(ctx -> executeWorldHelp(ctx.getSource())))

                // /world list
                .then(literal("list")
                    .executes(ctx -> executeWorlds(ctx.getSource())))

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
                    .executes(ctx -> executeGroups(ctx.getSource())))

                // /poi list [group#]
                .then(literal("list")
                    .executes(ctx -> executeList(ctx.getSource(), 0))
                    .then(argument("group#", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeList(ctx.getSource(),
                                getInteger(ctx, "group#")))))

                // /poi add <name> [description]
                .then(literal("add")
                    .then(argument("name", StringArgumentType.string())
                        .executes(ctx -> executeAdd(ctx.getSource(),
                                getString(ctx, "name"), ""))
                        .then(argument("description", StringArgumentType.greedyString())
                            .executes(ctx -> executeAdd(ctx.getSource(),
                                    getString(ctx, "name"),
                                    getString(ctx, "description"))))))
        );

        // --- /group <command> ---
        dispatcher.register(
            literal("group")
                .executes(ctx -> executeGroupHelp(ctx.getSource()))
                .then(literal("list")
                    .executes(ctx -> executeGroups(ctx.getSource())))
                .then(literal("add")
                    .then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> executeNewGroup(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("select")
                    .then(argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeSelectGroup(ctx.getSource(), getInteger(ctx, "number")))))
                .then(literal("clear")
                    .executes(ctx -> executeClearGroup(ctx.getSource())))
        );

        // --- /groups ---
        dispatcher.register(
            literal("groups")
                .executes(ctx -> executeGroups(ctx.getSource()))
        );

        // --- Aliases ---
        dispatcher.register(
            literal("worlds")
                .executes(ctx -> executeWorlds(ctx.getSource()))
        );

        dispatcher.register(
            literal("pois")
                .executes(ctx -> executeList(ctx.getSource(), 0))
        );
    }

    // ── /poi help ──────────────────────────────────────────────────────────────

    // ── Help ──────────────────────────────────────────────────────────────────

    private static int executeWorldHelp(FabricClientCommandSource source) {
        send(source, header("POI Tracker — World Management"));
        send(source, gray("  /world list           ") + "List your worlds");
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
        send(source, gray("  /poi setkey <key>     ") + "Save your API key");
        send(source, gray("  /poi list [group#]    ") + "List POIs (optionally by group)");
        send(source, gray("  /poi add <name> [desc]") + "Add POI (to current group if selected)");
        send(source, gray("  /poi reset            ") + "Clear ALL data and API key");
        send(source, "");
        send(source, gray("Run ") + "/world help" + gray(" or ") + "/group help" + gray(" for more."));
        send(source, gray("Website: ") + baseUrl + HELP_PATH);
        return 1;
    }

    private static int executeGroupHelp(FabricClientCommandSource source) {
        send(source, header("POI Tracker — Group Management"));
        send(source, gray("  /group list           ") + "List groups in current world");
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
        return 1;
    }

    // ── /poi worlds ────────────────────────────────────────────────────────────

    private static int executeWorlds(FabricClientCommandSource source) {
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

            PoiConfig cfg = PoiConfig.get();
            for (int i = 0; i < listToShow.size(); i++) {
                ApiModels.WorldSummary w = listToShow.get(i);
                boolean isSelected = w.id.equals(cfg.currentWorldId);
                boolean isSeedMatch = currentSeed != null && currentSeed.equals(w.seed);

                String marker = "";
                if (isSelected && isSeedMatch) marker = " §a[Current]§r";
                else if (isSelected) marker = " §c[Selected]§r";
                else if (isSeedMatch) marker = " §b[Seed Match]§r";

                send(source, "  §e" + (i + 1) + ".§r " + w.name + marker);
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

    private static int executeGroups(FabricClientCommandSource source) {
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
            for (int i = 0; i < groups.size(); i++) {
                ApiModels.WorldItem g = groups.get(i);
                int poiCount = (g.items != null) ? g.items.size() : 0;
                String marker = (g.id.equals(currentGroupId)) ? " §a[Selected]§r" : "";
                send(source, "  §e" + (i + 1) + ".§r " + g.name + gray(" (" + poiCount + " POIs)") + marker);
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

    private static int executeList(FabricClientCommandSource source, int groupNumber) {
        if (!checkApiKey(source)) return 0;
        String worldId = requireCurrentWorld(source);
        if (worldId == null) return 0;

        async(source, () -> {
            ApiClient api = new ApiClient();
            ApiModels.ItemsResponse resp = api.getItems(worldId);
            List<ApiModels.WorldItem> items = resp.items != null ? resp.items : List.of();

            if (groupNumber > 0) {
                // List POIs inside a specific group
                ApiModels.WorldItem group = PoiSession.get().getGroupByNumber(groupNumber);
                if (group == null) {
                    send(source, err("Invalid group number. Run /poi groups first."));
                    return;
                }
                List<ApiModels.WorldItem> pois = group.items != null ? group.items : List.of();
                send(source, header("POIs in: " + group.name));
                if (pois.isEmpty()) {
                    send(source, gray("  (empty)"));
                } else {
                    printPois(source, pois);
                }
            } else {
                // List everything
                send(source, header("POIs"));
                if (items.isEmpty()) {
                    send(source, gray("No POIs yet. Use /poi add <name> to add one."));
                    return;
                }
                for (ApiModels.WorldItem item : items) {
                    if ("group".equals(item.type)) {
                        send(source, "  §6[" + item.name + "]");
                        List<ApiModels.WorldItem> pois = item.items != null ? item.items : List.of();
                        printPois(source, pois);
                    } else {
                        printPoiLine(source, item);
                    }
                }
            }
        });
        return 1;
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
        });
        return 1;
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private static boolean checkApiKey(FabricClientCommandSource source) {
        if (!PoiConfig.get().hasApiKey()) {
            String baseUrl = PoiConfig.get().getApiBaseUrl();
            send(source, err("No API key set. Get one at " + baseUrl + HELP_PATH));
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

    // ── Chat helpers ───────────────────────────────────────────────────────────

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
