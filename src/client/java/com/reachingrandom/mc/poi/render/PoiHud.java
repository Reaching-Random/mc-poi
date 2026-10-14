package com.reachingrandom.mc.poi.render;

import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * The corner overlay: your block position and facing (N, NE, …), and the group /poi add uses
 * when one is selected. Off by default; /poi hud or Settings turns it on. Hidden with
 * the rest of the HUD (F1) and with the F3 debug screen.
 */
public final class PoiHud {

    private static final int MARGIN = 4;
    private static final int LINE_HEIGHT = 10;
    /** Vanilla's effect icons sit in rows of this height in the top-right corner. */
    private static final int EFFECT_ROW_HEIGHT = 26;
    private static final int BACKDROP = 0x80000000;

    private PoiHud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("points-of-interest", "hud"), PoiHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        PoiConfig cfg = PoiConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.hudEnabled || cfg.isOffMode() || mc.player == null) return;
        // Hidden with the rest of the HUD (F1), and while the F3 screen is open, which shows the same and more.
        // Not showDebugScreen(): it's also true when any debug entry is set to always show, like hitboxes.
        if (mc.options.hideGui || mc.debugEntries.isOverlayVisible()) return;

        List<String> lines = new ArrayList<>();
        lines.add(mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ()
                + "  " + facing(mc.player.getYRot()));
        if (cfg.currentGroupId != null) {
            lines.add("Group: " + (cfg.currentGroupName != null ? cfg.currentGroupName : "selected"));
        }

        // One GUI-scale step smaller than the rest of the HUD (3/4 at scale 4), so the font's
        // pixels still land on whole screen pixels and stay sharp
        int guiScale = mc.getWindow().getGuiScale();
        float scale = Math.max(1, guiScale - 1) / (float) guiScale;

        Font font = mc.font;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        boolean right = "right".equals(cfg.hudCorner);
        // Laid out in the scaled space; the effect icons' offset is in unscaled pixels
        int x = right ? Math.round(g.guiWidth() / scale) - MARGIN - width : MARGIN;
        int y = MARGIN + Math.round((right ? effectRowsHeight(mc) : 0) / scale);

        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        g.fill(x - 2, y - 2, x + width + 2, y + lines.size() * LINE_HEIGHT, BACKDROP);
        for (String line : lines) {
            g.drawString(font, line, x, y, 0xFFFFFFFF, false);
            y += LINE_HEIGHT;
        }
        g.pose().popMatrix();
    }

    /** Compass points in 45° steps, starting at yaw 0, which faces south (+Z). */
    private static final String[] FACINGS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    /** The compass point nearest the way the player faces. */
    static String facing(float yaw) {
        int step = Math.round(Math.floorMod(Math.round(yaw), 360) / 45f) % 8;
        return FACINGS[step];
    }

    /** How far down the effect icons reach: one row for good effects, one for bad. */
    private static int effectRowsHeight(Minecraft mc) {
        boolean good = false;
        boolean bad = false;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (!effect.showIcon()) continue;
            if (effect.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        return (good ? EFFECT_ROW_HEIGHT : 0) + (bad ? EFFECT_ROW_HEIGHT : 0);
    }
}
