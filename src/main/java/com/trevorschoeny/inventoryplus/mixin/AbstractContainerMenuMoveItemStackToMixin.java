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
 * Makes vanilla {@code moveItemStackTo} see locked slots as
 * "permanently full" — both passes (merge + empty) skip them naturally
 * and vanilla picks the next slot using its own per-screen-handler
 * iteration order.
 *
 * <h3>Two wraps, one goal</h3>
 *
 * Vanilla {@code moveItemStackTo} has two passes:
 *
 * <ol>
 *   <li><b>Merge pass</b> — for each destination slot, calls
 *       {@code slot.getItem()} and tries to merge same-item stacks.
 *       Does NOT consult {@code mayPlace}.</li>
 *   <li><b>Empty pass</b> — for each destination slot, calls
 *       {@code slot.getItem()} to check emptiness, then
 *       {@code slot.mayPlace(stack)} to validate placement.</li>
 * </ol>
 *
 * To make vanilla skip locked slots in BOTH passes:
 *
 * <ul>
 *   <li>{@code @WrapOperation} on {@code getItem} returns
 *       {@link ItemStack#EMPTY} for locked slots → merge pass sees
 *       no item to merge into → skips. Empty pass sees the slot as
 *       empty → falls through to mayPlace check.</li>
 *   <li>{@code @WrapOperation} on {@code mayPlace} returns
 *       {@code false} for locked slots → empty pass refuses to
 *       place. (Without this, empty pass would place into the locked
 *       slot since getItem said it was empty.)</li>
 * </ul>
 *
 * <p>Result: vanilla's loop simply doesn't see locked slots as valid
 * targets. It continues iterating to the next slot in its own order
 * (which respects per-screen-handler {@code quickMoveStack} ranges
 * and direction — e.g., crafting inputs are never targeted because
 * {@code InventoryMenu.quickMoveStack} doesn't include them in the
 * destination range).
 *
 * <h3>Why scoped to moveItemStackTo and not Slot.mayPlace directly</h3>
 *
 * {@code Slot.mayPlace} is also consulted from
 * {@code AbstractContainerMenu.doClick} for {@link
 * net.minecraft.world.inventory.ContainerInput#PICKUP} — manual cursor
 * placement. We don't want to block manual placement (per spec,
 * "manual cursor interaction is allowed"). Scoping the wrap to
 * {@code moveItemStackTo} keeps the block on shift-click only.
 *
 * <h3>Cross-thread firing (single-player + LAN)</h3>
 *
 * In single-player (and LAN-hosted games) the integrated server
 * shares the JVM with the client; these wraps fire on BOTH Render
 * and Server threads. Cross-thread correctness depends on {@link
 * LockedSlots#isLockable} using UUID equality — see that method's
 * javadoc.
 *
 * <h3>Dedicated-server multiplayer limitation</h3>
 *
 * On a dedicated server (separate JVM, IP not installed), these
 * wraps only fire on the client's Render thread. Server runs vanilla
 * unblocked → places items in locked slots → sync overrides client
 * prediction → lock defeated + flicker. Source-block via
 * {@link MultiPlayerGameModeShiftClickMixin} still works in
 * multiplayer (cancels the click packet at the source). For
 * destination-block in multiplayer, see the IPN-style cancel path
 * in that same mixin.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMoveItemStackToMixin {

    @WrapOperation(
            method = "moveItemStackTo",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack inventoryplus$treatLockedAsEmptyForMerge(Slot slot,
                                                                Operation<ItemStack> original) {
        if (LockedSlots.isLockedSlot(slot)) {
            // Merge pass: sees empty → skips.
            // Empty pass: sees empty → falls through to mayPlace,
            //   which our other wrap rejects → also skips.
            return ItemStack.EMPTY;
        }
        return original.call(slot);
    }

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
