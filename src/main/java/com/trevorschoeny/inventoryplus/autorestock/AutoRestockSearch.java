package com.trevorschoeny.inventoryplus.autorestock;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Source lookup for auto-restock. Scans the player's hotbar (0–8) and
 * main inventory (9–35) — armor and offhand excluded.
 *
 * <p><b>Scan order: hotbar first, then main inv.</b> First-match wins
 * for {@link #findSource} / {@link #findArmorSource}; the higher-durability
 * variants iterate the whole range and pick the least-damaged candidate.
 * Hotbar-first ordering means the active-hand restock path can usually
 * resolve via a selected-slot move (cheap, no inventory mutation)
 * rather than a full SWAP click.
 *
 * <p>The {@code excludeSlot} parameter on {@link #findSource} /
 * {@link #findHigherDurability} lets the caller skip the destination
 * slot itself — important for active-hotbar restock, where the slot
 * being refilled is also in the scan range.
 *
 * <p><b>Two-pass search for on-break refill.</b> {@link #findSource} and
 * {@link #findArmorSource} run a same-Item pass first. If that finds
 * nothing, they fall back to a same-kind pass driven by vanilla item
 * tags ({@code ItemTags.PICKAXES} etc. for tools,
 * {@code ItemTags.HEAD_ARMOR} etc. for armor) and pick the candidate
 * with the highest {@link ItemStack#getMaxDamage()} — effectively
 * "best material" (Netherite > Diamond > Iron > Stone > Wood/Gold in
 * the durability ordering). This lets a broken diamond pickaxe be
 * replaced by an iron pickaxe if no diamond is in inventory, rather
 * than leaving the active hand empty.
 *
 * <p>The before-break searches ({@link #findHigherDurability},
 * {@link #findHigherDurabilityArmor}) ALSO do the same fallback, gated
 * by {@link #FALLBACK_MIN_REMAINING} — the fallback candidate must have
 * more than that many points of durability remaining, so the swap
 * never pulls another about-to-break tool into the active slot.
 *
 * <p>The "auto-upgrade while still using a lower-tier tool" UX
 * surprise is acknowledged — the deferred Power-Users config can let
 * players disable the fallback if it bothers them. See
 * {@link #FALLBACK_MIN_REMAINING} comment.
 */
public final class AutoRestockSearch {

    private AutoRestockSearch() {}

    /** Sentinel returned when no source slot matches, or no exclusion. */
    public static final int NONE = -1;

    /** Hotbar range — inclusive lower bound. */
    public static final int HOTBAR_START = 0;

    /** Main inventory range — inclusive lower bound (hotbar end + 1). */
    public static final int MAIN_INV_START = 9;

    /** Main inventory range — exclusive upper bound. */
    public static final int MAIN_INV_END = 36;

    /**
     * Minimum remaining durability a fallback candidate must have during a
     * <i>before-break</i> swap. Without this gate, the swap could pull
     * another about-to-break tool into the active slot — pointless churn.
     * Matches {@code AutoRestockTicker.BEFORE_BREAK_THRESHOLD}.
     *
     * <p><b>Power-Users config (deferred):</b> "Disable best-material
     * fallback" should let players opt out of the cross-Item fallback
     * entirely (both on-break and before-break). Surfaced under the
     * Power Users tab when that tab gets populated. Not built yet — note
     * only.
     */
    private static final int FALLBACK_MIN_REMAINING = 10;

    /**
     * Finds a source slot for restocking the given broken / depleted stack.
     * Returns {@link #NONE} if no candidate found.
     *
     * <p>Walks slots {@code HOTBAR_START..MAIN_INV_END} (0–35) for an
     * exact-item match. Returns the first match. "Exact item" is
     * {@link ItemStack#is(net.minecraft.world.item.Item)} — same Item
     * identity; components (enchantments, names, durability) are not
     * compared because the spec's matching rule is "by item kind, not
     * name or NBT".
     *
     * @param excludeSlot a slot index in the hotbar that should be
     *                    skipped (e.g., the active hotbar slot when
     *                    restocking the active hand — that slot is the
     *                    destination, not a valid source). Pass
     *                    {@link #NONE} for no exclusion.
     */
    public static int findSource(Inventory inv, ItemStack brokenStack, int excludeSlot) {
        if (brokenStack == null || brokenStack.isEmpty()) return NONE;

        // Pass 1 — exact same-Item match. Locked sources skipped per spec.
        for (int i = HOTBAR_START; i < MAIN_INV_END; i++) {
            if (i == excludeSlot) continue;
            if (LockedSlots.isLocked(i)) continue;
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (candidate.is(brokenStack.getItem())) {
                return i;
            }
        }
        // Pass 2 — best-material fallback within the same tool kind.
        // Non-tool items (food/blocks/shield/etc.) return null here and
        // skip the fallback; they only have same-Item refill.
        TagKey<Item> toolTag = toolTagOf(brokenStack);
        if (toolTag == null) return NONE;
        // On-break path: no minRemaining constraint — any same-kind tool
        // beats an empty hand, even one that's also worn.
        return findBestByMaxDamage(inv, toolTag, excludeSlot, 0);
    }

    /**
     * Finds a source slot for armor restock. The broken armor's
     * {@link Equippable#slot()} determines which {@link EquipmentSlot} a
     * candidate must target.
     *
     * <p>Pass 1: exact-item match (same as {@link #findSource}).
     * <p>Pass 2: best-material fallback within the matching armor tag
     * ({@code HEAD_ARMOR}, {@code CHEST_ARMOR}, etc.) — no minRemaining
     * constraint since the original is already gone.
     *
     * <p>The returned slot's stack is suitable for a
     * {@link net.minecraft.world.inventory.ClickType#QUICK_MOVE} from main
     * inventory — vanilla's quick-move on InventoryMenu routes Equippable
     * items to their matching empty armor slot.
     */
    public static int findArmorSource(Inventory inv, ItemStack brokenArmor) {
        if (brokenArmor == null || brokenArmor.isEmpty()) return NONE;
        EquipmentSlot wantedSlot = equippableSlot(brokenArmor);
        if (wantedSlot == null) return NONE;

        // Pass 1 — exact same-Item match (with Equippable-slot guard). No
        // excludeSlot needed because the armor destination isn't in the
        // hotbar/main-inv scan range.
        for (int i = HOTBAR_START; i < MAIN_INV_END; i++) {
            if (LockedSlots.isLocked(i)) continue;
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (candidate.is(brokenArmor.getItem())) {
                // Confirm the candidate is equippable to the same slot —
                // protects against same-id-different-component edge cases
                // (e.g., re-textured custom helmet whose Equippable was
                // changed). For vanilla items this is always true.
                if (isEquippableToSlot(candidate, wantedSlot)) {
                    return i;
                }
            }
        }
        // Pass 2 — best-material fallback for the matching armor slot.
        TagKey<Item> armorTag = armorTagOf(wantedSlot);
        if (armorTag == null) return NONE;
        // On-break path: no minRemaining constraint.
        return findBestArmorByMaxDamage(inv, armorTag, wantedSlot, 0);
    }

    /**
     * Finds the best same-Item replacement for a before-break swap. Same
     * matching rule as {@link #findSource} (exact Item identity, locked
     * sources skipped) plus: candidate must have strictly more durability
     * remaining than {@code current}. "More durability remaining" is
     * equivalent to "lower {@link ItemStack#getDamageValue()}" because same
     * Item implies same max damage.
     *
     * <p>Scans the whole main inventory and returns the slot of the
     * least-damaged candidate — "any same Item" policy, but biased to the
     * freshest available so the player gets maximum mileage before the
     * next swap. Returns {@link #NONE} if no candidate exists.
     */
    public static int findHigherDurability(Inventory inv, ItemStack current, int excludeSlot) {
        if (current == null || current.isEmpty()) return NONE;

        // Pass 1 — same-Item with strictly lower damage value than current.
        int currentDamage = current.getDamageValue();
        int bestSlot = NONE;
        int bestDamage = currentDamage;       // candidate must be strictly < this
        for (int i = HOTBAR_START; i < MAIN_INV_END; i++) {
            if (i == excludeSlot) continue;
            if (LockedSlots.isLocked(i)) continue;
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(current.getItem())) continue;
            int candidateDamage = candidate.getDamageValue();
            if (candidateDamage < bestDamage) {
                bestDamage = candidateDamage;
                bestSlot = i;
            }
        }
        if (bestSlot != NONE) return bestSlot;

        // Pass 2 — best-material fallback within the same tool kind.
        // Candidate must have remaining > FALLBACK_MIN_REMAINING so we
        // don't pull another about-to-break tool into the active slot.
        TagKey<Item> toolTag = toolTagOf(current);
        if (toolTag == null) return NONE;
        return findBestByMaxDamage(inv, toolTag, excludeSlot, FALLBACK_MIN_REMAINING);
    }

    /**
     * Armor variant of {@link #findHigherDurability} — also enforces that
     * the candidate equips to the same slot as {@code currentArmor} (same
     * defensive check as {@link #findArmorSource} against same-id
     * different-component edge cases).
     */
    public static int findHigherDurabilityArmor(Inventory inv, ItemStack currentArmor) {
        if (currentArmor == null || currentArmor.isEmpty()) return NONE;
        EquipmentSlot wantedSlot = equippableSlot(currentArmor);
        if (wantedSlot == null) return NONE;

        // Pass 1 — same-Item with lower damage value than current, in the
        // matching equippable slot.
        int currentDamage = currentArmor.getDamageValue();
        int bestSlot = NONE;
        int bestDamage = currentDamage;
        for (int i = HOTBAR_START; i < MAIN_INV_END; i++) {
            if (LockedSlots.isLocked(i)) continue;
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(currentArmor.getItem())) continue;
            if (!isEquippableToSlot(candidate, wantedSlot)) continue;
            int candidateDamage = candidate.getDamageValue();
            if (candidateDamage < bestDamage) {
                bestDamage = candidateDamage;
                bestSlot = i;
            }
        }
        if (bestSlot != NONE) return bestSlot;

        // Pass 2 — best-material armor fallback for the matching slot.
        // Candidate must have remaining > FALLBACK_MIN_REMAINING.
        TagKey<Item> armorTag = armorTagOf(wantedSlot);
        if (armorTag == null) return NONE;
        return findBestArmorByMaxDamage(inv, armorTag, wantedSlot, FALLBACK_MIN_REMAINING);
    }

    /**
     * Returns the vanilla {@link ItemTags} entry the stack belongs to for
     * the five major tool kinds (pickaxes, axes, shovels, hoes, swords),
     * or {@code null} if it's not in any of them. Single-item "tools"
     * (shield, trident, bow, crossbow, fishing rod, etc.) return null and
     * fall back to same-Item refill only.
     */
    private static TagKey<Item> toolTagOf(ItemStack stack) {
        if (stack.is(ItemTags.PICKAXES)) return ItemTags.PICKAXES;
        if (stack.is(ItemTags.AXES))     return ItemTags.AXES;
        if (stack.is(ItemTags.SHOVELS))  return ItemTags.SHOVELS;
        if (stack.is(ItemTags.HOES))     return ItemTags.HOES;
        if (stack.is(ItemTags.SWORDS))   return ItemTags.SWORDS;
        return null;
    }

    /**
     * Returns the {@link ItemTags} entry for armor in the given equipment
     * slot, or {@code null} for non-armor slots.
     */
    private static TagKey<Item> armorTagOf(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD  -> ItemTags.HEAD_ARMOR;
            case CHEST -> ItemTags.CHEST_ARMOR;
            case LEGS  -> ItemTags.LEG_ARMOR;
            case FEET  -> ItemTags.FOOT_ARMOR;
            default    -> null;
        };
    }

    /**
     * Scans 0–35 for the candidate in {@code tag} with the highest
     * {@code maxDamage} — i.e., the best-material item of that kind.
     * Ties (e.g., two iron pickaxes) go to the hotbar copy first thanks
     * to the 0→35 scan order plus strict {@code >} comparison.
     *
     * @param minRemaining if {@code > 0}, candidates must have remaining
     *                     durability strictly greater than this value.
     *                     Used by the before-break path to avoid pulling
     *                     another about-to-break tool. Pass {@code 0} for
     *                     no constraint (on-break path).
     */
    private static int findBestByMaxDamage(Inventory inv, TagKey<Item> tag, int excludeSlot,
                                           int minRemaining) {
        int bestSlot = NONE;
        int bestMaxDamage = -1;
        for (int i = HOTBAR_START; i < MAIN_INV_END; i++) {
            if (i == excludeSlot) continue;
            if (LockedSlots.isLocked(i)) continue;
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(tag)) continue;
            if (minRemaining > 0) {
                int remaining = candidate.getMaxDamage() - candidate.getDamageValue();
                if (remaining <= minRemaining) continue;
            }
            int maxDamage = candidate.getMaxDamage();
            if (maxDamage > bestMaxDamage) {
                bestMaxDamage = maxDamage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /** Armor variant: also enforces matching equippable slot. */
    private static int findBestArmorByMaxDamage(Inventory inv, TagKey<Item> tag,
                                                EquipmentSlot wantedSlot, int minRemaining) {
        int bestSlot = NONE;
        int bestMaxDamage = -1;
        for (int i = HOTBAR_START; i < MAIN_INV_END; i++) {
            if (LockedSlots.isLocked(i)) continue;
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (!candidate.is(tag)) continue;
            if (!isEquippableToSlot(candidate, wantedSlot)) continue;
            if (minRemaining > 0) {
                int remaining = candidate.getMaxDamage() - candidate.getDamageValue();
                if (remaining <= minRemaining) continue;
            }
            int maxDamage = candidate.getMaxDamage();
            if (maxDamage > bestMaxDamage) {
                bestMaxDamage = maxDamage;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * Returns the {@link EquipmentSlot} this item equips to, or {@code null}
     * if it isn't equipment. 1.21.11 routes this through the
     * {@link DataComponents#EQUIPPABLE} component — there is no
     * {@code ArmorItem} subclass to instanceof-check against.
     */
    public static EquipmentSlot equippableSlot(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null ? equippable.slot() : null;
    }

    /**
     * True if the stack is equippable into the given slot per its
     * {@link Equippable} component.
     */
    public static boolean isEquippableToSlot(ItemStack stack, EquipmentSlot slot) {
        EquipmentSlot itemSlot = equippableSlot(stack);
        return itemSlot == slot;
    }
}
