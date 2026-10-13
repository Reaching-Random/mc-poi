package com.reachingrandom.mc.poi.gui;

import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.storage.PoiStorage;
import com.reachingrandom.mc.poi.storage.PoiStorageProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Adds a POI, or edits one: name, description, group, and position (typed, or "Use
 * current position", which is /poi move). Campsites can only be renamed or removed, since their
 * campfire decides where they are. Read-only members see the POI but can't change it.
 */
class PoiEditScreen extends Screen {

    private static final int LABEL_WIDTH = 64;
    private static final int FIELD_WIDTH = 230;
    private static final int COORD_WIDTH = 52;
    private static final List<String> DIMENSIONS = List.of("overworld", "nether", "end");

    /** A group a POI can be put in; a null id is Ungrouped. */
    record GroupChoice(String id, String name) {}

    private final PoiScreen parent;
    private final String worldId;
    private final ApiModels.WorldItem poi;   // null for a new POI
    private final String originalGroupId;
    private final boolean campsite;
    private final boolean readOnly;

    private final LinearLayout layout = LinearLayout.vertical().spacing(4);
    private final EditBox nameBox;
    private final EditBox descriptionBox;
    private final CycleButton<GroupChoice> groupButton;
    private final EditBox xBox;
    private final EditBox yBox;
    private final EditBox zBox;
    private final CycleButton<String> dimensionButton;
    private final StringWidget status;
    private Button saveButton;

    /**
     * @param poi      the POI to edit, or null to add one at {@code here}
     * @param groupId  the group it's in (or, for a new POI, the one to put it in); null for Ungrouped
     * @param groups   groups it may be put in, campsites excluded
     */
    PoiEditScreen(PoiScreen parent, String worldId, ApiModels.WorldItem poi, String groupId, boolean campsite,
                  boolean readOnly, List<GroupChoice> groups) {
        super(Component.literal(poi == null ? "New POI" : readOnly ? "POI (read-only)" : campsite ? "Edit campsite" : "Edit POI"));
        this.parent = parent;
        this.worldId = worldId;
        this.poi = poi;
        this.originalGroupId = groupId;
        this.campsite = campsite;
        this.readOnly = readOnly;

        Font font = Minecraft.getInstance().font;
        layout.addChild(new StringWidget(title, font));

        nameBox = new EditBox(font, FIELD_WIDTH, 20, Component.literal("Name"));
        nameBox.setMaxLength(200);
        nameBox.setValue(poi != null && poi.name != null ? poi.name : "");
        nameBox.setHint(Component.literal("Home Base").withStyle(ChatFormatting.DARK_GRAY));
        layout.addChild(row(font, "Name", nameBox));

        descriptionBox = new EditBox(font, FIELD_WIDTH, 20, Component.literal("Description"));
        descriptionBox.setMaxLength(1000);
        descriptionBox.setValue(poi != null && poi.description != null ? poi.description : "");
        descriptionBox.setHint(Component.literal("Optional").withStyle(ChatFormatting.DARK_GRAY));
        layout.addChild(row(font, "Description", descriptionBox));

        List<GroupChoice> choices = new ArrayList<>();
        choices.add(new GroupChoice(null, "Ungrouped"));
        choices.addAll(groups);
        GroupChoice initial = campsite ? new GroupChoice(groupId, "Campsites")
                : choices.stream().filter(c -> Objects.equals(c.id, groupId)).findFirst().orElse(choices.getFirst());
        if (campsite) choices = List.of(initial);
        groupButton = CycleButton.builder((GroupChoice c) -> Component.literal(c.name), initial)
                .withValues(choices)
                .displayOnlyValue()
                .create(0, 0, FIELD_WIDTH, 20, Component.literal("Group"));
        layout.addChild(row(font, "Group", groupButton));

        Minecraft mc = Minecraft.getInstance();
        boolean hasCoords = poi != null && poi.coords != null && poi.coords.x != null && poi.coords.z != null;
        xBox = coordBox(font, "x", hasCoords ? poi.coords.x : playerCoord(mc, 0));
        yBox = coordBox(font, "y", hasCoords && poi.coords.y != null ? poi.coords.y : playerCoord(mc, 1));
        zBox = coordBox(font, "z", hasCoords ? poi.coords.z : playerCoord(mc, 2));
        String dimension = poi != null && poi.dimension != null ? poi.dimension : PoiCommand.getDimension(mc);
        dimensionButton = CycleButton.builder((String d) -> Component.literal(PoiCommand.dimensionLabel(d)),
                        DIMENSIONS.contains(dimension) ? dimension : "overworld")
                .withValues(DIMENSIONS)
                .displayOnlyValue()
                .create(0, 0, FIELD_WIDTH - 3 * (COORD_WIDTH + 4), 20, Component.literal("Dimension"));
        LinearLayout coords = LinearLayout.horizontal().spacing(4);
        coords.addChild(xBox);
        coords.addChild(yBox);
        coords.addChild(zBox);
        coords.addChild(dimensionButton);
        layout.addChild(row(font, "Position", coords));

        Button here = Button.builder(Component.literal("Use current position"), b -> useCurrentPosition())
                .width(FIELD_WIDTH).build();
        layout.addChild(row(font, "", here));

        // A fixed one-line slot above the buttons, so messages never shift the layout; long ones scroll
        int width = LABEL_WIDTH + 4 + FIELD_WIDTH;
        status = new StringWidget(width, 12, Component.empty(), font).setMaxWidth(width, StringWidget.TextOverflow.SCROLLING);
        layout.addChild(status);

        LinearLayout buttons = LinearLayout.horizontal().spacing(4);
        int third = (LABEL_WIDTH + 4 + FIELD_WIDTH - 8) / 3;
        if (poi != null && !readOnly) {
            buttons.addChild(Button.builder(Component.literal(campsite ? "Remove…" : "Delete…"), b -> confirmDelete())
                    .width(third).build());
        } else {
            buttons.addChild(new StringWidget(third, 20, Component.empty(), font));
        }
        buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(third).build());
        if (!readOnly) {
            saveButton = buttons.addChild(Button.builder(Component.literal(poi == null ? "Add" : "Save"), b -> save())
                    .width(third).build());
            nameBox.setResponder(v -> saveButton.active = !v.isBlank());
            saveButton.active = !nameBox.getValue().isBlank();
        }
        layout.addChild(buttons);

        // What can't change here
        boolean locked = readOnly || campsite;
        for (EditBox box : List.of(xBox, yBox, zBox)) box.setEditable(!locked);
        descriptionBox.setEditable(!locked);
        nameBox.setEditable(!readOnly);
        groupButton.active = !locked;
        dimensionButton.active = !locked;
        here.active = !locked && mc.player != null;
        if (campsite && !readOnly) {
            Tooltip why = Tooltip.create(Component.literal("Campsites stay at their campfire, in the Campsites group."));
            here.setTooltip(why);
            groupButton.setTooltip(why);
        }
    }

    private static LinearLayout row(Font font, String label, net.minecraft.client.gui.layouts.LayoutElement field) {
        LinearLayout row = LinearLayout.horizontal().spacing(4);
        row.addChild(new StringWidget(LABEL_WIDTH, 20, Component.literal(label).withStyle(ChatFormatting.GRAY), font));
        row.addChild(field);
        return row;
    }

    private static EditBox coordBox(Font font, String axis, Double value) {
        EditBox box = new EditBox(font, COORD_WIDTH, 20, Component.literal(axis));
        box.setMaxLength(9);
        box.setHint(Component.literal(axis).withStyle(ChatFormatting.DARK_GRAY));
        if (value != null) box.setValue(String.valueOf((long) Math.floor(value)));
        return box;
    }

    private static Double playerCoord(Minecraft mc, int axis) {
        if (mc.player == null) return null;
        return (double) (axis == 0 ? mc.player.getBlockX() : axis == 1 ? mc.player.getBlockY() : mc.player.getBlockZ());
    }

    private void useCurrentPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        xBox.setValue(String.valueOf(mc.player.getBlockX()));
        yBox.setValue(String.valueOf(mc.player.getBlockY()));
        zBox.setValue(String.valueOf(mc.player.getBlockZ()));
        dimensionButton.setValue(PoiCommand.getDimension(mc));
    }

    @Override
    protected void init() {
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void setInitialFocus() {
        if (!readOnly) setInitialFocus(nameBox);
        else super.setInitialFocus();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, getRectangle());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void fail(String message) {
        status.setMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private Integer parse(EditBox box) {
        try {
            return Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void save() {
        String name = nameBox.getValue().trim();
        String description = descriptionBox.getValue().trim();
        Integer x = parse(xBox);
        Integer y = parse(yBox);
        Integer z = parse(zBox);
        if (name.isEmpty()) {
            fail("Give it a name.");
            return;
        }
        if (x == null || y == null || z == null) {
            fail("Position needs whole numbers for x, y and z.");
            return;
        }
        String dimension = dimensionButton.getValue();
        String groupId = groupButton.getValue().id();

        saveButton.active = false;
        status.setMessage(Component.literal("Saving…").withStyle(ChatFormatting.GRAY));
        Minecraft mc = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                PoiStorage storage = PoiStorageProvider.get();
                if (poi == null) {
                    storage.createPoi(worldId, groupId, name, description, x, y, z, dimension);
                } else {
                    boolean renamed = !name.equals(poi.name);
                    boolean described = !description.equals(poi.description == null ? "" : poi.description);
                    if (campsite) {
                        if (renamed) storage.renameCampsite(worldId, poi.id, name);
                    } else {
                        if (renamed || described) storage.updatePoi(worldId, poi.id, name, description);
                        if (moved(x, y, z, dimension)) storage.movePoi(worldId, poi.id, x, y, z, dimension);
                        // Only when it changed: a move puts the POI at the end of the group
                        if (!Objects.equals(groupId, originalGroupId)) storage.setPoiGroup(worldId, poi.id, groupId);
                    }
                }
                PoiCommand.refreshPoiList(storage, worldId);
                mc.execute(() -> {
                    onClose();
                    parent.reloadPois();
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    saveButton.active = true;
                    fail("Couldn't save: " + e.getMessage());
                });
            }
        });
    }

    private boolean moved(int x, int y, int z, String dimension) {
        ApiModels.Coords c = poi.coords;
        boolean same = c != null && c.x != null && c.z != null
                && (long) Math.floor(c.x) == x && (long) Math.floor(c.z) == z
                && c.y != null && (long) Math.floor(c.y) == y
                && dimension.equals(poi.dimension == null ? "overworld" : poi.dimension);
        return !same;
    }

    private void confirmDelete() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(yes -> {
            if (!yes) {
                mc.gui.setScreen(this);
                return;
            }
            mc.gui.setScreen(this);
            status.setMessage(Component.literal("Deleting…").withStyle(ChatFormatting.GRAY));
            CompletableFuture.runAsync(() -> {
                try {
                    PoiStorage storage = PoiStorageProvider.get();
                    if (campsite) storage.deleteCampsite(worldId, poi.id);
                    else storage.deletePoi(worldId, poi.id);
                    PoiCommand.refreshPoiList(storage, worldId);
                    mc.execute(() -> {
                        PoiCommand.untrack(poi);
                        onClose();
                        parent.reloadPois();
                    });
                } catch (Exception e) {
                    mc.execute(() -> fail("Couldn't delete: " + e.getMessage()));
                }
            });
        }, Component.literal((campsite ? "Remove the campsite " : "Delete ") + poi.name + "?"),
                Component.literal(campsite && com.reachingrandom.mc.poi.config.PoiConfig.get().isOnlineMode()
                        ? "An admin can restore it on the website for 30 days." : "This can't be undone.")));
    }
}
