package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.arrows.DeepArrowSearch;
import com.trevorschoeny.inventoryplus.features.arrows.DeepArrowSearch.PendingContainerArrow;
import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.menukit.config.GeneralOption;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * Extends {@link Player#getProjectile(ItemStack)} to search inside bundles,
 * shulker boxes, and the ender chest when vanilla's flat inventory scan
 * finds nothing.
 *
 * <p>Injects at RETURN — after vanilla has finished its own search. If vanilla
 * found a loose arrow, we clear any stale pending state and let it through.
 * If vanilla returned empty, we peek into containers and return a copy of
 * the first matching projectile found.
 *
 * <p><b>Why peek instead of extract?</b> This method is called in two
 * contexts: (1) as a boolean check in {@code BowItem.use()} / {@code
 * CrossbowItem.use()} where the result is immediately discarded, and (2)
 * as the actual retrieval in {@code releaseUsing()} / {@code tryLoadProjectiles()}.
 * If we extracted here, the use() check would steal an arrow from the
 * container just to answer "yes, arrows exist" — then discard it. Instead,
 * we peek and defer extraction to the weapon-specific RETURN injections
 * where we can confirm the shot was actually fired.
 *
 * @cairn 003-work-with-vanilla — returns a copy that vanilla's draw/useAmmo
 *        can process normally; extraction happens only after confirmation
 * @cairn 007-precise-mixin-injection — RETURN injection, non-destructive
 */
@Mixin(Player.class)
public class IPDeepArrowMixin {

    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void inventoryPlus$searchContainers(ItemStack weapon,
                                                CallbackInfoReturnable<ItemStack> cir) {
        // Only applies to projectile weapons (bow, crossbow)
        if (!(weapon.getItem() instanceof ProjectileWeaponItem projectileWeapon)) return;

        // Check the family-wide toggle
        boolean enabled = MenuKit.family("trevmods")
                .getGeneral(new GeneralOption<>("deep_arrow_search", true, Boolean.class));
        if (!enabled) return;

        Player self = (Player) (Object) this;

        if (!cir.getReturnValue().isEmpty()) {
            // Vanilla found a loose arrow — clear any stale pending state
            // from a previous getProjectile() call (e.g., the use() check)
            DeepArrowSearch.clearPending(self);
            return;
        }

        // Vanilla found nothing in the flat inventory — search containers.
        // Priority: bundles → shulker boxes → ender chest.
        Predicate<ItemStack> predicate = projectileWeapon.getAllSupportedProjectiles();
        PendingContainerArrow pending = DeepArrowSearch.peekContainers(self, predicate);

        if (pending != null) {
            DeepArrowSearch.setPending(self, pending);
            cir.setReturnValue(pending.arrowCopy());
        }
    }
}
