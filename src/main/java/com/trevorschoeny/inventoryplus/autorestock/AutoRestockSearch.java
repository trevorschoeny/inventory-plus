package com.trevorschoeny.inventoryplus.autorestock;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Main-inventory source lookup for auto-restock.
 *
 * <p><b>Current smoke-pass scope.</b> Pass 1 of the spec's search order
 * (same-item exact match) is implemented. Pass 2 (highest-tier same-kind
 * fallback) is deferred — vanilla 1.21.11 refactored the tier hierarchy
 * from inheritance ({@code TieredItem}/{@code ArmorItem} subclasses with
 * a {@code Tier} enum) to {@link DataComponents}-driven classification.
 * The replacement detection ("is this item a pickaxe? what material?")
 * needs item-id-keyed tables or {@code ItemTags}; not a library-side gap,
 * just an implementation pass that wasn't critical for the smoke.
 *
 * <p>Spec section affected: {@code auto-restock.md} §"Tools" pass-2
 * "Highest material as fallback" and §"Armor" pass-2. Same-material refill
 * (the common case: player has two iron pickaxes in inventory) works
 * correctly via pass 1.
 *
 * <p>Source range is the player's main inventory ({@code Inventory.items}
 * slots 9-35). Hotbar (0-8), armor, and offhand are excluded as sources
 * per spec §"Sources and exclusions". Locked-slots / pocket / shulker
 * nesting are deferred (not in 18b or off-by-default in spec).
 */
public final class AutoRestockSearch {

    private AutoRestockSearch() {}

    /** Sentinel returned when no source slot matches. */
    public static final int NONE = -1;

    /** Main inventory range — inclusive lower bound. */
    public static final int MAIN_INV_START = 9;

    /** Main inventory range — exclusive upper bound. */
    public static final int MAIN_INV_END = 36;

    /**
     * Finds a source slot in main inventory for restocking the given
     * broken / depleted stack. Returns {@link #NONE} if no candidate
     * found.
     *
     * <p>Pass 1: walk slots 9-35 for an exact-item match. Returns the
     * first match. "Exact item" is {@link ItemStack#is(net.minecraft.world.item.Item)}
     * — same Item identity. Components (enchantments, names, durability)
     * are not compared because the spec's matching rule is "by item kind,
     * not name or NBT".
     *
     * <p>Pass 2 (deferred — see class javadoc) would: for tools / armor,
     * scan again for the highest-tier same-kind item.
     */
    public static int findSource(Inventory inv, ItemStack brokenStack) {
        if (brokenStack == null || brokenStack.isEmpty()) return NONE;

        for (int i = MAIN_INV_START; i < MAIN_INV_END; i++) {
            ItemStack candidate = inv.getItem(i);
            if (candidate.isEmpty()) continue;
            if (candidate.is(brokenStack.getItem())) {
                return i;
            }
        }
        return NONE;
    }

    /**
     * Finds a source slot for armor restock. The broken armor's
     * {@link Equippable#slot()} determines which {@link EquipmentSlot} a
     * candidate must target.
     *
     * <p>Pass 1: exact-item match (same as {@link #findSource}).
     * <p>Pass 2 (deferred): same-slot armor with highest defense.
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

        // Pass 1 — exact-item match. For the smoke pass this is what we
        // need: player has two iron helmets, one breaks, the other gets
        // pulled to the helmet slot.
        for (int i = MAIN_INV_START; i < MAIN_INV_END; i++) {
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
        return NONE;
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
