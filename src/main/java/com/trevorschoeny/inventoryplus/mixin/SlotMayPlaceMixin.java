package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.ManualPlaceOverride;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blocks vanilla shift-click + shift-double-click placement into
 * locked player slots by returning false from {@link Slot#mayPlace}.
 *
 * <h3>Why mayPlace</h3>
 *
 * Vanilla's shift-click ({@code AbstractContainerMenu.moveItemStackTo})
 * iterates candidate destination slots and calls
 * {@link Slot#mayPlace} on each. If mayPlace returns false, vanilla
 * skips that slot. By returning false for locked slots, we keep
 * vanilla from depositing items into them.
 *
 * <p>Same mechanism catches shift-double-click consolidation
 * ({@link net.minecraft.world.inventory.ClickType#PICKUP_ALL}) — vanilla
 * iterates slots looking for stacks to consolidate into the cursor;
 * placement back into a locked slot is blocked by mayPlace.
 *
 * <p>Auto-pickup (server-side {@code Inventory.add}) does NOT go through
 * Slot.mayPlace — it accesses the items list directly. The
 * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsCorrector}
 * handles that case via post-hoc correction.
 *
 * <h3>Manual cursor placement exception</h3>
 *
 * Player-driven left-click pickup/place uses the same
 * {@link Slot#mayPlace} hook. To allow manual cursor placement into
 * locked slots (per spec §"Locked-slot rules": "Manual cursor
 * interaction is allowed"), we use a thread-local override
 * ({@link ManualPlaceOverride}) set by the click event listener for
 * the duration of a manual {@code PICKUP} click. If the override is
 * active, mayPlace returns its normal value (allow).
 */
@Mixin(Slot.class)
public abstract class SlotMayPlaceMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$blockIfLocked(ItemStack stack,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (ManualPlaceOverride.isActive()) return; // manual click — let vanilla decide

        Slot self = (Slot) (Object) this;
        if (LockedSlots.isLockedSlot(self)) {
            cir.setReturnValue(false);
        }
    }
}
