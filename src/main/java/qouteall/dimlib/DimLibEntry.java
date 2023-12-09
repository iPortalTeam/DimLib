package qouteall.dimlib;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.ducks.IMinecraftServer;

public class DimLibEntry implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(DimLibEntry.class);

	@Override
	public void onInitialize() {
		LOGGER.info("DimLib initializing");
		
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			((IMinecraftServer) server).dimlib_processTasks();
		});
	}
}