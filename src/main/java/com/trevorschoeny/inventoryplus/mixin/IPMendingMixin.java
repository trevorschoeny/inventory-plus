package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.mending.MendingHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>Mob-death mending path.</b> Repairs the Inventory Plus equipment
 * container (and optionally the wider inventory) using XP that would
 * have otherwise dropped as an orb.
 *
 * <p>Flow we're hooking into:
 * <pre>
 *   LivingEntity.getExperienceReward(level, killer)
 *     └─ EnchantmentHelper.processMobExperience(level, killer, thisMob, baseXp)
 *          └─ runIterationOnEquipment(killer, mendingVisitor)   // vanilla pass
 *          └─ return remaining  ← WE INJECT HERE, AT RETURN
 * </pre>
 *
 * <p>Vanilla's pass consumes XP on any mending items in the killer's
 * mainhand, offhand, and armor slots. Whatever's left becomes the
 * return value — and that return value becomes the XP orb the mob
 * drops. We shave off a bit more for MK containers before it gets that
 * far.
 *
 * <p><b>Argument order reminder:</b> the signature is
 * {@code processMobExperience(ServerLevel, Entity attacker, Entity target, int xp)}.
 * A previous version of this mixin checked {@code target instanceof Player}
 * which is wrong — {@code target} is the dying mob. Bytecode of
 * {@code LivingEntity.getExperienceReward} confirms the call is
 * {@code processMobExperience(level, killerParam, this, baseXp)}, so
 * the player is {@code attacker}.
 *
 * <p><b>Scope:</b> this mixin only handles XP at the moment of the
 * kill. For the far more common case where XP reaches the player via
 * orb pickup, see
 * {@link IPOrbMendingMixin}. Both mixins funnel their leftover through
 * {@link MendingHelper} so the MK equipment logic lives in one place.
 *
 * @cairn decision=007-precise-mixin-injection — injects at @At("RETURN")
 * rather than overwriting vanilla's processMobExperience.
 * @cairn decision=009-module-prefixed-mixin-naming — "IP" prefix.
 */
@Mixin(EnchantmentHelper.class)
public class IPMendingMixin {

    @Inject(method = "processMobExperience", at = @At("RETURN"), cancellable = true)
    private static void inventoryPlus$mendLeftoverOnMobDeath(
            ServerLevel level, Entity attacker, Entity target, int xpAmount,
            CallbackInfoReturnable<Integer> cir) {

        // `attacker` is the killer. For our purposes we only care if
        // it's a player — mob-on-mob kills don't interact with MK
        // equipment containers.
        if (!(attacker instanceof Player player)) return;

        int remainingXp = cir.getReturnValue();
        if (remainingXp <= 0) return;

        int leftover = MendingHelper.applyLeftoverXp(level, player, remainingXp);

        // Only overwrite the return value if we actually consumed
        // something — avoids an unnecessary boxing round-trip on the
        // common "no MK mending item to repair" case.
        if (leftover != remainingXp) {
            cir.setReturnValue(leftover);
        }
    }
}
