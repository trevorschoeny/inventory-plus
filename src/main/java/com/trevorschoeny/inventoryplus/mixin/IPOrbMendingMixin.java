package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.mending.MendingHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>Orb-pickup mending path.</b> Repairs the Inventory Plus equipment
 * container (and optionally the wider inventory) using XP from orbs
 * the player walks over.
 *
 * <p>This is the <i>primary</i> path that normal gameplay hits. When a
 * mob is killed it drops XP as {@code ExperienceOrb} entities; the
 * player picks them up; vanilla runs its own mending pass; leftover XP
 * gets added to the player's level bar. Specifically:
 *
 * <pre>
 *   ExperienceOrb.playerTouch(player)
 *     └─ ExperienceOrb.repairPlayerItems(player, value)
 *          └─ EnchantmentHelper.getRandomItemWith(REPAIR_WITH_XP, player, ...)
 *               // ↑ walks ONLY vanilla equipment slots; MK containers
 *               //   are invisible here
 *          └─ modifyDurabilityToRepairFromXp(...)
 *          └─ return leftover  ← WE INJECT HERE, AT RETURN
 *     └─ player.giveExperiencePoints(leftover)
 * </pre>
 *
 * <p>Without this mixin, mending items in the IP equipment container
 * (elytra, totem) would never repair from orb pickups — vanilla's
 * {@code getRandomItemWith} call has no idea MK containers exist, so it
 * just sees the vanilla slots, picks one (or none), and whatever's left
 * flies straight to the player's XP bar. We intercept at RETURN and
 * apply that leftover through {@link MendingHelper} before it's handed
 * back to {@code playerTouch}.
 *
 * <p><b>Priority note:</b> because we inject at RETURN, vanilla's
 * normal priority is preserved — your held mending sword will still
 * drain first, offhand next, then armor, then the elytra in the MK
 * equipment slot. This is deliberate: held-item-first matches player
 * expectations and vanilla's own ordering.
 *
 * @cairn decision=003-work-with-vanilla — vanilla runs its own mending
 * pass first; we only pick up whatever XP it didn't want. No
 * reimplementation of the XP→durability formula.
 * @cairn decision=007-precise-mixin-injection — @Inject at RETURN,
 * never @Overwrite.
 * @cairn decision=009-module-prefixed-mixin-naming — "IP" prefix.
 */
@Mixin(ExperienceOrb.class)
public class IPOrbMendingMixin {

    @Inject(method = "repairPlayerItems", at = @At("RETURN"), cancellable = true)
    private void inventoryPlus$mendLeftoverOnOrbPickup(
            ServerPlayer player, int value,
            CallbackInfoReturnable<Integer> cir) {

        int remainingXp = cir.getReturnValue();
        if (remainingXp <= 0) return;

        // player.level() returns ServerLevel directly on a ServerPlayer
        // reference (overloaded in ServerPlayer; in 1.21.11 there's no
        // serverLevel() accessor). Any ServerLevel works for the
        // registry lookup inside modifyDurabilityToRepairFromXp.
        int leftover = MendingHelper.applyLeftoverXp(
                player.level(), player, remainingXp);

        if (leftover != remainingXp) {
            cir.setReturnValue(leftover);
        }
    }
}
