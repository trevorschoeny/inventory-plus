package com.trevorschoeny.inventoryplus.movematching;

/**
 * The role of a {@link SlotGroup} within an open container menu. Used by
 * the targetability decision so we never confuse equipment slots with
 * the main inventory.
 *
 * <p>Roles are derived from each slot's backing container reference and,
 * for the player's own slots, the slot's {@code getContainerSlot()} index
 * (the well-known vanilla mapping: 0-8 hotbar, 9-35 main inv, 36+
 * equipment / offhand / body / saddle).
 *
 * <p>"Targetable" — whether a group hosts a move-matching button — is
 * a join of (role, current screen class). See {@link SlotGroup#targetable}.
 */
public enum SlotRole {
    /** Player hotbar — container = playerInv, containerSlot in [0, 9). Never a target, never a source per spec. */
    PLAYER_HOTBAR,

    /** Player main inventory 3×9 — container = playerInv, containerSlot in [9, 36). Always targetable. */
    PLAYER_MAIN_INV,

    /**
     * Player armor / offhand / body armor / saddle — container = playerInv,
     * containerSlot ≥ 36. Vanilla 1.21+ unified equipment access through
     * the Inventory wrapper, so these slots share the player inventory's
     * container reference but live in a distinct {@code containerSlot}
     * range. Never targetable; eligible as sources per Trev's permissive-
     * source rule.
     */
    PLAYER_EQUIPMENT,

    /**
     * Any slot whose backing container is NOT the player's inventory.
     * Covers external simplecontainers (chest, shulker, hopper, dispenser,
     * ender chest) AND specialized menus' own containers (crafting result,
     * crafting input, furnace fuel/input/output, brewing-stand bottles,
     * donkey saddle, etc.). Targetability depends on the active screen
     * class — see {@link SlotGroup#targetable}.
     */
    EXTERNAL;
}
