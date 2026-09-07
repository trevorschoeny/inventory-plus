package com.trevorschoeny.inventoryplus.lockeditems;

import com.trevorschoeny.inventoryplus.buttonmode.ModeState;

/**
 * The lock button's mode: which of the three {@link LockKind} stops a
 * press of {@code L} uses.
 *
 * <h3>Global only</h3>
 *
 * <p>Built with {@link ModeState#globalOnly} because the stops choose
 * <em>what</em> to lock, not <em>where</em>
 * (`features/button-modes.md`). A pin scopes a mode to one container,
 * and none of these three stops is container-shaped: slot locks belong
 * to a slot, item locks belong to an item type wherever it travels. So
 * middle-click does nothing on this button and its tooltip does not
 * offer the gesture.
 */
public final class LockedItemModes {

    private LockedItemModes() {}

    public static final ModeState<LockKind> MODE =
            ModeState.globalOnly("lock-kind", LockKind.class, LockKind.DEFAULT);

    public static void load() {
        MODE.load();
    }

    /** The stop in force. Nothing scopes it, so this takes no container. */
    public static LockKind current() {
        return MODE.global();
    }
}
