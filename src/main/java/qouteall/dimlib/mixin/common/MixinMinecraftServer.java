package qouteall.dimlib.mixin.common;

import com.google.common.collect.Maps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMinecraftServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer
    extends ReentrantBlockableEventLoop<TickTask> implements IMinecraftServer {
    
    public MixinMinecraftServer(String name) {
        super(name);
        throw new RuntimeException();
    }
    
    @Mutable
    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;
    
    @Shadow
    public abstract boolean isStopped();
    
    @Shadow
    public abstract boolean isDedicatedServer();
    
    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;
    
    @Shadow
    @Final
    private Executor executor;
    
    @Shadow
    protected abstract void waitUntilNextTick();
    
    @Unique
    private boolean ip_canDirectlyRegisterDimension = false;
    
    @Unique
    private boolean ip_finishedCreatingWorlds = false;
    
    @Unique
    private List<Runnable> dimlib_taskList;
    
    @Inject(method = "Lnet/minecraft/server/MinecraftServer;createLevels(Lnet/minecraft/server/level/progress/ChunkProgressListener;)V", at = @At("HEAD"))
    private void onBeforeCreateWorlds(
        ChunkProgressListener worldGenerationProgressListener, CallbackInfo ci
    ) {
        Validate.isTrue(
            !ip_canDirectlyRegisterDimension, "invalid server initialization status"
        );
        ip_canDirectlyRegisterDimension = true;
        
        DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.invoker().run(
            (MinecraftServer) (Object) this
        );
        
        ip_canDirectlyRegisterDimension = false;
    }
    
    @Inject(
        method = "Lnet/minecraft/server/MinecraftServer;createLevels(Lnet/minecraft/server/level/progress/ChunkProgressListener;)V",
        at = @At("RETURN")
    )
    private void onFinishedLoadingAllWorlds(
        CallbackInfo ci
    ) {
        ip_finishedCreatingWorlds = true;
    }
    
    @Override
    public void dimlib_addDimensionToWorldMap(ResourceKey<Level> dim, ServerLevel world) {
        // use read-copy-update to avoid concurrency issues
        LinkedHashMap<ResourceKey<Level>, ServerLevel> newMap =
            Maps.<ResourceKey<Level>, ServerLevel>newLinkedHashMap();
        
        Map<ResourceKey<Level>, ServerLevel> oldMap = this.levels;
        
        newMap.putAll(oldMap);
        newMap.put(dim, world);
        
        this.levels = newMap;
    }
    
    @Override
    public void dimlib_removeDimensionFromWorldMap(ResourceKey<Level> dimension) {
        // use read-copy-update to avoid concurrency issues
        LinkedHashMap<ResourceKey<Level>, ServerLevel> newMap =
            Maps.<ResourceKey<Level>, ServerLevel>newLinkedHashMap();
        
        Map<ResourceKey<Level>, ServerLevel> oldMap = this.levels;
        
        for (Map.Entry<ResourceKey<Level>, ServerLevel> entry : oldMap.entrySet()) {
            if (entry.getKey() != dimension) {
                newMap.put(entry.getKey(), entry.getValue());
            }
        }
        
        this.levels = newMap;
    }
    
    @Override
    public boolean dimlib_getCanDirectlyRegisterDimensions() {
        return ip_canDirectlyRegisterDimension;
    }
    
    @Override
    public boolean dimlib_getIsFinishedCreatingWorlds() {
        return ip_finishedCreatingWorlds;
    }
    
    @Override
    public LevelStorageSource.LevelStorageAccess dimlib_getStorageSource() {
        return storageSource;
    }
    
    @Override
    public Executor dimlib_getExecutor() {
        return executor;
    }
    
    @Override
    public void dimlib_waitUntilNextTick() {
        Validate.isTrue(!runningTask());
        
        waitUntilNextTick();
    }
    
    @Override
    public void dimlib_addTask(Runnable task) {
        if (dimlib_taskList == null) {
            dimlib_taskList = new ArrayList<>();
        }
        
        dimlib_taskList.add(task);
    }
    
    @Override
    public void dimlib_processTasks() {
        if (dimlib_taskList != null) {
            for (Runnable task : dimlib_taskList) {
                task.run();
            }
            dimlib_taskList = null;
        }
    }
}
