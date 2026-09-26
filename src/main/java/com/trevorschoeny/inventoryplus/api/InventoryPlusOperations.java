package com.trevorschoeny.inventoryplus.api;

import com.trevlar.menukit.window.BehaviorKey;
import com.trevlar.menukit.window.KindTag;
import com.trevlar.menukit.window.Tier;
import com.trevlar.menukit.window.TriBool;

import net.minecraft.resources.Identifier;

/**
 * The slot operations Inventory Plus performs, as MenuKit keys
 * ({@code plans/slot-operations.md}).
 *
 * <p>Each one is something Inventory Plus can do to a slot. MenuKit lists them
 * in {@code SlotOperations.all()} so a settings screen can name them, and a
 * lock can refuse any one of them on a slot. Inventory Plus asks
 * {@code SlotOperations.allows} with the matching key before it picks a slot,
 * and sends its clicks under {@code SlotOperations.as}, so a click it sends is
 * judged as the operation it serves rather than as the plain click or
 * shift-click it looks like.
 *
 * <p>Published here, rather than kept internal, so a companion can send its own
 * moves under the same keys: Inventory Max's totem restock is a restock, and
 * should be judged as one.
 *
 * <p>Every key follows MenuKit's recipe for an operation of one's own
 * ({@code docs/recipes.md}): a {@link TriBool} that defaults to allowed, on the
 * server tier, applying to vanilla and created slots alike. Because
 * {@link BehaviorKey} is a record, a key built from the same recipe with the
 * same id is equal to one of these, which is how a companion's server side can
 * name them without loading this class.
 *
 * <p>Two operations are deliberately absent: Firework Keybind and Hotbar Saves
 * load get their keys when they are built, since an operation for an unbuilt
 * feature would be a checkbox that does nothing.
 */
public final class InventoryPlusOperations {

    private InventoryPlusOperations() {}

    /** Sorting may move items in and out of a slot. Role BOTH. */
    public static final BehaviorKey<TriBool> SORT = operation("sort");

    /** Move Matching may take items out of a slot (the source side). Role TAKE. */
    public static final BehaviorKey<TriBool> MOVE_MATCHING_OUT = operation("move_matching_out");

    /** Move Matching may put items into a slot (the destination side). Role PUT. */
    public static final BehaviorKey<TriBool> MOVE_MATCHING_IN = operation("move_matching_in");

    /** Restock may pull a refill out of a slot. Role TAKE. */
    public static final BehaviorKey<TriBool> RESTOCK_TAKE = operation("restock_take");

    /** Restock may refill a slot when its item runs out or breaks. Role PUT. */
    public static final BehaviorKey<TriBool> RESTOCK_PUT = operation("restock_put");

    /**
     * Auto Tool Switch may swap a tool into or out of a slot. Role BOTH, and one
     * key rather than two because a swap takes and puts on both slots, the same
     * way MenuKit's own {@code HOTBAR_SWAP} is one key.
     */
    public static final BehaviorKey<TriBool> AUTO_TOOL_SWITCH = operation("auto_tool_switch");

    /**
     * Column Cycler may rotate items through a slot. Role BOTH. Separate from
     * {@link #HOTBAR_CYCLE} so a lock can block one cycler and not the other
     * (Trev, 2026-09-11).
     */
    public static final BehaviorKey<TriBool> COLUMN_CYCLE = operation("column_cycle");

    /** Hotbar Cycler may rotate items through a slot. Role BOTH. */
    public static final BehaviorKey<TriBool> HOTBAR_CYCLE = operation("hotbar_cycle");

    /** MenuKit's recipe for an operation of one's own, under this mod's namespace. */
    private static BehaviorKey<TriBool> operation(String path) {
        return BehaviorKey.of(Identifier.fromNamespaceAndPath("inventoryplus", path),
                TriBool.class, TriBool.TRUE, Tier.SERVER, KindTag.VANILLA_SLOT, KindTag.CREATED_SLOT);
    }
}
