package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.nested.CarriedPut;

import com.trevlar.slotkit.api.window.BehaviorKeys;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shift-click in into carried containers ({@link CarriedPut}): a shift-click on a
 * storage slot puts its items first into a carried container that holds them, and
 * vanilla's quick move takes what's left. Server only; the client's prediction is
 * corrected by the next sync.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuCarriedPutMixin {

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$intoCarried(int slotIndex, int buttonNum, ContainerInput input, Player player,
                                          CallbackInfo ci) {
        if (input != ContainerInput.QUICK_MOVE || !(player instanceof ServerPlayer sp)) return;
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;
        var slot = menu.slots.get(slotIndex);
        CarriedPut.fromSlot(sp, menu, slot, slot.getItem().getCount(), CarriedPut.Move.SHIFT_CLICK,
                BehaviorKeys.SHIFT_CLICK_OUT);
        if (!slot.hasItem()) ci.cancel();
    }
}
