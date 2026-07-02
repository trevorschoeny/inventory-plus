package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Access to {@link AbstractContainerScreen}'s {@code leftPos}/{@code topPos}
 * (protected, no public getter) — move-matching's slot-group-anchored button
 * placement needs them every frame to convert slot-local coordinates to
 * screen-space.
 *
 * <p>Backed by the {@link AbstractContainerScreenAccessor} mixin, whose field
 * references are compile-time remapped. The previous implementation reflected
 * by the Mojmap field names, which "sidesteps the remap pipeline" — i.e. looks
 * up dev-only names verbatim in a production (intermediary) runtime:
 * {@code NoSuchFieldException}, origin-rendered buttons, and the 1.2.0
 * production crash. Never reflect on Minecraft names by string.
 */
public final class ScreenLayout {

    private ScreenLayout() {}

    /** The screen's image-rectangle top-left X. */
    public static int leftPos(AbstractContainerScreen<?> screen) {
        return ((AbstractContainerScreenAccessor) screen).inventoryPlus$getLeftPos();
    }

    /** The screen's image-rectangle top-left Y. */
    public static int topPos(AbstractContainerScreen<?> screen) {
        return ((AbstractContainerScreenAccessor) screen).inventoryPlus$getTopPos();
    }
}
