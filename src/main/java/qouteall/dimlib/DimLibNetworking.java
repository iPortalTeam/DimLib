package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.mixin.client.IClientPacketListener;

import java.util.stream.Collectors;

public class DimLibNetworking {
    public static final Logger LOGGER = LoggerFactory.getLogger(DimLibNetworking.class);
    
    public static record DimSyncPacket(
        CompoundTag dimIdToTypeIdTag
    ) implements FabricPacket {
        public static final PacketType<DimSyncPacket> TYPE = PacketType.create(
            new ResourceLocation("dimlib", "dim_sync"),
            DimSyncPacket::read
        );
        
        public static DimSyncPacket read(FriendlyByteBuf buf) {
            CompoundTag compoundTag = buf.readNbt();
            return new DimSyncPacket(compoundTag);
        }
        
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(dimIdToTypeIdTag);
        }
        
        @Override
        public PacketType<?> getType() {
            return TYPE;
        }
        
        public static DimSyncPacket createPacket(MinecraftServer server) {
            RegistryAccess registryManager = server.registryAccess();
            Registry<DimensionType> dimensionTypes = registryManager.registryOrThrow(Registries.DIMENSION_TYPE);
            
            CompoundTag dimIdToDimTypeId = new CompoundTag();
            for (ServerLevel world : server.getAllLevels()) {
                ResourceKey<Level> dimId = world.dimension();
                
                DimensionType dimType = world.dimensionType();
                ResourceLocation dimTypeId = dimensionTypes.getKey(dimType);
                
                if (dimTypeId == null) {
                    LOGGER.error("Cannot find dimension type for {}", dimId.location());
                    LOGGER.error(
                        "Registered dimension types {}", dimensionTypes.keySet()
                    );
                    dimTypeId = BuiltinDimensionTypes.OVERWORLD.location();
                }
                
                dimIdToDimTypeId.putString(
                    dimId.location().toString(),
                    dimTypeId.toString()
                );
            }
            
            return new DimSyncPacket(dimIdToDimTypeId);
        }
        
        public ImmutableMap<ResourceKey<Level>, ResourceKey<DimensionType>> toMap() {
            CompoundTag tag = dimIdToTypeIdTag();
            
            ImmutableMap.Builder<ResourceKey<Level>, ResourceKey<DimensionType>> builder =
                new ImmutableMap.Builder<>();
            
            for (String key : tag.getAllKeys()) {
                ResourceKey<Level> dimId = ResourceKey.create(
                    Registries.DIMENSION,
                    new ResourceLocation(key)
                );
                String dimTypeId = tag.getString(key);
                ResourceKey<DimensionType> dimType = ResourceKey.create(
                    Registries.DIMENSION_TYPE,
                    new ResourceLocation(dimTypeId)
                );
                builder.put(dimId, dimType);
            }
            
            return builder.build();
        }
        
        @Environment(EnvType.CLIENT)
        public void handleOnNetworkingThread(ClientGamePacketListener listener) {
            LOGGER.info(
                "Client received dimension info\n{}",
                dimIdToTypeIdTag.getAllKeys().stream()
                    .map(k -> k + " - " + dimIdToTypeIdTag.getString(k))
                    .collect(Collectors.joining("\n"))
            );
            
            var dimIdToDimType = this.toMap();
            ClientDimensionInfo.accept(dimIdToDimType);
            ((IClientPacketListener) listener).ip_setLevels(dimIdToDimType.keySet());
            
            Minecraft.getInstance().execute(() -> {
                DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.invoker().run(
                    ClientDimensionInfo.getDimensionIds()
                );
            });
        }
    }
    
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            DimSyncPacket.TYPE.getId(),
            (client, handler, buf, responseSender) -> {
                // directly handle in networking thread
                // it does not touch world state, so it's safe
                DimSyncPacket dimSyncPacket = DimSyncPacket.TYPE.read(buf);
                dimSyncPacket.handleOnNetworkingThread(handler);
            }
        );
    }
}
