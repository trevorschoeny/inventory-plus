package com.trevorschoeny.inventoryplus.cyclable;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of {@link CycleHudSource}s + the shared cycle-animation clock.
 *
 * <p>The single seam {@link CycleHud} reads from. Cyclers register a source
 * (for HUD contents) and fire {@link #fireCycleAnimation} after a real
 * rotation (to drive the slide animation). The HUD never references any
 * specific cycler — it asks this registry "what's active on slot N?" and
 * "is an animation in flight?".
 *
 * <p>Reachable cross-mod: IPP's Pocket Cycler registers its own source here
 * exactly as IP's Column Cycler does. Public static, single-threaded client
 * use (registration at init, reads on the render thread).
 */
public final class CycleHudRegistry {

    private CycleHudRegistry() {}

    // ─── HUD sources ─────────────────────────────────────────────────

    private static final List<CycleHudSource> SOURCES = new ArrayList<>();

    /** Register a source. Idempotent on the exact instance. */
    public static void register(CycleHudSource source) {
        if (source == null || SOURCES.contains(source)) return;
        SOURCES.add(source);
    }

    /**
     * Returns the first non-null {@link CycleView} any registered source
     * produces for {@code hotbarSlot}, or {@code null} if none claims it.
     * (Sources claim disjoint cycles per slot in practice; first-match is
     * fine.)
     */
    public static CycleView activeView(int hotbarSlot) {
        for (CycleHudSource s : SOURCES) {
            CycleView view = s.cycleViewForHotbar(hotbarSlot);
            if (view != null) return view;
        }
        return null;
    }

    // ─── Shared animation clock ──────────────────────────────────────
    // One animation in flight at a time (the player is on one hotbar slot).
    // A cycler fires this after a successful rotation; CycleHud reads it to
    // compute the slide offset. State lives here (not in CycleHud) so any
    // cycler can drive it without touching the HUD.

    /** Hotbar slot whose cycle is currently animating, or -1. */
    private static int animatingSlot = -1;
    /** System millis when the current animation started. */
    private static long animationStartMillis = 0L;
    /** Direction of the in-flight animation, or null. */
    private static CyclerDirection animationDirection = null;

    /** Fire an animation for {@code hotbarSlot} in {@code direction}. */
    public static void fireCycleAnimation(int hotbarSlot, CyclerDirection direction) {
        animatingSlot = hotbarSlot;
        animationStartMillis = System.currentTimeMillis();
        animationDirection = direction;
    }

    public static int animatingSlot() { return animatingSlot; }
    public static long animationStartMillis() { return animationStartMillis; }
    public static CyclerDirection animationDirection() { return animationDirection; }

    /** Clear the animation clock (called by the HUD when an animation completes). */
    public static void clearAnimation() {
        animatingSlot = -1;
        animationDirection = null;
    }
}
