package com.trevorschoeny.inventoryplus.api;

/**
 * A source the shared cycle HUD can ask: "is there an active cycle on this
 * hotbar slot, and if so, what should I draw?"
 *
 * <p>Each cycler (Column Cycler here, Pocket Cycler in Inventory Max)
 * registers one implementation with {@link CycleHudRegistry}. The HUD
 * iterates sources and renders the first non-null {@link CycleView} for the
 * player's selected hotbar slot, so the HUD render code never depends on a
 * particular cycler and one HUD serves them all.
 *
 * <p>Each source owns its own gating: a source returns {@code null} unless
 * its feature is enabled, its HUD mode is on, and the given hotbar slot
 * actually has an active cycle for that cycler. So the HUD shows nothing
 * until some cycler claims the slot.
 */
public interface CycleHudSource {

    /**
     * Returns the {@link CycleView} to draw for {@code hotbarSlot} (0 to 8), or
     * {@code null} if this source has no active, HUD-enabled cycle there.
     *
     * <p>Called once per frame while no inventory screen is open, so keep it
     * cheap.
     */
    CycleView cycleViewForHotbar(int hotbarSlot);

    /**
     * Stack position when several cyclers are active on the same hotbar slot.
     * The HUD stacks strips from the bottom up by ascending value, so a lower
     * value sits nearer the hotbar. Pocket Cycler returns the lowest so it is
     * always on the bottom, and Column Cycler sits above it. Ties fall back to
     * registration order.
     *
     * <p>Default {@code 100} leaves room for future cyclers to slot in above
     * the bottom-pinned Pocket Cycler without colliding.
     */
    default int hudStackOrder() {
        return 100;
    }

    /**
     * Which axis this cycler's strip runs along: {@code true} = vertical,
     * {@code false} = horizontal. It holds whether the cycler is alone or
     * sharing the slot. Column Cycler is vertical (a column literally is one);
     * Pocket Cycler is horizontal. In the cross the two share the held-item
     * cell at the centre; alone, a vertical cycler gets a one-cell bar under
     * its held item and a horizontal one is the plain mini-hotbar strip.
     *
     * <p>Formerly {@code verticalInCross}, from when a lone cycler always
     * rendered horizontally. Renamed because the flag now decides the solo
     * layout too.
     */
    default boolean vertical() {
        return false;
    }
}
