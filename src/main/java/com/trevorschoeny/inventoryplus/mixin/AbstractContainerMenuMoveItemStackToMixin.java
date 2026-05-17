package com.trevorschoeny.inventoryplus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Blocks shift-click (and other moveItemStackTo-driven) placement into
 * locked player slots.
 *
 * <h3>Why scoped to moveItemStackTo</h3>
 *
 * Vanilla's {@code Slot.mayPlace} is consulted from multiple places:
 *
 * <ul>
 *   <li>{@code AbstractContainerMenu.moveItemStackTo} — shift-click
 *       destination iteration. We DO want to block here.</li>
 *   <li>{@code AbstractContainerMenu.doClick} for {@link
 *       net.minecraft.world.inventory.ClickType#PICKUP} — manual
 *       cursor placement. We DON'T want to block here (per spec,
 *       "manual cursor interaction is allowed").</li>
 * </ul>
 *
 * <p>An earlier implementation used a thread-local override flag set
 * during the click event and consumed by a {@code Slot.mayPlace}
 * mixin. That flag failed to cross the client-render-thread / server-
 * tick-thread boundary in single-player (set on Render thread by the
 * click listener, but not visible to the Server thread that re-runs
 * the click), causing a client-predict-then-server-reject flicker on
 * shift-click and similar.
 *
 * <p>{@code @WrapOperation} on the {@code mayPlace} call inside
 * {@code moveItemStackTo} scopes the block exactly to the shift-click
 * path — no thread-local plumbing needed, manual placement unaffected.
 *
 * <h3>Limitation matched to IPN</h3>
 *
 * Vanilla {@code moveItemStackTo} has two passes — first it tries to
 * merge into existing same-item destinations (which does NOT call
 * mayPlace), then it tries empty destinations (which does). The
 * merge-into-existing pass bypasses our block. Shift-clicking an item
 * type into a locked slot that already contains the same item with
 * room to grow will still merge. The post-hoc
 * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsCorrector}
 * is gated to "snapshot was empty" only, so it doesn't catch this
 * either. Matches IPN's behavior; filed for follow-up if it bites.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMoveItemStackToMixin {

    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean inventoryplus$blockLockedDestination(Slot slot, ItemStack stack,
                                                          Operation<Boolean> original) {
        if (LockedSlots.isLockedSlot(slot)) {
            return false;
        }
        return original.call(slot, stack);
    }
}
