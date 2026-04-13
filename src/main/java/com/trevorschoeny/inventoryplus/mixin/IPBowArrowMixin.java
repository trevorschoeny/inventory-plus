package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.arrows.DeepArrowSearch;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles deferred arrow extraction for bows after a shot completes.
 *
 * <p>When {@code IPDeepArrowMixin} returns a container arrow from
 * {@code getProjectile()}, the actual extraction is deferred until here.
 * At RETURN of {@code releaseUsing()}, we know the final outcome:
 *
 * <ul>
 *   <li><b>return true</b> — shot was fired. Check if vanilla's
 *       {@code useAmmo()} consumed the arrow (via {@code split()},
 *       which makes the copy empty). If consumed, extract from
 *       the container. If not consumed (Infinity enchantment),
 *       leave the container untouched.</li>
 *   <li><b>return false</b> — shot was cancelled (draw too short,
 *       item switched, etc.). Clear pending state, don't extract.</li>
 * </ul>
 *
 * @cairn 003-work-with-vanilla — lets vanilla's draw/useAmmo handle
 *        all consumption logic; we only sync the container afterwards
 * @cairn 007-precise-mixin-injection — RETURN injection only
 */
@Mixin(BowItem.class)
public class IPBowArrowMixin {

    @Inject(method = "releaseUsing", at = @At("RETURN"))
    private void inventoryPlus$resolveArrow(ItemStack weapon, Level level,
                                            LivingEntity entity, int timeLeft,
                                            CallbackInfoReturnable<Boolean> cir) {
        // Extraction must only happen server-side — client will see
        // the update via vanilla's container sync (broadcastChanges)
        if (level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;

        if (cir.getReturnValue()) {
            // Shot was fired — resolve: extract if consumed, leave if Infinity
            DeepArrowSearch.resolveConsumption(player);
        } else {
            // Cancelled (power < 0.1, item switched, etc.) — don't extract
            DeepArrowSearch.clearPending(player);
        }
    }
}
