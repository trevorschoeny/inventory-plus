package com.trevorschoeny.inventoryplus.lockgroups;

import net.minecraft.world.item.DyeColor;

/**
 * A lock group's colour as drawn: one of the 16 dye colours, semi-transparent, so a padlock or item mark tints but
 * never hides what is under it.
 */
public final class LockColours {

    private LockColours() {}

    /** How opaque a lock's mark is, matching the alpha the other indicators use. */
    private static final int ALPHA = 0xAB;

    /** {@code group}'s colour as ARGB at the marks' alpha. */
    public static int argb(LockGroup group) {
        return argb(group.colour(), ALPHA);
    }

    /** Dye colour {@code colour} as ARGB with {@code alpha}; gray for an unknown name. */
    public static int argb(String colour, int alpha) {
        int rgb = DyeColor.byName(colour, DyeColor.GRAY).getTextureDiffuseColor() & 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    /** A dye name as a player reads it: "light_blue" to "Light blue". */
    public static String displayName(String colour) {
        String s = colour.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
