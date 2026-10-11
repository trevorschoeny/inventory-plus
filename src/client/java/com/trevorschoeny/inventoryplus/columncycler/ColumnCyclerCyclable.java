package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.api.CyclerOperation;
import com.trevorschoeny.inventoryplus.api.HotbarCyclable;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.COLUMN_CYCLE;

import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.TriBool;


/**
 * Adapts Column Cycler's "rotate column" mechanic to the
 * {@link HotbarCyclable} contract.
 *
 * <p>Auto Tool Switch (and any other feature that wants "fetch this slot's
 * item into the active hand by cycling") consumes this through
 * {@link com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry}
 * without knowing it is Column Cycler. Other cyclers provide their own
 * {@link HotbarCyclable} implementations alongside this one.
 *
 * <h3>Slot ownership</h3>
 *
 * This cycler claims inventory slots (9-35) that are <b>directly</b> toggled
 * into a cycle, and only when the feature is enabled. Hotbar slots (0-8) are
 * not claimed, since they're already at their destination. Non-cycle
 * inventory slots are not claimed.
 *
 * <h3>Stateless</h3>
 *
 * Each {@link #bringToHotbar(int)} call returns a fresh
 * {@link CyclerOperation} that closes over the step count and reverse
 * direction needed to undo just that call. Multiple callers can interleave
 * operations (caller ordering still matters for correctness, but the
 * cycler itself doesn't coordinate across them).
 */
public final class ColumnCyclerCyclable implements HotbarCyclable {

    /**
     * Registered once via
     * {@link com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry#register}
     * during client init.
     */
    public static final ColumnCyclerCyclable INSTANCE = new ColumnCyclerCyclable();

    private ColumnCyclerCyclable() {}

    @Override
    public int hotbarPositionOf(int slot) {
        // Hotbar slots (0-8) are already at their destination. Their
        // derived cycle membership is a different concern (lock pairing)
        // handled inside ColumnCycler.
        if (slot < ColumnCycler.MIN_DIRECT_CYCLE_SLOT) return -1;
        if (slot > ColumnCycler.MAX_CYCLEABLE_CONTAINER_SLOT) return -1;
        // isCycleSlot also gates on the feature being enabled. If it is
        // off, no slots are claimed and Auto Tool Switch falls through to
        // Tier 3 (inventory swap).
        if (!ColumnCycler.isCycleSlot(slot)) return -1;
        // The slot's column is the hotbar slot the rotation brings it to.
        return slot % 9;
    }

    @Override
    public CyclerOperation bringToHotbar(int slot) {
        return bringToHotbar(slot, COLUMN_CYCLE, COLUMN_CYCLE);
    }

    /**
     * Brings the slot down as {@code take}/{@code put}: a restock or tool switch
     * rotating the column is judged as that, not as the player cycling. The undo
     * reverses under the same operation.
     */
    @Override
    public CyclerOperation bringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        // Re-validate the claim: the caller may have used a stale lookup, or
        // the slot's cycle state may have changed since the query.
        if (hotbarPositionOf(slot) == -1) return CyclerOperation.NO_OP;
        // planBringSlotToHotbar picks the cheaper of FORWARD and BACKWARD.
        // Each rotation costs N+1 click packets, so direction matters.
        final int column = slot % 9;
        ColumnCyclerRotator.RotationPlan plan =
                ColumnCyclerRotator.planBringSlotToHotbar(slot);
        if (plan == null || plan.steps() == 0) {
            // plan == null: the cycle is degenerate (single member) or the
            // slot dropped out of the cycle since the query.
            // steps == 0: the item is already at the hotbar (shouldn't
            // happen for a claimed inventory slot, but defensive).
            return CyclerOperation.NO_OP;
        }
        for (int i = 0; i < plan.steps(); i++) {
            ColumnCyclerRotator.rotate(column, plan.direction(), take, put);
        }
        // The undo is symmetric: same step count, opposite direction. It
        // deliberately doesn't re-query the cycle list, because it reverses
        // this rotation, not whatever the current state would say. If the
        // cycle list changed during the action window the reverse may not
        // restore perfectly, which matches the drift semantics in the
        // CyclerOperation javadoc.
        final int undoSteps = plan.steps();
        final ColumnCyclerRotator.Direction undoDirection =
                (plan.direction() == ColumnCyclerRotator.Direction.FORWARD)
                        ? ColumnCyclerRotator.Direction.BACKWARD
                        : ColumnCyclerRotator.Direction.FORWARD;
        return () -> {
            for (int i = 0; i < undoSteps; i++) {
                ColumnCyclerRotator.rotate(column, undoDirection, take, put);
            }
        };
    }

    /**
     * Whether bringing {@code slot} down would deliver it. Overridden rather
     * than left at the default because a column rotation drops refused slots
     * instead of refusing outright, so a refused ring slot makes the bring
     * quietly deliver the wrong item or nothing. See
     * {@link ColumnCyclerRotator#bringAllowed}.
     */
    @Override
    public boolean allowsBringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return hotbarPositionOf(slot) != -1 && ColumnCyclerRotator.bringAllowed(slot, take, put);
    }
}
