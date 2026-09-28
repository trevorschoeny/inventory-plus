package com.trevorschoeny.inventoryplus.buttonmode;

/**
 * One stop on a button's mode cycle. Implemented by the enums a
 * {@link ModeState} cycles through; the enum's declaration order is the
 * cycle order, and {@link #label()} is the line the tooltip shows.
 */
public interface ModeStop {
    String label();
}
