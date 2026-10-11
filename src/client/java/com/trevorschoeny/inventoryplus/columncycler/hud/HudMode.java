package com.trevorschoeny.inventoryplus.columncycler.hud;

/**
 * Visual style of the Column Cycler HUD overlay.
 *
 * <ul>
 *   <li>{@link #NONE}: no HUD overlay. Cycling still works.</li>
 *   <li>{@link #MINI_HOTBAR}: a second hotbar-style strip to the right
 *       of the player's hotbar, showing the active column's cycle
 *       members with a slide animation on rotation. Active item
 *       highlighted at position 2 (or position 1 when only 2 cycle
 *       members exist).</li>
 * </ul>
 *
 * <p>Diamond indicators (sprites around the active hotbar slot) are not
 * implemented yet. They would become a third value.
 */
public enum HudMode {
    NONE,
    MINI_HOTBAR;

    /**
     * Parse a mode name from JSON config. Returns {@code fallback} on a
     * null, empty or unknown name, so IPConfig.load tolerates config-file
     * edits and future enum additions.
     */
    public static HudMode fromName(String name, HudMode fallback) {
        if (name == null || name.isEmpty()) return fallback;
        for (HudMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return fallback;
    }
}
