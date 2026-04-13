package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.arrows.DeepArrowSearch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles deferred arrow extraction for crossbows after loading completes.
 *
 * <p>Crossbow arrow consumption happens in {@code tryLoadProjectiles()},
 * which is called from {@code onUseTick()} when the crossbow is fully
 * charged. This method calls {@code getProjectile()} then {@code draw()},
 * following the same pattern as bow — our pending state from
 * {@code IPDeepArrowMixin} applies here too.
 *
 * <p>Also clears stale pending state when crossbow charging is cancelled
 * via {@code releaseUsing()}.
 *
 * @cairn 003-work-with-vanilla — same deferred extraction pattern as bow
 * @cairn 007-precise-mixin-injection — RETURN injection only
 */
@Mixin(CrossbowItem.class)
public class IPCrossbowArrowMixin {

    /**
     * After {@code tryLoadProjectiles} completes, resolve the pending
     * container arrow. If loading succeeded (return true), extract from
     * the container. If failed (return false), clear pending.
     *
     * <p>Note: crossbows don't support Infinity, so the consumption
     * check in {@code resolveConsumption()} will always find the copy
     * consumed (isEmpty == true).
     */
    @Inject(method = "tryLoadProjectiles", at = @At("RETURN"))
    private static void inventoryPlus$resolveArrow(LivingEntity entity,
                                                   ItemStack weapon,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player player)) return;
        if (entity.level().isClientSide()) return;

        if (cir.getReturnValue()) {
            // Projectile loaded into crossbow — extract from container
            DeepArrowSearch.resolveConsumption(player);
        } else {
            // Loading failed — clear pending without extracting
            DeepArrowSearch.clearPending(player);
        }
    }

    /**
     * Clears stale pending state when crossbow charging is cancelled.
     * Without this, a pending from the {@code use()} check call would
     * linger until the next {@code getProjectile()} call — harmless but
     * untidy.
     */
    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void inventoryPlus$clearOnCancel(ItemStack weapon, Level level,
                                             LivingEntity entity, int timeLeft,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        DeepArrowSearch.clearPending(player);
    }
}
