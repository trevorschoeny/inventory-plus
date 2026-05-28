package com.trevorschoeny.inventoryplus.autotoolswitch;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Tool-finding logic for Auto Tool Switch.
 *
 * <p>Given a target (block to break or mob to attack) and the player's
 * inventory, finds the best matching tool/weapon slot — respecting the
 * three-tier resolution priority:
 * <ol>
 *   <li><b>Tier 1</b> — already in hotbar (slots 0-8, excluding the active slot).</li>
 *   <li><b>Tier 2</b> — in a cyclable hotbar position (slots 9-35 claimed by any
 *       {@link com.trevorschoeny.inventoryplus.cyclable.HotbarCyclable} via
 *       {@link HotbarCyclableRegistry#cyclablePosition}).</li>
 *   <li><b>Tier 3</b> — elsewhere in main inventory (slots 9-35 with no cyclable claim).</li>
 * </ol>
 *
 * <p><b>Tier-first.</b> Within a tier, picks by score (material/damage,
 * durability tiebreak). Tier 1 beats tier 2 beats tier 3 even when
 * higher-tier matches have weaker materials — minimal-disruption priority
 * per the spec.
 *
 * <p><b>Ties broken by proximity</b> — within a tier and at the same score,
 * the slot closer to the player's active hotbar slot wins. (Per Trev's
 * 2026-05-25 decision on cyclable tiebreaker, generalized to all tiers.)
 *
 * <p><b>Locked slots excluded.</b> {@link LockedSlots#isLocked} skips
 * protected slots. Locked Items (item-type protection) isn't implemented
 * yet; will plug in here when it lands.
 */
public final class ToolFinder {

    private ToolFinder() {}

    /** Result of a search — the slot index (0-35) plus its score. */
    public record Match(int slotIndex, double score) {}

    // ─── Public entry points ─────────────────────────────────────────

    /**
     * Find the best matching tool for breaking a block. Returns
     * {@code null} if no switch is needed — either the player's active
     * slot already holds a matching tool, or no tool in the inventory
     * matches.
     *
     * <p>"Matching" = either correct-tool-for-drops (when the block
     * requires a correct tool) or destroy-speed-bonus (when the block
     * doesn't, e.g., dirt+shovel). Avoids picking arbitrary items just
     * because they're high-tier (e.g., wouldn't pick a netherite sword
     * for dirt).
     *
     * <p><b>Active-slot short-circuit (Trev 2026-05-25):</b> if the
     * player's active slot already holds a matching tool, returns null
     * — no disruption. Even if a "better" tool exists elsewhere, the
     * current one is good enough; switching would surprise the player.
     */
    public static Match findBestForBlock(Inventory inv, BlockState blockState) {
        ItemStack activeStack = inv.getItem(inv.getSelectedSlot());
        if (isMatchingToolForBlock(activeStack, blockState)) return null;
        return findBestByTier(inv,
                (stack) -> isMatchingToolForBlock(stack, blockState),
                (stack) -> scoreToolForBlock(stack, blockState));
    }

    /**
     * Find the best matching weapon for attacking a mob. Returns
     * {@code null} if no switch is needed.
     *
     * <p>The {@code entity} parameter is currently unused but kept in
     * the signature for future mob-type-aware enchantment preference
     * (e.g., Smite on undead).
     *
     * <p><b>Preference dominates tier for weapons</b> (Trev 2026-05-25).
     * Unlike tools — where the closest tier wins for minimal disruption —
     * a weapon preference is an explicit player choice, so the preferred
     * type wins even across tiers. A preferred trident in main inventory
     * (tier 3) beats a non-preferred sword in the hotbar (tier 1). The
     * earlier "tier-first" approach caused a two-hit switch (sword on
     * hit 1, trident on hit 2); searching preferred-first fixes it.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Active is already the preferred type → no switch.</li>
     *   <li>Search for the <b>preferred</b> type across all tiers
     *       (tier-prioritized among preferred candidates). If found,
     *       switch to it — regardless of where a non-preferred weapon
     *       might sit.</li>
     *   <li>No preferred weapon anywhere + active is already a (non-
     *       preferred) weapon → no switch (don't thrash sword→sword).</li>
     *   <li>No preferred weapon + active isn't a weapon → fall back to
     *       the best available non-preferred weapon (tier-prioritized).</li>
     * </ol>
     */
    public static Match findBestForMob(Inventory inv, LivingEntity entity) {
        ItemStack activeStack = inv.getItem(inv.getSelectedSlot());
        // (1) Already on the preferred weapon — nothing to do.
        if (isPreferredWeapon(activeStack)) return null;
        // (2) Preferred type wins across all tiers. Search it first so a
        // preferred weapon in deep inventory beats a non-preferred one in
        // the hotbar.
        Match preferred = findBestByTier(inv,
                ToolFinder::isPreferredWeapon,
                ToolFinder::scoreWeapon);
        if (preferred != null) return preferred;
        // (3) No preferred weapon available. If the active slot already
        // holds a (non-preferred) weapon, leave it — don't thrash between
        // equally-valid non-preferred weapons.
        if (isWeapon(activeStack)) return null;
        // (4) Active isn't a weapon at all — fall back to the best
        // available non-preferred weapon, tier-prioritized.
        return findBestByTier(inv,
                ToolFinder::isWeapon,
                ToolFinder::scoreWeapon);
    }

    // ─── Tiered search ───────────────────────────────────────────────

    /**
     * Three-tier search: tier 1 first; if no match, tier 2; if no match,
     * tier 3. Stops at the first tier with a match — tier-first priority
     * (minimal disruption beats material upgrade).
     */
    private static Match findBestByTier(Inventory inv, Predicate<ItemStack> isMatch, ToDoubleFunction<ItemStack> scorer) {
        int activeSlot = inv.getSelectedSlot();
        // Tier 1: hotbar slots (0-8), excluding the active slot itself.
        Match t1 = scanRange(inv, 0, 8, activeSlot, isMatch, scorer, slot -> true);
        if (t1 != null) return t1;
        // Tier 2: cyclable inv slots (claimed by some HotbarCyclable).
        Match t2 = scanRange(inv, 9, 35, activeSlot, isMatch, scorer, ToolFinder::isCyclable);
        if (t2 != null) return t2;
        // Tier 3: remaining inv slots (not cyclable).
        return scanRange(inv, 9, 35, activeSlot, isMatch, scorer, slot -> !isCyclable(slot));
    }

    /**
     * Scan a slot range, applying the tier filter + match predicate +
     * scorer. Returns the highest-scoring match (with proximity-to-
     * active tiebreaker). Returns {@code null} if no slot matches.
     */
    private static Match scanRange(Inventory inv, int fromSlot, int toSlot, int activeSlot,
                                    Predicate<ItemStack> isMatch, ToDoubleFunction<ItemStack> scorer,
                                    IntPredicate tierFilter) {
        Match best = null;
        for (int i = fromSlot; i <= toSlot; i++) {
            // Skip the active slot — auto-switch wouldn't pick the slot
            // the player is already on (it's the current hand's slot,
            // and we already determined it's not the right tool by
            // virtue of being here).
            if (i == activeSlot) continue;
            if (!tierFilter.test(i)) continue;
            // Locked-slot exclusion — BUT cycle slots are exempt. Under
            // the default "Lock Cycle Slots" config, every cycle slot
            // is auto-locked by Column Cycler. That lock is cycle-
            // derived, not user-initiated; for Auto Tool Switch, the
            // cycle IS the access path, so we want to consider the
            // slot. Locked Items (item-type protection) not yet
            // implemented; placeholder filter would go here.
            if (LockedSlots.isLocked(i) && !isCyclable(i)) continue;
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isMatch.test(stack)) continue;
            double score = scorer.applyAsDouble(stack);
            if (best == null || score > best.score()) {
                best = new Match(i, score);
            } else if (score == best.score()) {
                // Tiebreaker: closer to active hotbar slot wins.
                int dCurr = columnDistance(i, activeSlot);
                int dBest = columnDistance(best.slotIndex(), activeSlot);
                if (dCurr < dBest) {
                    best = new Match(i, score);
                }
            }
        }
        return best;
    }

    /**
     * Column-based distance from a slot to the active hotbar slot.
     * For hotbar slots (0-8): direct column index. For inv slots
     * (9-35): col = slot % 9. Distance = absolute column difference.
     */
    private static int columnDistance(int slot, int activeHotbarSlot) {
        int col = (slot <= 8) ? slot : (slot % 9);
        return Math.abs(col - activeHotbarSlot);
    }

    /**
     * True if the inv slot (9-35) is claimed by some registered
     * HotbarCyclable — i.e., it's tier 2 reachable.
     */
    private static boolean isCyclable(int slot) {
        return HotbarCyclableRegistry.cyclablePosition(slot) != -1;
    }

    // ─── Block tool matching ─────────────────────────────────────────

    /**
     * True if this item is the "right" kind of tool for the block.
     * <ul>
     *   <li>Block requires a correct tool (stone, etc.) → only items
     *       that pass {@link ItemStack#isCorrectToolForDrops} match.</li>
     *   <li>Block doesn't require a correct tool (dirt, etc.) → only
     *       items that give a destroy-speed bonus (>1.0) match. This
     *       picks shovels for dirt while rejecting arbitrary items.</li>
     * </ul>
     */
    private static boolean isMatchingToolForBlock(ItemStack stack, BlockState blockState) {
        if (stack.isEmpty()) return false;
        if (blockState.requiresCorrectToolForDrops()) {
            return stack.isCorrectToolForDrops(blockState);
        } else {
            return stack.getDestroySpeed(blockState) > 1.0f;
        }
    }

    /**
     * Score for a tool against a specific block. Higher is better.
     *
     * <p>Primary signal: destroy speed. Vanilla destroy speed reflects
     * tier (Netherite > Diamond > Iron > Stone > Wood > Gold) implicitly
     * — the same proxy auto-restock uses. Sufficient for v1; refine to
     * proper tier/enchantment matching when use surfaces a problem.
     *
     * <p>Tiebreaker: remaining durability (higher = better) at a small
     * weight, so it doesn't override the speed signal.
     */
    private static double scoreToolForBlock(ItemStack stack, BlockState blockState) {
        double speed = stack.getDestroySpeed(blockState);
        double remainingDurability = remainingDurability(stack);
        return speed * 100.0 + remainingDurability * 0.001;
    }

    // ─── Weapon matching ─────────────────────────────────────────────

    /**
     * True if this item is a melee weapon.
     *
     * <p>Uses vanilla 1.21.11's composite enchantable tags:
     * {@link ItemTags#WEAPON_ENCHANTABLE} (swords + spears + axes + mace)
     * union {@link ItemTags#TRIDENT_ENCHANTABLE} (trident — separate from
     * WEAPON_ENCHANTABLE because tridents take a different enchant set).
     *
     * <p>Bows and crossbows are <b>not</b> in either tag (they live in
     * BOW_ENCHANTABLE / CROSSBOW_ENCHANTABLE), so they're excluded
     * automatically — per Trev's spec.
     *
     * <p>Modded melee weapons (spears, glaives, etc.) auto-included as
     * long as they're properly tagged into one of the sub-tags
     * (#minecraft:swords, #minecraft:spears, etc.).
     */
    private static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(ItemTags.WEAPON_ENCHANTABLE)
                || stack.is(ItemTags.TRIDENT_ENCHANTABLE);
    }

    /**
     * True if this item matches the player's currently-configured
     * weapon preference. Used by {@link #findBestForMob}'s three-case
     * active-slot logic and as the predicate when searching for the
     * preferred type only.
     */
    private static boolean isPreferredWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        WeaponPreference pref = IPConfig.autoToolSwitchWeaponPreference();
        return switch (pref) {
            case SWORD -> stack.is(ItemTags.SWORDS);
            case AXE -> stack.is(ItemTags.AXES);
            case MACE -> stack.is(Items.MACE);
            case TRIDENT -> stack.is(Items.TRIDENT);
            case SPEAR -> stack.is(ItemTags.SPEARS);
        };
    }

    /**
     * Score for a weapon. Higher is better.
     *
     * <p>Primary signal: <b>preferred weapon type wins</b> regardless
     * of other type's material — e.g., a preferred AXE in iron beats a
     * non-preferred SWORD in netherite. Configurable via the YACL
     * "Preferred Weapon" cycler.
     *
     * <p>Within the same type-tier (all preferred, or all non-
     * preferred), material via maxDamage proxy (Netherite > Diamond >
     * Iron > Stone > Wood > Gold; minor Gold mismatch with spec's
     * ordering — same caveat as auto-restock). Durability as a small
     * tiebreaker.
     *
     * <p>Mob-type-aware enchantment preference (Smite on undead,
     * Sharpness elsewhere) is deferred — the {@code entity} parameter
     * is plumbed through findBestForMob for that future work.
     */
    private static double scoreWeapon(ItemStack stack) {
        WeaponPreference pref = IPConfig.autoToolSwitchWeaponPreference();
        double typeScore;
        if (stack.is(ItemTags.SWORDS)) {
            typeScore = (pref == WeaponPreference.SWORD) ? 100_000.0 : 10_000.0;
        } else if (stack.is(ItemTags.AXES)) {
            typeScore = (pref == WeaponPreference.AXE) ? 100_000.0 : 8_000.0;
        } else if (stack.is(ItemTags.SPEARS)) {
            typeScore = (pref == WeaponPreference.SPEAR) ? 100_000.0 : 9_000.0;
        } else if (stack.is(Items.MACE)) {
            typeScore = (pref == WeaponPreference.MACE) ? 100_000.0 : 7_000.0;
        } else if (stack.is(Items.TRIDENT)) {
            typeScore = (pref == WeaponPreference.TRIDENT) ? 100_000.0 : 6_000.0;
        } else if (stack.is(ItemTags.WEAPON_ENCHANTABLE) || stack.is(ItemTags.TRIDENT_ENCHANTABLE)) {
            // Fallback for modded melee weapons that match the broad tag
            // but aren't in any of our hard-coded type buckets. Treat as
            // a generic low-priority weapon; preference can't apply.
            typeScore = 5_000.0;
        } else {
            return -1.0; // not a weapon
        }
        double materialScore = stack.getMaxDamage(); // 0 for non-damageable, fine
        double remainingDurability = remainingDurability(stack);
        return typeScore + materialScore * 0.1 + remainingDurability * 0.001;
    }

    // ─── Shared helpers ──────────────────────────────────────────────

    /**
     * Remaining durability of the stack — max damage minus current
     * damage value. Returns 0 for non-damageable items (where max
     * damage is 0).
     */
    private static double remainingDurability(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) return 0;
        return max - stack.getDamageValue();
    }
}
