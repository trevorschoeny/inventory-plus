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
 * Locked-slot protection for click-driven moves, applied at the
 * client's click-dispatch entry point so it works identically in
 * single-player, LAN-hosted, and dedicated-server multiplayer.
 *
 * <h3>Why this layer is the right one</h3>
 *
 * On a dedicated multiplayer server (separate JVM, IP not installed),
 * any server-side mixin in this mod can't run. The companion
 * {@link AbstractContainerMenuMoveItemStackToMixin} only fires
 * server-side when the JVM is shared (single-player). To get the lock
 * respected across all environments, the work has to happen on the
 * client before the click packet leaves — by either cancelling the
 * click outright, or synthesizing an equivalent move out of click
 * types the server universally respects.
 *
 * <h3>Three cases handled</h3>
 *
 * <ol>
 *   <li><b>QUICK_MOVE with locked source</b> — cancel. Items must not
 *       leave a locked slot via shift-click.</li>
 *   <li><b>THROW with locked source</b> — cancel. Q-drop must not
 *       empty a locked slot.</li>
 *   <li><b>QUICK_MOVE where some destinations are locked</b> — cancel
 *       the original shift-click, then synthesize the move using
 *       PICKUP clicks that skip locked slots. Mirrors vanilla's
 *       two-pass behavior (merge-into-existing first, then empty
 *       slots) with locked slots filtered out.</li>
 * </ol>
 *
 * <p>All other click types ({@code PICKUP}, {@code SWAP}, {@code CLONE},
 * {@code PICKUP_ALL}, {@code QUICK_CRAFT}) pass through unmodified.
 * Manual cursor interaction is allowed by design, and the synthesized
 * PICKUP clicks below recurse through this method without ever
 * matching the QUICK_MOVE/THROW gate.
 *
 * <h3>Why PICKUP-click synthesis instead of letting vanilla iterate</h3>
 *
 * In single-player the companion mixin would let vanilla's
 * {@code moveItemStackTo} naturally skip locked slots and fill the
 * unlocked ones — better UX than cancelling. But that same behavior
 * is impossible on a dedicated server (the server doesn't run the
 * mixin). To keep the UX consistent across environments, the client
 * does the iteration itself and issues individual {@code PICKUP}
 * clicks for each step. Those PICKUP packets are universally
 * server-respected, so the server moves items to the same unlocked
 * destinations the client picked.
 *
 * <h3>Destination iteration order</h3>
 *
 * Without per-screen-handler knowledge of vanilla's
 * {@code quickMoveStack} preferences, the synthesis iterates
 * {@code menu.slots} in index order. Items end up in non-locked
 * destinations correctly; the specific slot picked may differ from
 * what vanilla would have chosen by 1–2 positions in some screens.
 * Acceptable trade-off for not maintaining per-screen tables.
 *
 * <h3>What's still NOT covered</h3>
 *
 * Vanilla auto-pickup (mob drops, ground items) runs server-side via
 * {@code Inventory.add} → {@code getFreeSlot}, and is covered by
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
    private void inventoryplus$handleLockedShiftClick(
            int containerId, int slotId, int button, ClickType clickType,
            Player player, CallbackInfo ci) {
        // Only gate shift-click (QUICK_MOVE) and Q-drop (THROW).
        // PICKUP / SWAP / CLONE / PICKUP_ALL / QUICK_CRAFT all pass
        // through — including the synthesized PICKUPs this mixin
        // recurses into below.
        if (clickType != ClickType.QUICK_MOVE && clickType != ClickType.THROW) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot source = menu.slots.get(slotId);

        // Source-block: never let items leave a locked slot. Applies to
        // both QUICK_MOVE and THROW.
        if (LockedSlots.isLockedSlot(source)) {
            ci.cancel();
            return;
        }

        // Destination-block applies to QUICK_MOVE only. THROW discards
        // outside the menu so it never touches a destination slot.
        if (clickType != ClickType.QUICK_MOVE) return;

        if (!wouldShiftClickTouchLockedSlot(menu, source)) {
            // No locked-destination conflict — let vanilla shift-click
            // proceed normally. Faster + uses vanilla's exact iteration
            // order for the common case.
            return;
        }

        // Locked destination(s) in the way — synthesize a shift-click
        // out of PICKUP clicks that skip locked slots.
        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        synthesizeShiftClick(self, containerId, source, menu, player);
        ci.cancel();
    }

    /**
     * Cancel + replace shift-click: pick up the source onto the cursor,
     * iterate destinations skipping locked slots (merge pass, then
     * empty-slot pass, mirroring vanilla's two-pass {@code
     * moveItemStackTo}), and put any leftover items back on the source.
     *
     * <p>Each step is a {@link ClickType#PICKUP} click — universally
     * server-respected, no server-side mod required.
     */
    private void synthesizeShiftClick(MultiPlayerGameMode self, int containerId,
                                       Slot source, AbstractContainerMenu menu, Player player) {
        // Phase 1: pickup source onto cursor.
        self.handleInventoryMouseClick(containerId, source.index, 0,
                ClickType.PICKUP, player);

        if (menu.getCarried().isEmpty()) return;

        // Source classification for same-half filtering (see
        // wouldShiftClickTouchLockedSlot for rationale).
        boolean sourceIsPlayer = LockedSlots.isLockable(source);
        int sourceCS = sourceIsPlayer ? source.getContainerSlot() : -1;
        boolean sourceInMain = sourceIsPlayer && sourceCS >= 9 && sourceCS <= 35;
        boolean sourceInHotbar = sourceIsPlayer && sourceCS >= 0 && sourceCS <= 8;

        // Phase 2a: merge pass — fill same-item destinations with room.
        for (Slot dest : menu.slots) {
            if (menu.getCarried().isEmpty()) break;
            if (!isEligibleDestination(dest, source, sourceInMain, sourceInHotbar)) continue;
            ItemStack destStack = dest.getItem();
            if (destStack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(destStack, menu.getCarried())) continue;
            if (destStack.getCount() >= dest.getMaxStackSize(destStack)) continue;
            self.handleInventoryMouseClick(containerId, dest.index, 0,
                    ClickType.PICKUP, player);
        }

        // Phase 2b: empty pass — place remaining cursor into first
        // eligible empty slot.
        for (Slot dest : menu.slots) {
            if (menu.getCarried().isEmpty()) break;
            if (!isEligibleDestination(dest, source, sourceInMain, sourceInHotbar)) continue;
            if (!dest.getItem().isEmpty()) continue;
            if (!dest.mayPlace(menu.getCarried())) continue;
            self.handleInventoryMouseClick(containerId, dest.index, 0,
                    ClickType.PICKUP, player);
        }

        // Phase 3: if cursor still has items (no destination found, or
        // not all items fit), put them back on the source. The lock UI
        // doesn't lie about the underlying state.
        if (!menu.getCarried().isEmpty()) {
            self.handleInventoryMouseClick(containerId, source.index, 0,
                    ClickType.PICKUP, player);
        }
    }

    /**
     * Common filter for synthetic-shift-click destinations.
     *
     * <ul>
     *   <li>Skip the source itself.</li>
     *   <li>Skip locked slots — the whole point of this exercise.</li>
     *   <li>Skip same-half player slots (main↔main, hotbar↔hotbar) —
     *       vanilla never shift-clicks within a half.</li>
     * </ul>
     */
    private boolean isEligibleDestination(Slot dest, Slot source,
                                          boolean sourceInMain, boolean sourceInHotbar) {
        if (dest == source) return false;
        if (LockedSlots.isLockedSlot(dest)) return false;
        if (!LockedSlots.isLockable(dest)) {
            // External-container slot — always eligible (vanilla
            // shift-clicks player↔external freely).
            return true;
        }
        int destCS = dest.getContainerSlot();
        boolean destInMain = destCS >= 9 && destCS <= 35;
        boolean destInHotbar = destCS >= 0 && destCS <= 8;
        if (sourceInMain && destInMain) return false;
        if (sourceInHotbar && destInHotbar) return false;
        return true;
    }

    /**
     * Returns true if vanilla's shift-click iteration would attempt to
     * place items into any locked slot. Detection only — synthesis is
     * handled by {@link #synthesizeShiftClick}.
     */
    private boolean wouldShiftClickTouchLockedSlot(AbstractContainerMenu menu, Slot source) {
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()) return false;

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
            if (sourceInMain && destInMain) continue;
            if (sourceInHotbar && destInHotbar) continue;

            if (canMergeOrPlace(dest, sourceStack)) return true;
        }
        return false;
    }

    /**
     * Vanilla-equivalent prediction for "could moveItemStackTo place
     * this stack into this slot?" — doesn't mutate.
     *
     * <p>Calling {@code slot.mayPlace} from here does NOT trigger
     * {@link AbstractContainerMenuMoveItemStackToMixin}'s
     * {@code @WrapOperation} — that wrap is scoped to the call site
     * inside {@code moveItemStackTo}.
     */
    private boolean canMergeOrPlace(Slot slot, ItemStack stack) {
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) return slot.mayPlace(stack);
        if (!ItemStack.isSameItemSameComponents(inSlot, stack)) return false;
        return inSlot.getCount() < Math.min(slot.getMaxStackSize(inSlot), stack.getMaxStackSize());
    }
}
