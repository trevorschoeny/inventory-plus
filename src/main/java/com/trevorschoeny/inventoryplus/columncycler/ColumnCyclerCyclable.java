package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.cyclable.CyclerOperation;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclable;

/**
 * Adapts Column Cycler's "rotate column" mechanic to the
 * {@link HotbarCyclable} contract.
 *
 * <p>Auto Tool Switch (and any future feature that wants "fetch this
 * slot's item into the active hand by cycling") consumes this through
 * {@link com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry}
 * — never knowing this is Column Cycler specifically. New cyclers
 * (Pocket Cycler, Hotbar Swap, etc.) will provide their own
 * {@link HotbarCyclable} implementations alongside this one.
 *
 * <h3>Slot ownership</h3>
 *
 * This cycler claims inv slots (9-35) that are <b>directly</b> toggled
 * into a cycle, and only when the feature is enabled. Hotbar slots
 * (0-8) are <b>not</b> claimed — they're already at their destination
 * (no cycling needed). Non-cycle inv slots are not claimed.
 *
 * <h3>Stateless w.r.t. memory</h3>
 *
 * Each {@link #bringToHotbar(int)} call returns a fresh
 * {@link CyclerOperation} that closes over the step count + reverse
 * direction needed to undo just that call. No state stored on this
 * class. Multiple callers can interleave operations without
 * interference (caller ordering still matters for correctness, but the
 * cycler itself doesn't coordinate across them).
 */
public final class ColumnCyclerCyclable implements HotbarCyclable {

    /**
     * Singleton — registered once via
     * {@link com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry#register}
     * during client init. No instance state, so a single instance is
     * sufficient for all consumers.
     */
    public static final ColumnCyclerCyclable INSTANCE = new ColumnCyclerCyclable();

    private ColumnCyclerCyclable() {}

    @Override
    public int hotbarPositionOf(int slot) {
        // Only directly-cycleable inv slots (9-35) are claimed. Hotbar
        // slots (0-8) aren't claimed — they're already at destination;
        // their derived-cycle-membership is a different concern (lock
        // pairing, edit-mode rendering) handled inside ColumnCycler.
        if (slot < ColumnCycler.MIN_DIRECT_CYCLE_SLOT) return -1;
        if (slot > ColumnCycler.MAX_CYCLEABLE_CONTAINER_SLOT) return -1;
        // ColumnCycler.isCycleSlot also gates on feature-enabled — if
        // the feature is off, no slots are claimed and Auto Tool Switch
        // falls through to Tier 3 (inventory swap).
        if (!ColumnCycler.isCycleSlot(slot)) return -1;
        // The slot's column IS the hotbar position the rotation brings
        // it to: column = slot % 9; hotbar slot index = column.
        return slot % 9;
    }

    @Override
    public CyclerOperation bringToHotbar(int slot) {
        // Re-validate claim — caller may have used a stale lookup, or
        // the slot's cycle state may have changed between query and
        // bring-to-hotbar.
        if (hotbarPositionOf(slot) == -1) return CyclerOperation.NO_OP;
        // ColumnCyclerRotator owns the rotation algorithm + cycle-list
        // construction. planBringSlotToHotbar computes the cheaper of
        // FORWARD/BACKWARD (each rotation costs N+1 click packets, so
        // direction choice matters).
        final int column = slot % 9;
        ColumnCyclerRotator.RotationPlan plan =
                ColumnCyclerRotator.planBringSlotToHotbar(slot);
        if (plan == null || plan.steps() == 0) {
            // plan == null: cycle is degenerate (single member) or slot
            // dropped out of the cycle between query and bring; nothing
            // to do, nothing to undo.
            // steps == 0: item is already at the hotbar (shouldn't happen
            // for an inv slot we claimed, but defensive).
            return CyclerOperation.NO_OP;
        }
        // Forward: run the planned rotations.
        for (int i = 0; i < plan.steps(); i++) {
            ColumnCyclerRotator.rotate(column, plan.direction());
        }
        // Capture the reversal parameters in the undo closure. The undo
        // path is symmetric: same step count, opposite direction. The
        // closure intentionally doesn't re-query the cycle list — at
        // undo time the player may have toggled cycle membership, but
        // we want to reverse OUR rotation, not whatever the current
        // state would say. Best-effort: if the cycle list shape changed
        // during the action window, the reverse may not perfectly
        // restore — accepted, matches drift semantics in the
        // CyclerOperation javadoc.
        final int undoSteps = plan.steps();
        final ColumnCyclerRotator.Direction undoDirection =
                (plan.direction() == ColumnCyclerRotator.Direction.FORWARD)
                        ? ColumnCyclerRotator.Direction.BACKWARD
                        : ColumnCyclerRotator.Direction.FORWARD;
        return () -> {
            for (int i = 0; i < undoSteps; i++) {
                ColumnCyclerRotator.rotate(column, undoDirection);
            }
        };
    }
}
