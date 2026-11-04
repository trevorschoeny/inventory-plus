package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hit uses the item in hand now, not the one the player held at the last tick.
 *
 * <p>The server applies a hand item's attribute modifiers (attack damage, attack speed)
 * in the player's tick ({@code detectEquipmentUpdates}), but handles packets before it.
 * So a switch and a hit that arrive in the same tick (Auto Tool Switch swapping a sword
 * in on the hit, or a number key and a click together) hit with the new item's
 * enchantments on the old item's damage: a Sharpness V iron sword switched in over an
 * iron pickaxe did 7 instead of 9. Running the same equipment pass at the start of the
 * attack brings the modifiers up to date first. Where nothing changed it does nothing,
 * and the tick after finds nothing left to do.
 */
@Mixin(Player.class)
public abstract class PlayerAttackEquipmentMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void inventoryPlus$currentEquipment(Entity target, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide()) ((LivingEntityEquipmentAccessor) self).inventoryPlus$detectEquipmentUpdates();
    }
}
