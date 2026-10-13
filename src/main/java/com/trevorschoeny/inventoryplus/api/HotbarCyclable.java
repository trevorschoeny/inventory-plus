package com.trevorschoeny.inventoryplus.api;

import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.TriBool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Contract for anything that extends the player's hotbar reach by cycling.
 * Column Cycler implements it here; another mod with a ring (Hotbar Pockets)
 * can too.
 *
 * <h3>Why this exists</h3>
 *
 * Auto Tool Switch needs to fetch the right tool into the active hand. It
 * resolves in three tiers:
 * <ol>
 *   <li>The right tool is already in another hotbar slot: change the selected
 *       slot.</li>
 *   <li>The right tool is in a <b>cyclable hotbar position</b>, a slot a
 *       cycler's native action can bring into the hotbar: change the selected
 *       slot and ask the cycler to bring the item down.</li>
 *   <li>The right tool is elsewhere in the main inventory: swap it with the
 *       active hotbar slot by a vanilla inventory click.</li>
 * </ol>
 *
 * <p>This interface abstracts tier 2. Each cycler answers two questions for
 * any slot:
 * <ul>
 *   <li>Do you claim this slot, and which hotbar position does it resolve to?
 *       {@link #hotbarPositionOf(int)}</li>
 *   <li>Bring this slot's item down to the hotbar by your native action, and
 *       give me a handle to reverse it. {@link #bringToHotbar(int)}</li>
 * </ul>
 *
 * <p>Auto Tool Switch reaches the cyclers through
 * {@link HotbarCyclableRegistry} and never knows about Column Cycler or
 * Pocket Cycler specifically. A new cycler registers at mod init and
 * consumers don't change.
 *
 * <h3>Undo handles</h3>
 *
 * {@link #bringToHotbar(int)} returns a {@link CyclerOperation} that the
 * caller holds onto. To reverse the action (Auto Tool Switch's return after
 * the action, for example), it calls {@link CyclerOperation#undo()}.
 *
 * <p>The cycler keeps no memory between calls. Each call returns a fresh
 * handle that closes over the state needed to reverse just that call, so
 * several callers can interleave bring-to-hotbar operations, each with its
 * own handle. Caller ordering still matters, since the cycler doesn't
 * coordinate across consumers.
 *
 * <h3>Slot indexing</h3>
 *
 * The {@code slot} parameter throughout is a <b>container-slot index</b>
 * into the player's inventory (the 0-35 layout used by
 * {@code Slot.getContainerSlot()}): 0-8 hotbar, 9-35 main inventory. Cyclers
 * decide which of those indices they claim.
 *
 * <h3>Extra slots beyond the vanilla inventory</h3>
 *
 * Some cyclers source items from storage outside the 0-35 model entirely.
 * Pocket Cycler's pockets are backed by a separate attachment, not the
 * player's {@code Inventory}. Those slots still take part in tool search
 * (Auto Tool Switch, Restock) and still resolve to a hotbar position through
 * the cycler's native action.
 *
 * <p>{@link #extraSearchSlots(Player)} lists them. Each carries an
 * <b>encoded id</b> the cycler invents for itself, plus the live
 * {@link ItemStack}, read straight from the cycler's own backing so the
 * caller never needs to know where the content lives. The id:
 * <ul>
 *   <li>must be <b>disjoint from 0-35</b>, so callers can keep reading real
 *       inventory slots with {@code inv.getItem(i)} and never confuse the two
 *       namespaces, and</li>
 *   <li>must be <b>disjoint across cyclers</b>, the same rule the 0-35 claims
 *       follow, so a returned id routes to exactly one cycler.</li>
 * </ul>
 *
 * <p>{@link #hotbarPositionOf(int)} and {@link #bringToHotbar(int)} accept
 * these encoded ids too. A claimed extra id resolves to its hotbar position
 * and comes down by the cycler's native action, exactly like a claimed 0-35
 * slot, so callers treat extra slots as more tier 2 candidates and never
 * special-case pockets.
 */
public interface HotbarCyclable {

    /**
     * One searchable slot outside the 0-35 inventory model: an encoded
     * {@code id} (see "Extra slots" on the type) paired with the live
     * {@link ItemStack} in it.
     *
     * <p>The stack is the cycler's own live content, so callers score and
     * match against it without reaching into the cycler's backing. The id is
     * opaque to callers. They only hand it back to
     * {@link #hotbarPositionOf(int)} or {@link #bringToHotbar(int)} to act on
     * the slot.
     */
    record ExtraSlot(int id, ItemStack stack) {}

    /**
     * Searchable slots this cycler reaches that are <b>not</b> in the 0-35
     * inventory model, such as Pocket Cycler's attachment-backed pockets.
     * Empty by default, since a cycler whose slots all live in 0-35 (Column
     * Cycler) has nothing extra.
     *
     * <p>Return only slots that are currently <b>reachable</b>. A pocket that
     * exists server-side but isn't revealed in the current world contributes
     * nothing. Empty stacks may be included or skipped, since callers skip
     * empties anyway.
     *
     * <p>Read client-side on the client tick path, so keep it cheap.
     */
    default List<ExtraSlot> extraSearchSlots(Player player) {
        return List.of();
    }

    /**
     * Returns the hotbar position (0-8) this slot would resolve to if
     * brought down by this cycler's native action, or {@code -1} if this
     * cycler doesn't claim the slot.
     *
     * <p>Callers use this to classify a slot as tier 2 cyclable before
     * calling {@link #bringToHotbar(int)}, and to know which hotbar position
     * to select ahead of the bring-down.
     */
    int hotbarPositionOf(int slot);

    /**
     * Bring the item currently at {@code slot} into its associated
     * hotbar position by this cycler's native action (rotation, pocket cycle,
     * row swap). Returns an undo handle the caller holds onto.
     *
     * <p>Returns {@link CyclerOperation#NO_OP} if this cycler doesn't claim
     * the slot ({@link #hotbarPositionOf(int)} returns -1), or if the item is
     * already at the hotbar position.
     *
     * <p>The returned operation closes over the state needed to reverse just
     * this call. Call {@code undo()} at most once; calling it again is
     * undefined.
     */
    CyclerOperation bringToHotbar(int slot);

    /**
     * Move the content of an extra slot (see {@link #extraSearchSlots})
     * <b>out</b> toward its natural quick-move destination. This is the
     * non-held restock path, where the item relocates (a totem refilling the
     * equipment slot, armor equipping after a break) rather than rotating into
     * the hand the way {@link #bringToHotbar} does.
     *
     * <p>Returns {@code true} if this cycler owns {@code slot} and started the
     * move. On {@code false} the caller treats {@code slot} as an ordinary
     * inventory slot and moves it with a normal client-side click. The
     * default never claims a slot, because slots in the real 0-35 inventory
     * are directly clickable.
     *
     * <p>For a cycler whose backing is inert in-world (Pocket Cycler), the
     * move is server-authoritative, for the same reason
     * {@link #bringToHotbar} is. There is no undo handle, because a restock
     * move is permanent.
     */
    default boolean quickMoveOut(int slot) {
        return false;
    }

    // Moves that serve another operation.
    //
    // Restock and Auto Tool Switch reach a cycler's slot through
    // bringToHotbar and quickMoveOut, but what the player sees is a restock
    // or a tool switch, not a rotation. These overloads carry that operation
    // as a MenuKit take/put pair: take for the
    // slots the move empties, put for the slots it fills. A cycler that
    // performs the move with clicks sends them under
    // SlotOperations.as(take, put, ...); one that moves items server-side
    // names the operation to its server and asks SlotOperations.allows there.
    //
    // The defaults fall back to the plain methods, so an
    // implementer that does not override them keeps compiling and its moves
    // are judged as its own rotation, exactly as before.

    /**
     * {@link #bringToHotbar(int)}, performed as {@code take}/{@code put}
     * rather than as this cycler's own rotation. A rotation both empties and
     * fills every slot in its ring, so each ring slot must allow both.
     * Default: the plain method.
     */
    default CyclerOperation bringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return bringToHotbar(slot);
    }

    /**
     * {@link #quickMoveOut(int)}, performed as {@code take} on the slot it
     * empties and {@code put} on wherever the item lands. Default: the plain
     * method.
     */
    default boolean quickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return quickMoveOut(slot);
    }

    // Asking before choosing.
    //
    // The two moves above can turn out to do nothing: a cycler that finds the
    // move refused returns NO_OP or claims the slot and stops. By then a caller
    // may already have acted on its choice (Auto Tool Switch has changed the
    // selected slot), and it has certainly stopped looking for a better
    // source. So a search asks the cycler first, while it is still choosing,
    // and passes over a slot the move would refuse.
    //
    // Each question mirrors one move, because the two judge different slots: a
    // bring rotates a whole ring, a quick-move out only empties the one slot.
    // An implementer's move must refuse exactly when its query says no; the
    // simplest way to guarantee that is for the move to call its own query.
    // A cycler whose moves never refuse can leave the defaults.

    /**
     * Whether {@link #bringToHotbar(int, BehaviorKey, BehaviorKey)} would bring
     * {@code slot}'s item down if asked now, as {@code take}/{@code put}.
     * Asked only for a slot this cycler claims. Default: yes.
     */
    default boolean allowsBringToHotbar(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return true;
    }

    /**
     * Whether {@link #quickMoveOut(int, BehaviorKey, BehaviorKey)} would move
     * {@code slot}'s item out if asked now, as {@code take}/{@code put}.
     * Asked only for a slot this cycler claims. Default: yes.
     */
    default boolean allowsQuickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return true;
    }
}
