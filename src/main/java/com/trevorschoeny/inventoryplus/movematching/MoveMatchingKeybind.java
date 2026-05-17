package com.trevorschoeny.inventoryplus.movematching;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Screen-scoped {@code I} / {@code O} keybinds for Move Matching IN /
 * OUT.
 *
 * <p>Behavior per Trev / Lead 2026-05-16:
 *
 * <ul>
 *   <li>{@code I} pressed anywhere in a supported screen
 *       ({@code ContainerScreen} / shulker / hopper / dispenser) →
 *       trigger Move Matching IN.</li>
 *   <li>{@code O} pressed in the same scope → trigger Move Matching OUT.</li>
 *   <li>Outside those screens (standalone inventory, specialized UI,
 *       no UI) → no-op (the handler isn't registered).</li>
 * </ul>
 *
 * <p>No slot-hover requirement. The keys fire anywhere in the open
 * screen.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so a global keybind doesn't
 * steal {@code I} / {@code O} during normal gameplay. Promoting to
 * rebindable {@link net.minecraft.client.KeyMapping}s is filed in
 * DEFERRED.md.
 */
public final class MoveMatchingKeybind {

    private MoveMatchingKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        Direction direction = directionForKey(event.key());
                        if (direction == null) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> acs)) return;
                        // Edit mode disables MM I/O keybinds per Trev 2026-05-16.
                        if (com.trevorschoeny.inventoryplus.lockedslots.LockEditMode.isOn()) return;

                        List<SlotGroup> groups = SlotGroupDetector.detect(acs);
                        SlotGroup playerMainInv =
                                MoveMatchingButtons.findPlayerMainInv(groups);
                        if (playerMainInv == null) return;

                        MoveMatchingExecutor.execute(
                                Minecraft.getInstance(), playerMainInv, direction);
                    });
        });
    }

    private static @Nullable Direction directionForKey(int key) {
        if (key == GLFW.GLFW_KEY_I) return Direction.IN;
        if (key == GLFW.GLFW_KEY_O) return Direction.OUT;
        return null;
    }
}
