package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.mixin.client.IClientPacketListener;

public class DimLibNetworking {
    public static final Logger LOGGER = LoggerFactory.getLogger(DimLibNetworking.class);
    
    public static record DimSyncPacket(
        CompoundTag dimIdToTypeIdTag
    ) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<DimSyncPacket> TYPE =
            new Type<>(ResourceLocation.parse("dimlib:dim_sync"));
        
        public static final StreamCodec<FriendlyByteBuf, DimSyncPacket> CODEC =
            StreamCodec.of((b, p) -> p.write(b), DimSyncPacket::read);
        
        public static DimSyncPacket read(FriendlyByteBuf buf) {
            CompoundTag compoundTag = buf.readNbt();
            return new DimSyncPacket(compoundTag);
        }
        
        public void write(FriendlyByteBuf buf) {
            buf.writeNbt(dimIdToTypeIdTag);
        }
        
        public static DimSyncPacket createPacket(MinecraftServer server) {
            RegistryAccess registryManager = server.registryAccess();
            Registry<DimensionType> dimensionTypes = registryManager.lookupOrThrow(Registries.DIMENSION_TYPE);
            
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
                    ResourceLocation.parse(key)
                );
                String dimTypeId = tag.getString(key);
                ResourceKey<DimensionType> dimType = ResourceKey.create(
                    Registries.DIMENSION_TYPE,
                    ResourceLocation.parse(dimTypeId)
                );
                builder.put(dimId, dimType);
            }
            
            return builder.build();
        }
        
        @Environment(EnvType.CLIENT)
        public void handle(ClientGamePacketListener listener) {
            Validate.isTrue(
                Minecraft.getInstance().isSameThread(),
                "Not running in client thread"
            );
            
            LOGGER.info(
                "Client received dimension info\n{}",
                String.join("\n", dimIdToTypeIdTag.getAllKeys())
            );
            
            var dimIdToDimType = this.toMap();
            ClientDimensionInfo.accept(dimIdToDimType);
            ((IClientPacketListener) listener).ip_setLevels(dimIdToDimType.keySet());
            
            DimensionAPI.CLIENT_DIMENSION_UPDATE_EVENT.invoker().run(
                ClientDimensionInfo.getDimensionIds()
            );
        }
        
        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
    
    public static void init() {
        PayloadTypeRegistry.playS2C().register(
            DimSyncPacket.TYPE, DimSyncPacket.CODEC
        );
    }
    
    @SuppressWarnings("resource")
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            DimSyncPacket.TYPE,
            (p, c) -> {
                // it's now handled in client thread, not networking thread
                p.handle(c.client().getConnection());
            }
        );
    }
}
