package com.trevorschoeny.inventoryplus.columncycler;

/**
 * Per-session edit-mode state for Column Cycler.
 *
 * <p>While ON:
 * <ul>
 *   <li>Slot clicks become cycle-membership-toggle clicks (inv + hotbar only,
 *       container-slot 0-35).</li>
 *   <li>Gray overlay renders on inv + hotbar slots.</li>
 *   <li>The {@code C} keybind is unchanged in behavior — it toggles
 *       cycle membership the same way regardless of edit mode. Edit
 *       mode is purely a click-mode for players who'd rather mouse
 *       than keyboard.</li>
 * </ul>
 *
 * <p>Auto-disables on every {@code ScreenEvents.AFTER_INIT} via {@link #reset}.
 */
public final class ColumnCyclerEditMode {

    private ColumnCyclerEditMode() {}

    private static boolean editing = false;

    public static boolean isOn() {
        return editing;
    }

    public static void set(boolean on) {
        editing = on;
    }

    public static void toggle() {
        set(!editing);
    }

    public static void reset() {
        editing = false;
    }
}
