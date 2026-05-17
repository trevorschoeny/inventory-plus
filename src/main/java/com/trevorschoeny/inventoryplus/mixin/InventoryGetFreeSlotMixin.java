package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin on {@link Inventory#getFreeSlot} — vanilla calls this during
 * auto-pickup to find an empty slot for an incoming item. We make it
 * skip locked slots.
 *
 * <h3>The single-player no-flicker win</h3>
 *
 * In single-player Minecraft, the "server" runs in the same JVM as the
 * client (the integrated server). Any mixin we load is loaded into that
 * JVM, so it affects the integrated server's {@code getFreeSlot} too.
 * Result: in single-player, items never land in locked slots in the
 * first place — no flicker, no post-hoc correction needed.
 *
 * <p>On a dedicated multiplayer server, the server doesn't have this
 * mod loaded, so the server's {@code getFreeSlot} runs unmodified.
 * Items still land in locked slots there, and
 * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsCorrector}
 * cleans up post-hoc (with a brief flicker). The combination gives:
 *
 * <ul>
 *   <li>Single-player → no flicker (server-side prevention via shared
 *       JVM).</li>
 *   <li>Multiplayer → corrected post-hoc (flicker; matches IPN's
 *       behavior).</li>
 *   <li>True multiplayer no-flicker would require a server-side
 *       companion mod (IPP territory).</li>
 * </ul>
 *
 * <h3>Local-player check</h3>
 *
 * The mixin only applies when the inventory belongs to the local
 * player ({@code Minecraft.getInstance().player}). This is required
 * because in single-player the integrated server has player
 * inventories for every joined player; we should only filter the
 * LOCAL player's pickup. (For dedicated server: this check would
 * always pass on the client and always fail on server-side since
 * the mod isn't loaded there at all.)
 *
 * <h3>Scope limitations</h3>
 *
 * Mixin only on {@code getFreeSlot}. Vanilla's
 * {@code getSlotWithRemainingSpace} (the "merge into existing partial
 * stack" path) is NOT mixed in — auto-pickup adding to an
 * already-occupied locked slot still goes through. Matches IPN's
 * scope. The post-hoc corrector simplification ("snapshot was empty"
 * only) also matches this limitation.
 */
@Mixin(Inventory.class)
public abstract class InventoryGetFreeSlotMixin {

    static {
        com.trevorschoeny.inventoryplus.InventoryPlusClient.LOGGER.info(
                "[locked-slots] InventoryGetFreeSlotMixin LOADED");
    }

    @Shadow @Final
    private NonNullList<ItemStack> items;

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$skipLockedSlots(CallbackInfoReturnable<Integer> cir) {
        Inventory self = (Inventory) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (self.player != mc.player) return;

        com.trevorschoeny.inventoryplus.InventoryPlusClient.LOGGER.info(
                "[locked-slots] getFreeSlot called; locked set = {}",
                com.trevorschoeny.inventoryplus.lockedslots.LockedSlots.getLockedSlots());

        for (int i = 0; i < items.size(); i++) {
            if (LockedSlots.isLocked(i)) continue;
            if (items.get(i).isEmpty()) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }
}
