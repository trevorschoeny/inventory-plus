package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.autorestock.AutoRestockSuppression;

import com.trevorschoeny.inventoryplus.api.HotbarCyclable;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.COLUMN_CYCLE;

import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.TriBool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * Physical-rotation engine for Column Cycler. Given a column index and a
 * direction, walks the column's cycle members (top inventory slots down to
 * the hotbar) and sends a {@link ContainerInput#SWAP} click sequence that physically
 * moves items through the slots.
 *
 * <h3>Cycle list layout</h3>
 *
 * Vanilla {@code Inventory.items[]} layout:
 * <ul>
 *   <li>0-8: hotbar (y=142, visible at bottom of player inv GUI)</li>
 *   <li>9-17: main inventory row at y=84, the top row visually</li>
 *   <li>18-26: middle row at y=102</li>
 *   <li>27-35: bottom row at y=120, just above the hotbar</li>
 * </ul>
 *
 * <p>The cycle list is sorted top to bottom visually: {@code [9+col,
 * 18+col, 27+col, col]}. Only slots that are actually cycle members
 * are included. The hotbar is always the last element when the column
 * is active, since any inventory slot in the column being a cycle member
 * makes the hotbar slot a derived cycle member.
 *
 * <h3>Rotation algorithm</h3>
 *
 * Given cycle list {@code [s0, s1, ..., sN-1]} where {@code s0} is the
 * topmost visible slot and {@code sN-1} is the hotbar slot, the rotation
 * is {@code N-1} {@link ContainerInput#SWAP} clicks, each exchanging one
 * ring slot with the hotbar slot (button = its hotbar index):
 * <pre>
 *   forward  (toward the hotbar, hotbar wraps to the top): swap s0, s1, ..., sN-2
 *   backward (away from the hotbar, top wraps to it):      swap sN-2, ..., s1, s0
 * </pre>
 * Swaps, not PICKUP clicks through the cursor. With PICKUP, cycling stopped
 * partway and the hand jumped slots: a PICKUP onto a stack of the same item
 * merges instead of exchanging, which left an item on the cursor, so every
 * later rotation no-oped on the occupied cursor. A swap never merges and
 * never uses the cursor.
 *
 * <p>The hotbar slot's contents change wholesale (an empty ring slot can
 * rotate into the hand), so each rotation marks it for
 * {@link AutoRestockSuppression}; otherwise Restock reads the hand as used
 * up and moves the selection to a backup stack.
 *
 * <h3>No-op cases</h3>
 *
 * <ul>
 *   <li>Cycle list has fewer than 2 slots (nothing to rotate).</li>
 *   <li>Cursor is already holding an item (the player is mid-move; a
 *       rotation under it would surprise them).</li>
 *   <li>The last slot is not a hotbar slot (a swap's button names one).</li>
 *   <li>Any container-slot-to-menu-slot lookup fails (the slot isn't
 *       in the currently open menu's slot list).</li>
 * </ul>
 */
public final class ColumnCyclerRotator {

    private ColumnCyclerRotator() {}

    public enum Direction { FORWARD, BACKWARD }

    /**
     * Listener for rotation events, fired after a successful rotation
     * sends its click packets. The HUD overlay uses it to drive its slide
     * animation.
     *
     * <p>Not fired for no-op cases (cycle has fewer than 2 slots, cursor
     * is holding an item, slot lookup failed), so the listener only sees
     * rotations that actually moved items.
     */
    @FunctionalInterface
    public interface RotationListener {
        void onRotation(int column, Direction direction);
    }

    private static final List<RotationListener> ROTATION_LISTENERS = new ArrayList<>();

    /** Called once per consumer at mod init. The same instance won't register twice. */
    public static void addRotationListener(RotationListener listener) {
        if (listener == null) return;
        if (ROTATION_LISTENERS.contains(listener)) return;
        ROTATION_LISTENERS.add(listener);
    }

    /**
     * Rotate the cycle slots in {@code column} (0-8) in the given direction.
     * Sends {@code N-1} SWAP clicks to the active container menu. No-op if the
     * cycle has fewer than 2 slots, if the cursor is holding an item, or if any
     * slot lookup fails.
     */
    public static void rotate(int column, Direction direction) {
        rotate(column, direction, COLUMN_CYCLE, COLUMN_CYCLE);
    }

    /**
     * {@link #rotate(int, Direction)} performed as {@code take}/{@code put}: the
     * Column Cycler's own key when the player cycles, a restock's or Auto Tool
     * Switch's when one of them brings an item down through the column.
     */
    public static void rotate(int column, Direction direction,
                              BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (column < 0 || column > 8) return;

        if (!rotateSlots(buildCycleList(column), direction, "column-cycler", take, put)) return;

        for (RotationListener listener : ROTATION_LISTENERS) {
            listener.onRotation(column, direction);
        }
    }

    /**
     * The rotation engine: physically rotate an arbitrary ordered list of
     * the local player's container slots by one step.
     *
     * <p>Public and membership-agnostic so Hotbar Cycler can reuse it
     * rather than growing a second rotator. Column Cycler passes one
     * column's cycle members; Hotbar Cycler passes one column's toggled
     * row slots plus that column's hotbar slot, nine times over. The
     * click sequence, the cursor rule, and the wrap semantics are
     * identical either way. Only the choice of slots differs.
     *
     * <p>{@code containerSlots} is ordered visually top to bottom with
     * the destination (hotbar) slot last, matching {@link #buildCycleList}.
     * FORWARD shifts items toward that last slot and wraps it to the
     * front; BACKWARD reverses.
     *
     * <p>Each call is self-contained (swaps only, cursor untouched), so
     * consecutive calls compose safely.
     *
     * @param feature label for the debug log, so a skipped rotation names
     *                the cycler that asked for it
     * @return true if clicks were sent; false for any no-op case (fewer
     *         than 2 slots, cursor occupied, slot not in the open menu)
     */
    public static boolean rotateSlots(List<Integer> containerSlots, Direction direction,
                                      String feature, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        if (containerSlots.size() < 2) return false;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return false;

        // The cursor must be empty, otherwise the rotation interleaves the
        // carried item into the cycle.
        if (!menu.getCarried().isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[{}] rotate skipped — cursor holding {}", feature, menu.getCarried());
            return false;
        }

        // A slot that refuses the operation sits the rotation out: it keeps its
        // item and the ring turns around it. Locks refuse through the veto
        // (Locks), so a locked item stays put the same way. Both cyclers
        // inherit it; Hotbar Cycler rotates its nine columns through here too.
        containerSlots = withoutRefused(containerSlots, menu, player, take, put, feature);
        int n = containerSlots.size();
        if (n < 2) return false;

        int[] menuSlots = new int[n];
        for (int i = 0; i < n; i++) {
            int menuIdx = PlayerMenuSlots.menuIndexOf(menu, player, containerSlots.get(i));
            if (menuIdx < 0) {
                InventoryPlusClient.LOGGER.debug(
                        "[{}] rotate skipped — container slot {} not in current menu",
                        feature, containerSlots.get(i));
                return false;
            }
            menuSlots[i] = menuIdx;
        }

        // The destination is the hotbar slot a SWAP names by its index.
        int hotbar = containerSlots.get(n - 1);
        if (hotbar < 0 || hotbar > 8) {
            InventoryPlusClient.LOGGER.debug("[{}] rotate skipped — last slot {} is not a hotbar slot",
                    feature, hotbar);
            return false;
        }

        // Swap each other ring slot with the hotbar slot, top down to go
        // forward, bottom up to go back (see the class doc for why swaps).
        int[] swapSeq = new int[n - 1];
        for (int i = 0; i < n - 1; i++) {
            swapSeq[i] = direction == Direction.FORWARD ? menuSlots[i] : menuSlots[n - 2 - i];
        }

        // A deliberate change to the hand, not the item running out.
        AutoRestockSuppression.markExternalChange(hotbar);

        int containerId = menu.containerId;
        // Sent as the operation the rotation serves, so MenuKit judges these
        // swaps as a cycle (or the restock / tool switch riding it), not as
        // the hotbar-swap key a lock on swapping would refuse.
        SlotOperations.as(take, put, () -> {
            for (int slotIdx : swapSeq) {
                gameMode.handleContainerInput(containerId, slotIdx, hotbar, ContainerInput.SWAP, player);
            }
        });
        return true;
    }

    /**
     * The ring minus any slot that refuses {@code take} or {@code put}. Every
     * ring slot is both emptied and filled by a rotation, so it must allow both.
     * A slot not on the open menu is kept; the lookup after this fails on it and
     * the rotation no-ops, as it always has.
     */
    private static List<Integer> withoutRefused(List<Integer> containerSlots, AbstractContainerMenu menu,
                                                LocalPlayer player, BehaviorKey<TriBool> take,
                                                BehaviorKey<TriBool> put, String feature) {
        List<Integer> kept = new ArrayList<>(containerSlots.size());
        for (Integer containerSlot : containerSlots) {
            int menuIdx = PlayerMenuSlots.menuIndexOf(menu, player, containerSlot);
            if (menuIdx >= 0) {
                Slot slot = menu.getSlot(menuIdx);
                if (!SlotOperations.allows(menu, slot, player, take)) continue;
                if (!put.equals(take) && !SlotOperations.allows(menu, slot, player, put)) continue;
            }
            kept.add(containerSlot);
        }
        if (kept.size() != containerSlots.size()) {
            InventoryPlusClient.LOGGER.debug(
                    "[{}] rotating {} of {} slots, the rest refuse {}",
                    feature, kept.size(), containerSlots.size(), take.id());
        }
        return kept;
    }

    /**
     * Whether bringing {@code slot} down would deliver it: every slot in its
     * column's ring, the hotbar slot included, allows both {@code take} and
     * {@code put}, and is on the open menu.
     *
     * <p>That is the rule {@link #withoutRefused} applies, asked of the whole
     * ring at once. A rotation does not refuse outright; it drops refused
     * slots and rotates what is left, and the bring plan was counted on the
     * full ring, so a single refused slot means the wrong item, or nothing,
     * reaches the hand. Asking this before choosing is what keeps Auto Tool
     * Switch from moving the selection for a bring that cannot land.
     */
    public static boolean bringAllowed(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        for (int containerSlot : buildCycleList(slot % 9)) {
            int menuIdx = PlayerMenuSlots.menuIndexOf(menu, player, containerSlot);
            if (menuIdx < 0) return false;
            Slot ringSlot = menu.getSlot(menuIdx);
            if (!SlotOperations.allows(menu, ringSlot, player, take)) return false;
            if (!put.equals(take) && !SlotOperations.allows(menu, ringSlot, player, put)) return false;
        }
        return true;
    }

    /**
     * The cycle slot list for {@code column}: directly toggled inventory
     * slots in visual top-to-bottom order, with the hotbar slot appended
     * last. Empty when the column has no active inventory cycle members.
     * Shared by {@link #rotate} and {@link #planBringSlotToHotbar} so both
     * agree on ordering and membership.
     */
    private static List<Integer> buildCycleList(int column) {
        List<Integer> list = new ArrayList<>(4);
        int top = 9 + column;
        int mid = 18 + column;
        int bot = 27 + column;
        int hotbar = column;
        if (ColumnCycler.isCycleSlot(top)) list.add(top);
        if (ColumnCycler.isCycleSlot(mid)) list.add(mid);
        if (ColumnCycler.isCycleSlot(bot)) list.add(bot);
        // The hotbar is implicitly part of the cycle when the column is active.
        if (list.isEmpty()) return list;
        list.add(hotbar);
        return list;
    }

    /**
     * A rotation plan: how many times to call {@link #rotate} with which
     * {@link Direction} to bring the item at a specific slot down to the
     * hotbar position. Used by
     * {@link ColumnCyclerCyclable#bringToHotbar(int)} to compose Column
     * Cycler with the cycler-agnostic
     * {@link com.trevorschoeny.inventoryplus.cyclable.HotbarCyclable}
     * contract.
     */
    public record RotationPlan(int steps, Direction direction) {}

    /**
     * Compute the rotation plan to bring the item currently at inventory
     * slot {@code slot} into its column's hotbar position. Returns
     * {@code null} if {@code slot} isn't an active cycle member, isn't
     * an inventory slot, or its column doesn't have enough cycle members to
     * rotate.
     *
     * <p>Picks the cheaper of FORWARD and BACKWARD by step count. Each
     * rotation sends {@code N-1} SWAP packets, so direction matters for
     * chains.
     *
     * <h3>Step math</h3>
     *
     * Given the cycle list {@code [s0, s1, ..., s(N-2), hotbar]} with
     * the target slot at index {@code i}:
     * <ul>
     *   <li>FORWARD steps to bring item-at-i to hotbar: {@code (N-1) - i}
     *       (each forward shifts everything one position toward the
     *       hotbar; item at i ends up at hotbar after that many steps).</li>
     *   <li>BACKWARD steps to bring item-at-i to hotbar: {@code i + 1}
     *       (each backward shifts everything one position away from the
     *       hotbar, with the topmost wrapping to hotbar; item at i ends
     *       up at hotbar after that many steps).</li>
     * </ul>
     *
     * <p>On a tie, FORWARD wins, matching the rotator's default direction.
     */
    public static RotationPlan planBringSlotToHotbar(int slot) {
        if (slot < ColumnCycler.MIN_DIRECT_CYCLE_SLOT) return null;
        if (slot > ColumnCycler.MAX_CYCLEABLE_CONTAINER_SLOT) return null;
        int column = slot % 9;
        List<Integer> cycleList = buildCycleList(column);
        int n = cycleList.size();
        if (n < 2) return null; // single-member cycle: nothing to rotate.
        int slotIndex = cycleList.indexOf(slot);
        if (slotIndex == -1) return null; // slot not in cycle (shouldn't happen if caller filtered).
        int hotbarIndex = n - 1;
        if (slotIndex == hotbarIndex) return new RotationPlan(0, Direction.FORWARD);
        int forwardSteps = hotbarIndex - slotIndex;
        int backwardSteps = slotIndex + 1;
        if (forwardSteps <= backwardSteps) {
            return new RotationPlan(forwardSteps, Direction.FORWARD);
        } else {
            return new RotationPlan(backwardSteps, Direction.BACKWARD);
        }
    }
}
