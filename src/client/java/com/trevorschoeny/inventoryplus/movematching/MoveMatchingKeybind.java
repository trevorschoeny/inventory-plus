package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Screen-scoped {@code I} and {@code O} keybinds for Move Matching in and
 * out.
 *
 * <ul>
 *   <li>{@code I} pressed anywhere in a supported screen
 *       ({@code ContainerScreen}, shulker, hopper, dispenser) runs Move
 *       Matching in.</li>
 *   <li>{@code O} in the same screens runs Move Matching out.</li>
 *   <li>Outside those screens (the standalone inventory, specialized UI,
 *       no UI) nothing happens, because the handler isn't registered.</li>
 * </ul>
 *
 * <p>No slot-hover requirement. The keys fire anywhere in the open
 * screen.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so a global keybind doesn't
 * steal {@code I} and {@code O} during normal gameplay.
 */
public final class MoveMatchingKeybind {

    private MoveMatchingKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        Direction direction = directionForEvent(event);
                        if (direction == null) return;
                        if (!IPConfig.moveMatchingEnabled()) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> acs)) return;

                        List<SlotGroup> groups = SlotGroupDetector.detect(acs);
                        SlotGroup playerMainInv =
                                MoveMatchingButtons.findPlayerMainInv(groups);
                        if (playerMainInv == null) return;

                        MoveMatchingExecutor.execute(
                                Minecraft.getInstance(), playerMainInv, direction,
                                MoveMatchingModes.current(direction));
                    });
        });
    }

    private static @Nullable Direction directionForEvent(net.minecraft.client.input.KeyEvent event) {
        if (IPKeybinds.MOVE_MATCHING_IN.matches(event))  return Direction.IN;
        if (IPKeybinds.MOVE_MATCHING_OUT.matches(event)) return Direction.OUT;
        return null;
    }
}
