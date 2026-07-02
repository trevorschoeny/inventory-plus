package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels shift-click and Q-drop packets that would violate locked
 * slots before they leave the client.
 *
 * <h3>Split responsibility — SP/LAN vs dedicated MP</h3>
 *
 * Locked-slot protection runs on two layers depending on environment:
 *
 * <ol>
 *   <li><b>SP / LAN</b> — the integrated server shares the JVM, so
 *       {@link AbstractContainerMenuMoveItemStackToMixin}'s
 *       {@code getItem} + {@code mayPlace} wraps fire on both Render
 *       and Server threads. Vanilla {@code moveItemStackTo} sees
 *       locked slots as empty + unplaceable → naturally skips them
 *       using its own per-screen iteration order. This mixin
 *       short-circuits for SP/LAN destination-block: vanilla
 *       already does the right thing.</li>
 *   <li><b>Dedicated MP</b> — server JVM doesn't run our mixins,
 *       so vanilla shift-click would place items in locked slots
 *       server-side. This mixin cancels the original packet and
 *       synthesizes the move via {@link ContainerInput#PICKUP} packets
 *       targeted at non-locked slots — PICKUP is universally
 *       server-respected, no server-side companion needed.</li>
 * </ol>
 *
 * <p>Source-block (shift-click or Q-drop FROM a locked slot) runs
 * in both environments — vanilla iteration skip can't help once
 * items have already left the source.
 *
 * <p>Manual cursor interaction ({@code PICKUP}, {@code SWAP},
 * {@code CLONE}, {@code PICKUP_ALL}, {@code QUICK_CRAFT}) passes
 * through unmodified — by spec, "manual cursor interaction is
 * allowed."
 *
 * <h3>MP synthesis iteration order</h3>
 *
 * Per Trev 2026-05-16: iterate {@code menu.slots} starting at the
 * first main-inventory slot (the 3×9 grid — player container-slot
 * 9) and continue forward through hotbar, armor, offhand. For each
 * candidate, skip locked slots, skip same-half (vanilla never
 * shift-clicks main→main or hotbar→hotbar), and skip slots where
 * {@code canMergeOrPlace} is false. Send a {@link ContainerInput#PICKUP}
 * for the first eligible slot, then continue if the cursor still
 * has items.
 *
 * <p>This is a simpler heuristic than vanilla's per-screen-handler
 * preference (e.g., chest shift-click iterates hotbar-first in
 * reverse); items end up in the right kind of slot (non-locked,
 * compatible) but the specific slot picked may differ from vanilla
 * by a few positions. Acceptable trade-off for not maintaining
 * per-screen tables, and only affects dedicated-MP UX (SP/LAN gets
 * the exact vanilla order via the @WrapOperations).
 *
 * <h3>Auto-pickup is not covered here</h3>
 *
 * Vanilla auto-pickup (mob drops, ground items) runs server-side
 * via {@code Inventory.add} → {@code getFreeSlot}, covered by
 * {@link InventoryGetFreeSlotMixin} in SP/LAN only. Multiplayer
 * auto-pickup into locked slots remains a hole pending a server-side
 * companion mod — see DEFERRED.md.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeShiftClickMixin {

    @Inject(
            method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void inventoryplus$handleLockedShiftClick(
            int containerId, int slotId, int button, ContainerInput clickType,
            Player player, CallbackInfo ci) {
        // Only gate shift-click (QUICK_MOVE) and Q-drop (THROW).
        // PICKUP / SWAP / CLONE / PICKUP_ALL / QUICK_CRAFT all pass
        // through — including the synthesized PICKUPs this mixin
        // recurses into below.
        if (clickType != ContainerInput.QUICK_MOVE && clickType != ContainerInput.THROW) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot source = menu.slots.get(slotId);

        // Source-block: never let items leave a locked slot. Both
        // QUICK_MOVE and THROW. Both SP and MP.
        if (LockedSlots.isLockedSlot(source)) {
            ci.cancel();
            return;
        }

        // Destination-block applies to QUICK_MOVE only.
        if (clickType != ContainerInput.QUICK_MOVE) return;

        // SP/LAN: vanilla iteration skip handles it. Let the click go
        // through normally.
        if (Minecraft.getInstance().hasSingleplayerServer()) return;

        // Dedicated MP: check whether the click would even touch a
        // locked slot. If not, let vanilla handle it normally (no
        // synthesis overhead).
        if (!wouldShiftClickTouchLockedSlot(menu, source)) return;

        // Dedicated MP + locked destination in the way: cancel the
        // original click and synthesize the move via PICKUPs.
        ci.cancel();
        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        synthesizeShiftClick(self, containerId, slotId, source, menu, player);
    }

    /**
     * Dedicated-MP synthesis: pick up the source onto the cursor,
     * iterate destinations from the main-inventory start, and send
     * PICKUPs for each eligible slot until the cursor is empty. Any
     * leftover goes back on the source.
     */
    private void synthesizeShiftClick(MultiPlayerGameMode self, int containerId,
                                       int sourceSlotId, Slot source,
                                       AbstractContainerMenu menu, Player player) {
        if (source.getItem().isEmpty()) return;

        // Phase 1: pick up source onto cursor.
        self.handleContainerInput(containerId, sourceSlotId, 0,
                ContainerInput.PICKUP, player);
        if (menu.getCarried().isEmpty()) return;

        // Source classification for same-half filtering.
        boolean sourceIsPlayer = LockedSlots.isLockable(source);
        int sourceCS = sourceIsPlayer ? source.getContainerSlot() : -1;
        boolean sourceInMain = sourceIsPlayer && sourceCS >= 9 && sourceCS <= 35;
        boolean sourceInHotbar = sourceIsPlayer && sourceCS >= 0 && sourceCS <= 8;

        // Find the menu-slot index that corresponds to player
        // container-slot 9 (start of the 3×9 main inv grid). Starting
        // iteration there skips crafting input / result / armor /
        // chest-slots, which vanilla shift-click doesn't target anyway.
        int mainInvStartIdx = findMainInvStartIndex(menu);

        // Phase 2: iterate forward through menu.slots from main inv,
        // PICKUP-place into the first eligible non-locked slot, repeat
        // until cursor empty.
        for (int i = mainInvStartIdx; i < menu.slots.size(); i++) {
            if (menu.getCarried().isEmpty()) break;
            Slot dest = menu.slots.get(i);
            if (dest == source) continue;
            if (LockedSlots.isLockedSlot(dest)) continue;

            // Same-half filter — vanilla doesn't shift-click within a
            // half. Skip dest if it's in the same half as the source.
            if (LockedSlots.isLockable(dest)) {
                int destCS = dest.getContainerSlot();
                boolean destInMain = destCS >= 9 && destCS <= 35;
                boolean destInHotbar = destCS >= 0 && destCS <= 8;
                if (sourceInMain && destInMain) continue;
                if (sourceInHotbar && destInHotbar) continue;
            }

            if (!canMergeOrPlace(dest, menu.getCarried())) continue;
            self.handleContainerInput(containerId, i, 0,
                    ContainerInput.PICKUP, player);
        }

        // Phase 3: leftover goes back on the source.
        if (!menu.getCarried().isEmpty()) {
            self.handleContainerInput(containerId, sourceSlotId, 0,
                    ContainerInput.PICKUP, player);
        }
    }

    /**
     * Returns the menu-slot index whose backing slot is the player's
     * main-inv slot 9 — the start of the 3×9 grid. Falls back to 0
     * if no main-inv slot is found in the menu (shouldn't happen for
     * any vanilla screen, but safe default).
     */
    private int findMainInvStartIndex(AbstractContainerMenu menu) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (LockedSlots.isLockable(s) && s.getContainerSlot() == 9) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Conservative prediction: returns true if vanilla's shift-click
     * iteration would attempt to place items into any locked slot.
     * Same-half filter avoids false-positives within the player half.
     *
     * <p>Used to decide whether to invoke synthesis in dedicated MP.
     * SP/LAN doesn't need this — vanilla's own iteration skip via
     * the @WrapOperations handles everything.
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
     */
    private boolean canMergeOrPlace(Slot slot, ItemStack stack) {
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) return slot.mayPlace(stack);
        if (!ItemStack.isSameItemSameComponents(inSlot, stack)) return false;
        return inSlot.getCount() < Math.min(slot.getMaxStackSize(inSlot), stack.getMaxStackSize());
    }
}
