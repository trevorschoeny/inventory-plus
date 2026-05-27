package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Physical-rotation engine for Column Cycler. Given a column index +
 * direction, walks the column's cycle members (top inv slots → hotbar)
 * and sends a {@link ClickType#PICKUP} click sequence that physically
 * moves items through the slots.
 *
 * <h3>Cycle list layout</h3>
 *
 * Vanilla {@code Inventory.items[]} layout:
 * <ul>
 *   <li>0-8: hotbar (y=142, visible at bottom of player inv GUI)</li>
 *   <li>9-17: main inv row at y=84 — the TOP row visually</li>
 *   <li>18-26: middle row at y=102</li>
 *   <li>27-35: bottom row at y=120 — JUST ABOVE the hotbar</li>
 * </ul>
 *
 * <p>The cycle list is sorted top → bottom visually: {@code [9+col,
 * 18+col, 27+col, col]}. Only slots that are actually cycle members
 * are included; the hotbar is always the LAST element when the column
 * is active (since any inv slot in the column being a cycle member
 * makes the hotbar slot a derived cycle member).
 *
 * <h3>Rotation algorithm</h3>
 *
 * Given cycle list {@code [s0, s1, ..., sN-1]} where {@code s0} is the
 * topmost visible slot and {@code sN-1} is the hotbar slot.
 *
 * <p><b>Forward</b> = items shift toward hotbar (the slot just above
 * the hotbar moves into hand; previous hotbar item wraps to top).
 * Click sequence:
 * <pre>
 *   click sN-1   → cursor = old-hotbar item
 *   click s0     → cursor swaps; s0 has old-hotbar; cursor has s0's old item
 *   click s1     → s1 has cursor's; cursor has s1's old item
 *   ...
 *   click sN-2   → sN-2 has cursor's; cursor has sN-2's old item
 *   click sN-1   → cursor empties into hotbar
 * </pre>
 *
 * <p><b>Backward</b> = items shift away from hotbar (top slot's item
 * wraps to hotbar; everything else shifts up). Click sequence:
 * <pre>
 *   click sN-1   → cursor = old-hotbar item
 *   click sN-2   → sN-2 has old-hotbar; cursor has sN-2's old item
 *   ...
 *   click s0     → s0 has cursor's; cursor has s0's old item
 *   click sN-1   → cursor empties into hotbar
 * </pre>
 *
 * <p>{@code N+1} packets total either direction.
 *
 * <h3>No-op cases</h3>
 *
 * <ul>
 *   <li>Cycle list has fewer than 2 slots (nothing to rotate).</li>
 *   <li>Cursor is already holding an item — rotation would interleave
 *       the cursor item into the cycle in unintended ways. Player must
 *       drop the cursor first.</li>
 *   <li>Any container-slot-to-menu-slot lookup fails (the slot isn't
 *       in the currently open menu's slot list).</li>
 * </ul>
 */
public final class ColumnCyclerRotator {

    private ColumnCyclerRotator() {}

    public enum Direction { FORWARD, BACKWARD }

    /**
     * Listener for rotation events. Fired AFTER a successful rotation
     * sends its click packets — used by the HUD overlay to drive its
     * slide animation, and by anything else that wants to react to a
     * cycle advance.
     *
     * <p>NOT fired for no-op cases (cycle has fewer than 2 slots,
     * cursor is holding an item, slot lookup failed). The listener only
     * sees real rotations that actually moved items.
     */
    @FunctionalInterface
    public interface RotationListener {
        void onRotation(int column, Direction direction);
    }

    /**
     * Registered listeners. List used (not Set) because listeners are
     * registered once at mod init and notification order is irrelevant
     * for the current consumers; the small registration cost over a
     * Set is fine for a list this short.
     */
    private static final List<RotationListener> ROTATION_LISTENERS = new ArrayList<>();

    /**
     * Register a listener. Typically called once per consumer at mod
     * init. Idempotent guard: the same instance won't register twice.
     */
    public static void addRotationListener(RotationListener listener) {
        if (listener == null) return;
        if (ROTATION_LISTENERS.contains(listener)) return;
        ROTATION_LISTENERS.add(listener);
    }

    /**
     * Rotate the cycle slots in {@code column} (0-8) in the given direction.
     * Sends {@code (N+1) * 1} PICKUP click packets to the active container
     * menu. No-op if the cycle has fewer than 2 slots, if the cursor is
     * holding an item, or if any slot lookup fails.
     */
    public static void rotate(int column, Direction direction) {
        if (column < 0 || column > 8) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        // Cursor must be empty — otherwise rotation interleaves the
        // carried item into the cycle. Player should drop it first.
        if (!menu.getCarried().isEmpty()) {
            InventoryPlusClient.LOGGER.debug(
                    "[column-cycler] rotate skipped — cursor holding {}", menu.getCarried());
            return;
        }

        // Build the cycle slot list: visually top → bottom, hotbar last.
        // Shared with planBringSlotToHotbar so both call sites agree on
        // index ordering and membership filtering.
        List<Integer> containerSlots = buildCycleList(column);
        int n = containerSlots.size();
        if (n < 2) return;

        // Resolve menu slot indices.
        int[] menuSlots = new int[n];
        UUID localUuid = player.getUUID();
        for (int i = 0; i < n; i++) {
            int menuIdx = findMenuSlotIndex(menu, containerSlots.get(i), localUuid);
            if (menuIdx < 0) {
                InventoryPlusClient.LOGGER.debug(
                        "[column-cycler] rotate skipped — container slot {} not in current menu",
                        containerSlots.get(i));
                return;
            }
            menuSlots[i] = menuIdx;
        }

        // Build click sequence per direction. Both start and end at the
        // hotbar slot (cursor pickup ... cursor drop).
        int[] clickSeq = new int[n + 1];
        clickSeq[0] = menuSlots[n - 1]; // hotbar pickup
        if (direction == Direction.FORWARD) {
            // Walk s0 → sN-2
            for (int i = 0; i < n - 1; i++) {
                clickSeq[i + 1] = menuSlots[i];
            }
        } else {
            // Walk sN-2 → s0
            for (int i = 0; i < n - 1; i++) {
                clickSeq[i + 1] = menuSlots[n - 2 - i];
            }
        }
        clickSeq[n] = menuSlots[n - 1]; // hotbar drop

        // Send the clicks. The server processes them sequentially; the
        // client predicts cursor state between clicks so the chain
        // behaves correctly without per-click round-trips.
        int containerId = menu.containerId;
        for (int slotIdx : clickSeq) {
            gameMode.handleInventoryMouseClick(containerId, slotIdx, 0, ClickType.PICKUP, player);
        }

        // Notify listeners — fired only after a successful rotation
        // (all early-return paths above skip this). Used by the HUD
        // overlay's slide animation, among other consumers.
        for (RotationListener listener : ROTATION_LISTENERS) {
            listener.onRotation(column, direction);
        }
    }

    /**
     * Find the menu slot index for the given container slot index in the
     * local player's inventory. Returns -1 if not present in this menu.
     *
     * <p>Uses UUID equality (not reference equality) to identify the
     * local player's inventory — the same cross-thread stability concern
     * as {@code LockedSlots.isLockable}.
     */
    private static int findMenuSlotIndex(AbstractContainerMenu menu, int containerSlot, UUID localUuid) {
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory inv)) continue;
            if (!inv.player.getUUID().equals(localUuid)) continue;
            if (slot.getContainerSlot() == containerSlot) return slot.index;
        }
        return -1;
    }

    /**
     * Build the cycle slot list for {@code column}: directly-toggled inv
     * slots in visual top→bottom order, with the hotbar slot appended
     * last (when the column has at least one active inv member).
     *
     * <p>Shared by {@link #rotate} and {@link #planBringSlotToHotbar} so
     * both agree on index ordering and membership. Returns an empty list
     * when the column has no active inv cycle members.
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
        // Hotbar is implicitly part of the cycle when the column is active.
        // If no inv slots are toggled, ColumnCycler.isCycleSlot(hotbar)
        // returns false; list stays empty.
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
     * Compute the rotation plan to bring the item currently at the inv
     * slot {@code slot} into its column's hotbar position. Returns
     * {@code null} if {@code slot} isn't an active cycle member, isn't
     * an inv slot, or its column doesn't have enough cycle members to
     * rotate.
     *
     * <p>Picks the cheaper of FORWARD vs BACKWARD by step count — each
     * rotation sends {@code N+1} PICKUP packets, so direction choice
     * matters for chains.
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
     * <p>On tie (equal step counts), FORWARD wins — matches the
     * existing rotator default direction convention.
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
