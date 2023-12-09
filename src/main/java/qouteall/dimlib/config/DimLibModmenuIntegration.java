package qouteall.dimlib.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.midnightdust.lib.config.MidnightConfig;
import qouteall.dimlib.DimLibEntry;

public class DimLibModmenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> MidnightConfig.getScreen(screen, DimLibEntry.MODID);
    }
}
