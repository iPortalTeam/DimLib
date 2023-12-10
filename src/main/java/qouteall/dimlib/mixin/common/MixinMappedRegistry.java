package qouteall.dimlib.mixin.common;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.dimlib.ducks.IMappedRegistry;

import java.util.List;
import java.util.Map;

@Mixin(MappedRegistry.class)
public abstract class MixinMappedRegistry<T> implements IMappedRegistry {
    @Shadow
    @Final
    private Map<ResourceLocation, Holder.Reference<T>> byLocation;
    
    @Shadow
    @Final
    private static Logger LOGGER;
    
    @Shadow
    public abstract @Nullable T byId(int id);
    
    @Shadow
    @Final
    private ObjectList<Holder.Reference<T>> byId;
    
    @Shadow
    @Final
    private Reference2IntMap<T> toId;
    
    @Shadow
    @Final
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;
    
    @Shadow
    @Final
    private ResourceKey<? extends Registry<T>> key;
    
    @Shadow
    @Final
    private Map<T, Holder.Reference<T>> byValue;
    
    @Shadow
    @Final
    private Map<T, Lifecycle> lifecycles;
    
    @Shadow
    private @Nullable List<Holder.Reference<T>> holdersInOrder;
    
    @Shadow
    private boolean frozen;
    
    @Override
    public boolean dimlib_getIsFrozen() {
        return frozen;
    }
    
    @Override
    public void dimlib_setIsFrozen(boolean cond) {
        frozen = cond;
    }
    
    @Override
    public boolean dimlib_forceRemove(ResourceLocation id) {
        Holder.Reference<T> holder = byLocation.remove(id);
        
        if (holder == null) {
            return false;
        }
        
        T value = holder.value();
        
        int intId = toId.getInt(value);
        
        if (intId == -1) {
            LOGGER.error("[ImmPtl] missing integer id for {}", value);
        }
        else {
            toId.removeInt(value);
            byId.set(intId, null);
        }
        
        byKey.remove(ResourceKey.create(key, id));
        byValue.remove(value);
        lifecycles.remove(value);
        
        holdersInOrder = null;
        
        return true;
    }
    
}
