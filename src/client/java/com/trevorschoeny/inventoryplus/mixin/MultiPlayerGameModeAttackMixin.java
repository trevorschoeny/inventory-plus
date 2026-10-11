package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoToolSwitch;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires Auto Tool Switch's combat-path logic at the very start of
 * {@code MultiPlayerGameMode.attack(Player, Entity)}, before vanilla
 * computes attack damage or sends the attack packet.
 *
 * <p>Fabric's {@code AttackEntityCallback} fires too late in the attack
 * flow, so the switched weapon didn't take effect until after the first hit
 * landed. Injecting at HEAD of the attack method makes the slot change
 * happen before vanilla reads {@code player.getMainHandItem()} for damage.
 *
 * <p>No {@code @Overwrite} and no {@code @Inject(cancellable=true)}. The
 * HEAD injection doesn't alter the attack flow, it only sets up the
 * player's hand.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeAttackMixin {

    @Inject(
            method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
    )
    private void inventoryplus$preAttack(Player player, Entity target, CallbackInfo ci) {
        AutoToolSwitch.preAttackEntity(player, target);
    }
}
