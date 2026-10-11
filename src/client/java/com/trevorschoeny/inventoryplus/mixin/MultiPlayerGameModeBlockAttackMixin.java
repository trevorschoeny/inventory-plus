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
 * vanilla's block-attack methods, before vanilla reads the player's
 * main-hand item to compute destroy speed or apply creative-mode
 * one-shot break logic.
 *
 * <p>With Fabric's {@code AttackBlockCallback}, tools sometimes switched
 * after the first hit's destroy speed was already computed. Injecting at
 * HEAD of the game-mode methods makes the slot change happen first.
 *
 * <p>Two methods cover both game modes, and each fires once per left-button
 * press:
 * <ul>
 *   <li>{@code startDestroyBlock(BlockPos, Direction)}: survival sustained
 *       mining.</li>
 *   <li>{@code destroyBlock(BlockPos)}: creative single-hit break.</li>
 * </ul>
 *
 * <p>No {@code @Overwrite}, and the HEAD injection doesn't alter the flow.
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
