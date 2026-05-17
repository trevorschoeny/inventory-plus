package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the two halves of a {@link CompoundContainer} (vanilla
 * double-chest wrapper) for {@link
 * com.trevorschoeny.inventoryplus.sort.ContainerIdentity} to derive a
 * stable block-position identity.
 *
 * <p>Vanilla keeps {@code container1} and {@code container2} private.
 * For sort persistence, we need a {@code BlockPos}-based identity that
 * survives world reloads; using one of the halves' chest block entity
 * gives that. Either half works; identity uses container1 by
 * convention so both halves of the same double chest resolve to the
 * same key regardless of which half the player clicked.
 */
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {

    @Accessor("container1")
    Container inventoryplus$getContainer1();

    @Accessor("container2")
    Container inventoryplus$getContainer2();
}
