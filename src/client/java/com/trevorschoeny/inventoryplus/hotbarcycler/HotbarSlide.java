package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.trevlar.menukit.api.hud.HudCycleStrip;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * The in-flight hotbar slide: what was in each hotbar cell before the last
 * row rotation, which way it went, and when it started.
 *
 * <p>Hotbar Cycler has no HUD strip of its own. The cycle is shown in the
 * vanilla hotbar. On a rotation the real inventory changes instantly, so by
 * the time the HUD draws, every hotbar cell already holds its new item. The
 * animation therefore needs exactly one extra thing per cell: the item that
 * was there a moment ago, to draw sliding out while the new one slides in.
 * That is all this holds.
 *
 * <p>One global slide, not one per cell: a row rotation moves all nine cells
 * at once on one clock. A press mid-slide restarts from the new state,
 * because the next snapshot is taken from the already-rotated inventory.
 * Presses are not queued.
 *
 * <p>Time and easing come from MenuKit's cycle strip
 * ({@link HudCycleStrip#slideProgress}), so the hotbar and the strip beside it
 * move as one. The mixin that draws this reads it every frame
 * and gets nothing once the slide has run its course.
 */
public final class HotbarSlide {

    private HotbarSlide() {}

    private static ItemStack @Nullable [] outgoing;
    /** MenuKit's slide start, on its own clock (System.currentTimeMillis never reported done). */
    private static HudCycleStrip.SlideStart start = HudCycleStrip.begin();
    private static HudCycleStrip.Direction direction = HudCycleStrip.Direction.FORWARD;

    /**
     * Begin a slide. {@code outgoingSnapshot} is the hotbar (0-8) as it was
     * before the rotation. The caller copies the stacks, this keeps them.
     */
    public static void start(ItemStack[] outgoingSnapshot, HudCycleStrip.Direction dir) {
        if (outgoingSnapshot == null || outgoingSnapshot.length != HotbarCycler.COLUMNS) return;
        outgoing = outgoingSnapshot;
        direction = dir;
        start = HudCycleStrip.begin();
    }

    /** True while a slide is running. Clears itself the first time it's asked after the end. */
    public static boolean isActive() {
        if (outgoing == null) return false;
        if (HudCycleStrip.slideDone(start)) {
            outgoing = null;
            return false;
        }
        return true;
    }

    /** Eased progress, 0 to 1 (with the spring's brief overshoot). Only meaningful while active. */
    public static float progress() {
        return HudCycleStrip.slideProgress(start);
    }

    public static HudCycleStrip.Direction direction() {
        return direction;
    }

    /** What hotbar cell {@code slot} held before the rotation, or EMPTY if unknown. */
    public static ItemStack outgoing(int slot) {
        ItemStack[] o = outgoing;
        if (o == null || slot < 0 || slot >= o.length) return ItemStack.EMPTY;
        return o[slot];
    }
}
