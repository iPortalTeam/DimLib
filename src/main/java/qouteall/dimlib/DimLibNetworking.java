package qouteall.dimlib;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
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

public class DimLibNetworking {
    public static final Logger LOGGER = LoggerFactory.getLogger(DimLibNetworking.class);
    
    public static record DimSyncPacket(
        CompoundTag dimIdToDimTypeId
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
            buf.writeNbt(dimIdToDimTypeId);
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
        
        @Environment(EnvType.CLIENT)
        public void handleOnNetworkingThread(ClientGamePacketListener listener) {
            ClientDimensionInfo.acceptSyncPacket(this);
        }
    }
}
