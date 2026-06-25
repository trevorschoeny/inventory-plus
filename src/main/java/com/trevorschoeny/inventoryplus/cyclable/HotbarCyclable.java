package com.trevorschoeny.inventoryplus.cyclable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Contract for anything that can "extend the player's hotbar reach" via
 * cycling — Column Cycler is the first implementer, Pocket Cycler (IPP)
 * and a future Hotbar Swap will follow.
 *
 * <h3>Why this exists</h3>
 *
 * Auto Tool Switch needs to fetch the right tool into the active hand. Its
 * three-tier resolution priority is:
 * <ol>
 *   <li>Right tool already in another hotbar slot → just change the
 *       selected slot.</li>
 *   <li>Right tool in a <b>cyclable hotbar position</b> — a slot reachable
 *       into the hotbar via a cycler's native action → change selected
 *       slot + ask the cycler to bring the item down.</li>
 *   <li>Right tool elsewhere in main inventory → swap with the active
 *       hotbar slot via a vanilla inventory click.</li>
 * </ol>
 *
 * <p>Tier 2 is what this interface abstracts. Each cycler answers two
 * questions for any given slot:
 * <ul>
 *   <li>"Do you claim this slot, and if so, which hotbar position does
 *       it resolve to?" — {@link #hotbarPositionOf(int)}.</li>
 *   <li>"Bring this slot's item down to the hotbar via your native action,
 *       and give me a handle to reverse what you just did." —
 *       {@link #bringToHotbar(int)}.</li>
 * </ul>
 *
 * <p>Auto Tool Switch consumes the cyclers through
 * {@link HotbarCyclableRegistry} — it never knows about Column Cycler /
 * Pocket Cycler specifically. New cyclers slot in by registering at mod
 * init; consumers stay cycler-agnostic forever.
 *
 * <h3>State model — bidirectional via undo handles</h3>
 *
 * {@link #bringToHotbar(int)} returns a {@link CyclerOperation} that the
 * caller holds onto. When the caller wants to reverse the action
 * (e.g., Auto Tool Switch's "auto-return after action" path), it calls
 * {@link CyclerOperation#undo()}.
 *
 * <p>The cycler is stateless w.r.t. ephemeral memory — each call returns
 * a fresh handle that closes over whatever state is needed to reverse
 * just that call. Multiple callers can interleave bring-to-hotbar
 * operations without interfering, each holding its own undo handle.
 * (Caller ordering still matters for correctness; the cycler doesn't
 * coordinate across consumers.)
 *
 * <h3>Slot indexing</h3>
 *
 * The {@code slot} parameter throughout is a <b>container-slot index</b>
 * into the player's inventory (the 0-35 layout used by
 * {@code Slot.getContainerSlot()}): 0-8 hotbar, 9-35 main inv. Cyclers
 * decide which subset of those indices they claim.
 *
 * <h3>Extra slots — reaching past the vanilla inventory</h3>
 *
 * Some cyclers source items from storage that lives <b>outside</b> the
 * 0-35 model entirely — Pocket Cycler's pockets are backed by a separate
 * attachment, not the player's {@code Inventory}. Those slots still want
 * to participate in tool search (Auto Tool Switch, Auto-Restock) and
 * still resolve to a hotbar position via the cycler's native action.
 *
 * <p>{@link #extraSearchSlots(Player)} surfaces them. Each carries an
 * <b>encoded id</b> the cycler invents for itself, plus the live
 * {@link ItemStack} (read straight from the cycler's own backing, so the
 * caller never needs to know where the content actually lives). The id:
 * <ul>
 *   <li>must be <b>disjoint from 0-35</b> (so callers can keep reading
 *       real inventory slots via {@code inv.getItem(i)} and never confuse
 *       the two namespaces), and</li>
 *   <li>must be <b>disjoint across cyclers</b> — the same disjointness
 *       contract the 0-35 claims already follow, so a returned id routes
 *       to exactly one cycler.</li>
 * </ul>
 *
 * <p>{@link #hotbarPositionOf(int)} and {@link #bringToHotbar(int)} accept
 * these encoded ids too — a claimed extra id resolves to its hotbar
 * position and brings down via the cycler's native action, exactly like a
 * claimed 0-35 slot. Callers therefore treat extra slots as just more
 * Tier-2 candidates; they never special-case "is this a pocket".
 */
public interface HotbarCyclable {

    /**
     * One searchable slot that lives outside the 0-35 inventory model —
     * an encoded {@code id} (see the "Extra slots" contract on the type)
     * paired with the live {@link ItemStack} currently in it.
     *
     * <p>The stack is the cycler's own live content, so callers score /
     * match against it directly without reaching into the cycler's
     * backing. The id is opaque to callers — they only hand it back to
     * {@link #hotbarPositionOf(int)} / {@link #bringToHotbar(int)} (and
     * any future extra-slot op) to act on the slot.
     */
    record ExtraSlot(int id, ItemStack stack) {}

    /**
     * Searchable slots this cycler reaches that are <b>not</b> in the 0-35
     * inventory model (e.g., Pocket Cycler's attachment-backed pockets).
     * Returns an empty list by default — cyclers whose slots all live in
     * 0-35 (Column Cycler) have nothing extra to contribute.
     *
     * <p>Only slots that are currently <b>reachable</b> should be returned
     * — e.g., a pocket that exists server-side but isn't revealed in the
     * current world contributes nothing. Empty stacks may be included or
     * skipped at the cycler's discretion; callers skip empties anyway.
     *
     * <p>Read client-side and called on the client tick path, so keep it
     * cheap (O(small)).
     */
    default List<ExtraSlot> extraSearchSlots(Player player) {
        return List.of();
    }

    /**
     * Returns the hotbar position (0-8) this slot would resolve to if
     * brought down via this cycler's native action, or {@code -1} if
     * this cycler doesn't claim the slot.
     *
     * <p>Callers can use this to (a) classify a slot as "Tier 2
     * cyclable" before deciding to call {@link #bringToHotbar(int)},
     * and (b) know which hotbar position to change the selected-slot
     * to ahead of the bring-down.
     */
    int hotbarPositionOf(int slot);

    /**
     * Bring the item currently at {@code slot} into its associated
     * hotbar position via this cycler's native action (rotation, pocket
     * cycle, row swap, etc.). Returns an undo handle the caller holds
     * onto.
     *
     * <p>If this cycler doesn't claim the slot (i.e.,
     * {@link #hotbarPositionOf(int)} returns -1), returns
     * {@link CyclerOperation#NO_OP}.
     *
     * <p>If the requested item is already at the hotbar position (no
     * cycling needed), returns {@link CyclerOperation#NO_OP}.
     *
     * <p>The returned operation closes over whatever state is needed
     * to reverse just this call. Calling {@code undo()} multiple times
     * is undefined — callers should call it at most once.
     */
    CyclerOperation bringToHotbar(int slot);

    /**
     * Move the content of an extra slot (see {@link #extraSearchSlots})
     * <b>out</b> toward its natural quick-move destination — the non-held
     * restock path, where the item should relocate (a totem refilling the
     * equipment slot, armor equipping after a break) rather than rotate
     * into the hand the way {@link #bringToHotbar} does.
     *
     * <p>Returns {@code true} if this cycler owns {@code slot} and
     * initiated the move; {@code false} otherwise — in which case the
     * caller treats {@code slot} as an ordinary inventory slot and moves it
     * with a normal client-side click. The default never claims a slot:
     * cyclers whose slots live in the real 0-35 inventory don't need this,
     * because those slots are directly clickable.
     *
     * <p>For a cycler whose backing is inert in-world (Pocket Cycler), the
     * move is server-authoritative — same reason {@link #bringToHotbar} is.
     * This is fire-and-forget: a restock move is permanent, so there's no
     * undo handle.
     */
    default boolean quickMoveOut(int slot) {
        return false;
    }
}
