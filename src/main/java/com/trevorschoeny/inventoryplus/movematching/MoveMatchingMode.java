package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.buttonmode.ModeStop;

/**
 * Move Matching's four modes, in cycle order (`features/move-matching.md`).
 * Exclusive, not combinable, by decision. Each direction carries its own
 * value; see {@link MoveMatchingModes}.
 */
public enum MoveMatchingMode implements ModeStop {

    /** Every matching item. */
    ALL("All"),
    /** Everything but one item per stack, left as a seed. A stack of 1 stays. */
    BUT_ONE("But-one"),
    /** Everything but one full stack of each type. */
    BUT_ONE_STACK("But-one-stack"),
    /** A type moves only if the destination fits all of it; otherwise none of it. */
    NO_OVERFLOW("No-overflow");

    private final String label;

    MoveMatchingMode(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }
}
