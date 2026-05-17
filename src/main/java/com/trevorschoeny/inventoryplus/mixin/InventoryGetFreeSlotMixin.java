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
 * <h3>Single-player no-flicker</h3>
 *
 * In single-player, the integrated server runs in the same JVM as the
 * client. The mixin applies to BOTH client and server invocations →
 * items never land in locked slots → no flicker, no post-hoc correction
 * needed.
 *
 * <h3>Local-player check via UUID</h3>
 *
 * The mixin needs to gate to "the local player's inventory" so we don't
 * affect other players on a LAN-hosted game. The check uses UUID equality
 * — {@code LocalPlayer} (client) and {@code ServerPlayer} (integrated
 * server) are DIFFERENT Java objects for the same logical player, so
 * reference equality ({@code self.player != mc.player}) returns false on
 * the server thread and the mixin would early-return there. UUID
 * equality is stable across the client/server divide.
 *
 * <h3>Scope limitation</h3>
 *
 * Only {@code getFreeSlot} is mixed in; vanilla's
 * {@code getSlotWithRemainingSpace} (merge-into-existing-partial-stack
 * path) is not. Auto-pickup adding to a locked slot that already
 * contains the same item with room slips through. Matches IPN's scope.
 */
@Mixin(Inventory.class)
public abstract class InventoryGetFreeSlotMixin {

    @Shadow @Final
    private NonNullList<ItemStack> items;

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$skipLockedSlots(CallbackInfoReturnable<Integer> cir) {
        Inventory self = (Inventory) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        // UUID-based local-player check. LocalPlayer (client thread) and
        // ServerPlayer (server thread) are distinct Java objects but
        // share UUID for the same logical player.
        if (!self.player.getUUID().equals(mc.player.getUUID())) return;

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
