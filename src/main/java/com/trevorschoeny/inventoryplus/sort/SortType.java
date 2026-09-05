package com.trevorschoeny.inventoryplus.sort;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Sort modes the cycle stops on, per IP spec (`IP features/sorting.md`).
 *
 * <h3>Each stop carries its own ordering</h3>
 *
 * <p>The comparator lives on the enum constant rather than in a branch
 * inside {@link Sorter}. Sort types differ <em>only</em> in how they
 * order the chunk list — group, distribute, and apply are shared by all
 * of them — so the ordering is the one thing a stop actually owns.
 * Putting it here means adding a stop is a one-line change with no edit
 * to {@link Sorter}, instead of another arm on a branch that would grow
 * to seven.
 *
 * <p>A {@code null} comparator means "declared in the spec, not
 * implemented yet". {@link Sorter} throws
 * {@code UnsupportedOperationException} for those so a stored value
 * can't silently misbehave.
 *
 * <h3>Comparators order item groups, not stacks</h3>
 *
 * <p>A comparator sees exactly one entry per item type, and that
 * entry's {@code getCount()} is the total of the item across the whole
 * region. So "quantity" means "how much of this item you have", not
 * "how big is this one stack". {@link Sorter} splits each group into
 * max-size stacks only after the ordering is decided, which is what
 * keeps every stack of an item adjacent. Empty slots are padded on
 * afterwards and always trail.
 *
 * <p>This is deliberate. Ranking loose stacks instead strands an item's
 * leftover partial away from its full stacks, and a chest where coal
 * appears in two unrelated places reads as unsorted however correct the
 * ordering is. Trev 2026-09-04.
 */
public enum SortType {

    /**
     * Most of an item first; ties broken alphabetically. Default.
     *
     * <p>Ranks by the item's total across the region, so a chest with
     * 150 cobblestone leads with all three of its cobblestone stacks
     * before moving to the next item. An item held as many small stacks
     * can therefore outrank one held as a single full stack, which is
     * the honest reading of "sort by quantity" once stacks of a type
     * are kept together.
     */
    QUANTITY_DESC(Comparator
            .<ItemStack>comparingInt(s -> -s.getCount())
            .thenComparing(SortType::idOf)),

    /** Least of an item first. */
    QUANTITY_ASC(null),

    /** Alphabetical descending (Z first). */
    ID_DESC(null),

    /**
     * Alphabetical ascending (A first) — "sort by type".
     *
     * <p>Item ID is the primary key. The count tiebreaker never fires,
     * since there is one entry per item type.
     */
    ID_ASC(Comparator
            .<ItemStack, String>comparing(SortType::idOf)
            .thenComparingInt(s -> -s.getCount())),

    /** Highest rarity first (Epic → Common). */
    RARITY_DESC(null),

    /** Lowest rarity first. */
    RARITY_ASC(null),

    /** Sort off for this container — keybind no-ops while stored. */
    DISABLED(null);

    /** Chunk ordering for this stop, or null if not implemented yet. */
    private final @Nullable Comparator<ItemStack> comparator;

    SortType(@Nullable Comparator<ItemStack> comparator) {
        this.comparator = comparator;
    }

    /**
     * The chunk ordering for this stop, or {@code null} if the type is
     * declared but not implemented. Callers must handle null —
     * {@link Sorter} turns it into an
     * {@code UnsupportedOperationException}.
     */
    public @Nullable Comparator<ItemStack> comparator() {
        return comparator;
    }

    /** Registry ID — the ordering key for ID sorts, the tiebreaker for the rest. */
    private static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Default for containers with no stored type. */
    public static final SortType DEFAULT = QUANTITY_DESC;
}
