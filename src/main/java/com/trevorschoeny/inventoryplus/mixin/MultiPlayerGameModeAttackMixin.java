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
 * {@code MultiPlayerGameMode.attack(Player, Entity)} — BEFORE vanilla
 * computes attack damage or sends the attack packet.
 *
 * <p>Why this exists instead of using Fabric's {@code AttackEntityCallback}:
 * the callback fires too late in the attack-processing flow, so the
 * switched weapon doesn't take effect until after the first hit is
 * already applied (Trev's report 2026-05-25). Injecting at HEAD of the
 * attack method guarantees the slot change happens before vanilla reads
 * {@code player.getMainHandItem()} for damage computation.
 *
 * <p>Uses precise injection per the project's §0030 mixin policy — no
 * {@code @Overwrite}, no {@code @Inject(cancellable=true)}. The HEAD
 * injection is observation-only; it does not alter the attack flow
 * itself, just preconditions the player's hand.
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
