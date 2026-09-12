package qouteall.dimlib.ducks;

import net.minecraft.resources.Identifier;

public interface IMappedRegistry {
    public boolean dimlib_forceRemove(Identifier id);

    boolean dimlib_getIsFrozen();

    /**
     * Note: un-freeze is only safe when no place use its holder.
     */
    void dimlib_setIsFrozen(boolean cond);
}
