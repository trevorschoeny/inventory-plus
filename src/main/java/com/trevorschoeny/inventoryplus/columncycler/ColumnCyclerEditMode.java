package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

/**
 * Per-session edit-mode state for Column Cycler — mirrors
 * {@link LockEditMode}'s pattern.
 *
 * <p>While ON:
 * <ul>
 *   <li>Slot clicks become cycle-membership-toggle clicks (inv + hotbar only,
 *       container-slot 0-35).</li>
 *   <li>Gray overlay renders on inv + hotbar slots (same visual as Lock
 *       edit mode — Trev wants both to read identically).</li>
 *   <li>The {@code C} keybind is unchanged in behavior — it toggles
 *       cycle membership the same way regardless of edit mode. Edit
 *       mode is purely a click-mode for players who'd rather mouse
 *       than keyboard.</li>
 * </ul>
 *
 * <p><b>Mutual exclusion</b> with {@link LockEditMode}: entering one edit
 * mode forces the other off. Only one click-gesture-meaning at a time.
 *
 * <p>Auto-disables on every {@code ScreenEvents.AFTER_INIT} via {@link #reset}
 * — same lifecycle as {@link LockEditMode}.
 */
public final class ColumnCyclerEditMode {

    private ColumnCyclerEditMode() {}

    private static boolean editing = false;

    public static boolean isOn() {
        return editing;
    }

    public static void set(boolean on) {
        if (on) LockEditMode.setRaw(false);
        editing = on;
    }

    /** Skip the mutual-exclusion cross-call — used by the other edit mode's setter. */
    public static void setRaw(boolean on) {
        editing = on;
    }

    public static void toggle() {
        set(!editing);
    }

    public static void reset() {
        editing = false;
    }
}
