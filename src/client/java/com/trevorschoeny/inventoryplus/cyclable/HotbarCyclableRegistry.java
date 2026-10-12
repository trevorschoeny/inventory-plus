package com.trevorschoeny.inventoryplus.cyclable;

import com.trevorschoeny.inventoryplus.api.CyclerOperation;
import com.trevorschoeny.inventoryplus.api.HotbarCyclable;
import com.trevorschoeny.inventoryplus.api.HotbarCyclable.ExtraSlot;
import net.minecraft.world.entity.player.Player;

import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.TriBool;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry of {@link HotbarCyclable} implementations.
 *
 * <p>Consumers (Auto Tool Switch, for one) query this registry to classify
 * slots and dispatch bring-to-hotbar operations. They never reference
 * specific cyclers, which keeps Auto Tool Switch cycler-agnostic.
 *
 * <h3>Registration</h3>
 *
 * Each cycler registers itself at mod init (typically in
 * {@code InventoryPlusClient.onInitializeClient()} for IP-side cyclers,
 * and in IPP's client init for IPP-side cyclers).
 *
 * <p>Registration order doesn't matter for correctness: each cycler
 * claims a disjoint subset of slot indices, so {@link #cyclablePosition}
 * and {@link #bringToHotbar} return on the first match. By contract,
 * cyclers don't overlap their slot claims.
 *
 * <h3>Slot-coverage contract</h3>
 *
 * Cyclers are responsible for claiming only slots they can actually act
 * on. {@link #cyclablePosition} returning a value other than -1 implies
 * {@link #bringToHotbar} will execute (or correctly no-op if the slot's
 * cycle state is mid-flux).
 */
public final class HotbarCyclableRegistry {

    private HotbarCyclableRegistry() {}

    /** Insertion-ordered. Written at init, read on the client thread only. */
    private static final List<HotbarCyclable> CYCLERS = new ArrayList<>();

    /** Called once per cycler at mod init. The same instance won't register twice. */
    public static void register(HotbarCyclable cycler) {
        if (cycler == null) return;
        if (CYCLERS.contains(cycler)) return;
        CYCLERS.add(cycler);
    }

    /**
     * Returns the hotbar position (0-8) this slot is reachable into via
     * any registered cycler, or {@code -1} if no cycler claims the slot.
     *
     * <p>Hotbar slots themselves (0-8) are not claimed by any cycler.
     * They're already at their destination ("Tier 1" in
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
     * HotbarCyclable#extraSearchSlots(Player) extra searchable slots}: the
     * slots that live outside the 0-35 inventory model (Pocket Cycler's
     * pockets, for example). Callers fold these into tool search alongside
     * the real 0-35 slots. The returned {@link HotbarCyclable.ExtraSlot} ids
     * route back through {@link #cyclablePosition} and {@link #bringToHotbar}
     * like any other claimed slot.
     *
     * <p>Returns an empty list when no cycler contributes extras (the common
     * case, Column Cycler alone). Order follows registration order, then each
     * cycler's own ordering. Callers that care about priority ("inventory
     * before pockets") scan 0-35 first, then this.
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
     * outside the cycler's scope, such as changing the player's selected
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
     * handled it: an extra, out-of-inventory slot (such as a pocket) whose
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

    /**
     * {@link #bringToHotbar(int)} for a move that serves {@code take}/{@code put}
     * (a restock, a tool switch) rather than the cycler's own rotation.
     */
    public static CyclerOperation bringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        for (HotbarCyclable c : CYCLERS) {
            if (c.hotbarPositionOf(slot) != -1) {
                return c.bringToHotbar(slot, take, put);
            }
        }
        return CyclerOperation.NO_OP;
    }

    /** {@link #quickMoveOut(int)} for a move that serves {@code take}/{@code put}. */
    public static boolean quickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        for (HotbarCyclable c : CYCLERS) {
            if (c.quickMoveOut(slot, take, put)) return true;
        }
        return false;
    }

    // Asking before choosing (see HotbarCyclable)

    /**
     * Whether the cycler that claims {@code slot} would bring it down as
     * {@code take}/{@code put}. True for a slot no cycler claims: that is an
     * ordinary inventory slot, judged where it is clicked.
     */
    public static boolean allowsBringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        for (HotbarCyclable c : CYCLERS) {
            if (c.hotbarPositionOf(slot) != -1) return c.allowsBringToHotbar(slot, take, put);
        }
        return true;
    }

    /** {@link #allowsBringToHotbar}, for a quick-move out. */
    public static boolean allowsQuickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        for (HotbarCyclable c : CYCLERS) {
            if (c.hotbarPositionOf(slot) != -1) return c.allowsQuickMoveOut(slot, take, put);
        }
        return true;
    }

    /**
     * {@link #extraSearchSlots}, keeping only the slots a bring-to-hotbar
     * serving {@code take}/{@code put} would be allowed to use. For a search
     * whose chosen source will be brought into the hand. The caller names the
     * move because it is the one that knows which move it will make; the
     * search itself stays move-agnostic.
     */
    public static List<HotbarCyclable.ExtraSlot> extraSlotsToBring(Player player, BehaviorKey<TriBool> take,
                                                                   BehaviorKey<TriBool> put) {
        List<HotbarCyclable.ExtraSlot> kept = new ArrayList<>();
        for (HotbarCyclable.ExtraSlot ex : extraSearchSlots(player)) {
            if (allowsBringToHotbar(ex.id(), take, put)) kept.add(ex);
        }
        return kept;
    }

    /** {@link #extraSlotsToBring}, for a search whose source will be quick-moved out. */
    public static List<HotbarCyclable.ExtraSlot> extraSlotsToQuickMove(Player player, BehaviorKey<TriBool> take,
                                                                       BehaviorKey<TriBool> put) {
        List<HotbarCyclable.ExtraSlot> kept = new ArrayList<>();
        for (HotbarCyclable.ExtraSlot ex : extraSearchSlots(player)) {
            if (allowsQuickMoveOut(ex.id(), take, put)) kept.add(ex);
        }
        return kept;
    }
}
