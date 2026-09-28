package com.trevorschoeny.inventoryplus.columncycler.hud;

/**
 * Visual style of the Column Cycler HUD overlay.
 *
 * <p>Per the column-cycler spec, three options are envisioned:
 * <ul>
 *   <li>{@link #NONE} — no HUD overlay; cycling still works.</li>
 *   <li>{@link #MINI_HOTBAR} — a second hotbar-style strip to the right
 *       of the player's hotbar, showing the active column's cycle
 *       members with a slide animation on rotation. Active item
 *       highlighted at position 2 (or position 1 when only 2 cycle
 *       members exist).</li>
 *   <li>(Future) Diamond indicators — sprites around the active hotbar
 *       slot. Not yet implemented; will become a third enum value when
 *       it ships.</li>
 * </ul>
 */
public enum HudMode {
    NONE,
    MINI_HOTBAR;

    /**
     * Parse a mode name from JSON config. Returns {@code fallback} on
     * null / empty / unknown name. Used by IPConfig.load to tolerate
     * config-file edits or future enum additions.
     */
    public static HudMode fromName(String name, HudMode fallback) {
        if (name == null || name.isEmpty()) return fallback;
        for (HudMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return fallback;
    }
}
