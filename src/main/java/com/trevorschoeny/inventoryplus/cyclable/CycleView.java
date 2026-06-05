package com.trevorschoeny.inventoryplus.cyclable;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A cycler-agnostic snapshot of what the shared cycle HUD should draw for a
 * given hotbar slot: the ordered items in the cycle's display window, which
 * index holds the "current" (in-hand) item, and the raw visual order.
 *
 * <p>Produced by a {@link CycleHudSource} (Column Cycler, Pocket Cycler, …)
 * and consumed by {@link CycleHud}. The HUD render code never knows which
 * cycler produced the view — it just draws the items and highlights the
 * current index.
 *
 * <h3>Display-order convention</h3>
 *
 * {@code orderedItems} is in left-to-right display order; {@code highlightIndex}
 * points at the in-hand item:
 * <ul>
 *   <li>2 members: {@code [current, other]}, highlight 0.</li>
 *   <li>3+ members: {@code [prev, current, next, next-next, …]}, highlight 1.</li>
 * </ul>
 *
 * <p>{@code visualOrder} is the raw top→bottom layout with the in-hand (hotbar)
 * item LAST. The HUD cross's <b>vertical</b> arm uses this directly: it stacks
 * the column upward from the held item at the bottom (no reorder), so the
 * column reads the same as the real inventory column.
 *
 * @param orderedItems   the cycle's items in display order (never null; may
 *                       contain {@link ItemStack#EMPTY} entries)
 * @param highlightIndex index into {@code orderedItems} of the in-hand item
 * @param visualOrder    raw top→bottom order, in-hand item last
 */
public record CycleView(List<ItemStack> orderedItems, int highlightIndex, List<ItemStack> visualOrder) {

    /** Number of slots the HUD strip will draw. */
    public int size() {
        return orderedItems.size();
    }

    /**
     * Build a view from a cycle's items in visual top→bottom order with the
     * in-hand (hotbar) item LAST — the shape both Column Cycler and Pocket
     * Cycler produce. Applies the shared display convention for
     * {@code orderedItems} and retains the raw list as {@code visualOrder}.
     * Caller must pass at least 2 items.
     */
    public static CycleView fromVisualOrder(List<ItemStack> visualTopToBottomHotbarLast) {
        List<ItemStack> v = visualTopToBottomHotbarLast;
        int n = v.size();
        List<ItemStack> out = new ArrayList<>(n);
        int highlight;
        if (n == 2) {
            out.add(v.get(1));          // current = hotbar (last)
            out.add(v.get(0));          // other
            highlight = 0;
        } else {
            out.add(v.get(0));          // prev (topmost)
            out.add(v.get(n - 1));      // current = hotbar (last)
            for (int k = n - 2; k >= 1; k--) {
                out.add(v.get(k));      // next, next-next, …
            }
            highlight = 1;
        }
        return new CycleView(out, highlight, List.copyOf(v));
    }
}
