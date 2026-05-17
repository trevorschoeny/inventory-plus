package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.Minecraft;
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
 * Cancels shift-click and Q-drop packets that would violate locked
 * slots before they leave the client.
 *
 * <h3>The split-responsibility model</h3>
 *
 * Locked-slot protection runs on two layers:
 *
 * <ol>
 *   <li><b>Vanilla iteration skip</b> (via
 *       {@link AbstractContainerMenuMoveItemStackToMixin}) — makes
 *       vanilla {@code moveItemStackTo} see locked slots as
 *       permanently full / unplaceable, so vanilla naturally picks
 *       the next slot in its own per-screen-handler iteration order.
 *       Works in single-player and LAN (where the integrated server
 *       shares the JVM and runs the same mixins).</li>
 *   <li><b>Packet cancellation</b> (this mixin) — for cases the
 *       vanilla iteration skip can't cover:
 *       <ul>
 *         <li>Source slot is locked → cancel always. Even with
 *             vanilla iteration skip, items would still LEAVE the
 *             locked slot.</li>
 *         <li>Destination would be locked AND we're on a dedicated
 *             multiplayer server → cancel. The vanilla iteration
 *             skip can't help here because the server JVM doesn't
 *             run this mod's mixins.</li>
 *       </ul></li>
 * </ol>
 *
 * <p>Manual cursor interaction ({@code PICKUP}, {@code SWAP},
 * {@code CLONE}, {@code PICKUP_ALL}, {@code QUICK_CRAFT}) passes
 * through unmodified — by spec, "manual cursor interaction is
 * allowed."
 *
 * <h3>Why SP+LAN gets a different code path than dedicated MP</h3>
 *
 * In SP and LAN-hosted games, the integrated server is in the same
 * JVM. Both client (Render thread) and server (Server thread) run
 * this mod's mixin chain, including {@link
 * AbstractContainerMenuMoveItemStackToMixin}'s vanilla-iteration
 * skip. Letting vanilla handle the shift-click — without
 * cancellation — produces correct behavior on both threads, with
 * vanilla's own per-screen logic picking the right next slot. UX
 * is identical to vanilla (smart fill, locked skipped, no flicker).
 *
 * <p>On a dedicated server (separate JVM, IP not installed), the
 * server runs vanilla without our mixins. If we let the click
 * through, the server would place items in the locked slot, sync
 * back, lock defeated + flicker. So we cancel. Trade-off: shift-click
 * into a locked-destination inventory does nothing in dedicated MP
 * (player must manually move). Matches IPN's behavior.
 *
 * <p>Filed for future improvement: synthesize the shift-click via
 * PICKUP packets in dedicated MP so the UX matches SP. Snapshot-
 * revert-replay approach using vanilla's own iteration. See
 * DEFERRED.md.
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
            method = "handleInventoryMouseClick(IIILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void inventoryplus$handleLockedShiftClick(
            int containerId, int slotId, int button, ClickType clickType,
            Player player, CallbackInfo ci) {
        // Only gate shift-click (QUICK_MOVE) and Q-drop (THROW).
        if (clickType != ClickType.QUICK_MOVE && clickType != ClickType.THROW) return;

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != containerId) return;
        if (slotId < 0 || slotId >= menu.slots.size()) return;

        Slot source = menu.slots.get(slotId);

        // Source-block: never let items leave a locked slot. Applies to
        // both QUICK_MOVE and THROW, both single-player and multiplayer.
        if (LockedSlots.isLockedSlot(source)) {
            ci.cancel();
            return;
        }

        // Destination-block applies to QUICK_MOVE only.
        if (clickType != ClickType.QUICK_MOVE) return;

        // SP/LAN: let vanilla handle. The mixin chain (in
        // AbstractContainerMenuMoveItemStackToMixin) makes vanilla
        // naturally skip locked slots in both passes, using its own
        // per-screen-handler iteration order. UX matches vanilla.
        if (Minecraft.getInstance().hasSingleplayerServer()) return;

        // Dedicated MP: server doesn't run our mixins, so vanilla skip
        // would only happen client-side and the server would override.
        // Cancel the click to prevent server-side placement into locked
        // slots. Trade-off: shift-click is a no-op when any destination
        // would be locked. See DEFERRED.md for the synthesis path that
        // would make MP UX match SP.
        if (wouldShiftClickTouchLockedSlot(menu, source)) {
            ci.cancel();
        }
    }

    /**
     * Conservative prediction: returns true if vanilla's shift-click
     * iteration would attempt to place items into any locked slot.
     * Same-half filter avoids false-positives within the player half.
     *
     * <p>Used only in the dedicated-MP path. In SP/LAN we don't need
     * this — vanilla's own iteration handles it via the mixin chain.
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
            // Same-half filter — vanilla doesn't shift-click within a half.
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
