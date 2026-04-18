package com.reachingrandom.mc.poi.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reachingrandom.mc.poi.api.ApiModels;
import com.reachingrandom.mc.poi.command.PoiSession;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class PoiDirectionRenderer {

    private static final float LABEL_SCALE = 0.025f;
    private static final double INDICATOR_DISTANCE = 12.0;

    public static void render(WorldRenderContext context) {
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

        MultiBufferSource bufferSource = context.consumers();
        if (bufferSource == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        Font font = mc.font;
        PoseStack poseStack = context.matrices();

        for (ApiModels.WorldItem poi : tracked) {
            // Skip POIs that are in a different dimension
            String poiDim = poi.dimension != null ? poi.dimension : "overworld";
            if (!currentDimension.equals(poiDim)) continue;
            renderPoi(poseStack, bufferSource, font, cameraPos, poi);
        }
    }

    private static void renderPoi(PoseStack poseStack, MultiBufferSource bufferSource,
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
        poseStack.mulPose(Axis.YP.rotation(yaw));
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        // Line 1: POI name
        String name = poi.name;
        float halfWidthName = font.width(name) / 2f;
        Matrix4f matrix = poseStack.last().pose();
        font.drawInBatch(
                name,
                -halfWidthName, 0f,
                0xFFFFFFFF,
                false,
                matrix,
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0x60000000,
                0x00F000F0
        );

        // Line 2: horizontal distance in blocks (more useful for navigation)
        int distBlocks = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        String distText = distBlocks + " blocks";
        float halfWidthDist = font.width(distText) / 2f;
        // Move down ~10 text units (font line height is ~9px at scale 1)
        poseStack.translate(0, 10, 0);
        Matrix4f matrix2 = poseStack.last().pose();
        font.drawInBatch(
                distText,
                -halfWidthDist, 0f,
                0xFFCCCCCC,
                false,
                matrix2,
                bufferSource,
                Font.DisplayMode.SEE_THROUGH,
                0x60000000,
                0x00F000F0
        );

        poseStack.popPose();
    }
}
