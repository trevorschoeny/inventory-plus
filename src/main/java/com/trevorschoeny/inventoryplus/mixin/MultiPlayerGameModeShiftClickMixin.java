package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels shift-click and Q-drop packets at the source so they never
 * reach the server. This is the multiplayer-correct pattern for
 * locked-slot protection — IPN's well-tested approach.
 *
 * <h3>Why packet cancellation, not destination mixin</h3>
 *
 * The companion {@link AbstractContainerMenuMoveItemStackToMixin} can
 * only protect locked slots when its bytecode runs. In single-player
 * the integrated server shares the JVM, so the mixin fires on both
 * Render and Server threads → full protection. On a dedicated server
 * (separate JVM, IP not installed), the server runs vanilla
 * {@code moveItemStackTo} unblocked, places the item in the locked
 * slot, and syncs back → lock defeated + flicker.
 *
 * <p>This mixin sidesteps the problem by cancelling the click packet
 * before it leaves the client. The server never sees the click, so
 * there's nothing to reconcile and no server reality to override the
 * client prediction. Works identically on single-player, LAN-hosted,
 * and dedicated-server multiplayer.
 *
 * <h3>What gets cancelled</h3>
 *
 * <ul>
 *   <li><b>QUICK_MOVE with locked source</b> — items would leave a
 *       locked slot. Always cancel.</li>
 *   <li><b>THROW with locked source</b> — Q-drop would empty a locked
 *       slot. Always cancel.</li>
 *   <li><b>QUICK_MOVE where any predicted destination is locked</b> —
 *       items would enter a locked slot. Cancel the whole click
 *       (all-or-nothing, matching IPN). Trade-off: if some destinations
 *       are unlocked and some are locked, vanilla would normally fill
 *       the unlocked ones, but we cancel everything. Player can still
 *       manually move items to the unlocked slots.</li>
 * </ul>
 *
 * <p>All other click types ({@code PICKUP}, {@code SWAP}, {@code CLONE},
 * {@code PICKUP_ALL}, {@code QUICK_CRAFT}) pass through unmodified —
 * manual cursor interaction is allowed by design.
 *
 * <h3>Destination prediction heuristic</h3>
 *
 * Vanilla's per-screen-handler {@code quickMoveStack} picks
 * destinations from a screen-specific range. Precise prediction
 * requires hardcoded per-screen tables (IPN's {@code qMoveSlotMapping}
 * pattern). Instead, this mixin uses a simpler heuristic:
 *
 * <ol>
 *   <li>Iterate every locked slot in the menu (locked slots are always
 *       player slots by definition of {@link LockedSlots#isLockable}).</li>
 *   <li>Skip if the locked slot is in the same "half" as the source
 *       (main↔main, hotbar↔hotbar) — vanilla never shift-clicks within
 *       a half.</li>
 *   <li>Check {@code canMergeOrPlace} — the slot is a valid target if
 *       empty + {@code mayPlace} returns true, or non-empty with the
 *       same item and room. {@code mayPlace} naturally handles
 *       slot-type compatibility (e.g., stone in armor slot returns
 *       false).</li>
 * </ol>
 *
 * <p>This catches the merge-into-existing case that
 * {@link AbstractContainerMenuMoveItemStackToMixin} can't (vanilla's
 * first pass in {@code moveItemStackTo} skips mayPlace and goes
 * straight to merge logic). Net protection is strictly broader than
 * the @WrapOperation mixin alone.
 *
 * <h3>Auto-pickup is not covered here</h3>
 *
 * This mixin only catches CLICK-driven movement. Vanilla auto-pickup
 * (mob drops, ground items) runs server-side via {@code Inventory.add}
 * → {@code getFreeSlot}, and is covered by
 * {@link InventoryGetFreeSlotMixin} in single-player only. Multiplayer
 * auto-pickup into locked slots remains a hole pending a server-side
 * companion mod — see DEFERRED.md.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeShiftClickMixin {

    @Inject(
            method = "handleInventoryMouseClick(IIILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void inventoryplus$cancelLockedShiftClick(
            int containerId, int slotId, int button, ClickType clickType,
            Player player, CallbackInfo ci) {
        // Only gate shift-click (QUICK_MOVE) and Q-drop (THROW).
        if (clickType != ClickType.QUICK_MOVE && clickType != ClickType.THROW) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot source = menu.slots.get(slotId);

        // Source-block: never let items leave a locked slot.
        if (LockedSlots.isLockedSlot(source)) {
            ci.cancel();
            return;
        }

        // Destination-block: only relevant for shift-click. THROW always
        // discards outside the menu.
        if (clickType == ClickType.QUICK_MOVE
                && wouldShiftClickTouchLockedSlot(menu, source)) {
            ci.cancel();
        }
    }

    /**
     * Returns true if vanilla's shift-click iteration would attempt to
     * place items into any locked slot. See class javadoc for the
     * heuristic.
     */
    private boolean wouldShiftClickTouchLockedSlot(AbstractContainerMenu menu, Slot source) {
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()) return false;

        // Source classification — only "player half" sources need
        // same-half filtering. External-container sources skip the
        // filter entirely (their destination is the full player inv).
        boolean sourceIsPlayer = LockedSlots.isLockable(source);
        int sourceCS = sourceIsPlayer ? source.getContainerSlot() : -1;
        boolean sourceInMain = sourceIsPlayer && sourceCS >= 9 && sourceCS <= 35;
        boolean sourceInHotbar = sourceIsPlayer && sourceCS >= 0 && sourceCS <= 8;

        for (Slot dest : menu.slots) {
            if (dest == source) continue;
            if (!LockedSlots.isLockedSlot(dest)) continue;
            int destCS = dest.getContainerSlot();
            boolean destInMain = destCS >= 9 && destCS <= 35;
            boolean destInHotbar = destCS >= 0 && destCS <= 8;

            // Same-half filter — vanilla doesn't shift-click within a
            // half (main inv → main inv etc.). Avoids false positives
            // when a locked slot lives in the same half as the source.
            if (sourceInMain && destInMain) continue;
            if (sourceInHotbar && destInHotbar) continue;

            if (canMergeOrPlace(dest, sourceStack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vanilla-equivalent check for "could moveItemStackTo place this
     * stack into this slot?". Used for prediction; doesn't mutate.
     *
     * <p>Calling {@code slot.mayPlace} from here does NOT trigger
     * {@link AbstractContainerMenuMoveItemStackToMixin}'s
     * {@code @WrapOperation} — that wrap is scoped to the call site
     * inside {@code moveItemStackTo}, not the method definition.
     */
    private boolean canMergeOrPlace(Slot slot, ItemStack stack) {
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) {
            return slot.mayPlace(stack);
        }
        if (!ItemStack.isSameItemSameComponents(inSlot, stack)) return false;
        return inSlot.getCount() < Math.min(slot.getMaxStackSize(inSlot), stack.getMaxStackSize());
    }
}
