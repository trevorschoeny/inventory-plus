package com.trevorschoeny.inventoryplus.features.mending;

import com.trevorschoeny.inventoryplus.InventoryPlusConfig;
import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.data.MKInventory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Shared mending logic for the Inventory Plus "equipment" MK container
 * (elytra/totem slots) and — optionally — the player's wider inventory.
 *
 * <p>Minecraft funnels mending XP through two independent entry points:
 *
 * <ul>
 *   <li><b>Mob death:</b>
 *       {@code LivingEntity.getExperienceReward} →
 *       {@code EnchantmentHelper.processMobExperience}. Vanilla iterates
 *       the killer's equipment slots via {@code runIterationOnEquipment}
 *       and consumes XP on any mending items it finds; leftover XP
 *       becomes the value of the dropped XP orb. Hooked by
 *       {@link com.trevorschoeny.inventoryplus.mixin.IPMendingMixin}.</li>
 *   <li><b>Orb pickup:</b>
 *       {@code ExperienceOrb.playerTouch} →
 *       {@code ExperienceOrb.repairPlayerItems}. Vanilla uses its own
 *       private implementation here — {@code getRandomItemWith(
 *       REPAIR_WITH_XP, livingEntity, ...)} — which walks only the
 *       vanilla equipment slots (hand/offhand/armor). MK containers are
 *       invisible to this call. Hooked by
 *       {@link com.trevorschoeny.inventoryplus.mixin.IPOrbMendingMixin}.</li>
 * </ul>
 *
 * <p>Both mixins run <i>after</i> vanilla's own mending pass and hand
 * us whatever XP survived. This helper then applies that leftover to
 * the MK equipment container and, if the inventory-wide toggle is on,
 * continues into the rest of the player's storage. Vanilla-priority is
 * preserved: hand/offhand/armor and equipment slots are drained before
 * any wider search happens.
 *
 * @cairn decision=003-work-with-vanilla — we reuse vanilla's
 * {@code modifyDurabilityToRepairFromXp} rather than reimplementing
 * XP→durability math. The helper only supplies extra containers to
 * search; the repair itself stays in Mojang's hands.
 */
public final class MendingHelper {

    private MendingHelper() {}

    /**
     * Applies leftover mending XP to the MK equipment container, then
     * (if enabled) to the rest of the player's storage. Returns the
     * final leftover XP for the caller to pass back via
     * {@code cir.setReturnValue(...)}.
     *
     * <p>Returns {@code startingXp} unchanged (and does nothing) when
     * there's no XP to spend, no server-side equipment container exists
     * for this player, and the inventory-wide toggle is off.
     *
     * @param level      the server level — needed by
     *                   {@code EnchantmentHelper.modifyDurabilityToRepairFromXp}
     *                   to look up the REPAIR_WITH_XP enchantment
     *                   component via the registry. Any ServerLevel
     *                   works, since registries are shared.
     * @param player     the player whose containers we're searching.
     * @param startingXp the leftover XP from the upstream vanilla
     *                   mending pass.
     * @return leftover XP after our passes; never greater than
     *         {@code startingXp}.
     */
    public static int applyLeftoverXp(ServerLevel level, Player player, int startingXp) {
        if (startingXp <= 0) return startingXp;
        int remainingXp = startingXp;

        // ── Equipment container pass ─────────────────────────────────
        // The "equipment" MK container holds the Inventory Plus elytra
        // and totem slots. Totems never take damage so they'll be
        // skipped by the isDamaged() check, but the elytra slot is the
        // main beneficiary. Items are mutated in place by vanilla's
        // modifyDurabilityToRepairFromXp; we setChanged() afterwards so
        // MenuKit's sync/save hooks fire.
        MKContainer container = MenuKit.getContainerForPlayer(
                "equipment", player.getUUID(), true);
        if (container != null) {
            boolean anythingRepaired = false;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (remainingXp <= 0) break;

                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !stack.isDamaged()) continue;

                int before = remainingXp;
                remainingXp = tryRepair(level, stack, remainingXp);
                if (remainingXp < before) {
                    anythingRepaired = true;
                }
            }
            if (anythingRepaired) {
                container.setChanged();
            }
        }

        // ── Inventory-wide mending pass (optional toggle) ────────────
        // See InventoryPlusConfig.mendingInventoryWide. When enabled, we
        // continue past the equipment container into the rest of the
        // player's storage (unheld hotbar slots, main inventory, pockets,
        // etc.). Runs AFTER the equipment pass above so equipment slots
        // get priority, matching vanilla's "closer-to-your-hand wins"
        // behavior.
        if (InventoryPlusConfig.get().mendingInventoryWide && remainingXp > 0) {
            remainingXp = applyInventoryWideMending(level, player, remainingXp);
        }

        return remainingXp;
    }

    /**
     * Scans the player's full storage for mending-enchanted damaged
     * items and applies repair. Skips slots already handled upstream
     * (vanilla armor/offhand, the held hotbar slot) and the equipment
     * container (already handled by {@link #applyLeftoverXp}).
     *
     * <p>Uses {@link MKInventory#forEachPlayerSlot} so we walk every
     * player-bound MenuKit container (pockets, etc.) plus the vanilla
     * main inventory / hotbar in one pass.
     */
    private static int applyInventoryWideMending(ServerLevel level, Player player, int startingXp) {
        // Boxed so the lambda can mutate it — ItemStack mutation is
        // fine in-place, but the running XP counter isn't.
        final int[] rem = { startingXp };
        final int selectedHotbar = player.getInventory().getSelectedSlot();

        MKInventory.forEachPlayerSlot(player, (loc, stack) -> {
            if (rem[0] <= 0) return false; // stop iteration

            String name = loc.containerName();

            // Skip vanilla slots that vanilla's own processMobExperience
            // / repairPlayerItems already iterated via
            // runIterationOnEquipment or getRandomItemWith. We don't
            // want to double-dip.
            if (name.equals("mk:armor") || name.equals("mk:offhand")) return true;
            if (name.equals("mk:hotbar") && loc.localPos() == selectedHotbar) return true;

            // Skip the equipment container — handled by the pass above.
            if (name.equals("equipment")) return true;

            if (stack.isEmpty() || !stack.isDamaged()) return true;

            int before = rem[0];
            rem[0] = tryRepair(level, stack, rem[0]);
            if (rem[0] < before) {
                // Stack was mutated in place by tryRepair — notify the
                // container so sync/save hooks fire. setChanged() is
                // idempotent so calling it per-mutation (rather than
                // tracking once per container) is fine.
                loc.container().setChanged();
            }
            return rem[0] > 0;
        });

        return rem[0];
    }

    /**
     * Port of vanilla's {@code ExperienceOrb.repairPlayerItems}
     * per-item repair step — the thing that actually makes a mending
     * item heal. Given a stack and a pool of XP, applies as much repair
     * as possible to the stack and returns leftover XP.
     *
     * <p>Algorithm (matches 1.21.11 bytecode):
     * <ol>
     *   <li>Ask {@code modifyDurabilityToRepairFromXp} how much
     *       durability the given XP <i>could</i> repair on this stack.
     *       For a mending item this is {@code xp * 2}; for a non-mending
     *       item it's just {@code xp} unchanged (the visitor doesn't
     *       fire).</li>
     *   <li>Clamp that to the stack's current damage value —
     *       {@code actualRepair = min(repairable, damage)}. This
     *       prevents over-repair on nearly-pristine items.</li>
     *   <li>Mutate the stack: {@code damage -= actualRepair}.</li>
     *   <li>Compute XP <i>consumed</i> proportionally to how much
     *       repair we actually did vs. what was offered:
     *       {@code xpUsed = actualRepair * xp / repairable}.
     *       When repair is uncapped this collapses to
     *       {@code xpUsed = xp / 2} (i.e., mending's "1 XP per 2
     *       durability" rate). When repair is capped by damage, less
     *       XP is spent, so the caller can carry leftover to the next
     *       item.</li>
     *   <li>Return {@code xp - xpUsed}.</li>
     * </ol>
     *
     * <p><b>Important:</b> this is the detail I got wrong on the first
     * attempt. The old code treated the return value of
     * {@code modifyDurabilityToRepairFromXp} as "leftover XP" — it is
     * not. It returns durability points. The stack is never modified
     * by that function; the caller is responsible for
     * {@code setDamageValue}.
     *
     * <p>For non-mending items, {@code repairable == xp}, so
     * {@code xpUsed = actualRepair * xp / xp = actualRepair}. Since
     * the stack isn't actually affected by mending, we also wouldn't
     * want to alter it — the guard {@code repairable == xp} short-
     * circuits and returns the input unchanged.
     */
    private static int tryRepair(ServerLevel level, ItemStack stack, int xp) {
        if (xp <= 0) return xp;

        int repairable = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, xp);

        // If the function returned our input unchanged, the stack has
        // no REPAIR_WITH_XP component (no mending). Skip without
        // touching the stack.
        if (repairable <= xp) {
            // Edge case: if `repairable < xp`, the visitor ran but
            // returned less than the input — unlikely for vanilla
            // mending, but treat it the same as "no mending" to stay
            // safe. The only well-defined behavior is repairable > xp
            // (e.g., xp*2 for mending level 1).
            //
            // For non-mending items this is the normal path and we
            // leave xp untouched.
            return xp;
        }

        int actualRepair = Math.min(repairable, stack.getDamageValue());
        if (actualRepair <= 0) return xp;

        // Actually heal the stack.
        stack.setDamageValue(stack.getDamageValue() - actualRepair);

        // Proportional XP cost: if mending offered 6 durability for 3
        // XP but we only needed 4 (because the item was only at
        // damage=4), xpUsed = 4 * 3 / 6 = 2, leaving 1 XP for the next
        // item. This matches vanilla's behavior.
        int xpUsed = actualRepair * xp / repairable;
        return xp - xpUsed;
    }
}
