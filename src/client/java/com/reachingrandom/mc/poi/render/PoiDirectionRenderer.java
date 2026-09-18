package com.reachingrandom.mc.poi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.PoiSession;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PoiDirectionRenderer {

    private static final float LABEL_SCALE = 0.025f;
    private static final double INDICATOR_DISTANCE = 12.0;
    /** Packed light (block+sky at max) so labels ignore world lighting. */
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int BACKDROP_COLOR = 0x60000000;
    private static final int NO_OUTLINE = 0;

    public static void render(LevelRenderContext context) {
        if (PoiConfig.get().isOffMode()) return;
        List<ApiModels.WorldItem> tracked = PoiSession.get().getTrackedPois();
        if (tracked.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Determine the player's current dimension so we only render same-dimension POIs
        var dim = mc.player.level().dimension();
        String currentDimension;
        if (dim.equals(Level.NETHER)) currentDimension = "nether";
        else if (dim.equals(Level.END)) currentDimension = "end";
        else currentDimension = "overworld";

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Font font = mc.font;
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector collector = context.submitNodeCollector();

        for (ApiModels.WorldItem poi : tracked) {
            // Skip POIs that are in a different dimension
            String poiDim = poi.dimension != null ? poi.dimension : "overworld";
            if (!currentDimension.equals(poiDim)) continue;
            renderPoi(poseStack, collector, font, cameraPos, poi);
        }
    }

    private static void renderPoi(PoseStack poseStack, SubmitNodeCollector collector,
                                   Font font, Vec3 cameraPos, ApiModels.WorldItem poi) {
        if (poi == null || poi.coords == null
                || poi.coords.x == null || poi.coords.z == null) return;

        // Use the interpolated camera position as the projection origin to avoid
        // per-tick jumping. camera.position() is already smoothed between ticks.
        double tx = poi.coords.x;
        double ty = poi.coords.y != null ? poi.coords.y : cameraPos.y;
        double tz = poi.coords.z;

        double dx = tx - cameraPos.x;
        double dy = ty - cameraPos.y;
        double dz = tz - cameraPos.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 0.001) return; // Already at the POI

        // Project label along the 3D direction, clamped to actual POI position.
        double projDist = Math.min(dist, INDICATOR_DISTANCE);
        double relX = (dx / dist) * projDist;
        double relY = (dy / dist) * projDist;
        double relZ = (dz / dist) * projDist;

        // Yaw-only billboard: rotate around Y so the label faces the camera
        float yaw = (float) Math.atan2(relX, relZ);

        poseStack.pushPose();
        poseStack.translate(relX, relY, relZ);
        poseStack.rotate(Axis.YP, yaw);
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        // Line 1: POI name
        String name = poi.name;
        float halfWidthName = font.width(name) / 2f;
        collector.submitText(
                poseStack,
                -halfWidthName, 0f,
                Component.literal(name).getVisualOrderText(),
                false,
                Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT,
                0xFFFFFFFF,
                BACKDROP_COLOR,
                NO_OUTLINE
        );

        // Line 2: horizontal distance in blocks (more useful for navigation)
        int distBlocks = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String distText = distBlocks + " blocks";
        float halfWidthDist = font.width(distText) / 2f;
        // Move down ~10 text units (font line height is ~9px at scale 1)
        collector.submitText(
                poseStack,
                -halfWidthDist, 10f,
                Component.literal(distText).getVisualOrderText(),
                false,
                Font.DisplayMode.SEE_THROUGH,
                FULL_BRIGHT,
                0xFFCCCCCC,
                BACKDROP_COLOR,
                NO_OUTLINE
        );

        poseStack.popPose();
    }
}
