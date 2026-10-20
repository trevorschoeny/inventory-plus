package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.carried.CarriedPick;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pick block from carried containers: before vanilla sends its pick packet, fetch the
 * picked item from a carried shulker box, bundle or the ender chest into an empty
 * inventory slot when the inventory has none ({@link CarriedPick}). The fetch reaches the
 * server first, so vanilla's pick, arriving after it, finds the item and brings it to the
 * hand as it would any inventory item. Never cancels vanilla's pick.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPickBlockMixin {

    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"))
    private void inventoryplus$pickFromCarried(CallbackInfo ci) {
        CarriedPick.beforePick((Minecraft) (Object) this);
    }
}
