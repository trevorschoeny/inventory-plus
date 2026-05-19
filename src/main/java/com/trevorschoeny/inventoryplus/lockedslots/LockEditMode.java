package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerEditMode;

/**
 * Per-session edit-mode state for Locked Slots.
 *
 * <p>Edit mode is a UI mode triggered by clicking the lock-edit toggle
 * button. While ON:
 * <ul>
 *   <li>Slot clicks become lock-toggle clicks instead of item interactions.</li>
 *   <li>Move Matching widget clicks + I/O keybinds are blocked.</li>
 * </ul>
 *
 * <p>Edit mode auto-disables when the player closes the screen (per
 * Trev 2026-05-16 — Option A lifecycle). This is implemented by the
 * client wireup calling {@link #reset} on every {@code ScreenEvents.AFTER_INIT}.
 *
 * <p>State is intentionally global (one flag) rather than per-screen —
 * only one screen is open at a time anyway, and the reset-on-init
 * pattern makes it behave per-screen correctly.
 *
 * <p><b>Mutual exclusion</b> with {@link ColumnCyclerEditMode}: entering one
 * edit mode forces the other off (only one slot-click gesture meaning can
 * be active at a time). The cross-class reset goes through {@link #setRaw} /
 * {@link ColumnCyclerEditMode#setRaw} to avoid recursion.
 */
public final class LockEditMode {

    private LockEditMode() {}

    private static boolean editing = false;

    public static boolean isOn() {
        return editing;
    }

    public static void set(boolean on) {
        if (on) ColumnCyclerEditMode.setRaw(false);
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
