package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Access to {@link AbstractContainerScreen}'s protected fields with no
 * public getter — {@code leftPos}/{@code topPos} for move-matching's
 * slot-group-anchored button placement, and {@code hoveredSlot} for
 * agreeing with vanilla's own answer to "what's under the cursor".
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

    /**
     * Vanilla's own hover resolution for this frame, written by
     * {@code extractContents} — a created slot wins over the vanilla slot
     * it covers, and a point over a panel's empty space resolves to
     * {@code null}. Prefer this to re-deriving hover with a manual bounds
     * scan, which can only re-discover what vanilla already decided and,
     * on a covered slot, can disagree with it (MenuKit, 2026-09-07).
     */
    public static @Nullable Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        return ((AbstractContainerScreenAccessor) screen).inventoryPlus$getHoveredSlot();
    }
}
