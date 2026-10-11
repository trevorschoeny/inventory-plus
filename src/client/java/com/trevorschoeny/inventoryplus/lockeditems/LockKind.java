package com.trevorschoeny.inventoryplus.lockeditems;

import org.jetbrains.annotations.Nullable;

/**
 * What a lock group attaches to. Every
 * group has exactly one kind, fixed for the two defaults and picked when a
 * custom group is made.
 *
 * <ul>
 *   <li><b>Slot</b> locks the position, whatever ends up sitting in it.</li>
 *   <li><b>Item</b> locks every item of a type: every diamond pickaxe.</li>
 *   <li><b>Exact</b> locks one item as it is, components and all, however
 *       worn it later gets.</li>
 * </ul>
 *
 * <p>The kinds are independent and combine freely: one stack can sit in a
 * slot lock and carry an item lock and an exact lock at once. Before lock
 * groups these were the lock button's three stops. The button now cycles
 * groups, and a group carries its kind.
 */
public enum LockKind {

    SLOT("slot", "Slot", "Locks the slot itself. Whatever sits in it stays put, and moves "
            + "leave the slot alone even after you swap in something else."),
    ITEM("item", "Item", "Locks every item of one type, wherever it is. Lock one diamond "
            + "pickaxe and every diamond pickaxe is locked."),
    EXACT("exact", "Exact item", "Locks one item as it is, enchantments, name and all, "
            + "however worn it gets. Another item of the same type isn't locked.");

    /** How the kind is written in {@code config.json}. */
    private final String id;
    private final String displayName;
    /** How the kind works, in a sentence or two: the settings menu's hover text. */
    private final String description;

    LockKind(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String description() {
        return description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** True for the kinds that lock an item rather than a slot. */
    public boolean locksItems() {
        return this != SLOT;
    }

    /** The kind written as {@code id}, or {@code null} for anything else. */
    public static @Nullable LockKind fromId(@Nullable String id) {
        for (LockKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
