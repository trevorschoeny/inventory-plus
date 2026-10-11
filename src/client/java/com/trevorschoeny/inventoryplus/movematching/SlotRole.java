package com.trevorschoeny.inventoryplus.movematching;

/**
 * The role of a {@link SlotGroup} within an open container menu. Used by
 * the targetability decision so equipment slots are never confused with
 * the main inventory.
 *
 * <p>Roles are derived from each slot's backing container reference and,
 * for the player's own slots, the slot's {@code getContainerSlot()} index
 * (the well-known vanilla mapping: 0-8 hotbar, 9-35 main inv, 36+
 * equipment / offhand / body / saddle).
 *
 * <p>Whether a group is targetable (hosts a Move Matching button) depends on
 * both its role and the current screen class. See {@link SlotGroup#targetable}.
 */
public enum SlotRole {
    /** Player hotbar: container is playerInv, containerSlot in [0, 9). Never a target, never a source. */
    PLAYER_HOTBAR,

    /** Player main inventory 3x9: container is playerInv, containerSlot in [9, 36). Always targetable. */
    PLAYER_MAIN_INV,

    /**
     * Player armor, offhand, body armor and saddle: container is playerInv,
     * containerSlot 36 and up. Vanilla 1.21+ unified equipment access
     * through the Inventory wrapper, so these slots share the player
     * inventory's container reference but live in a distinct
     * {@code containerSlot} range. Never targetable, but they may be
     * sources.
     */
    PLAYER_EQUIPMENT,

    /**
     * Any slot whose backing container is not the player's inventory.
     * Covers external simple containers (chest, shulker, hopper, dispenser,
     * ender chest) and specialized menus' own containers (crafting result
     * and input, furnace fuel, input and output, brewing-stand bottles,
     * donkey saddle). Targetability depends on the active screen class.
     * See {@link SlotGroup#targetable}.
     */
    EXTERNAL;
}
