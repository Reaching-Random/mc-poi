package com.reachingrandom.mc.poi.gui;

import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.command.PoiSession;
import com.reachingrandom.mc.poi.command.SharingCommands;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;

/**
 * The Settings tab: storage mode, API key, account link, and the campfire and
 * shared-world behavior settings. Rebuilt on every screen init; what must survive
 * (feedback, link state) lives on {@link PoiScreen}.
 */
class SettingsTab extends GridLayoutTab {

    private static final int WIDTH = 310;
    private static final int BUTTON_WIDTH = 80;
    private static final int LABEL_WIDTH = WIDTH - 2 * (BUTTON_WIDTH + 4);

    private enum Mode {
        OFFLINE("Offline"),
        ONLINE("Online"),
        OFF("Off");

        final String label;

        Mode(String label) {
            this.label = label;
        }

        static Mode current() {
            PoiConfig cfg = PoiConfig.get();
            return cfg.isOffMode() ? OFF : cfg.isOnlineMode() ? ONLINE : OFFLINE;
        }
    }

    private final PoiScreen screen;
    private final CycleButton<Mode> modeButton;
    private final EditBox keyBox;
    private final Button saveKeyButton;
    private final StringWidget keyStatus;
    private final Button removeKeyButton;
    private final StringWidget linkStatus;
    private final Button linkButton;
    private final StringWidget worldLine;
    private final MultiLineTextWidget feedbackText;

    SettingsTab(PoiScreen screen, Font font) {
        super(Component.literal("Settings"));
        this.screen = screen;
        PoiConfig cfg = PoiConfig.get();

        GridLayout.RowHelper rows = layout.rowSpacing(4).columnSpacing(4).createRowHelper(3);

        // No "Storage" heading: the mode button says it, and the tab needs the height on small windows
        modeButton = rows.addChild(CycleButton.builder((Mode m) -> Component.literal(m.label), Mode.current())
                .withValues(Mode.values())
                .create(0, 0, WIDTH, 20, Component.literal("Storage"), (button, mode) -> changeMode(button, mode)), 3);

        keyBox = rows.addChild(new EditBox(font, LABEL_WIDTH + 4 + BUTTON_WIDTH, 20, Component.literal("API key")), 2);
        keyBox.setMaxLength(256);
        keyBox.setHint(Component.literal("Paste an API key").withStyle(ChatFormatting.DARK_GRAY));
        // Masked, so a key pasted while streaming or recording stays private
        keyBox.addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        saveKeyButton = rows.addChild(Button.builder(Component.literal("Save key"), b -> saveKey())
                .width(BUTTON_WIDTH).build());

        keyStatus = rows.addChild(new StringWidget(LABEL_WIDTH, 20, Component.empty(), font));
        rows.addChild(Button.builder(Component.literal("Get a key…"),
                        b -> ConfirmLinkScreen.confirmLinkNow(screen, URI.create(PoiCommand.helpUrl())))
                .width(BUTTON_WIDTH).build());
        removeKeyButton = rows.addChild(Button.builder(Component.literal("Remove key"), b -> confirmRemoveKey())
                .width(BUTTON_WIDTH).build());

        linkStatus = rows.addChild(new StringWidget(LABEL_WIDTH + 4 + BUTTON_WIDTH, 20, Component.empty(), font), 2);
        linkButton = rows.addChild(Button.builder(Component.literal("Link"), b -> toggleLink())
                .width(BUTTON_WIDTH).build());

        rows.addChild(heading("Behavior", font), 3);
        LinearLayout pair = LinearLayout.horizontal().spacing(4);
        int half = (WIDTH - 4) / 2;
        pair.addChild(CycleButton.onOffBuilder(cfg.campfireCampsites)
                .create(0, 0, half, 20, Component.literal("Campfire campsites"),
                        (button, on) -> PoiCommand.setCampfireCampsites(on)));
        pair.addChild(CycleButton.builder((String h) -> Component.literal(switch (h) {
                            case "left" -> "Top left";
                            case "right" -> "Top right";
                            default -> "Off";
                        }), cfg.hudEnabled ? cfg.hudCorner : "off")
                .withValues("off", "left", "right")
                .withTooltip(h -> net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Your position and the /group select group, in a corner")))
                .create(0, 0, half, 20, Component.literal("HUD"), (button, h) -> PoiCommand.setHud(h)));
        rows.addChild(pair, 3);
        rows.addChild(CycleButton.onOffBuilder(cfg.autoSelectSharedWorlds)
                .create(0, 0, WIDTH, 20, Component.literal("Auto-select shared worlds"),
                        (button, on) -> PoiCommand.setAutoSelectSharedWorlds(on)), 3);

        worldLine = rows.addChild(new StringWidget(WIDTH, 12, Component.empty(), font), 3);
        feedbackText = rows.addChild(new MultiLineTextWidget(Component.empty(), font).setMaxWidth(WIDTH).setMaxRows(2), 3);

        if (screen.linkState == PoiScreen.LinkState.UNKNOWN) screen.checkLink();
        refresh();
    }

    private static StringWidget heading(String text, Font font) {
        return new StringWidget(WIDTH, 12, Component.literal(text).withStyle(ChatFormatting.YELLOW), font);
    }

    /** Brings every label and button in line with the current config. Called every tick. */
    void refresh() {
        PoiConfig cfg = PoiConfig.get();
        boolean online = cfg.isOnlineMode() && cfg.hasApiKey();

        if (modeButton.getValue() != Mode.current()) modeButton.setValue(Mode.current());

        saveKeyButton.active = !keyBox.getValue().isBlank();
        removeKeyButton.active = cfg.hasApiKey();
        String key = cfg.apiKey == null ? "" : cfg.apiKey;
        keyStatus.setMessage(cfg.hasApiKey()
                ? Component.literal("Key saved (…" + key.substring(Math.max(0, key.length() - 4)) + ")")
                : Component.literal("No key saved").withStyle(ChatFormatting.GRAY));

        linkButton.active = online && screen.linkState != PoiScreen.LinkState.CHECKING;
        linkButton.setMessage(Component.literal(screen.linkState == PoiScreen.LinkState.LINKED ? "Unlink" : "Link"));
        linkStatus.setMessage(switch (online ? screen.linkState : PoiScreen.LinkState.UNKNOWN) {
            case LINKED -> Component.literal("Account linked as " + screen.linkedName);
            case NOT_LINKED -> Component.literal("Minecraft account not linked");
            case CHECKING -> Component.literal("Checking account link…").withStyle(ChatFormatting.GRAY);
            case FAILED -> Component.literal("Couldn't check the account link").withStyle(ChatFormatting.GRAY);
            case UNKNOWN -> Component.literal("Account link needs online mode").withStyle(ChatFormatting.GRAY);
        });

        String worldId = cfg.currentWorldId;
        ApiModels.WorldSummary world = worldId == null ? null : PoiSession.get().knownWorld(worldId);
        worldLine.setMessage(Component.literal(cfg.isOffMode() ? "World: none (turned off)"
                : world != null ? "World: " + world.name
                : worldId != null ? "World: selected"
                : "World: none selected (run /world list)").withStyle(ChatFormatting.GRAY));

        feedbackText.setMessage(screen.feedback);
    }

    private void changeMode(CycleButton<Mode> button, Mode mode) {
        PoiConfig cfg = PoiConfig.get();
        switch (mode) {
            case OFFLINE -> {
                screen.feedback = Component.literal("Switched to offline storage.");
                PoiCommand.goOffline(screen::post, "");
            }
            case ONLINE -> {
                if (!cfg.hasApiKey()) {
                    button.setValue(Mode.current());
                    screen.feedback = Component.literal("Paste your API key below and save it to go online.");
                    screen.setFocused(keyBox);
                    return;
                }
                screen.feedback = Component.literal("Switched to online storage.");
                PoiCommand.goOnline(screen::post, "");
                screen.checkLink();
            }
            case OFF -> {
                PoiCommand.turnOff();
                screen.feedback = Component.literal("Points of Interest is off. Tracking labels are hidden.");
            }
        }
    }

    private void saveKey() {
        String key = keyBox.getValue().trim();
        if (key.isEmpty()) return;
        keyBox.setValue("");
        screen.feedback = Component.literal("API key saved. Switched to online storage.");
        screen.linkState = PoiScreen.LinkState.CHECKING;
        PoiCommand.saveApiKey(key, screen::post, "")
                .whenComplete((v, e) -> Minecraft.getInstance().execute(screen::checkLink));
    }

    private void confirmRemoveKey() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                screen.feedback = Component.literal("API key removed. Switched to offline storage.");
                screen.linkState = PoiScreen.LinkState.UNKNOWN;
                PoiCommand.removeApiKey(screen::post, "");
            }
            mc.gui.setScreen(screen);
        }, Component.literal("Remove your API key?"), Component.literal(
                "Points of Interest switches to offline storage. Your online POIs stay on the site;"
                        + " save the key again to get back to them.")));
    }

    private void toggleLink() {
        Minecraft mc = Minecraft.getInstance();
        if (screen.linkState == PoiScreen.LinkState.LINKED) {
            screen.linkState = PoiScreen.LinkState.CHECKING;
            SharingCommands.unlink().whenComplete((v, e) -> mc.execute(() -> {
                if (e != null) {
                    screen.linkState = PoiScreen.LinkState.LINKED;
                    screen.feedback = Component.literal("Couldn't unlink: " + PoiScreen.reason(e));
                } else {
                    screen.setLinked(null);
                    screen.feedback = Component.literal("Unlinked " + mc.getUser().getName() + ".");
                }
            }));
        } else {
            screen.linkState = PoiScreen.LinkState.CHECKING;
            SharingCommands.link().whenComplete((account, e) -> mc.execute(() -> {
                if (e != null) {
                    screen.linkState = PoiScreen.LinkState.NOT_LINKED;
                    screen.feedback = Component.literal("Couldn't link: " + PoiScreen.reason(e));
                } else {
                    screen.setLinked(account);
                    screen.feedback = Component.literal("Linked " + account.name + " to your Reaching Random account.");
                }
            }));
        }
    }
}
