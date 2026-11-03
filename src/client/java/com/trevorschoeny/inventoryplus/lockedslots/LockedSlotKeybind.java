package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-scoped {@code L} keybind: locks the slot under the cursor, empty or not, or
 * unlocks it if it is locked. Hold {@code L} and sweep to give every slot entered the
 * first slot's new state. A placed container's slots lock only with Inventory Plus on
 * the server ({@link LockedSlots#isLockableHere}).
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so {@code L} only fires inside
 * container screens.
 */
public final class LockedSlotKeybind {

    private LockedSlotKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (!IPKeybinds.LOCK_SLOT.matches(event)) return;
                        // Paused locks are paused for L too: nothing applied that cannot be seen.
                        if (!IPConfig.locksEnabled()) return;
                        // key auto-repeat fires afterKeyPress every repeat
                        // tick while L is held; ignore once an L-drag is
                        // active so the same slot isn't re-toggled on
                        // every repeat. The tick handler clears the drag
                        // on key release.
                        if (LockedSlotsDragController.isLKeyDragActive()) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Slot hovered = slotUnderMouse(currentAcs);
                        if (hovered == null) return;

                        if (!LockedSlots.isLockableHere(hovered)) return;
                        boolean now = LockedSlots.toggle(hovered);
                        // Hold L and sweep: every slot entered gets the first
                        // slot's new state. Keyed by slot.index (menu-unique), so a
                        // container slot can't collide with a player slot.
                        LockedSlotsDragController.startLKeyDrag(hovered.index, now);
                    });
        });
    }

    /**
     * The slot under the cursor, agreeing with vanilla's own resolution
     * rather than re-deriving it.
     *
     * <p>An earlier version of this walked {@code menu.slots} and bounds-
     * tested each one by hand, using {@code isActive()} to skip whatever a
     * created slot (an Inventory Max pocket) was covering. That worked only as long
     * as MenuKit's compositing happened to leave the covered vanilla slot
     * inactive; once MenuKit restored plain paint-over (composited panels
     * inside vanilla's slot pass, "the covered vanilla slot is active
     * again"), the independent scan found that vanilla slot first again, and
     * {@code L} locked the inventory slot under the pocket rather than the
     * pocket itself.
     *
     * <p>{@link ScreenLayout#hoveredSlot} is vanilla's own per-frame answer,
     * which MenuKit's {@code getHoveredSlot} interception already resolves
     * a created slot ahead of the vanilla slot it covers for. Reading it
     * agrees with whatever compositing MenuKit does internally, without
     * this class needing to know what that is. The {@code isActive()}
     * check stays as a defensive no-op: vanilla's own resolution already
     * honours it before this ever sees the result.
     */
    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs) {
        Slot slot = ScreenLayout.hoveredSlot(acs);
        return (slot != null && slot.isActive()) ? slot : null;
    }
}
