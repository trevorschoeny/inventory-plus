package com.trevorschoeny.inventoryplus.api;

/**
 * Undo handle returned by {@link HotbarCyclable#bringToHotbar(int)}.
 *
 * <p>The caller holds onto this handle for as long as it might want to
 * reverse the bring-to-hotbar action. For Auto Tool Switch that is from the
 * switch until the action completes: on left-button release for a tool, or
 * after the attack cooldown for a weapon.
 *
 * <p>Each operation closes over its own reversal state, so the cycler keeps
 * no global memory and several consumers can each hold their own handles.
 */
@FunctionalInterface
public interface CyclerOperation {

    /**
     * Reverse the action this operation represents. Should be called at
     * most once per operation; behavior of calling more than once is
     * undefined.
     *
     * <p>Implementations should tolerate state that has drifted since the
     * bring-to-hotbar: the player may have cycled by hand in the meantime, or
     * made some slots no longer cyclable. Then undo should be a best-effort
     * no-op rather than a crash.
     */
    void undo();

    /**
     * Returned when there was nothing to do (the slot isn't claimed, or the
     * item was already in the hotbar). Calling {@code undo()} on it is safe.
     */
    CyclerOperation NO_OP = () -> {};
}
