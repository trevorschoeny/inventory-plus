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
 * <p>{@code @WrapOperation} on the {@code mayPlace} call inside
 * {@code moveItemStackTo} scopes the block exactly to the shift-click
 * path — manual cursor placement is naturally unaffected because that
 * code path doesn't traverse {@code moveItemStackTo}.
 *
 * <h3>Cross-thread firing (single-player only)</h3>
 *
 * In single-player the integrated server shares the JVM with the
 * client; this mixin fires on BOTH the Render thread (client predict)
 * and the Server thread (authoritative). The cross-thread correctness
 * depends on {@link
 * com.trevorschoeny.inventoryplus.lockedslots.LockedSlots#isLockable}
 * using UUID equality (not reference equality) for the player-inventory
 * check — see that method's javadoc for the why.
 *
 * <h3>Limitation — merge-into-existing pass</h3>
 *
 * Vanilla {@code moveItemStackTo} has two passes — first it tries to
 * merge into existing same-item destinations (which does NOT call
 * mayPlace), then it tries empty destinations (which does). The
 * merge-into-existing pass bypasses our block. Shift-clicking an item
 * type into a locked slot that already contains the same item with
 * room to grow will still merge. Matches IPN's behavior.
 *
 * <h3>Limitation — dedicated-server multiplayer</h3>
 *
 * On a dedicated server (separate JVM, IP not loaded), this mixin
 * fires on the client's Render thread but NOT server-side. Server
 * places the item, syncs back, client gets the authoritative state →
 * flicker on shift-click into locked slot. See "Locked Slots —
 * dedicated-server multiplayer support" in
 * {@code @ Inventory Plus/2 | Working Files/DEFERRED.md} for IPN's
 * packet-cancellation pattern that would fix this.
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
