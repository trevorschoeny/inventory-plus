package com.trevorschoeny.inventoryplus.autorestock;

/**
 * Lets a sibling feature tell Auto-Restock that it is <b>deliberately</b>
 * changing a hotbar slot's contents, so the change isn't mistaken for the held
 * item being used up.
 *
 * <h3>Why this exists</h3>
 *
 * Auto-Restock detects depletion by diffing a per-tick snapshot of the watched
 * slots ({@link AutoRestockTicker}). It can't tell <i>why</i> a slot's contents
 * changed — only that they did. A feature that swaps the hand item wholesale
 * (Inventory Plus Plus' pocket cycler brings a pocket item into the hand; a
 * wrap can bring an <i>empty</i> pocket in) trips that diff: the hand reads
 * empty, Auto-Restock assumes the item ran out, finds a backup stack, and
 * switches the selected slot to it — a jarring, wrong slot jump.
 *
 * <p>The fix is cooperation, not detection: the feature performing the
 * deliberate change marks the slot here, and Auto-Restock re-baselines that
 * slot (accepts the new contents as normal) instead of refilling.
 *
 * <h3>Why a time window</h3>
 *
 * The change is often server-authoritative (the pocket cycler sends a request
 * and the result arrives a round-trip later), so the mark can't be a single
 * instant — it covers a short window long enough for the change to land. Each
 * Auto-Restock tick within the window re-baselines the slot, so the change is
 * absorbed whenever it arrives. The window is deliberately generous; the only
 * cost of an over-long window is Auto-Restock skipping a genuine depletion that
 * happens within it (rare, and invisible — far better than a wrong slot jump).
 *
 * <p>Client-side state — Auto-Restock runs on the client tick.
 */
public final class AutoRestockSuppression {

    private AutoRestockSuppression() {}

    /** How long after a mark the slot stays suppressed — covers the change's round-trip. */
    private static final long WINDOW_MILLIS = 500L;

    /** Per-hotbar-slot (0–8) suppression expiry, in {@code currentTimeMillis} epoch. */
    private static final long[] expiryMillis = new long[9];

    /**
     * Mark a hotbar slot as deliberately changing now. Auto-Restock will
     * re-baseline (not refill) it for the next {@link #WINDOW_MILLIS}.
     */
    public static void markExternalChange(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return;
        expiryMillis[hotbarSlot] = System.currentTimeMillis() + WINDOW_MILLIS;
    }

    /** True while {@code hotbarSlot} is within its deliberate-change window. */
    public static boolean isExternallyChanging(int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot > 8) return false;
        return System.currentTimeMillis() < expiryMillis[hotbarSlot];
    }
}
