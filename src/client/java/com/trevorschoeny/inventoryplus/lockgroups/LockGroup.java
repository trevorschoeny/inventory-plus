package com.trevorschoeny.inventoryplus.lockgroups;

import com.trevorschoeny.inventoryplus.lockeditems.LockKind;

import org.jetbrains.annotations.Nullable;

/**
 * One lock group (`plans/lock-groups.md`): what it attaches to, what it is
 * called, and how it looks. What a group stops is not here; it lives in the
 * reach record with every other reach choice ({@link Reach}).
 *
 * @param id     stable id, never shown: {@code slot_lock} and {@code exact_item}
 *               for the defaults, {@code g1}, {@code g2}, ... for custom groups
 * @param kind   what a lock of this group attaches to
 * @param name   what the player calls it
 * @param colour a dye colour's name ({@code gray}, {@code red}, ...)
 * @param key    the {@code KeyMapping} name that applies this group, or
 *               {@code null}. Only Slot lock has one until Keybindery's
 *               runtime bindings exist; the field is here now so they drop
 *               in without a version bump.
 */
public record LockGroup(String id, LockKind kind, String name, String colour, @Nullable String key) {

    /** The group's key in the reach record: {@code inventoryplus:lock/<id>}. */
    public String reachKey() {
        return Reach.lockKey(id);
    }

    /** True for the two groups that always exist. */
    public boolean isDefault() {
        return id.equals(Reach.SLOT_LOCK) || id.equals(Reach.EXACT_ITEM);
    }
}
