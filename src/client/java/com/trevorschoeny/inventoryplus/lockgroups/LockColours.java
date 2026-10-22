package com.trevorschoeny.inventoryplus.lockgroups;

import net.minecraft.world.item.DyeColor;

/**
 * A lock group's color as drawn, semi-transparent, so a padlock or item mark
 * tints but never hides what is under it. Stored as a dye color's name
 * ({@code red}) or, for one chosen on the color field's Custom tab, as
 * {@code #rrggbb}.
 */
public final class LockColours {

    private LockColours() {}

    /** How opaque a lock's mark is, matching the alpha the other indicators use. */
    private static final int ALPHA = 0xAB;

    /** {@code group}'s color as ARGB at the marks' alpha. */
    public static int argb(LockGroup group) {
        return argb(group.colour(), ALPHA);
    }

    /** {@code colour} as ARGB with {@code alpha}; gray for an unknown name. */
    public static int argb(String colour, int alpha) {
        return (alpha << 24) | rgb(colour);
    }

    /** {@code colour} as {@code 0xRRGGBB}: a dye's, or the hex it holds; gray for an unknown name. */
    public static int rgb(String colour) {
        if (isHex(colour)) return Integer.parseInt(colour.substring(1), 16);
        return DyeColor.byName(colour, DyeColor.GRAY).getTextureDiffuseColor() & 0xFFFFFF;
    }

    /**
     * How {@code rgb} is stored: a dye's name when it is exactly a dye's
     * color (the color field's Basic tab), so files stay readable and an
     * older Inventory Plus still reads it, else {@code #rrggbb}.
     */
    public static String of(int rgb) {
        int c = rgb & 0xFFFFFF;
        for (DyeColor dye : DyeColor.values()) {
            if ((dye.getTextureDiffuseColor() & 0xFFFFFF) == c) return dye.getName();
        }
        return String.format("#%06x", c);
    }

    /** Whether {@code colour} is a hex color, {@code #rrggbb}. */
    public static boolean isHex(String colour) {
        return colour.length() == 7 && colour.charAt(0) == '#' && colour.substring(1).chars().allMatch(
                ch -> Character.digit(ch, 16) >= 0);
    }
}
