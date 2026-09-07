package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.trevorschoeny.inventoryplus.api.CyclerDirection;
import com.trevorschoeny.inventoryplus.cyclable.CycleSlide;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * The in-flight hotbar slide: what was in each hotbar cell before the last
 * row rotation, which way it went, and when it started.
 *
 * <p>Hotbar Cycler has no HUD strip of its own; the cycle is shown in the
 * vanilla hotbar. On a rotation the real inventory changes instantly, so by
 * the time the HUD draws, every hotbar cell already holds its NEW item. The
 * animation therefore needs exactly one extra thing per cell: the item that
 * was there a moment ago, to draw sliding out while the new one slides in.
 * That is all this holds.
 *
 * <p>One global slide, not one per cell: a row rotation moves all nine cells
 * at once on one clock. A press mid-slide restarts from the new state,
 * because the next snapshot is taken from the already-rotated inventory. No
 * queueing, per the spec.
 *
 * <p>Time and easing come from {@link CycleSlide}, shared with the mini-hotbar
 * so the two feel identical. The mixin that draws this reads it every frame
 * and gets nothing once the slide has run its course.
 */
public final class HotbarSlide {

    private HotbarSlide() {}

    private static ItemStack @Nullable [] outgoing;
    private static long startMillis;
    private static CyclerDirection direction = CyclerDirection.FORWARD;

    /**
     * Begin a slide. {@code outgoingSnapshot} is the hotbar (0-8) as it was
     * BEFORE the rotation; the caller copies the stacks, this keeps them.
     */
    public static void start(ItemStack[] outgoingSnapshot, CyclerDirection dir) {
        if (outgoingSnapshot == null || outgoingSnapshot.length != HotbarCycler.COLUMNS) return;
        outgoing = outgoingSnapshot;
        direction = dir;
        startMillis = System.currentTimeMillis();
    }

    /** True while a slide is running. Clears itself the first time it's asked after the end. */
    public static boolean isActive() {
        if (outgoing == null) return false;
        if (CycleSlide.isDone(startMillis)) {
            outgoing = null;
            return false;
        }
        return true;
    }

    /** Eased progress 0→1 (with the spring's brief overshoot). Only meaningful while active. */
    public static float progress() {
        return CycleSlide.progress(startMillis);
    }

    public static CyclerDirection direction() {
        return direction;
    }

    /** What hotbar cell {@code slot} held before the rotation; EMPTY if unknown. */
    public static ItemStack outgoing(int slot) {
        ItemStack[] o = outgoing;
        if (o == null || slot < 0 || slot >= o.length) return ItemStack.EMPTY;
        return o[slot];
    }
}
