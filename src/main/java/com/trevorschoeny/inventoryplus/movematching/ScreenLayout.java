package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.lang.reflect.Field;

/**
 * Reflective access to {@link AbstractContainerScreen}'s {@code leftPos}
 * and {@code topPos} fields.
 *
 * <h3>Why reflection</h3>
 *
 * These two fields are {@code protected} with no public getter. Move-
 * matching's slot-group-anchored button placement needs them every frame
 * to convert slot-local coordinates to screen-space. The cleaner
 * alternatives — an access widener or a Mixin accessor — would require
 * mod-wide infrastructure for two single-field reads; for the smoke
 * pass, cached reflection is the minimum-cost choice.
 *
 * <p>An earlier attempt used an access widener; it failed to remap
 * cleanly in Loom 1.14 against this workspace's mojang-named mappings
 * ({@code Failed to remap 2 mods} during runClient setup). Reflection
 * sidesteps the remap pipeline entirely.
 *
 * <h3>Caching</h3>
 *
 * The {@link Field} handles resolve once at class load and are set
 * accessible at the same time. Subsequent reads are direct
 * {@code Field.getInt} calls — cheap enough for per-frame rendering.
 * Reflection cost is well below 100ns per call, far smaller than the
 * blit operation that follows.
 */
public final class ScreenLayout {

    private ScreenLayout() {}

    private static final Field LEFT_POS_FIELD;
    private static final Field TOP_POS_FIELD;

    static {
        Field left = null;
        Field top = null;
        try {
            left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            left.setAccessible(true);
            top = AbstractContainerScreen.class.getDeclaredField("topPos");
            top.setAccessible(true);
        } catch (NoSuchFieldException e) {
            // Field name shift would be catastrophic — move-matching can't
            // place buttons. Log and leave the fields null; the getters
            // below return 0 in that case, which makes buttons render at
            // screen origin (visible but wrong) so the regression is
            // visually obvious rather than silent.
            InventoryPlusClient.LOGGER.error(
                    "[move-matching] AbstractContainerScreen.leftPos/topPos lookup failed "
                    + "— button placement will be broken. Mapping shift?", e);
        }
        LEFT_POS_FIELD = left;
        TOP_POS_FIELD = top;
    }

    /** Returns the screen's image-rectangle top-left X (or 0 on lookup failure). */
    public static int leftPos(AbstractContainerScreen<?> screen) {
        if (LEFT_POS_FIELD == null) return 0;
        try {
            return LEFT_POS_FIELD.getInt(screen);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }

    /** Returns the screen's image-rectangle top-left Y (or 0 on lookup failure). */
    public static int topPos(AbstractContainerScreen<?> screen) {
        if (TOP_POS_FIELD == null) return 0;
        try {
            return TOP_POS_FIELD.getInt(screen);
        } catch (IllegalAccessException e) {
            return 0;
        }
    }
}
