package com.trevorschoeny.inventoryplus.config;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;


/**
 * Vanilla {@link KeyMapping}s for IP's keybinds, registered so they appear
 * in the vanilla Controls menu and can be rebound.
 *
 * <h3>Why KeyMapping despite screen-scoped use</h3>
 *
 * The screen-scoped keybinds fire from {@code ScreenKeyboardEvents.afterKeyPress}
 * (in-screen input), not from {@code KeyMapping.consumeClick} (game-world
 * input). The KeyMapping registration is for bind discovery and remapping:
 * the Controls menu reads the registered mappings to show them. The actual
 * key match at runtime uses {@link KeyMapping#matches(int, int)} against the
 * current key and scancode from {@code afterKeyPress}.
 *
 * <h3>Defaults</h3>
 *
 * <ul>
 *   <li>{@code L}: Lock Slot toggle (Locked Slots)</li>
 *   <li>{@code I}: Move Matching in</li>
 *   <li>{@code O}: Move Matching out</li>
 *   <li>{@code S}: Sort</li>
 *   <li>{@code C}: Toggle Cycle Slot (Column Cycler)</li>
 *   <li>Up and Down: Cycle Forward and Backward (Column Cycler)</li>
 *   <li>{@code ]} and {@code [}: Cycle Forward and Backward (Hotbar Cycler)</li>
 * </ul>
 *
 * <p>Translation keys follow the convention
 * {@code key.inventoryplus.<action>}. Categories live under the
 * vanilla Controls menu's "Inventory Plus" section
 * ({@code key.category.inventoryplus.controls}: 26.x looks a category's
 * name up as {@code key.category.<namespace>.<path>}).
 */
public final class IPKeybinds {

    private IPKeybinds() {}

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("inventoryplus", "controls"));

    public static final KeyMapping SORT = new KeyMapping(
            "key.inventoryplus.sort",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_S,
            CATEGORY);

    public static final KeyMapping MOVE_MATCHING_IN = new KeyMapping(
            "key.inventoryplus.move_matching_in",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_I,
            CATEGORY);

    public static final KeyMapping MOVE_MATCHING_OUT = new KeyMapping(
            "key.inventoryplus.move_matching_out",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_O,
            CATEGORY);

    /**
     * Edit mode's keys, their own group in Controls. They act on the slot under the
     * mouse and only while edit mode is on, so they share letters with nothing else on
     * a container screen. Hold one and sweep to apply it to every slot passed over.
     */
    public static final KeyMapping.Category EDIT_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("inventoryplus", "edit_mode"));

    public static final KeyMapping EDIT_LOCK = new KeyMapping(
            "key.inventoryplus.edit_lock", InputConstants.Type.KEYBOARD, InputConstants.KEY_L, EDIT_CATEGORY);

    public static final KeyMapping EDIT_RESERVE = new KeyMapping(
            "key.inventoryplus.edit_reserve", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, EDIT_CATEGORY);

    public static final KeyMapping EDIT_CYCLE = new KeyMapping(
            "key.inventoryplus.edit_cycle", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, EDIT_CATEGORY);

    // Column Cycler moved off the brackets to the arrows so Hotbar Cycler
    // could take them. Minecraft stores bindings in options.txt, so existing
    // installs keep the brackets and get Hotbar Cycler double-bound until
    // rebound. Up cycles forward and Down backward, because the other way
    // round reads backwards.
    public static final KeyMapping CYCLE_FORWARD = new KeyMapping(
            "key.inventoryplus.cycle_forward",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_UP,
            CATEGORY);

    public static final KeyMapping CYCLE_BACKWARD = new KeyMapping(
            "key.inventoryplus.cycle_backward",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_DOWN,
            CATEGORY);

    /** Hotbar Cycler forward: rows shift down toward the hotbar. */
    public static final KeyMapping HOTBAR_CYCLE_FORWARD = new KeyMapping(
            "key.inventoryplus.hotbar_cycle_forward",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_RBRACKET,
            CATEGORY);

    /** Hotbar Cycler backward: rows shift up, away from the hotbar. */
    public static final KeyMapping HOTBAR_CYCLE_BACKWARD = new KeyMapping(
            "key.inventoryplus.hotbar_cycle_backward",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LBRACKET,
            CATEGORY);

    /**
     * Opens the settings menu. Unbound until a default is chosen. Works in the
     * world (polled on the client tick) and on container screens (matched in
     * {@code afterKeyPress}, like the other screen keys).
     */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.inventoryplus.open_settings",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    /**
     * Hides every tooltip while held. Space by default,
     * which Jump shares: 26.2 maps a key to a list of mappings, so both fire.
     * Read as a raw held state ({@link #isHeld}), never consumed, so it works
     * on every screen and a focused button or text field still gets Space.
     */
    public static final KeyMapping HIDE_TOOLTIPS = new KeyMapping(
            "key.inventoryplus.hide_tooltips",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_SPACE,
            CATEGORY);

    /**
     * Whether every key of {@code mapping}'s chord is physically down right
     * now. Vanilla releases mapping states while a screen is open, so a key
     * that must work under any screen (the pause menu included) reads
     * Keybindery's physical state instead ({@link KeybinderyAPI#isHeld}).
     */
    public static boolean isHeld(KeyMapping mapping) {
        return KeybinderyAPI.getInstance().isHeld(mapping);
    }

    /** Register all keybinds with Fabric. Call once from client init. */
    public static void register() {
        KeyMappingHelper.registerKeyMapping(SORT);
        KeyMappingHelper.registerKeyMapping(MOVE_MATCHING_IN);
        KeyMappingHelper.registerKeyMapping(MOVE_MATCHING_OUT);
        KeyMappingHelper.registerKeyMapping(EDIT_LOCK);
        KeyMappingHelper.registerKeyMapping(EDIT_RESERVE);
        KeyMappingHelper.registerKeyMapping(EDIT_CYCLE);
        KeyMappingHelper.registerKeyMapping(CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(CYCLE_BACKWARD);
        KeyMappingHelper.registerKeyMapping(HOTBAR_CYCLE_FORWARD);
        KeyMappingHelper.registerKeyMapping(HOTBAR_CYCLE_BACKWARD);
        KeyMappingHelper.registerKeyMapping(OPEN_SETTINGS);
        KeyMappingHelper.registerKeyMapping(HIDE_TOOLTIPS);
    }
}
