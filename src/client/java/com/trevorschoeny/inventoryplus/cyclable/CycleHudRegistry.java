package com.trevorschoeny.inventoryplus.cyclable;

import com.trevorschoeny.inventoryplus.api.CycleHudSource;
import com.trevorschoeny.inventoryplus.api.CycleView;
import com.trevorschoeny.inventoryplus.api.CyclerDirection;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of {@link CycleHudSource}s + the shared cycle-animation clock.
 *
 * <p>The single seam {@link CycleHud} reads from. Cyclers register a source
 * (for HUD contents) and fire {@link #fireCycleAnimation} after a real
 * rotation (to drive the slide animation). The HUD never references any
 * specific cycler — it asks this registry "what's active on slot N?" and
 * "is an animation in flight for this source?".
 *
 * <p>Reachable cross-mod: IPP's Pocket Cycler registers its own source here
 * exactly as IP's Column Cycler does. Public static, single-threaded client
 * use (registration at init, reads on the render thread).
 *
 * <h3>Stacking (2026-06-04)</h3>
 *
 * Multiple cyclers can be active on the SAME hotbar slot (e.g. Column Cycler
 * and Pocket Cycler both on the held slot). {@link #activeViews} returns them
 * ALL, ordered bottom→top by {@link CycleHudSource#hudStackOrder()}, so the HUD
 * can stack a strip per cycler. (This replaced an earlier first-match-wins
 * {@code activeView} that assumed cyclers never shared a slot.)
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
     * A cycle active on a hotbar slot: which source produced it, plus the
     * {@link CycleView} to draw. The source is carried so the HUD can look up
     * <em>that</em> cycler's animation (each animates independently).
     */
    public record ActiveCycle(CycleHudSource source, CycleView view) {}

    /**
     * Every cycle active on {@code hotbarSlot}, ordered BOTTOM→TOP for stacking:
     * ascending {@link CycleHudSource#hudStackOrder()} (lower = nearer the
     * hotbar / bottom of the stack). Pocket Cycler pins to the bottom; Column
     * Cycler stacks above it. One element in the common case (a single cycler
     * claims the slot); empty when none does.
     */
    public static List<ActiveCycle> activeViews(int hotbarSlot) {
        List<ActiveCycle> out = new ArrayList<>(SOURCES.size());
        for (CycleHudSource s : SOURCES) {
            CycleView view = s.cycleViewForHotbar(hotbarSlot);
            if (view != null) out.add(new ActiveCycle(s, view));
        }
        out.sort(Comparator.comparingInt(ac -> ac.source().hudStackOrder()));
        return out;
    }

    // ─── Per-source animation clock ──────────────────────────────────
    // Each cycler animates INDEPENDENTLY. When two strips are stacked, cycling
    // the pocket must slide only the pocket strip — not the column strip above
    // it. A single global slot-keyed clock would slide every strip on that slot
    // at once, so state is keyed by source. A cycler fires this after a
    // successful rotation; CycleHud reads it per strip to compute the slide.

    /** One in-flight slide for a source. */
    public record Animation(int hotbarSlot, long startMillis, CyclerDirection direction) {}

    private static final Map<CycleHudSource, Animation> ANIMATIONS = new HashMap<>();

    /** Fire a slide for {@code source} on {@code hotbarSlot} in {@code direction}. */
    public static void fireCycleAnimation(CycleHudSource source, int hotbarSlot, CyclerDirection direction) {
        if (source == null) return;
        ANIMATIONS.put(source, new Animation(hotbarSlot, System.currentTimeMillis(), direction));
    }

    /** The in-flight slide for {@code source}, or {@code null} if none. */
    public static Animation animationFor(CycleHudSource source) {
        return ANIMATIONS.get(source);
    }

    /** Clear {@code source}'s slide (called by the HUD when it completes). */
    public static void clearAnimation(CycleHudSource source) {
        ANIMATIONS.remove(source);
    }

    // ─── Change-gated slides ─────────────────────────────────────────
    // Hotbar Cycler rotates whole rows, which may or may not alter what a
    // given preview shows: a column whose cycle members sit in untouched rows
    // changes only its held cell; a pocket's own slots never change at all.
    // The spec says a preview slides only where the rotation actually changed
    // its contents. That decision is made here, generically, so a cycler that
    // fires it never needs to know which sources exist (Pocket Cycler lives in
    // another mod) or how each one derives its view.

    /**
     * Every source's current view for {@code hotbarSlot}, keyed by source, for
     * a later {@link #fireChangedViews}. Sources with no active cycle there
     * map to {@code null}, so a cycle appearing or vanishing also counts as a
     * change.
     */
    public static Map<CycleHudSource, CycleView> snapshotViews(int hotbarSlot) {
        Map<CycleHudSource, CycleView> out = new HashMap<>();
        for (CycleHudSource s : SOURCES) {
            out.put(s, s.cycleViewForHotbar(hotbarSlot));
        }
        return out;
    }

    /**
     * Fire a slide in {@code direction} for each source whose view on
     * {@code hotbarSlot} now differs from {@code before} (a
     * {@link #snapshotViews} taken before the rotation). Sources whose
     * contents did not change are left alone and do not move.
     */
    public static void fireChangedViews(int hotbarSlot, CyclerDirection direction,
                                        Map<CycleHudSource, CycleView> before) {
        for (CycleHudSource s : SOURCES) {
            CycleView was = before.get(s);
            CycleView now = s.cycleViewForHotbar(hotbarSlot);
            if (viewChanged(was, now)) fireCycleAnimation(s, hotbarSlot, direction);
        }
    }

    /** Item-by-item comparison of the raw visual order; ItemStack has no content equals. */
    private static boolean viewChanged(CycleView was, CycleView now) {
        if (was == null || now == null) return was != now;
        List<ItemStack> a = was.visualOrder(), b = now.visualOrder();
        if (a.size() != b.size()) return true;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return true;
        }
        return false;
    }
}
