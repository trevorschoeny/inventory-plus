package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks double-click "collect all of type" (vanilla {@code PICKUP_ALL}) from
 * draining a locked slot. Per the locked-slot rules, double-click consolidation
 * is treated as automation — it must skip locked slots like shift-click does.
 *
 * <h3>Why a separate enforcement point</h3>
 *
 * The PICKUP_ALL path does <b>not</b> go through {@code moveItemStackTo} (which
 * the shift-click wrap targets); it's a self-contained loop in {@code clicked}
 * that gates each candidate slot on {@code canTakeItemForPickAll}. Returning
 * false there makes the consolidation skip the slot.
 *
 * <p>Client-side (IP). Covers player + ender locks (both the render and
 * integrated-server threads via {@link LockedSlots#isLockedSlot}'s cross-thread
 * dispatch) and placed-container locks as client prediction. On the
 * integrated-server thread {@code isLockedSlot} defers container slots to the
 * companion's server authority, so this no-ops there — matching the shift-click
 * split exactly.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class LockedSlotPickAllMixin {

    @Inject(method = "canTakeItemForPickAll", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$blockPickAllFromLocked(
            ItemStack stack, Slot slot, CallbackInfoReturnable<Boolean> cir) {
        if (LockedSlots.isLockedSlot(slot)) {
            cir.setReturnValue(false);
        }
    }
}
