package com.trevorschoeny.inventoryplus.cyclable;

/**
 * Undo handle returned by {@link HotbarCyclable#bringToHotbar(int)}.
 *
 * <p>The caller holds onto this handle for as long as it might want to
 * reverse the bring-to-hotbar action. For Auto Tool Switch, the lifetime
 * is from the moment the switch happens until "the action completes" —
 * on LMB release for tool/mining, or after the attack-cooldown for
 * weapons (per `auto-tool-switch.md`).
 *
 * <p>Functional pattern: each operation closes over its own reversal
 * state — no global memory in the cycler. Multiple consumers can each
 * hold their own handles independently.
 */
@FunctionalInterface
public interface CyclerOperation {

    /**
     * Reverse the action this operation represents. Should be called at
     * most once per operation; behavior of calling more than once is
     * undefined.
     *
     * <p>Implementations should be tolerant of "state has drifted since
     * bring-to-hotbar happened" — e.g., the player may have manually
     * cycled in the meantime, made some slots no longer cyclable, etc.
     * In drift cases, undo should be a best-effort no-op rather than
     * crash.
     */
    void undo();

    /**
     * No-op singleton — returned when there was nothing to do (slot
     * isn't claimed, item was already at hotbar, etc.). Callers can
     * call {@code undo()} on this freely.
     */
    CyclerOperation NO_OP = () -> {};
}
