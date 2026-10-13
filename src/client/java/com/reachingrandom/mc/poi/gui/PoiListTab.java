package com.reachingrandom.mc.poi.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.campsite.CampsiteIndex;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.command.PoiSession;
import com.reachingrandom.mc.poi.config.PoiConfig;
import com.reachingrandom.mc.poi.storage.PoiStorageProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The POIs tab: the selected world's POIs with a folder sidebar, a dimension filter
 * and a search box. A POI's checkbox toggles tracking; the rest of its row opens the editor. Below the
 * sidebar, New and Edit… manage groups; Add POI here… adds one in the selected group.
 *
 * <p>The group open in the sidebar is also the current group (/group select): where /poi add
 * puts new POIs, and what the HUD shows. All and Ungrouped clear it; Tracked and Campsites,
 * which can't take new POIs, leave it alone. The tab opens on the current group.
 */
class PoiListTab implements Tab {

    private static final int SIDEBAR_WIDTH = 110;
    private static final int GAP = 6;
    private static final int PADDING_TOP = 6;

    private static final String ALL = "*";
    private static final String UNGROUPED = "";
    /** Virtual folder of tracked POIs. Not a group ID, which are never blank or start with '@'. */
    private static final String TRACKED = "@tracked";

    private static final Identifier CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox");
    private static final Identifier CHECKBOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");
    private static final Identifier CHECKBOX_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
    private static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED =
            Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");

    private enum DimensionFilter {
        ALL("All dimensions", null),
        OVERWORLD("Overworld", "overworld"),
        NETHER("Nether", "nether"),
        END("End", "end");

        final String label;
        final String id;

        DimensionFilter(String label, String id) {
            this.label = label;
            this.id = id;
        }
    }

    private record Folder(String id, String name) {}

    /** A POI with the folder it is in and its distance from the player (NaN if not in this dimension). */
    private record Row(ApiModels.WorldItem poi, String folderId, String folderName, String dimension, double distance) {}

    private final PoiScreen screen;

    // What's loaded. A change of world or storage mode (from Settings) reloads.
    private String worldId;
    private String storageMode;
    private int loadGeneration;
    private String currentDimension;
    private double playerX;
    private double playerZ;

    private final List<Folder> folders = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> folderCounts = new HashMap<>();
    /**
     * The POIs the Tracked folder shows: those tracked when it was opened. Untracking one
     * there leaves its row (unchecked) until you switch folders, so a misclick is easy to undo.
     */
    private final Set<Row> trackedSnapshot = new HashSet<>();
    /** Shown in place of the list while loading or when POIs can't be listed; null once loaded. */
    private Component status;
    /** The selected world's POIs have loaded (possibly none). */
    private boolean loaded;
    /** The campsite group's id, which campfires manage, or null. */
    private String campsiteGroupId;
    /** A result or error from the last group action, shown in the footer for a while. */
    private Component notice;
    private long noticeUntil;

    private String folderFilter = ALL;
    /** The tab has opened on the current group once; later reloads keep whatever is open. */
    private boolean openedOnCurrentGroup;
    private DimensionFilter dimensionFilter = DimensionFilter.ALL;
    private String query = "";

    private Font font;
    private CycleButton<DimensionFilter> dimensionButton;
    private EditBox searchBox;
    private FolderList folderList;
    private PoiList poiList;
    private Button untrackAllButton;
    private Button addButton;
    private Button newGroupButton;
    private Button editGroupButton;
    private int left;

    PoiListTab(PoiScreen screen) {
        this.screen = screen;
        reload();
    }

    @Override
    public Component getTabTitle() {
        return Component.literal("POIs");
    }

    @Override
    public Component getTabExtraNarration() {
        return Component.empty();
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    /** Reloads if Settings changed the world or storage mode. Called every tick. */
    void tick() {
        PoiConfig cfg = PoiConfig.get();
        if (!Objects.equals(worldId, cfg.currentWorldId) || !Objects.equals(storageMode, cfg.storageMode)) reload();
    }

    /** Loads the POIs again, keeping the filters. */
    void reload() {
        Minecraft mc = Minecraft.getInstance();
        PoiConfig cfg = PoiConfig.get();
        worldId = cfg.currentWorldId;
        storageMode = cfg.storageMode;
        currentDimension = PoiCommand.getDimension(mc);
        playerX = mc.player != null ? mc.player.getX() : 0;
        playerZ = mc.player != null ? mc.player.getZ() : 0;
        folders.clear();
        rows.clear();
        loaded = false;
        int generation = ++loadGeneration;

        if (cfg.isOffMode()) {
            status = Component.literal("Points of Interest is turned off. Turn it on in Settings.");
        } else if (cfg.isOnlineMode() && !cfg.hasApiKey()) {
            status = Component.literal("No API key set. Add one in Settings, or switch to offline.");
        } else if (worldId == null || worldId.isBlank()) {
            status = Component.literal("No world selected. Run /world list, then /world select <#>.");
        } else {
            status = Component.literal("Loading…");
            String loadingWorld = worldId;
            CompletableFuture.runAsync(() -> {
                try {
                    List<ApiModels.WorldItem> items = PoiStorageProvider.get().listItems(loadingWorld);
                    mc.execute(() -> {
                        if (generation == loadGeneration) setItems(items);
                    });
                } catch (Exception e) {
                    mc.execute(() -> {
                        if (generation == loadGeneration) status = Component.literal("Could not load POIs: " + e.getMessage());
                    });
                }
            });
        }
        if (folderList != null) rebuildLists();
    }

    private void setItems(List<ApiModels.WorldItem> items) {
        folders.clear();
        rows.clear();
        loaded = true;
        ApiModels.WorldItem campsites = CampsiteIndex.findGroup(items);
        campsiteGroupId = campsites != null ? campsites.id : null;
        boolean hasUngrouped = false;
        for (ApiModels.WorldItem item : items) {
            if ("group".equals(item.type)) {
                String name = isBlank(item.name) ? "(unnamed)" : item.name;
                folders.add(new Folder(item.id, name));
                if (item.items != null) {
                    for (ApiModels.WorldItem poi : item.items) addRow(poi, item.id, name);
                }
            } else if (addRow(item, UNGROUPED, "Ungrouped")) {
                hasUngrouped = true;
            }
        }
        if (hasUngrouped) folders.add(new Folder(UNGROUPED, "Ungrouped"));

        // Tracked first, then nearest in this dimension, then the rest by name.
        // Toggling doesn't re-sort, so rows never jump under the cursor.
        PoiSession session = PoiSession.get();
        rows.sort(Comparator
                .comparing((Row r) -> !session.isTracked(r.poi))
                .thenComparing(r -> Double.isNaN(r.distance))
                .thenComparingDouble(r -> Double.isNaN(r.distance) ? 0 : r.distance)
                .thenComparing(r -> r.poi.name.toLowerCase(Locale.ROOT)));

        status = rows.isEmpty() && folders.isEmpty() ? Component.literal("No POIs yet. Add one with Add POI here… or /poi add <name>.") : null;
        if (pendingFolder != null) {
            String id = pendingFolder.get();
            pendingFolder = null;
            if (id != null) {
                folderFilter = id;
                followFolder();
            }
        } else if (!openedOnCurrentGroup) {
            // Open on the current group, if it's still here
            String current = PoiConfig.get().currentGroupId;
            if (current != null && folders.stream().anyMatch(f -> f.id.equals(current))) folderFilter = current;
        }
        openedOnCurrentGroup = true;
        if (folderList != null) rebuildLists();
    }

    private boolean addRow(ApiModels.WorldItem poi, String folderId, String folderName) {
        if (isBlank(poi.name)) return false;
        String dimension = poi.dimension == null ? "overworld" : poi.dimension;
        double distance = dimension.equals(currentDimension) && PoiCommand.canTrack(poi)
                ? Math.hypot(poi.coords.x - playerX, poi.coords.z - playerZ)
                : Double.NaN;
        rows.add(new Row(poi, folderId, folderName, dimension, distance));
        return true;
    }

    private boolean matchesDimensionAndQuery(Row row) {
        if (dimensionFilter.id != null && !dimensionFilter.id.equals(row.dimension)) return false;
        if (query.isEmpty()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        return row.poi.name.toLowerCase(Locale.ROOT).contains(q)
                || (row.poi.description != null && row.poi.description.toLowerCase(Locale.ROOT).contains(q));
    }

    /** Recomputes folder counts and the visible POIs from the current filters. */
    private void applyFilters() {
        folderCounts.clear();
        List<PoiEntry> visible = new ArrayList<>();
        for (Row row : rows) {
            if (!matchesDimensionAndQuery(row)) continue;
            folderCounts.merge(ALL, 1, Integer::sum);
            folderCounts.merge(row.folderId, 1, Integer::sum);
            boolean shown = switch (folderFilter) {
                case ALL -> true;
                case TRACKED -> trackedSnapshot.contains(row);
                default -> folderFilter.equals(row.folderId);
            };
            if (shown) visible.add(new PoiEntry(row));
        }
        poiList.replaceEntries(visible);
        poiList.setScrollAmount(0);
    }

    private void snapshotTracked() {
        trackedSnapshot.clear();
        PoiSession session = PoiSession.get();
        for (Row row : rows) {
            if (session.isTracked(row.poi)) trackedSnapshot.add(row);
        }
    }

    /** Tracked POIs that pass the dimension and search filters, counted live for the sidebar. */
    private int trackedCount() {
        PoiSession session = PoiSession.get();
        int count = 0;
        for (Row row : rows) {
            if (session.isTracked(row.poi) && matchesDimensionAndQuery(row)) count++;
        }
        return count;
    }

    private void rebuildLists() {
        snapshotTracked();
        List<Folder> shownFolders = new ArrayList<>();
        if (status == null) {
            shownFolders.add(new Folder(ALL, "All"));
            shownFolders.add(new Folder(TRACKED, "Tracked"));
            shownFolders.addAll(folders);
        }
        List<FolderEntry> entries = shownFolders.stream().map(FolderEntry::new).toList();
        folderList.replaceEntries(entries);
        entries.stream().filter(e -> e.folder.id.equals(folderFilter)).findFirst()
                .ifPresentOrElse(folderList::setSelectedQuietly, () -> {
                    if (!entries.isEmpty()) {
                        folderFilter = ALL;
                        folderList.setSelectedQuietly(entries.getFirst());
                    }
                });
        applyFilters();
    }

    // ── Widgets and layout ────────────────────────────────────────────────────

    /** Creates this tab's widgets. Called on every screen init; the data above survives. */
    void initWidgets(Minecraft mc, Font font) {
        this.font = font;
        dimensionButton = CycleButton.builder((DimensionFilter d) -> Component.literal(d.label), dimensionFilter)
                .withValues(DimensionFilter.values())
                .displayOnlyValue()
                .create(0, 0, SIDEBAR_WIDTH, 20, Component.literal("Dimension"), (button, value) -> {
                    dimensionFilter = value;
                    applyFilters();
                });

        searchBox = new EditBox(font, 0, 0, 200, 20, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search…").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            query = value.trim();
            applyFilters();
        });

        folderList = new FolderList(mc, SIDEBAR_WIDTH, 100, 0);
        poiList = new PoiList(mc, 200, 100, 0);
        untrackAllButton = Button.builder(Component.literal("Untrack all"), b -> PoiCommand.clearTracking())
                .width(80).build();
        addButton = Button.builder(Component.literal("Add POI here…"), b -> addHere()).width(90).build();
        newGroupButton = Button.builder(Component.literal("+ New"), b -> newGroup())
                .width((SIDEBAR_WIDTH - 4) / 2).build();
        newGroupButton.setTooltip(Tooltip.create(Component.literal("New group")));
        editGroupButton = Button.builder(Component.literal("Edit…"), b -> editGroup())
                .width((SIDEBAR_WIDTH - 4) / 2).build();
        editGroupButton.setTooltip(Tooltip.create(Component.literal("Rename or delete the selected group")));

        rebuildLists();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(dimensionButton);
        consumer.accept(searchBox);
        consumer.accept(folderList);
        consumer.accept(poiList);
        consumer.accept(untrackAllButton);
        consumer.accept(addButton);
        consumer.accept(newGroupButton);
        consumer.accept(editGroupButton);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        left = screen.contentLeft();
        int right = screen.contentRight();
        int top = area.top() + PADDING_TOP;
        int listLeft = left + SIDEBAR_WIDTH + GAP;
        int listTop = top + 20 + GAP;
        int listHeight = area.bottom() - listTop;

        dimensionButton.setPosition(left, top);
        searchBox.setPosition(listLeft, top);
        searchBox.setWidth(right - listLeft);
        folderList.updateSizeAndPosition(SIDEBAR_WIDTH, listHeight - 24, left, listTop);
        newGroupButton.setPosition(left, area.bottom() - 20);
        editGroupButton.setPosition(left + SIDEBAR_WIDTH - editGroupButton.getWidth(), area.bottom() - 20);
        poiList.updateSizeAndPosition(right - listLeft, listHeight, listLeft, listTop);
        // In the footer, beside Done
        untrackAllButton.setPosition(right - 2 * 80 - 4, area.bottom() + 5);
        addButton.setPosition(untrackAllButton.getX() - 4 - addButton.getWidth(), area.bottom() + 5);
    }

    /** The status message over the list and the footer summary. */
    void renderOverlay(GuiGraphics g, int footerTop) {
        int listMiddle = (poiList.getX() + poiList.getRight()) / 2;
        if (status != null) {
            g.drawCenteredString(font, status, listMiddle, poiList.getY() + 20, 0xFFA0A0A0);
        } else if (poiList.children().isEmpty()) {
            g.drawCenteredString(font, Component.literal("No POIs match these filters."), listMiddle, poiList.getY() + 20, 0xFFA0A0A0);
        }

        updateButtons();
        int footerWidth = addButton.getX() - left - 6;
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(notice, footerWidth)),
                    left, footerTop + 11, 0xFFFFFFFF, false);
            return;
        }
        ApiModels.WorldSummary world = worldId == null ? null : PoiSession.get().knownWorld(worldId);
        String summary = PoiSession.get().getTrackedPois().size() + " tracked · " + poiList.children().size() + " shown";
        // The world's name goes first when there's room, and is the first thing dropped
        if (world != null && font.width(world.name + " · " + summary) <= footerWidth) summary = world.name + " · " + summary;
        g.drawString(font, font.plainSubstrByWidth(summary, footerWidth), left, footerTop + 11, 0xFFA0A0A0, false);
    }

    // ── Adding, editing, groups ───────────────────────────────────────────────

    private boolean readOnly() {
        ApiModels.WorldSummary world = worldId == null ? null : PoiSession.get().knownWorld(worldId);
        return world != null && "read-only".equals(world.role);
    }

    /** A real group is selected: not All, Tracked or Ungrouped. */
    private boolean groupSelected() {
        return !ALL.equals(folderFilter) && !TRACKED.equals(folderFilter) && !UNGROUPED.equals(folderFilter);
    }

    private void updateButtons() {
        boolean canAdd = loaded && !readOnly();
        boolean inCampsites = folderFilter.equals(campsiteGroupId);
        addButton.active = canAdd && !inCampsites && Minecraft.getInstance().player != null;
        addButton.setTooltip(inCampsites ? Tooltip.create(Component.literal("Campfires add campsites: name a campfire when you place it."))
                : readOnly() ? Tooltip.create(Component.literal("You have read-only access to this world.")) : null);
        newGroupButton.active = canAdd;
        editGroupButton.active = canAdd && groupSelected() && !inCampsites;
    }

    private List<PoiEditScreen.GroupChoice> groupChoices() {
        return folders.stream()
                .filter(f -> !UNGROUPED.equals(f.id) && !f.id.equals(campsiteGroupId))
                .map(f -> new PoiEditScreen.GroupChoice(f.id, f.name))
                .toList();
    }

    /** Makes the open folder the current group, as /group select and /group clear do. */
    private void followFolder() {
        if (TRACKED.equals(folderFilter) || folderFilter.equals(campsiteGroupId)) return;
        PoiConfig cfg = PoiConfig.get();
        Folder folder = groupSelected() ? folders.stream().filter(f -> f.id.equals(folderFilter)).findFirst().orElse(null) : null;
        String id = folder != null ? folder.id : null;
        if (Objects.equals(id, cfg.currentGroupId)) return;
        cfg.currentGroupId = id;
        cfg.currentGroupName = folder != null ? folder.name : null;
        cfg.save();
    }

    private void addHere() {
        String groupId = groupSelected() && !folderFilter.equals(campsiteGroupId) ? folderFilter : null;
        Minecraft.getInstance().setScreen(new PoiEditScreen(screen, worldId, null, groupId, false, false, groupChoices()));
    }

    private void openEditor(Row row) {
        String groupId = UNGROUPED.equals(row.folderId) ? null : row.folderId;
        boolean campsite = row.folderId.equals(campsiteGroupId);
        Minecraft.getInstance().setScreen(new PoiEditScreen(screen, worldId, row.poi, groupId, campsite, readOnly(), groupChoices()));
    }

    private void showNotice(Component message) {
        notice = message;
        noticeUntil = System.currentTimeMillis() + 6000;
    }

    /** Runs a storage call off the game thread, then reloads; a failure shows in the footer. */
    private void runThenReload(String failure, StorageCall call, Component success) {
        Minecraft mc = Minecraft.getInstance();
        String world = worldId;
        CompletableFuture.runAsync(() -> {
            try {
                call.run(PoiStorageProvider.get());
                PoiCommand.refreshPoiList(PoiStorageProvider.get(), world);
                mc.execute(() -> {
                    if (success != null) showNotice(success);
                    reload();
                });
            } catch (Exception e) {
                mc.execute(() -> showNotice(Component.literal(failure + ": " + e.getMessage()).withStyle(ChatFormatting.RED)));
            }
        });
    }

    @FunctionalInterface
    private interface StorageCall {
        void run(com.reachingrandom.mc.poi.storage.PoiStorage storage) throws Exception;
    }

    private void newGroup() {
        Minecraft.getInstance().setScreen(new NamePromptScreen(screen, "New group", "", null, "Create", name -> {
            String[] created = new String[1];
            runThenReload("Couldn't create the group", storage -> created[0] = storage.createGroup(worldId, name).id,
                    Component.literal("Created the group " + name + "."));
            // Select it once the list has it
            pendingFolder = () -> created[0];
        }));
    }

    /** After a reload, select this folder if it's there (a group just created). */
    private java.util.function.Supplier<String> pendingFolder;

    private void editGroup() {
        Folder folder = folders.stream().filter(f -> f.id.equals(folderFilter)).findFirst().orElse(null);
        if (folder == null) return;
        long count = rows.stream().filter(r -> r.folderId.equals(folder.id)).count();
        Minecraft mc = Minecraft.getInstance();
        String note = count == 0 ? "It's empty." : "Deleting it moves its " + count + (count == 1 ? " POI" : " POIs") + " to Ungrouped.";
        mc.setScreen(new NamePromptScreen(screen, "Edit group", folder.name, note, "Rename",
                name -> runThenReload("Couldn't rename the group", storage -> {
                    storage.renameGroup(worldId, folder.id, name);
                    PoiConfig cfg = PoiConfig.get();
                    if (folder.id.equals(cfg.currentGroupId)) {
                        cfg.currentGroupName = name;
                        cfg.save();
                    }
                }, Component.literal("Renamed " + folder.name + " to " + name + ".")),
                "Delete group…", () -> mc.setScreen(new ConfirmScreen(yes -> {
                    mc.setScreen(screen);
                    if (!yes) return;
                    folderFilter = ALL;
                    runThenReload("Couldn't delete the group", storage -> {
                        storage.deleteGroup(worldId, folder.id);
                        mc.execute(() -> PoiCommand.forgetCurrentGroup(folder.id));
                    }, Component.literal("Deleted " + folder.name + ". Its POIs are now ungrouped."));
                }, Component.literal("Delete the group " + folder.name + "?"), Component.literal(
                        count == 0 ? "It's empty." : "Its " + count + (count == 1 ? " POI moves" : " POIs move") + " to Ungrouped. None are deleted.")))));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String formatDistance(double meters) {
        return meters < 1000 ? Math.round(meters) + "m" : String.format(Locale.ROOT, "%.1fkm", meters / 1000);
    }

    // ── Folder sidebar ────────────────────────────────────────────────────────

    private class FolderList extends ObjectSelectionList<FolderEntry> {
        private boolean quiet;

        FolderList(Minecraft mc, int width, int height, int y) {
            super(mc, width, height, y, 16);
        }

        void setSelectedQuietly(FolderEntry entry) {
            quiet = true;
            setSelected(entry);
            quiet = false;
        }

        @Override
        public void setSelected(FolderEntry entry) {
            super.setSelected(entry);
            if (!quiet && entry != null && !entry.folder.id.equals(folderFilter)) {
                folderFilter = entry.folder.id;
                followFolder();
                snapshotTracked();
                applyFilters();
            }
        }

        @Override
        public int getRowWidth() {
            return width - 10;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }
    }

    private class FolderEntry extends ObjectSelectionList.Entry<FolderEntry> {
        final Folder folder;

        FolderEntry(Folder folder) {
            this.folder = folder;
        }

        private int count() {
            return TRACKED.equals(folder.id) ? trackedCount() : folderCounts.getOrDefault(folder.id, 0);
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float delta) {
            String count = String.valueOf(count());
            int countX = getContentRight() - font.width(count);
            String name = font.plainSubstrByWidth(folder.name, countX - getContentX() - 4);
            int y = getContentYMiddle() - 4;
            g.drawString(font, name, getContentX(), y, 0xFFFFFFFF, false);
            g.drawString(font, count, countX, y, 0xFF808080, false);
        }

        @Override
        public Component getNarration() {
            return Component.literal(folder.name + ", " + count() + " POIs");
        }
    }

    // ── POI list ──────────────────────────────────────────────────────────────

    private class PoiList extends ObjectSelectionList<PoiEntry> {
        PoiList(Minecraft mc, int width, int height, int y) {
            super(mc, width, height, y, 24);
        }

        @Override
        public int getRowWidth() {
            return width - 10;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - 6;
        }
    }

    private class PoiEntry extends ObjectSelectionList.Entry<PoiEntry> {
        private static final int BOX = 14;

        /** The checkbox, with a little slack so near misses still count. */
        private boolean overCheckbox(double mouseX) {
            return mouseX < getContentX() + BOX + 3;
        }
        final Row row;

        PoiEntry(Row row) {
            this.row = row;
        }

        private void toggle() {
            if (!PoiCommand.canTrack(row.poi)) return;
            if (PoiSession.get().isTracked(row.poi)) {
                PoiCommand.untrack(row.poi);
            } else {
                PoiCommand.track(row.poi);
            }
        }

        @Override
        public void renderContent(GuiGraphics g, int mouseX, int mouseY, boolean hovered, float delta) {
            boolean trackable = PoiCommand.canTrack(row.poi);
            boolean tracked = PoiSession.get().isTracked(row.poi);
            int x = getContentX();
            if (trackable) {
                boolean overBox = hovered && overCheckbox(mouseX);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, tracked
                                ? (overBox ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED)
                                : (overBox ? CHECKBOX_HIGHLIGHTED : CHECKBOX),
                        x, getContentYMiddle() - BOX / 2, BOX, BOX);
            }

            int textX = x + BOX + 6;
            int textWidth = getContentRight() - textX;
            g.drawString(font, font.plainSubstrByWidth(row.poi.name, textWidth),
                    textX, getContentY() + 1, trackable ? 0xFFFFFFFF : 0xFF808080, false);

            List<String> details = new ArrayList<>();
            if (ALL.equals(folderFilter) || TRACKED.equals(folderFilter)) details.add(row.folderName);
            if (dimensionFilter == DimensionFilter.ALL) details.add(PoiCommand.dimensionLabel(row.dimension));
            if (!Double.isNaN(row.distance)) details.add(formatDistance(row.distance));
            if (!trackable) details.add("no coordinates");
            g.drawString(font, font.plainSubstrByWidth(String.join(" · ", details), textWidth),
                    textX, getContentY() + 11, 0xFF909090, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (PoiCommand.canTrack(row.poi) && overCheckbox(event.x())) toggle();
            else openEditor(row);
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            // Space toggles tracking, like the checkbox; Enter opens the editor, like the row
            if (event.key() == InputConstants.KEY_SPACE) {
                toggle();
                return true;
            }
            if (event.isConfirmation()) {
                openEditor(row);
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public Component getNarration() {
            boolean tracked = PoiSession.get().isTracked(row.poi);
            return Component.literal(row.poi.name + (tracked ? ", tracked" : ", not tracked"));
        }
    }
}
