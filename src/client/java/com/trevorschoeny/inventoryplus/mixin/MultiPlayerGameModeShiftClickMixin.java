package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;

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
 * Dedicated servers: re-routes a shift-click that vanilla's server would land
 * in a slot refusing it, so it lands elsewhere instead.
 *
 * <h3>Why this is still needed</h3>
 *
 * Every lock and reach choice is one MenuKit veto now ({@code Locks}), and
 * MenuKit asks it at every seam it has, on the client before a click is sent
 * and, in single player, on the integrated server that runs the move. A
 * dedicated server runs no Inventory Plus, so where a shift-click lands is
 * decided there without it. This mixin keeps that one case working: when
 * vanilla's routing would put items into a slot that refuses shift-click in,
 * it cancels the click and sends the move as PICKUP clicks, which any server
 * respects, into slots that allow it. It can go once MenuKit routes
 * shift-clicks itself.
 *
 * <p>The source side needs nothing here: a shift-click out of, or a drop from,
 * a slot that refuses it is refused by MenuKit's own client seam before any
 * packet is sent.
 *
 * <h3>Iteration order</h3>
 *
 * From the first main-inventory slot (player container slot 9) forward
 * through hotbar, armor and offhand, skipping the source's own half and
 * slots that cannot take the stack. This is simpler than vanilla's
 * per-screen preference, so the slot picked may differ by a few positions.
 * Only dedicated servers see it.
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
        // Only shift-click. Everything else, including the synthesized PICKUPs
        // this mixin recurses into below, passes through.
        if (clickType != ContainerInput.QUICK_MOVE) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot source = menu.slots.get(slotId);

        // Single player: the integrated server asks MenuKit, and so the veto,
        // at its own moveItemStackTo seam. Let the click go through.
        //
        // Trap, and it cost a released defect in 1.5.0: that server-side answer
        // runs on the server thread, so a lock store that only answers on the
        // render thread is invisible there. Every store Locks.on reads must
        // answer on both threads before it counts as enforced.
        if (Minecraft.getInstance().hasSingleplayerServer()) return;

        // Dedicated server: only step in when vanilla's routing would reach a
        // slot that refuses shift-click in.
        if (!wouldShiftClickTouchRefusedSlot(menu, source, player)) return;

        // A refusing destination is in the way: cancel the click and send the
        // move as PICKUPs into slots that allow it.
        ci.cancel();
        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        synthesizeShiftClick(self, containerId, slotId, source, menu, player);
    }

    /**
     * Picks up the source onto the cursor, walks destinations from the start
     * of the main inventory, and sends PICKUPs for each eligible slot until
     * the cursor is empty. Any leftover goes back on the source.
     */
    private void synthesizeShiftClick(MultiPlayerGameMode self, int containerId,
                                       int sourceSlotId, Slot source,
                                       AbstractContainerMenu menu, Player player) {
        if (source.getItem().isEmpty()) return;

        // Pick up the source onto the cursor. These PICKUPs stand in for the
        // player's shift-click, so they are sent under vanilla's own
        // shift-click keys and judged as what the player did, not as plain
        // clicks.
        SlotOperations.as(BehaviorKeys.SHIFT_CLICK_OUT, BehaviorKeys.SHIFT_CLICK_IN,
                () -> self.handleContainerInput(containerId, sourceSlotId, 0, ContainerInput.PICKUP, player));
        if (menu.getCarried().isEmpty()) return;

        boolean sourceIsPlayer = LockedSlots.isLockable(source);
        int sourceCS = sourceIsPlayer ? source.getContainerSlot() : -1;
        boolean sourceInMain = sourceIsPlayer && sourceCS >= 9 && sourceCS <= 35;
        boolean sourceInHotbar = sourceIsPlayer && sourceCS >= 0 && sourceCS <= 8;

        // Start at player container slot 9, the start of the 3x9 main grid.
        // That skips crafting input and result, armor and chest slots, which
        // vanilla shift-click doesn't target anyway.
        int mainInvStartIdx = findMainInvStartIndex(menu);

        // Place into each eligible slot in turn until the cursor is empty.
        for (int i = mainInvStartIdx; i < menu.slots.size(); i++) {
            if (menu.getCarried().isEmpty()) break;
            Slot dest = menu.slots.get(i);
            if (dest == source) continue;
            if (!SlotOperations.allows(menu, dest, player, BehaviorKeys.SHIFT_CLICK_IN)) continue;

            // Vanilla doesn't shift-click within a half.
            if (LockedSlots.isLockable(dest)) {
                int destCS = dest.getContainerSlot();
                boolean destInMain = destCS >= 9 && destCS <= 35;
                boolean destInHotbar = destCS >= 0 && destCS <= 8;
                if (sourceInMain && destInMain) continue;
                if (sourceInHotbar && destInHotbar) continue;
            }

            if (!canMergeOrPlace(dest, menu.getCarried())) continue;
            final int destSlotId = i;
            SlotOperations.as(BehaviorKeys.SHIFT_CLICK_OUT, BehaviorKeys.SHIFT_CLICK_IN,
                    () -> self.handleContainerInput(containerId, destSlotId, 0, ContainerInput.PICKUP, player));
        }

        // Leftover goes back on the source. Putting the remainder back is part of the shift-click taking from the
        // source, so the source's own shift-click-in rule cannot strand it on
        // the cursor.
        if (!menu.getCarried().isEmpty()) {
            SlotOperations.as(BehaviorKeys.SHIFT_CLICK_OUT, BehaviorKeys.SHIFT_CLICK_OUT,
                    () -> self.handleContainerInput(containerId, sourceSlotId, 0, ContainerInput.PICKUP, player));
        }
    }

    /**
     * The menu index of the player's main-inventory slot 9, the start of the
     * 3x9 grid, or 0 if the menu has none (no vanilla screen lacks one).
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
     * Conservative prediction: true if vanilla's shift-click iteration could
     * place items into a slot that refuses shift-click in. The same-half
     * filter avoids false positives within the player half.
     */
    private boolean wouldShiftClickTouchRefusedSlot(AbstractContainerMenu menu, Slot source, Player player) {
        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()) return false;

        boolean sourceIsPlayer = LockedSlots.isLockable(source);
        int sourceCS = sourceIsPlayer ? source.getContainerSlot() : -1;
        boolean sourceInMain = sourceIsPlayer && sourceCS >= 9 && sourceCS <= 35;
        boolean sourceInHotbar = sourceIsPlayer && sourceCS >= 0 && sourceCS <= 8;

        for (Slot dest : menu.slots) {
            if (dest == source) continue;
            if (SlotOperations.allows(menu, dest, player, BehaviorKeys.SHIFT_CLICK_IN)) continue;
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
     * Whether vanilla's moveItemStackTo could place this stack into this
     * slot, without changing anything.
     */
    private boolean canMergeOrPlace(Slot slot, ItemStack stack) {
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) return slot.mayPlace(stack);
        if (!ItemStack.isSameItemSameComponents(inSlot, stack)) return false;
        return inSlot.getCount() < Math.min(slot.getMaxStackSize(inSlot), stack.getMaxStackSize());
    }
}
