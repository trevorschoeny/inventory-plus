package com.trevorschoeny.inventoryplus.lockeditems;

import org.jetbrains.annotations.Nullable;

/**
 * What a lock group attaches to (`plans/lock-groups.md`, "A group"). Every
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
 * slot lock and carry an item lock and an exact lock at once. Until the
 * Lock Groups build these were the lock button's three stops; the button
 * now cycles groups, and a group carries its kind.
 */
public enum LockKind {

    SLOT("slot", "Slot"),
    ITEM("item", "Item"),
    EXACT("exact", "Exact item");

    /** How the kind is written in {@code config.json}. */
    private final String id;
    private final String displayName;

    LockKind(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
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
