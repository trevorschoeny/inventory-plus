package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.buttonmode.ModeStop;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/**
 * The four sort orders, in cycle order (`features/sorting.md`). Right-click
 * on the Sort button walks this list; `features/button-modes.md` has the
 * gesture and scope rules.
 *
 * <h3>Each stop carries its own ordering</h3>
 *
 * <p>The comparator lives on the constant rather than in a branch inside
 * {@link Sorter}. Stops differ only in how they order the chunk list, so
 * adding one is a one-line comparator with no edit to the sorter.
 *
 * <h3>Comparators order item groups, not stacks</h3>
 *
 * <p>A comparator sees one entry per item type, whose {@code getCount()} is
 * the item's total across the region. {@link Sorter} splits each group into
 * max-size stacks only after the ordering is decided, which keeps every
 * stack of an item adjacent. Category and Rarity look the item up once per
 * type for the same reason. Empty slots are padded on afterwards and trail.
 *
 * <p>Three stops from the earlier seven (Quantity ↑, ID ↓, Rarity ↑) were
 * cut on 2026-09-05: with a right-click cycle every stop costs presses, so
 * symmetry stopped being free. Disabled went the same day (Trev): a stop
 * that does nothing still costs a press on every lap of the cycle.
 */
public enum SortType implements ModeStop {

    /**
     * Vanilla's creative-tab order: blocks with blocks, tools with tools,
     * food with food. Default. Rank comes from the game's own tabs
     * ({@link CategoryOrder}) so it tracks vanilla across versions.
     */
    CATEGORY("Category", Comparator
            .<ItemStack>comparingInt(CategoryOrder::rankOf)
            .thenComparing(SortType::idOf)),

    /** Most of an item first; ties alphabetical. */
    QUANTITY_DESC("Quantity ↓", Comparator
            .<ItemStack>comparingInt(s -> -s.getCount())
            .thenComparing(SortType::idOf)),

    /** Alphabetical by item id, A first. */
    ID_ASC("ID ↑", Comparator
            .<ItemStack, String>comparing(SortType::idOf)
            .thenComparingInt(s -> -s.getCount())),

    /** Highest rarity first (Epic → Rare → Uncommon → Common); ties alphabetical. */
    RARITY_DESC("Rarity ↓", Comparator
            .<ItemStack>comparingInt(s -> -s.getRarity().ordinal())
            .thenComparing(SortType::idOf));

    private final String label;
    private final Comparator<ItemStack> comparator;

    SortType(String label, Comparator<ItemStack> comparator) {
        this.label = label;
        this.comparator = comparator;
    }

    @Override
    public String label() {
        return label;
    }

    /** The chunk ordering. Every stop has one. */
    public Comparator<ItemStack> comparator() {
        return comparator;
    }

    /** Registry id: the key for ID ↑, the tiebreaker for the rest. */
    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Global default. Category is the order that reads as tidy (`sorting.md`). */
    public static final SortType DEFAULT = CATEGORY;
}
