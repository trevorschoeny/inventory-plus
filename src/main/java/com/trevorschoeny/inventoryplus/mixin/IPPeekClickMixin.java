package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.ContainerPeek;
import com.trevorschoeny.inventoryplus.ContainerPeekClient;
import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts right-clicks on peekable items (shulker boxes, bundles, ender chests)
 * in any inventory screen and sends a peek request to the server instead of
 * vanilla's half-stack pickup behavior.
 *
 * <p>Part of <b>Inventory Plus</b> — Container Peek feature.
 */
@Mixin(AbstractContainerScreen.class)
public class IPPeekClickMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void inventoryPlus$interceptPeekClick(Slot slot, int slotId, int button,
                                                   ClickType clickType, CallbackInfo ci) {
        // Only intercept right-clicks (button 1) with PICKUP type
        if (button != 1 || clickType != ClickType.PICKUP) return;
        if (slot == null) return;

        ItemStack stack = slot.getItem();
        if (!ContainerPeek.isPeekable(stack)) return;

        // Toggle: if already peeking at this slot, close the peek
        if (ContainerPeekClient.getPeekedSlot() == slotId) {
            ClientPlayNetworking.send(new PeekC2SPayload(-1));
        } else {
            // Open peek for this slot (closes any existing peek on server)
            ClientPlayNetworking.send(new PeekC2SPayload(slotId));
        }

        // Cancel vanilla's half-stack pickup behavior
        ci.cancel();
    }
}
