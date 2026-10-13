package com.reachingrandom.mc.poi.gui;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.blaze3d.platform.InputConstants;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.SharingCommands;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.concurrent.CompletionException;

/**
 * The Points of Interest screen: a POIs tab (list, filters, tracking), a World tab
 * (worlds, sharing, invites) and a Settings tab (storage mode, API key, account link,
 * behavior). Opened with the I key or /poi menu.
 */
public class PoiScreen extends Screen {

    static final int MAX_WIDTH = 440;
    static final int FOOTER_HEIGHT = 30;

    private static KeyMapping openKey;
    private static volatile boolean openPending;

    /** Registers the open key and the tick hook that opens the screen. */
    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("points-of-interest", "main"));
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.points-of-interest.open", InputConstants.Type.KEYSYM, InputConstants.KEY_I, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) openPending = true;
            if (openPending) {
                openPending = false;
                if (mc.player != null) mc.gui.setScreen(new PoiScreen());
            }
        });
    }

    /** Opens the screen on the next client tick, after whatever screen is open now closes. */
    public static void openNextTick() {
        openPending = true;
    }

    enum LinkState { UNKNOWN, CHECKING, LINKED, NOT_LINKED, FAILED }

    private static final int POIS = 0;
    private static final int WORLD = 1;
    private static final int SETTINGS = 2;

    private final PoiListTab listTab;
    private final WorldTab worldTab;
    // Kept here rather than in the tabs, which are rebuilt on resize and after confirm prompts
    Component feedback = Component.empty();
    LinkState linkState = LinkState.UNKNOWN;
    String linkedName;

    private int selectedTab;
    private TabManager tabManager;
    private MenuTabBar tabBar;
    private SettingsTab settingsTab;
    private Button doneButton;

    public PoiScreen() {
        super(Component.literal("Points of Interest"));
        listTab = new PoiListTab(this);
        worldTab = new WorldTab(this);
        // When it's off, the POIs tab has nothing to show; Settings is where to turn it on
        selectedTab = PoiConfig.get().isOffMode() ? SETTINGS : POIS;
    }

    @Override
    protected void init() {
        if (tabManager != null) {
            var current = tabManager.getCurrentTab();
            selectedTab = current == worldTab ? WORLD : current == settingsTab ? SETTINGS : POIS;
        }

        tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
        listTab.initWidgets(minecraft, font);
        worldTab.initWidgets(minecraft, font);
        settingsTab = new SettingsTab(this, font);
        tabBar = MenuTabBar.builder(tabManager, width).addTabs(listTab, worldTab, settingsTab).build();
        addRenderableWidget(tabBar);
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(80).build());

        tabBar.selectTab(selectedTab, false);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (tabBar == null) return;
        tabBar.arrangeElements(width);
        int top = tabBar.getRectangle().bottom();
        int footerTop = height - FOOTER_HEIGHT;
        tabManager.setTabArea(new ScreenRectangle(0, top, width, footerTop - top));
        doneButton.setPosition(contentRight() - doneButton.getWidth(), footerTop + 5);
    }

    int contentLeft() {
        return (width - Math.min(MAX_WIDTH, width - 16)) / 2;
    }

    int contentRight() {
        return width - contentLeft();
    }

    /** Loads the POIs tab again, after an add, edit or delete elsewhere. */
    void reloadPois() {
        listTab.reload();
    }

    /** Builds the widgets again, keeping the tab: for when a tab's title or layout changes. */
    void rebuild() {
        rebuildWidgets();
    }

    @Override
    public void tick() {
        listTab.tick();
        worldTab.tick();
        if (tabManager.getCurrentTab() == settingsTab) settingsTab.refresh();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (tabManager.getCurrentTab() == listTab) listTab.extractOverlay(g, height - FOOTER_HEIGHT);
        if (tabManager.getCurrentTab() == worldTab) worldTab.extractOverlay(g);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Same key closes it, like the inventory, unless you're typing in a text box
        if (openKey != null && openKey.matches(event) && !(getFocused() instanceof EditBox)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Shared state ──────────────────────────────────────────────────────────

    /** Shows a message under the settings, from any thread. Takes legacy § formatting. */
    void post(String message) {
        Minecraft.getInstance().execute(() -> feedback = Component.literal(message.strip()));
    }

    /** Looks up whether this Minecraft account is linked. Online mode with a key only. */
    void checkLink() {
        PoiConfig cfg = PoiConfig.get();
        if (!cfg.isOnlineMode() || !cfg.hasApiKey()) {
            linkState = LinkState.UNKNOWN;
            return;
        }
        linkState = LinkState.CHECKING;
        SharingCommands.findLinkedAccount().whenComplete((account, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                linkState = LinkState.FAILED;
            } else {
                setLinked(account);
            }
        }));
    }

    void setLinked(ApiModels.LinkedAccount account) {
        linkState = account != null ? LinkState.LINKED : LinkState.NOT_LINKED;
        linkedName = account != null ? account.name : null;
    }

    /** A readable reason from a failed background call. */
    static String reason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof AuthenticationException) {
            return "Mojang couldn't confirm your session. Restart the game from the launcher and try again.";
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
