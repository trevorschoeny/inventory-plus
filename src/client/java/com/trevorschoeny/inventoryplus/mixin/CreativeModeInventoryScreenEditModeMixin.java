package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.editmode.EditMode;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Edit mode on creative's inventory tab: creative's screen handles slot actions in its
 * own {@code slotClicked}, which never reaches the one {@link AbstractContainerScreenEditModeMixin}
 * guards, so the same refusal goes here too.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenEditModeMixin {

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), cancellable = true)
    private void inventoryPlus$editSwallow(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (EditMode.isOn()) ci.cancel();
    }
}
