package com.trevorschoeny.inventoryplus.cyclable;

/**
 * A source the shared cycle HUD can ask: "is there an active cycle on this
 * hotbar slot, and if so, what should I draw?"
 *
 * <p>Each cycler (Column Cycler in IP, Pocket Cycler in IPP, future Hotbar
 * Swap) registers one implementation with {@link CycleHudRegistry}. The HUD
 * iterates sources and renders the first non-null {@link CycleView} for the
 * player's currently-selected hotbar slot. This keeps the HUD render code
 * fully cycler-agnostic — the architecture seam that lets one HUD serve all
 * cyclers (the generalization Trev approved 2026-06-02).
 *
 * <p>Each source owns its own gating: a source returns {@code null} unless
 * its feature is enabled, its HUD mode is on, AND the given hotbar slot
 * actually has an active cycle for that cycler. So the HUD shows nothing
 * until some cycler claims the slot.
 */
public interface CycleHudSource {

    /**
     * Returns the {@link CycleView} to draw for {@code hotbarSlot} (0–8), or
     * {@code null} if this source has no active, HUD-enabled cycle there.
     *
     * <p>Called once per frame while an inventory screen is closed; keep it
     * cheap (O(small)).
     */
    CycleView cycleViewForHotbar(int hotbarSlot);
}
