package com.trevorschoeny.inventoryplus.sort;

/**
 * Sort modes the cycle stops on, per IP spec (`IP features/sorting.md`).
 *
 * <p>MVP implements only {@link #QUANTITY_DESC}; the rest are wired in
 * the type system + persistence layer so cycle/power-user work can
 * fill them in without re-shaping storage. {@link Sorter} throws
 * {@code UnsupportedOperationException} for unimplemented types so
 * a stored non-default value can't silently misbehave.
 */
public enum SortType {
    /** Largest stacks first. Default for new containers. */
    QUANTITY_DESC,
    /** Smallest stacks first. */
    QUANTITY_ASC,
    /** Alphabetical descending (Z first). */
    ID_DESC,
    /** Alphabetical ascending (A first). */
    ID_ASC,
    /** Highest rarity first (Epic → Common). */
    RARITY_DESC,
    /** Lowest rarity first. */
    RARITY_ASC,
    /** Sort off for this container — keybind no-ops while stored. */
    DISABLED;

    public static final SortType DEFAULT = QUANTITY_DESC;
}
