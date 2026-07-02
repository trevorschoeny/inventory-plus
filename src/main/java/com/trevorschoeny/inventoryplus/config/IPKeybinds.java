package com.trevorschoeny.inventoryplus.config;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

/**
 * Vanilla {@link KeyMapping}s for IP's four screen-scoped keybinds —
 * registered so they appear in the vanilla Controls menu and are
 * user-rebindable.
 *
 * <h3>Why KeyMapping despite screen-scoped use</h3>
 *
 * Each of these keybinds fires from {@code ScreenKeyboardEvents.afterKeyPress}
 * (in-screen input), not from {@code KeyMapping.consumeClick} (game-world
 * input). The KeyMapping registration is for *bind discovery and
 * remapping* — the Controls menu reads the registered mappings to show
 * them. The actual key match at runtime uses
 * {@link KeyMapping#matches(int, int)} against the current key + scancode
 * from {@code afterKeyPress}.
 *
 * <h3>Defaults</h3>
 *
 * Per Trev 2026-05-17 the defaults preserve current behavior:
 * <ul>
 *   <li>{@code L} — Lock Slot toggle (LockedSlots feature)</li>
 *   <li>{@code I} — Move Matching IN</li>
 *   <li>{@code O} — Move Matching OUT</li>
 *   <li>{@code S} — Sort</li>
 *   <li>{@code C} — Toggle Cycle Slot (Column Cycler feature)</li>
 *   <li>{@code ]} — Cycle Forward (Column Cycler — items shift toward hotbar)</li>
 *   <li>{@code [} — Cycle Backward (Column Cycler — items shift away from hotbar)</li>
 * </ul>
 *
 * <p>Translation keys follow the convention
 * {@code key.inventoryplus.<action>}; categories live under the
 * vanilla Controls menu's "Inventory Plus" section
 * ({@code category.inventoryplus.controls}).
 */
public final class IPKeybinds {

    private IPKeybinds() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("inventoryplus", "controls"));

    public static final KeyMapping LOCK_SLOT = new KeyMapping(
            "key.inventoryplus.lock_slot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            CATEGORY);

    public static final KeyMapping SORT = new KeyMapping(
            "key.inventoryplus.sort",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_S,
            CATEGORY);

    public static final KeyMapping MOVE_MATCHING_IN = new KeyMapping(
            "key.inventoryplus.move_matching_in",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            CATEGORY);

    public static final KeyMapping MOVE_MATCHING_OUT = new KeyMapping(
            "key.inventoryplus.move_matching_out",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    public static final KeyMapping CYCLE_SLOT = new KeyMapping(
            "key.inventoryplus.cycle_slot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    public static final KeyMapping CYCLE_FORWARD = new KeyMapping(
            "key.inventoryplus.cycle_forward",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY);

    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping(
            "key.inventoryplus.cycle_backward",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            CATEGORY);

    /**
     * Auto Tool Switch "return to previous tool" — defaults to <b>Left Shift
     * (Sneak)</b> so "sneak to return" works out of the box (rebindable; it
     * shares the key with vanilla Sneak, which is fine — both fire). Unlike the
     * others (screen-scoped via {@code afterKeyPress}), this one is game-world
     * input, polled via {@link KeyMapping#consumeClick()} on the client tick.
     * Used by the HOTKEY_TIMED / HOTKEY_ANYTIME return modes.
     */
    public static final KeyMapping AUTO_SWITCH_RETURN = new KeyMapping(
            "key.inventoryplus.auto_switch_return",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            CATEGORY);

    /** Register all keybinds with Fabric. Call once from client init. */
    public static void register() {
        KeyMappingHelper.registerKeyMapping(LOCK_SLOT);
        KeyMappingHelper.registerKeyMapping(SORT);
        KeyMappingHelper.registerKeyMapping(MOVE_MATCHING_IN);
        KeyMappingHelper.registerKeyMapping(MOVE_MATCHING_OUT);
        KeyMappingHelper.registerKeyMapping(CYCLE_SLOT);
        KeyMappingHelper.registerKeyMapping(CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(CYCLE_BACKWARD);
        KeyMappingHelper.registerKeyMapping(AUTO_SWITCH_RETURN);
    }
}
