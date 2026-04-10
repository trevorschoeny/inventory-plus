package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.ContainerPeek;
import com.trevorschoeny.inventoryplus.ContainerPeekClient;
import com.trevorschoeny.inventoryplus.InventoryPlus;
import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.data.MKInventory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
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
 * in any container screen and sends a peek request using the unified player
 * inventory position from {@link MKInventory}.
 *
 * <p>Note: Creative inventory has its own mixin ({@link IPCreativePeekMixin})
 * because it overrides slotClicked behavior.
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

        // Resolve to unified player inventory position via MKInventory
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>)(Object) this;
        MKContext context = MKContext.fromScreen(self);

        int unifiedPos = MKInventory.toUnifiedPlayerPos(
                mc.player.containerMenu, context, slotId);

        // Only peek items in the player's own inventory (not items in chests, etc.)
        if (unifiedPos < 0) return;

        // Toggle: if already peeking at this position, close the peek
        if (ContainerPeekClient.getPeekedSlot() == unifiedPos) {
            ClientPlayNetworking.send(new PeekC2SPayload(-1));
        } else {
            ClientPlayNetworking.send(new PeekC2SPayload(unifiedPos));
        }

        ci.cancel();
    }
}
