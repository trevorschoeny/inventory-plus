package com.trevorschoeny.inventoryplus.buttonmode;

/**
 * A brief darkening of a button after any click, so a right-click that
 * changes nothing visible on the sprite still reads as a press rather
 * than a miss. One instance per button; feeds the button's tint supplier
 * through {@link ModeGestures#tint}.
 */
public final class PressFeedback {

    private static final long FLASH_MILLIS = 120L;
    /** Semi-transparent black inside the border. */
    private static final int FLASH_TINT = 0x70000000;

    private long pressedAt = 0L;

    public void press() {
        pressedAt = System.currentTimeMillis();
    }

    /** The flash tint while active, else 0. */
    public int tint() {
        return System.currentTimeMillis() - pressedAt < FLASH_MILLIS ? FLASH_TINT : 0;
    }
}
