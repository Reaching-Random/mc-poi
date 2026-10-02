package com.reachingrandom.mc.poi;

import com.reachingrandom.mc.poi.campsite.CampsiteTracker;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.render.PoiDirectionRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

public class PointsofinterestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register /poi client-side commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				PoiCommand.register(dispatcher));

		// World-space direction indicator for tracked POIs
		WorldRenderEvents.AFTER_ENTITIES.register(PoiDirectionRenderer::render);

		// Name campfires to save them as POIs in the "Campsites" group
		CampsiteTracker.register();

		// Each Minecraft world or server has its own POI world. Select it on join
		// (which also refreshes the POI list and restores tracked POIs, so
		// /poi track <#> works without a manual /pois call first), and drop
		// everything on disconnect so nothing carries over into the next world.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> PoiCommand.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PoiCommand.onDisconnect());
	}
}