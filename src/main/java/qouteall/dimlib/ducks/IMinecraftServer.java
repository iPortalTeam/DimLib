package qouteall.dimlib.ducks;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.util.concurrent.Executor;

public interface IMinecraftServer {
    
    LevelStorageSource.LevelStorageAccess dimlib_getStorageSource();
    
    Executor dimlib_getExecutor();
    
    void dimlib_addDimensionToWorldMap(ResourceKey<Level> dim, ServerLevel world);
    
    void dimlib_removeDimensionFromWorldMap(ResourceKey<Level> dimension);
    
    void dimlib_waitUntilNextTick();
    
    boolean dimlib_getCanDirectlyRegisterDimensions();
    
    boolean dimlib_getIsFinishedCreatingWorlds();
    
    void dimlib_addTask(Runnable task);
    
    void dimlib_processTasks();
}
