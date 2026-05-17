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
 * OUT (Trev / Lead 2026-05-16 simplification).
 *
 * <p>Single behavior per key — no slot-hover requirement:
 *
 * <ul>
 *   <li>{@code I} pressed anywhere inside the inventory screen when a
 *       supported container is open → trigger Move Matching IN.</li>
 *   <li>{@code O} pressed under the same conditions → trigger Move
 *       Matching OUT.</li>
 *   <li>Container not open (standalone inventory, specialized UI) →
 *       no-op.</li>
 * </ul>
 *
 * <p>The pre-simplification keybind required hovering a slot in a
 * targetable group; that requirement is dropped — the keybind now
 * fires anywhere in the inventory UI as long as an external
 * simplecontainer is open.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so {@code I} / {@code O}
 * only fire inside simplecontainer screens — outside those screens
 * there's no meaningful action, and a global keybind would steal
 * the keys during normal gameplay. Promoting to rebindable
 * {@link net.minecraft.client.KeyMapping}s is filed in DEFERRED.md.
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

                        // Same gate as MoveMatchingButtons — only fire when
                        // an external simplecontainer is actually open.
                        List<SlotGroup> groups = SlotGroupDetector.detect(acs);
                        if (!MoveMatchingButtons.hasExternalTargetable(groups)) return;
                        SlotGroup playerMainInv = MoveMatchingButtons.findPlayerMainInv(groups);
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
