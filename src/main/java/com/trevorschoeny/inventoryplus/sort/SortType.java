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
 * <h3>Comparators order chunks, not slots</h3>
 *
 * <p>By the time a comparator runs, {@link Sorter} has already merged
 * every same-item stack into max-size chunks. Each element is one
 * target stack, so the comparator decides purely what order those
 * stacks sit in. Empty slots are padded on afterwards and always trail.
 *
 * <h3>Why ID_ASC is the default</h3>
 *
 * <p>Quantity-primary ordering scatters an item's leftover partial
 * stack away from its full stacks — a 51-coal sorts next to a 53-iron
 * rather than next to the 64-coal it came from — which reads as "it
 * didn't sort" even though it matched the spec exactly. Sorting by item
 * ID keeps every chunk of a type adjacent by construction, which is
 * what players mean by "sorted". Trev 2026-09-04.
 */
public enum SortType {

    /**
     * Largest stacks first; ties broken alphabetically.
     *
     * <p>This deliberately splits an item's partial stack away from its
     * full ones — inherent to ranking by count, not a defect. Kept
     * because "show me the biggest stacks" is a real use (see the
     * spec's rationale), just no longer the default.
     */
    QUANTITY_DESC(Comparator
            .<ItemStack>comparingInt(s -> -s.getCount())
            .thenComparing(SortType::idOf)),

    /** Smallest stacks first. */
    QUANTITY_ASC(null),

    /** Alphabetical descending (Z first). */
    ID_DESC(null),

    /**
     * Alphabetical ascending (A first) — "sort by type". Default.
     *
     * <p>Item ID is the primary key, so all chunks of one item land
     * adjacent. Within a type, bigger stacks come first, which puts the
     * full stacks ahead of the leftover partial.
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

    /**
     * Default for containers with no stored type — "sort by type"
     * (alphabetical, A first). See the class javadoc for why this isn't
     * {@link #QUANTITY_DESC}.
     */
    public static final SortType DEFAULT = ID_ASC;
}
