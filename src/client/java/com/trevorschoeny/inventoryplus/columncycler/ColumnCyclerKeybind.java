package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-scoped {@code C} keybind: toggles cycle membership on the
 * cycleable player slot currently under the cursor.
 *
 * <p>No-op when:
 * <ul>
 *   <li>{@code columnCyclerEnabled} is off (Power Users feature gate).</li>
 *   <li>The hovered slot is not cycleable (armor, offhand, an external
 *       container or a non-player inventory).</li>
 * </ul>
 *
 * <p>Holding C sweeps across multiple slots. The key is the only way to
 * add slots now that edit mode is gone.
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
                        // key auto-repeat: ignore once a C-drag is active.
                        if (ColumnCyclerDragController.isCKeyDragActive()) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Slot hovered = slotUnderMouse(currentAcs);
                        if (hovered == null || !ColumnCycler.isCycleable(hovered)) return;
                        int slotIdx = hovered.getContainerSlot();
                        ColumnCycler.toggleByContainerSlot(slotIdx);
                        boolean newState = ColumnCycler.isCycleSlot(slotIdx);
                        // Start a C-key drag so the user can hold C and
                        // sweep across multiple slots, coercing each to the
                        // first slot's new state.
                        ColumnCyclerDragController.startCKeyDrag(slotIdx, newState);
                    });
        });
    }

    /**
     * The slot vanilla highlights under the cursor ({@link ScreenLayout#hoveredSlot}):
     * a created slot ahead of a vanilla one it covers, and the slot's 1-pixel rim
     * included, so the gaps between a grid's slots count too.
     */
    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs) {
        Slot slot = ScreenLayout.hoveredSlot(acs);
        return (slot != null && slot.isActive()) ? slot : null;
    }
}
