package com.reachingrandom.mc.poi;

import com.reachingrandom.mc.poi.campsite.CampsiteTracker;
import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.command.SharingCommands;
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

		// Name campfires to save them as POIs in the "Campsites" group
		CampsiteTracker.register();

		// Shared worlds: reload when someone else changes the selected world, and show
		// invites sent to this account
		SharingCommands.registerWatcher();

		// Each Minecraft world or server has its own POI world. Select it on join
		// (which also refreshes the POI list and restores tracked POIs, so
		// /poi track <#> works without a manual /pois call first), and drop
		// everything on disconnect so nothing carries over into the next world.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			PoiCommand.onJoin();
			SharingCommands.onJoin();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			PoiCommand.onDisconnect();
			SharingCommands.onDisconnect();
		});
	}
}