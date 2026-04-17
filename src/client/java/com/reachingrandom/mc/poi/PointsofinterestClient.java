package com.reachingrandom.mc.poi;

import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.config.PoiConfig;
import com.reachingrandom.mc.poi.render.PoiDirectionRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public class PointsofinterestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register /poi client-side commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				PoiCommand.register(dispatcher));

		// World-space direction indicator for tracked POIs
		LevelRenderEvents.BEFORE_GIZMOS.register(PoiDirectionRenderer::render);

		// Auto-refresh the POI list whenever the player joins a world so that
		// /poi track <#> works immediately without a manual /pois call first.
		// Works in both online and offline storage modes — no API key required
		// for offline mode.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			PoiConfig cfg = PoiConfig.get();
			if (cfg.currentWorldId != null) {
				PoiCommand.refreshAndRestoreTrackedAsync(cfg.currentWorldId);
			}
		});
	}
}