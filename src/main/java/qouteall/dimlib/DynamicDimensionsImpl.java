package qouteall.dimlib;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMappedRegistry;
import qouteall.dimlib.ducks.IMinecraftServer;
import qouteall.dimlib.mixin.common.IEWorldBorder;

import java.io.IOException;
import java.util.List;

public class DynamicDimensionsImpl {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static boolean isRemovingDimension = false;
    
    public static void init() {
    
    }
    
    public static void addDimensionDynamically(
        MinecraftServer server,
        ResourceLocation dimensionId,
        LevelStem levelStem
    ) {
        /**{@link MinecraftServer#createLevels(ChunkProgressListener)}*/
        
        ResourceKey<Level> dimensionResourceKey = ResourceKey.create(
            Registries.DIMENSION, dimensionId
        );
        
        Validate.isTrue(server.isSameThread(), "this should be called in server main thread");
        Validate.isTrue(server.isRunning(), "Server is not running");
        
        if (server.getLevel(dimensionResourceKey) != null) {
            throw new RuntimeException("Dimension " + dimensionId + " already exists.");
        }
        
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        WorldBorder worldBorder = overworld.getWorldBorder();
        Validate.notNull(worldBorder, "Overworld world border is null");
        
        WorldData worldData = server.getWorldData();
        ServerLevelData serverLevelData = worldData.overworldData();
        
        long seed = worldData.worldGenOptions().seed();
        long obfuscatedSeed = BiomeManager.obfuscateSeed(seed);
        
        DerivedLevelData derivedLevelData = new DerivedLevelData(
            worldData, serverLevelData
        );
        
        ServerLevel newWorld = new ServerLevel(
            server,
            ((IMinecraftServer) server).dimlib_getExecutor(),
            ((IMinecraftServer) server).dimlib_getStorageSource(),
            derivedLevelData,
            dimensionResourceKey,
            levelStem,
            new DummyProgressListener(),
            false, // isDebug
            obfuscatedSeed,
            ImmutableList.of(),
            false, // only true for overworld
            overworld.getRandomSequences()
        );
        
        worldBorder.addListener(
            new BorderChangeListener.DelegateBorderChangeListener(newWorld.getWorldBorder())
        );
        
        ((IMinecraftServer) server).dimlib_addDimensionToWorldMap(dimensionResourceKey, newWorld);
        
        /**
         * register it into registry, so it will be saved in
         * {@link WorldGenSettings#encode(DynamicOps, WorldOptions, RegistryAccess)} ,
         * so it will be saved into level.dat
         * */
        Registry<LevelStem> levelStemRegistry = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
        ((IMappedRegistry) levelStemRegistry).dimlib_setIsFrozen(false);
        ((MappedRegistry<LevelStem>) levelStemRegistry).register(
            ResourceKey.create(Registries.LEVEL_STEM, dimensionId),
            levelStem, Lifecycle.stable()
        );
        ((IMappedRegistry) levelStemRegistry).dimlib_setIsFrozen(true);
        
        worldBorder.applySettings(serverLevelData.getWorldBorder());
        
        LOGGER.info("Added Dimension {}", dimensionId);
        
        var dimSyncPacket = ServerPlayNetworking.createS2CPacket(
            DimLibNetworking.DimSyncPacket.createPacket(server)
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(dimSyncPacket);
        }
        
        DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.levelKeys());
    }
    
    public static void removeDimensionDynamically(ServerLevel world) {
        MinecraftServer server = world.getServer();
        
        Validate.isTrue(server.isSameThread());
        
        ResourceKey<Level> dimension = world.dimension();
        
        if (dimension == Level.OVERWORLD || dimension == Level.NETHER || dimension == Level.END) {
            throw new RuntimeException("Cannot remove vanilla dimension");
        }
        
        Validate.isTrue(server.isRunning(), "Server is not running");
        
        LOGGER.info("Started Removing Dimension {}", dimension.location());
        
        ((IMinecraftServer) server).dimlib_addTask(() -> {
            DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.invoker().accept(world);
            
            evacuatePlayersFromDimension(world);
            
            /**{@link MinecraftServer#stopServer()}*/
            
            long startTime = System.nanoTime();
            long lastLogTime = System.nanoTime();
            
            isRemovingDimension = true;
            
            ((IMinecraftServer) server).dimlib_removeDimensionFromWorldMap(dimension);
            
            try {
                while (world.getChunkSource().chunkMap.hasWork()) {
                    world.getChunkSource().removeTicketsOnClosing();
                    world.getChunkSource().tick(() -> true, false);
                    world.getChunkSource().pollTask();
                    server.pollTask();
                    
                    if (System.nanoTime() - lastLogTime > DimLibUtil.secondToNano(1)) {
                        lastLogTime = System.nanoTime();
                        LOGGER.info("waiting for chunk tasks to finish");
                    }
                    
                    if (System.nanoTime() - startTime > DimLibUtil.secondToNano(15)) {
                        LOGGER.error("Waited too long for chunk tasks");
                        break;
                    }
                    
                    ((IMinecraftServer) server).dimlib_waitUntilNextTick();
                }
            }
            catch (Throwable e) {
                LOGGER.error("Error when waiting for chunk tasks", e);
            }
            
            isRemovingDimension = false;
            
            LOGGER.info(
                "Finished chunk tasks in {} seconds",
                DimLibUtil.nanoToSecond(System.nanoTime() - startTime)
            );
            
            LOGGER.info(
                "Chunk num: {}     Has entities: {}",
                world.getChunkSource().chunkMap.size(),
                world.getAllEntities().iterator().hasNext()
            );
            
            server.saveAllChunks(false, true, false);
            
            try {
                world.close();
            }
            catch (IOException e) {
                LOGGER.error("Error when closing world", e);
            }
            
            resetWorldBorderListener(server);
            
            // force remove it from registry, so it will not be saved into level.dat
            Registry<LevelStem> levelStemRegistry = server.registryAccess()
                .registryOrThrow(Registries.LEVEL_STEM);
            ((IMappedRegistry) levelStemRegistry).dimlib_forceRemove(dimension.location());
            
            LOGGER.info("Removed Dimension {}", dimension.location());
            
            Packet<ClientCommonPacketListener> dimSyncPacket = ServerPlayNetworking.createS2CPacket(
                DimLibNetworking.DimSyncPacket.createPacket(server)
            );
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(dimSyncPacket);
            }
            
            DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.invoker().run(server, server.levelKeys());
        });
    }
    
    private static void resetWorldBorderListener(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        
        WorldBorder worldBorder = overworld.getWorldBorder();
        List<BorderChangeListener> borderChangeListeners =
            ((IEWorldBorder) worldBorder).ip_getListeners();
        borderChangeListeners.clear();
        for (ServerLevel serverWorld : server.getAllLevels()) {
            if (serverWorld != overworld) {
                worldBorder.addListener(
                    new BorderChangeListener.DelegateBorderChangeListener(serverWorld.getWorldBorder())
                );
            }
        }
        server.getPlayerList().addWorldborderListener(overworld);
    }
    
    private static void evacuatePlayersFromDimension(ServerLevel world) {
        MinecraftServer server = world.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        Validate.notNull(overworld, "Overworld is null");
        
        List<ServerPlayer> players = world.getPlayers(p -> true);
        
        BlockPos sharedSpawnPos = overworld.getSharedSpawnPos();
        
        for (ServerPlayer player : players) {
            player.teleportTo(
                overworld,
                sharedSpawnPos.getX(), sharedSpawnPos.getY(), sharedSpawnPos.getZ(),
                0, 0
            );
            player.sendSystemMessage(
                Component.literal(
                    "Teleported to spawn pos because dimension %s had been removed"
                        .formatted(world.dimension().location())
                )
            );
        }
    }
    
    private static class DummyProgressListener implements ChunkProgressListener {
        
        @Override
        public void updateSpawnPos(ChunkPos center) {
        
        }
        
        @Override
        public void onStatusChange(ChunkPos chunkPosition, @Nullable ChunkStatus newStatus) {
        
        }
        
        @Override
        public void start() {
        
        }
        
        @Override
        public void stop() {
        
        }
    }
    
}
