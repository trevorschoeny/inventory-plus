package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-scoped {@code C} keybind — toggles cycle membership on the
 * cycleable player slot currently under the cursor.
 *
 * <p>No-op when:
 * <ul>
 *   <li>{@code columnCyclerEnabled} is off (Power Users feature gate).</li>
 *   <li>The hovered slot is not cycleable (armor / offhand / external
 *       container / non-player inventory).</li>
 * </ul>
 *
 * <p>Works regardless of edit mode — mirroring the {@code L} keybind's
 * always-on behavior so the player can use the keyboard gesture
 * without leaving edit mode (Trev 2026-05-19). In edit mode the
 * C-key drag is suppressed (LMB-drag is the edit-mode bulk gesture);
 * outside edit mode, holding C lets the player sweep across multiple
 * slots.
 *
 * <p>Pattern mirrors {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotKeybind}.
 */
public final class ColumnCyclerKeybind {

    private ColumnCyclerKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (!IPConfig.columnCyclerEnabled()) return;
                        if (!IPKeybinds.CYCLE_SLOT.matches(event)) return;
                        // GLFW auto-repeat: ignore once a C-drag is active.
                        if (ColumnCyclerDragController.isCKeyDragActive()) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Minecraft mc = Minecraft.getInstance();
                        double mouseX = mc.mouseHandler.xpos()
                                * (double) mc.getWindow().getGuiScaledWidth()
                                / (double) mc.getWindow().getScreenWidth();
                        double mouseY = mc.mouseHandler.ypos()
                                * (double) mc.getWindow().getGuiScaledHeight()
                                / (double) mc.getWindow().getScreenHeight();

                        Slot hovered = slotUnderMouse(currentAcs, mouseX, mouseY);
                        if (hovered == null || !ColumnCycler.isCycleable(hovered)) return;
                        int slotIdx = hovered.getContainerSlot();
                        ColumnCycler.toggleByContainerSlot(slotIdx);
                        boolean newState = ColumnCycler.isCycleSlot(slotIdx);
                        // Start a C-key drag in non-edit mode so the user
                        // can hold C and sweep across multiple slots,
                        // coercing each to the first slot's new state. In
                        // edit mode the drag is the LMB-drag mechanic;
                        // C stays a single-slot toggle to avoid two
                        // drag gestures competing.
                        if (!ColumnCyclerEditMode.isOn()) {
                            ColumnCyclerDragController.startCKeyDrag(slotIdx, newState);
                        }
                    });
        });
    }

    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs,
                                                 double mouseX, double mouseY) {
        int leftPos = ScreenLayout.leftPos(acs);
        int topPos = ScreenLayout.topPos(acs);
        for (Slot slot : acs.getMenu().slots) {
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16
                    && mouseY >= sy && mouseY < sy + 16) {
                return slot;
            }
        }
        return null;
    }
}
