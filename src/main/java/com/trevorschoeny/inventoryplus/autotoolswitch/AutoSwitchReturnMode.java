package com.trevorschoeny.inventoryplus.autotoolswitch;

/**
 * How Auto Tool Switch returns the hotbar to its pre-switch state after a switch.
 *
 * <ul>
 *   <li>{@link #OFF} — never return; the switch is permanent (you keep the tool
 *       until you change it yourself).</li>
 *   <li>{@link #AUTOMATIC} — return automatically once you've stopped for the
 *       window (the window counts down only while idle, so it won't yank the tool
 *       mid-action).</li>
 *   <li>{@link #HOTKEY_TIMED} — after you stop, a window opens; press the "return
 *       to previous tool" keybind within it to return. Window passes → the tool
 *       stays (new baseline).</li>
 *   <li>{@link #HOTKEY_ANYTIME} — the keybind returns you whenever there's an
 *       active switch, with no window.</li>
 * </ul>
 *
 * <p>The return keybind defaults to <b>Sneak (Shift)</b> — so "sneak to return"
 * works out of the box; rebind it under Controls. (Sneak isn't a separate mode;
 * it's just the keybind's default.)
 *
 * <p>The window length (for the {@linkplain #isWindowed() windowed} modes —
 * {@link #AUTOMATIC} + {@link #HOTKEY_TIMED}) is
 * {@code IPConfig.autoToolSwitchReturnCooldownSeconds}. Cycled via the YACL
 * config screen; persisted in {@code config.json} as the enum name. Migrated
 * from the old boolean {@code autoToolSwitchReturn} (true → {@link #AUTOMATIC},
 * false → {@link #OFF}).
 */
public enum AutoSwitchReturnMode {
    OFF("Off"),
    AUTOMATIC("Automatic"),
    HOTKEY_TIMED("Hotkey (timed)"),
    HOTKEY_ANYTIME("Hotkey (anytime)");

    private final String displayName;

    AutoSwitchReturnMode(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label for the config screen. */
    public String displayName() {
        return displayName;
    }

    /** True if this mode returns at all (anything but {@link #OFF}). */
    public boolean returnsAtAll() {
        return this != OFF;
    }

    /** True if this mode uses the timed window (automatic delay / hotkey-timed). */
    public boolean isWindowed() {
        return this == AUTOMATIC || this == HOTKEY_TIMED;
    }

    /**
     * Parse a mode name from JSON config. Returns {@code fallback} on
     * null / empty / unknown — tolerates config edits + future additions.
     */
    public static AutoSwitchReturnMode fromName(String name, AutoSwitchReturnMode fallback) {
        if (name == null || name.isEmpty()) return fallback;
        for (AutoSwitchReturnMode m : values()) {
            if (m.name().equals(name)) return m;
        }
        return fallback;
    }
}
