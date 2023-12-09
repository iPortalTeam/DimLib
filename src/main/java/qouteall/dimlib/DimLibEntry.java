package qouteall.dimlib;

import eu.midnightdust.lib.config.MidnightConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.config.DimLibConfig;
import qouteall.dimlib.ducks.IMinecraftServer;

public class DimLibEntry implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(DimLibEntry.class);
	public static final String MODID = "dimlib";
	
	@Override
	public void onInitialize() {
		LOGGER.info("DimLib initializing");
		
		DynamicDimensionsImpl.init();
		
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			DimsCommand.register(dispatcher);
		});
		
		DimensionTemplate.init();
		
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			((IMinecraftServer) server).dimlib_processTasks();
		});
		
		MidnightConfig.init(
			MODID, DimLibConfig.class
		);
	}
}