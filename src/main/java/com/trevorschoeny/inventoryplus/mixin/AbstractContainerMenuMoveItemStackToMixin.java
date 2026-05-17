package com.trevorschoeny.inventoryplus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Defense-in-depth block on locked-slot destinations inside
 * {@code moveItemStackTo}.
 *
 * <h3>Role within the locked-slot mixin chain</h3>
 *
 * Primary protection is now {@link MultiPlayerGameModeShiftClickMixin},
 * which cancels shift-click packets at the source (IPN's pattern,
 * works in single + multiplayer). This mixin is the secondary line:
 *
 * <ul>
 *   <li>Catches any {@code moveItemStackTo} call that bypasses the
 *       client packet path — e.g., other mods invoking shift-click
 *       semantics directly without going through
 *       {@code MultiPlayerGameMode.handleInventoryMouseClick}.</li>
 *   <li>In single-player, runs on both Render and Server threads
 *       (UUID equality in {@link
 *       com.trevorschoeny.inventoryplus.lockedslots.LockedSlots#isLockable})
 *       so the lock is respected even if a mixin further upstream
 *       didn't fire.</li>
 * </ul>
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
 * <p>{@code @WrapOperation} on the {@code mayPlace} call inside
 * {@code moveItemStackTo} scopes the block exactly to the shift-click
 * path — manual cursor placement is naturally unaffected because that
 * code path doesn't traverse {@code moveItemStackTo}.
 *
 * <h3>Limitation — merge-into-existing pass</h3>
 *
 * Vanilla {@code moveItemStackTo} has two passes — first it tries to
 * merge into existing same-item destinations (which does NOT call
 * mayPlace), then it tries empty destinations (which does). The
 * merge-into-existing pass bypasses this mixin. That gap is now
 * closed by {@link MultiPlayerGameModeShiftClickMixin}'s
 * destination prediction, which handles both empty AND
 * merge-with-room cases via {@code canMergeOrPlace}.
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
            // Diagnostic at DEBUG — verified working on both Render and
            // Server threads in single-player (UUID-equality in isLockable
            // makes the check cross-thread-safe). Bump to INFO temporarily
            // when debugging multiplayer where the server JVM won't have
            // this mixin and the block can only fire on Render thread.
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] mixin blocked moveItemStackTo into container-slot {}",
                    slot.getContainerSlot());
            return false;
        }
        return original.call(slot, stack);
    }
}
