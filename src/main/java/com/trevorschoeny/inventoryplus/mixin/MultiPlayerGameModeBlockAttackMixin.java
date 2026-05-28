package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoToolSwitch;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires Auto Tool Switch's block-attack logic at the very start of
 * vanilla's block-attack methods — BEFORE vanilla reads the player's
 * main-hand item to compute destroy speed or apply creative-mode
 * one-shot break logic.
 *
 * <p>Why this exists instead of Fabric's {@code AttackBlockCallback}:
 * Trev's 2026-05-25 report — tools were sometimes switching AFTER the
 * first hit's destroy speed was already computed. Injecting at HEAD of
 * the game-mode methods guarantees our slot change happens first.
 *
 * <p>Two methods to cover both gamemodes:
 * <ul>
 *   <li>{@code startDestroyBlock(BlockPos, Direction)} — survival
 *       sustained mining. Fires once per LMB press.</li>
 *   <li>{@code destroyBlock(BlockPos)} — creative single-hit break.
 *       Fires once per LMB press.</li>
 * </ul>
 *
 * <p>Uses precise injection per §0030 — no {@code @Overwrite}, HEAD
 * injection is observation-only.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeBlockAttackMixin {

    @Inject(
            method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD")
    )
    private void inventoryplus$preStartDestroyBlock(BlockPos pos, Direction direction,
                                                     CallbackInfoReturnable<Boolean> cir) {
        AutoToolSwitch.preAttackBlock(pos);
    }

    @Inject(
            method = "destroyBlock(Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD")
    )
    private void inventoryplus$preDestroyBlock(BlockPos pos,
                                                CallbackInfoReturnable<Boolean> cir) {
        AutoToolSwitch.preAttackBlock(pos);
    }
}
