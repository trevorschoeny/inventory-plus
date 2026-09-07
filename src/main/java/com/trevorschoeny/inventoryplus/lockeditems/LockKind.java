package com.trevorschoeny.inventoryplus.lockeditems;

import com.trevorschoeny.inventoryplus.buttonmode.ModeStop;

/**
 * What a press of {@code L} locks, in cycle order
 * (`plans/locked-items.md`, `features/button-modes.md`). The lock
 * button carries these three stops and the one in force decides which
 * kind of protection the player gets.
 *
 * <h3>A granularity choice, not a scope one</h3>
 *
 * <p>The three stops all answer "lock what?", never "lock where?". That
 * is why the lock button's mode is global-only: pinning "lock the item
 * type" to one chest would be meaningless, since the protection it
 * creates is not container-shaped in the first place.
 *
 * <h3>Unlocking ignores the stop</h3>
 *
 * <p>Only locking consults this. {@code L} on something already locked
 * removes the lock whatever stop is showing, so a player never has to
 * remember which stop created it. See
 * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotKeybind}.
 */
public enum LockKind implements ModeStop {

    /** The hovered slot, whatever ends up sitting in it. The original behaviour. */
    SLOT("Lock Slot"),

    /** The hovered item's type: every diamond pickaxe, not just this one. */
    ITEM("Lock Item"),

    /** This item as it is, components and all, however worn it later gets. */
    EXACT("Lock Exact Item");

    private final String label;

    LockKind(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    /**
     * Global default. Slot locking is what the button did before this
     * feature existed, so an upgrading player's {@code L} keeps behaving
     * as it always has until they choose otherwise.
     */
    public static final LockKind DEFAULT = SLOT;
}
