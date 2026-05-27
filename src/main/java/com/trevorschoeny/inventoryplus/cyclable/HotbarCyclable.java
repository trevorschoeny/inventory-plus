package com.trevorschoeny.inventoryplus.cyclable;

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
 */
public interface HotbarCyclable {

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
}
