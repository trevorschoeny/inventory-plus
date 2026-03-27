package com.trevorschoeny.inventoryplus;

/**
 * Sorting algorithm options for the Sort Region feature.
 *
 * <p>Each variant defines how items are ordered after consolidation
 * (partial stacks are always merged first, regardless of method).
 */
public enum SortMethod {

    /**
     * Sort by total count of each item type (most abundant first),
     * then alphabetically by registry ID as tiebreaker. This is the
     * default — it groups your bulk materials at the top.
     */
    MOST_ITEMS("Sort by Most Items"),

    /**
     * Sort alphabetically by Minecraft registry ID (e.g.,
     * "minecraft:cobblestone" before "minecraft:diamond"). Produces
     * a stable, predictable order that doesn't change as quantities
     * shift. Secondary sort: largest stack first within the same item.
     */
    BY_ID("Sort by ID");

    private final String displayName;

    SortMethod(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label shown in the config dropdown. */
    public String displayName() {
        return displayName;
    }
}
