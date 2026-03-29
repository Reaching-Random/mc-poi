package com.reachingrandom.mc.poi;

import com.reachingrandom.mc.poi.command.PoiCommand;
import com.reachingrandom.mc.poi.config.PoiConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class PointsofinterestClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register /poi client-side commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
				PoiCommand.register(dispatcher));
	}
}