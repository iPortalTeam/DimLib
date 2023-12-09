package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ClientDimensionInfo {
    public static ImmutableSet<ResourceKey<Level>> dimensionIds;
    public static ImmutableMap<ResourceKey<Level>, ResourceKey<DimensionType>> dimensionIdToType;
    
    public static Set<ResourceKey<Level>> getDimensionIds() {
        if (dimensionIds == null) {
            throw new IllegalStateException("The dimension info has not been synced yet");
        }
        return dimensionIds;
    }
    
    public static Map<ResourceKey<Level>, ResourceKey<DimensionType>> getDimensionIdToType() {
        if (dimensionIdToType == null) {
            throw new IllegalStateException("The dimension info has not been synced yet");
        }
        return dimensionIdToType;
    }
    
    // this is invoked on networking thread (earlier than in client thread)
    static void acceptSyncPacket(DimLibNetworking.DimSyncPacket dimSyncPacket) {
        CompoundTag tag = dimSyncPacket.dimIdToDimTypeId();
        
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
        
        dimensionIdToType = builder.build();
        dimensionIds = dimensionIdToType.keySet();
    }
    
    // this is not API
    public static void cleanup() {
    
    }
}
