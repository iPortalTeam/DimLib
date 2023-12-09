package qouteall.dimlib.ducks;

import net.minecraft.resources.ResourceLocation;

public interface IMappedRegistry {
    public boolean dimlib_forceRemove(ResourceLocation id);
    
    boolean dimlib_getIsFrozen();
    
    /**
     * Note: un-freeze is only safe when no place use its holder.
     */
    void dimlib_setIsFrozen(boolean cond);
}
