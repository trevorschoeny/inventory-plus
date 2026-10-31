package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.nested.CarriedPut;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Item pickup into carried containers ({@link CarriedPut}): every item given to the
 * inventory with no slot named (a pickup, {@code /give}, a crafting grid's returns) goes
 * first into a carried container that holds it. Server only. What's left goes on into
 * the inventory as vanilla puts it; when nothing is left the item counts as picked up.
 */
@Mixin(Inventory.class)
public abstract class InventoryAddCarriedPutMixin {

    @Shadow @Final public Player player;

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$intoCarried(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty() || !(player instanceof ServerPlayer sp)) return;
        if (CarriedPut.into(sp, stack, CarriedPut.Move.PICKUP) > 0 && stack.isEmpty()) cir.setReturnValue(true);
    }
}
