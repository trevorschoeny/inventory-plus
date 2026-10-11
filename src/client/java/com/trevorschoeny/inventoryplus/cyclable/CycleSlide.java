package com.trevorschoeny.inventoryplus.cyclable;

/**
 * The one slide motion every cycler animation shares: 220 ms, ease-out-back.
 *
 * <p>Lifted out of {@link CycleHud} so the hotbar slide (Hotbar Cycler's
 * animation drawn in the vanilla hotbar itself), the mini-hotbar strip, and
 * the column arm all run on the same curve and clock. Hotbar Cycler's slide
 * must match the mini-hotbar's duration and spring, and sharing the code
 * keeps them from drifting apart.
 *
 * <p>Pure functions of time. Whoever owns an animation stores its start
 * time and asks here for progress.
 */
public final class CycleSlide {

    private CycleSlide() {}

    public static final long DURATION_MILLIS = 220L;

    /**
     * Eased progress for a slide that began at {@code startMillis}: 0 at the
     * start, 1 (exactly) at rest. Overshoots to roughly 1.1 just before
     * settling, which is the spring. Safe to call after the slide has ended;
     * it stays clamped at 1.
     */
    public static float progress(long startMillis) {
        return easeOutBack((float) (System.currentTimeMillis() - startMillis) / DURATION_MILLIS);
    }

    /** True once the slide that began at {@code startMillis} has run its course. */
    public static boolean isDone(long startMillis) {
        return System.currentTimeMillis() - startMillis >= DURATION_MILLIS;
    }

    /** Ease-out-back spring curve: overshoots about 10% then settles to 1.0. */
    public static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        float tm1 = t - 1f;
        return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1;
    }
}
