package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forgets a placed container's client-side locks when the local player
 * breaks it. Half of the pruning story for location-keyed locks (the other
 * half trims on open, in {@code LockedSlots.currentContainerKey}).
 *
 * <p>The identity is computed at HEAD, while the block state still exists,
 * so a double chest resolves to its canonical half; the prune runs at RETURN
 * only if vanilla reports the break succeeded, so a swing that did not break
 * anything drops nothing. Chests broken by other players are not observed
 * here; their keys age out on the next open, or never, and cost a line each.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeDestroyBlockMixin {

    @Unique
    private String inventoryplus$breakingKey;

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void inventoryplus$captureKey(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        inventoryplus$breakingKey = ContainerIdentity.forBlock(pos).key();
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void inventoryplus$pruneOnBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        String key = inventoryplus$breakingKey;
        inventoryplus$breakingKey = null;
        if (key != null && cir.getReturnValueZ()) LockedSlots.pruneContainerAt(key);
    }
}
