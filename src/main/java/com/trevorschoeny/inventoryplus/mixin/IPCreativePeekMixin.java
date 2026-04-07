package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.ContainerPeek;
import com.trevorschoeny.inventoryplus.ContainerPeekClient;
import com.trevorschoeny.inventoryplus.InventoryPlus;
import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.menukit.data.MKInventory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts right-clicks on peekable items in the creative inventory screen.
 * Handles both slotClicked (for inventory tab slots) and mouseClicked (for
 * hotbar slots which may bypass slotClicked in creative mode).
 *
 * <p>Part of <b>Inventory Plus</b> — Container Peek feature.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class IPCreativePeekMixin extends AbstractContainerScreen<net.minecraft.world.inventory.AbstractContainerMenu> {

    protected IPCreativePeekMixin() { super(null, null, null); }

    // ── slotClicked: catches most creative inventory clicks ──────────────
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void inventoryPlus$interceptCreativePeekClick(Slot slot, int slotId, int button,
                                                           ClickType clickType, CallbackInfo ci) {
        if (button != 1 || clickType != ClickType.PICKUP) return;
        if (slot == null) return;

        ItemStack stack = slot.getItem();
        if (!ContainerPeek.isPeekable(stack)) return;

        int unifiedPos = slot.getContainerSlot();
        if (unifiedPos < 0 || unifiedPos > 40) return;

        if (ContainerPeekClient.getPeekedSlot() == unifiedPos) {
            ClientPlayNetworking.send(new PeekC2SPayload(-1));
        } else {
            ClientPlayNetworking.send(new PeekC2SPayload(unifiedPos));
        }
        ci.cancel();
    }

    // ── mouseClicked: catches hotbar clicks that bypass slotClicked ──────
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void inventoryPlus$interceptCreativeHotbarPeek(MouseButtonEvent event, boolean bl,
                                                            CallbackInfoReturnable<Boolean> cir) {
        // Only right-clicks (button 1)
        if (event.button() != 1) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Find the slot under the cursor
        if (this.hoveredSlot == null) return;
        Slot hovered = this.hoveredSlot;

        // Only peek items that are in the player's own Inventory container
        // (not crafting grid, crafting result, or other non-inventory slots)
        if (!(hovered.container instanceof net.minecraft.world.entity.player.Inventory)) return;

        ItemStack stack = hovered.getItem();
        if (!ContainerPeek.isPeekable(stack)) return;

        int unifiedPos = hovered.getContainerSlot();
        if (unifiedPos < 0 || unifiedPos > MKInventory.OFFHAND) return;

        if (ContainerPeekClient.getPeekedSlot() == unifiedPos) {
            ClientPlayNetworking.send(new PeekC2SPayload(-1));
        } else {
            ClientPlayNetworking.send(new PeekC2SPayload(unifiedPos));
        }
        cir.setReturnValue(true);
    }
}
