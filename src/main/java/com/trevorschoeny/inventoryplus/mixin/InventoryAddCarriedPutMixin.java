package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.nested.CarriedPut;
import com.trevorschoeny.inventoryplus.rules.BoxSettings;

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
 * Pickups into carried shulker boxes by their Intake ({@link CarriedPut}): every item
 * given to the inventory with no slot named (a pickup, {@code /give}, a crafting grid's
 * returns). Boxes set to Before inventory take it first; the inventory takes what's left
 * as vanilla puts it; boxes set to After inventory take what the inventory had no room
 * for. When nothing is left the item counts as picked up. Server only.
 */
@Mixin(Inventory.class)
public abstract class InventoryAddCarriedPutMixin {

    @Shadow @Final public Player player;

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$beforeInventory(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty() || !(player instanceof ServerPlayer sp)) return;
        if (CarriedPut.into(sp, stack, BoxSettings.When.BEFORE) > 0 && stack.isEmpty()) cir.setReturnValue(true);
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), cancellable = true)
    private void inventoryplus$afterInventory(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot != -1 || stack.isEmpty() || !(player instanceof ServerPlayer sp)) return;
        if (CarriedPut.into(sp, stack, BoxSettings.When.AFTER) > 0) cir.setReturnValue(true);
    }
}
