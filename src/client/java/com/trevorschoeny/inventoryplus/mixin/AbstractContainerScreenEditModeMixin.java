package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.editmode.EditMode;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Edit mode's hold on a container screen's slots ({@link EditMode}).
 *
 * <ul>
 *   <li><b>The click</b>: once the screen's widgets and panels have passed on it (the
 *       edit button itself, a dialog), a click in edit mode does nothing: edit mode
 *       works by keys on the slot under the mouse.</li>
 *   <li><b>Every other slot action</b> (number keys, Q, pick block, offhand swap, a
 *       release that would finish a drag or a double-click) ends in
 *       {@code slotClicked}, so refusing there stops them all, whatever key the player
 *       has bound.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenEditModeMixin {

    @Inject(method = "mouseClicked",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;getHoveredSlot(DD)Lnet/minecraft/world/inventory/Slot;"),
            cancellable = true)
    private void inventoryPlus$editClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (EditMode.isOn()) cir.setReturnValue(true);
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"), cancellable = true)
    private void inventoryPlus$editSwallow(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (EditMode.isOn()) ci.cancel();
    }
}
