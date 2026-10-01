package com.trevorschoeny.inventoryplus.config;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
 *   <li>Down / Up — Cycle Forward / Backward (Column Cycler)</li>
 *   <li>{@code ]} / {@code [} — Cycle Forward / Backward (Hotbar Cycler)</li>
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

    // Column Cycler moved off the brackets to the arrows on 2026-09-04 so
    // Hotbar Cycler could take them; see cycle-modes.md for the six-keybind
    // table. Minecraft stores bindings in options.txt, so existing installs
    // keep the brackets and get Hotbar Cycler double-bound until rebound.
    public static final KeyMapping CYCLE_FORWARD = new KeyMapping(
            "key.inventoryplus.cycle_forward",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DOWN,
            CATEGORY);

    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping(
            "key.inventoryplus.cycle_backward",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UP,
            CATEGORY);

    /** Hotbar Cycler forward — rows shift down toward the hotbar. */
    public static final KeyMapping HOTBAR_CYCLE_FORWARD = new KeyMapping(
            "key.inventoryplus.hotbar_cycle_forward",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY);

    /** Hotbar Cycler backward — rows shift up away from the hotbar. */
    public static final KeyMapping HOTBAR_CYCLE_BACKWARD = new KeyMapping(
            "key.inventoryplus.hotbar_cycle_backward",
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

    /**
     * Opens the settings menu. Unbound until a default is chosen. Works in the
     * world (polled on the client tick) and on container screens (matched in
     * {@code afterKeyPress}, like the other screen keys).
     */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.inventoryplus.open_settings",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    /**
     * Hides every tooltip while held (Trev, 2026-09-30). Space by default,
     * which Jump shares: 26.2 maps a key to a list of mappings, so both fire.
     * Read as a raw held state ({@link #isHeld}), never consumed, so it works
     * on every screen and a focused button or text field still gets Space.
     */
    public static final KeyMapping HIDE_TOOLTIPS = new KeyMapping(
            "key.inventoryplus.hide_tooltips",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_SPACE,
            CATEGORY);

    /**
     * Whether every key of {@code mapping}'s chord is physically down right
     * now. Vanilla releases mapping states while a screen is open, so a key
     * that must work under any screen (the pause menu included) is polled
     * from the window instead.
     *
     * <p>ponytail: {@code Chord.isActiveHeld} is marked internal in
     * Keybindery; ask Keybindery to publish a held-state read and switch to it.
     */
    public static boolean isHeld(KeyMapping mapping) {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow() != null
                && KeybinderyAPI.getInstance().getChord(mapping).isActiveHeld(mc.getWindow().handle());
    }

    /** Register all keybinds with Fabric. Call once from client init. */
    public static void register() {
        KeyMappingHelper.registerKeyMapping(LOCK_SLOT);
        KeyMappingHelper.registerKeyMapping(SORT);
        KeyMappingHelper.registerKeyMapping(MOVE_MATCHING_IN);
        KeyMappingHelper.registerKeyMapping(MOVE_MATCHING_OUT);
        KeyMappingHelper.registerKeyMapping(CYCLE_SLOT);
        KeyMappingHelper.registerKeyMapping(CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(CYCLE_BACKWARD);
        KeyMappingHelper.registerKeyMapping(HOTBAR_CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(HOTBAR_CYCLE_BACKWARD);
        KeyMappingHelper.registerKeyMapping(AUTO_SWITCH_RETURN);
        KeyMappingHelper.registerKeyMapping(OPEN_SETTINGS);
        KeyMappingHelper.registerKeyMapping(HIDE_TOOLTIPS);
    }
}
