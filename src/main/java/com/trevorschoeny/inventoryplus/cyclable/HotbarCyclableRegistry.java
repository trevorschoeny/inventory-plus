package com.trevorschoeny.inventoryplus.cyclable;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry of {@link HotbarCyclable} implementations.
 *
 * <p>Consumers (Auto Tool Switch and future features) query this registry
 * to classify slots and dispatch bring-to-hotbar operations — they never
 * reference specific cyclers directly. This is the architectural seam
 * that keeps Auto Tool Switch cycler-agnostic.
 *
 * <h3>Registration</h3>
 *
 * Each cycler registers itself at mod init (typically in
 * {@code InventoryPlusClient.onInitializeClient()} for IP-side cyclers,
 * and in IPP's client init for IPP-side cyclers).
 *
 * <p>Registration order doesn't matter for correctness — each cycler
 * claims a disjoint subset of slot indices, so {@link #cyclablePosition}
 * and {@link #bringToHotbar} return on the first match. By contract,
 * cyclers don't overlap their slot claims.
 *
 * <h3>Slot-coverage contract</h3>
 *
 * Cyclers are responsible for claiming only slots they can actually act
 * on. {@link #cyclablePosition} returning a non-{-1 value implies
 * {@link #bringToHotbar} will execute (or correctly no-op if the slot's
 * cycle state is mid-flux).
 */
public final class HotbarCyclableRegistry {

    private HotbarCyclableRegistry() {}

    /**
     * Insertion-ordered list of registered cyclers. ArrayList is fine —
     * the list is touched once at init (writes) and on every cyclable
     * lookup (reads); contention isn't a concern in the client-side
     * single-threaded model.
     */
    private static final List<HotbarCyclable> CYCLERS = new ArrayList<>();

    /**
     * Register a cycler. Typically called once per cycler at mod init.
     * Idempotent guard: if the exact instance is already registered,
     * skip — protects against accidental double-init.
     */
    public static void register(HotbarCyclable cycler) {
        if (cycler == null) return;
        if (CYCLERS.contains(cycler)) return;
        CYCLERS.add(cycler);
    }

    /**
     * Returns the hotbar position (0-8) this slot is reachable into via
     * any registered cycler, or {@code -1} if no cycler claims the slot.
     *
     * <p>Note: hotbar slots themselves (0-8) are <b>not</b> claimed by
     * any cycler — they're already at their destination ("Tier 1" in
     * Auto Tool Switch's resolution). This method tells the caller
     * specifically about Tier 2 (cyclable). The caller's hotbar-direct
     * check is its own responsibility.
     */
    public static int cyclablePosition(int slot) {
        for (HotbarCyclable c : CYCLERS) {
            int pos = c.hotbarPositionOf(slot);
            if (pos != -1) return pos;
        }
        return -1;
    }

    /**
     * Aggregate every registered cycler's {@linkplain
     * HotbarCyclable#extraSearchSlots(Player) extra searchable slots} —
     * the slots that live outside the 0-35 inventory model (e.g., Pocket
     * Cycler's pockets). Callers fold these into tool search alongside the
     * real 0-35 slots; the returned {@link HotbarCyclable.ExtraSlot} ids
     * route back through {@link #cyclablePosition} / {@link #bringToHotbar}
     * like any other claimed slot.
     *
     * <p>Returns an empty list when no cycler contributes extras (the
     * common case — Column Cycler alone). Order follows registration order
     * then each cycler's own ordering; callers that care about priority
     * (e.g., "inventory before pockets") scan 0-35 first, then this.
     */
    public static List<HotbarCyclable.ExtraSlot> extraSearchSlots(Player player) {
        if (CYCLERS.isEmpty()) return List.of();
        List<HotbarCyclable.ExtraSlot> all = new ArrayList<>();
        for (HotbarCyclable c : CYCLERS) {
            all.addAll(c.extraSearchSlots(player));
        }
        return all;
    }

    /**
     * Dispatch a bring-to-hotbar operation to whichever cycler claims
     * the slot. Returns the cycler's undo handle, or
     * {@link CyclerOperation#NO_OP} if no cycler claims the slot.
     *
     * <p>The caller is responsible for any additional state management
     * outside the cycler's scope — e.g., changing the player's selected
     * hotbar slot to match the cycler's destination position.
     */
    public static CyclerOperation bringToHotbar(int slot) {
        for (HotbarCyclable c : CYCLERS) {
            if (c.hotbarPositionOf(slot) != -1) {
                return c.bringToHotbar(slot);
            }
        }
        return CyclerOperation.NO_OP;
    }

    /**
     * Dispatch a {@linkplain HotbarCyclable#quickMoveOut(int) quick-move-out}
     * to whichever cycler owns the slot. Returns {@code true} if a cycler
     * handled it — an extra/out-of-inventory slot (e.g., a pocket) whose
     * content was moved server-side toward its destination. Returns
     * {@code false} when no cycler claims the slot, signalling the caller to
     * move it as an ordinary inventory slot via a normal client-side click.
     */
    public static boolean quickMoveOut(int slot) {
        for (HotbarCyclable c : CYCLERS) {
            if (c.quickMoveOut(slot)) return true;
        }
        return false;
    }
}
