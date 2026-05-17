package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Post-hoc auto-pickup correction — moves items out of locked slots
 * that arrived via vanilla auto-pickup (mob drops, ground pickup,
 * etc.).
 *
 * <h3>Why post-hoc and not preventative</h3>
 *
 * Vanilla's auto-pickup runs server-side via {@code Inventory.add},
 * which doesn't consult {@link Slot#mayPlace}. A client-only mod
 * can't block the server's decision. The {@link
 * com.trevorschoeny.inventoryplus.mixin.SlotMayPlaceMixin} handles
 * the shift-click + shift-double-click cases (those go through
 * mayPlace); this corrector handles what slips through (auto-pickup).
 *
 * <h3>Detection model</h3>
 *
 * For each locked slot, the corrector keeps a tick-start snapshot.
 * Each tick:
 * <ol>
 *   <li>Read the slot's current item.</li>
 *   <li>Compare to the snapshot.</li>
 *   <li>If the slot was empty at snapshot AND now has items AND no
 *       manual click occurred on it this tick → auto-pickup happened
 *       → move all of the new items out to a non-locked player slot.</li>
 *   <li>Update the snapshot.</li>
 * </ol>
 *
 * <p>Simplified to "snapshot-was-empty" only — doesn't handle the case
 * of stack-growth in an already-occupied locked slot (auto-pickup
 * merging into an existing partial stack). That case loses partial
 * protection; filed for the polish round.
 *
 * <h3>Manual touch tracking</h3>
 *
 * The slot-clicked mixin records "manual touched this tick" on slots
 * the player explicitly clicks. The corrector skips those slots — the
 * player is intentionally interacting; don't undo their work.
 *
 * <h3>Move-out target</h3>
 *
 * First non-locked player main-inv slot with space (or empty). If no
 * destination is found, the items stay in the locked slot for now (the
 * player can manually move them; the lock UI doesn't lie about the
 * underlying state).
 */
public final class LockedSlotsCorrector {

    private LockedSlotsCorrector() {}

    /** Per-locked-slot: copy of the slot's item at the start of the previous tick. */
    private static final Map<Integer, ItemStack> tickStartSnapshot = new HashMap<>();

    /** Per-tick set of container-slot indices that the player manually clicked. */
    private static final Set<Integer> manuallyClickedThisTick = new HashSet<>();

    /** Called from the slot-clicked mixin when the player clicks a player slot. */
    public static void recordManualClick(int containerSlotIndex) {
        manuallyClickedThisTick.add(containerSlotIndex);
    }

    /**
     * Called by {@link LockedSlots#toggleByContainerSlot} when a slot's
     * lock state changes. On lock acquisition with an existing item, we
     * pre-seed the snapshot with the current item so the "is this slot
     * gaining items?" check doesn't false-positive on the existing
     * contents.
     */
    public static void onLockChanged(int containerSlotIndex, boolean nowLocked) {
        if (nowLocked) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                tickStartSnapshot.put(
                        containerSlotIndex,
                        mc.player.getInventory().getItem(containerSlotIndex).copy());
            }
        } else {
            tickStartSnapshot.remove(containerSlotIndex);
        }
    }

    /**
     * Tick handler — registered against
     * {@code ClientTickEvents.END_CLIENT_TICK}.
     */
    public static void tick(Minecraft mc) {
        if (mc == null) return;
        LocalPlayer player = mc.player;
        if (player == null) {
            tickStartSnapshot.clear();
            manuallyClickedThisTick.clear();
            return;
        }
        Inventory inv = player.getInventory();
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;

        for (int slotIdx : LockedSlots.getLockedSlots()) {
            if (slotIdx < 0 || slotIdx > LockedSlots.MAX_PLAYER_CONTAINER_SLOT) continue;
            ItemStack current = inv.getItem(slotIdx);
            ItemStack snapshot = tickStartSnapshot.getOrDefault(slotIdx, ItemStack.EMPTY);
            boolean manualThisTick = manuallyClickedThisTick.contains(slotIdx);

            if (!manualThisTick
                    && snapshot.isEmpty()
                    && !current.isEmpty()) {
                // Auto-pickup landed in a previously-empty locked slot.
                // Move it out.
                correctOut(mc, player, gameMode, slotIdx);
            }

            // Update snapshot for next tick — uses current state AFTER
            // any correction. If correction succeeded, snapshot reflects
            // empty again; if no destination found, snapshot reflects
            // the still-occupied state (won't re-correct until item leaves
            // and re-enters via auto-pickup).
            tickStartSnapshot.put(slotIdx, inv.getItem(slotIdx).copy());
        }

        manuallyClickedThisTick.clear();
    }

    /**
     * Sends the click sequence to move items out of the locked slot to
     * the first available non-locked player slot.
     *
     * <p>Strategy: SWAP via PICKUP+PICKUP. Pick up the locked slot's
     * stack, then place into the first eligible destination (same-item-
     * with-space first, then empty slot).
     */
    private static void correctOut(Minecraft mc, LocalPlayer player,
                                   MultiPlayerGameMode gameMode, int sourceContainerSlot) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        // Find the menu-slot index for our source container-slot.
        Slot sourceMenuSlot = findMenuSlot(menu, player, sourceContainerSlot);
        if (sourceMenuSlot == null) return;

        // PICKUP source → cursor holds the items.
        gameMode.handleInventoryMouseClick(
                menu.containerId, sourceMenuSlot.index, 0, ClickType.PICKUP, player);

        // Pass 1 — same-item destination with space (in non-locked player slots).
        Set<Integer> lockedSet = LockedSlots.getLockedSlots();
        for (Slot dest : menu.slots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (!isPlayerSlot(dest, player)) continue;
            if (lockedSet.contains(dest.getContainerSlot())) continue;
            ItemStack destStack = dest.getItem();
            if (destStack.isEmpty()) continue;
            if (!destStack.is(carried.getItem())) continue;
            if (destStack.getCount() >= destStack.getMaxStackSize()) continue;
            gameMode.handleInventoryMouseClick(
                    menu.containerId, dest.index, 0, ClickType.PICKUP, player);
        }

        // Pass 2 — empty destination.
        for (Slot dest : menu.slots) {
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) break;
            if (!isPlayerSlot(dest, player)) continue;
            if (lockedSet.contains(dest.getContainerSlot())) continue;
            if (!dest.getItem().isEmpty()) continue;
            gameMode.handleInventoryMouseClick(
                    menu.containerId, dest.index, 0, ClickType.PICKUP, player);
        }

        // If items are stuck on the cursor (no destination found), put
        // them back where they came from. The lock will appear ineffective
        // for this pickup, but at least we don't drop items on the floor.
        if (!menu.getCarried().isEmpty()) {
            gameMode.handleInventoryMouseClick(
                    menu.containerId, sourceMenuSlot.index, 0, ClickType.PICKUP, player);
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] correction for slot {} found no destination — items stay",
                    sourceContainerSlot);
        } else {
            InventoryPlusClient.LOGGER.debug(
                    "[locked-slots] corrected auto-pickup into slot {}", sourceContainerSlot);
        }
    }

    private static @Nullable Slot findMenuSlot(AbstractContainerMenu menu, LocalPlayer player,
                                               int containerSlotIndex) {
        Inventory playerInv = player.getInventory();
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv && slot.getContainerSlot() == containerSlotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static boolean isPlayerSlot(Slot slot, LocalPlayer player) {
        return slot.container == player.getInventory();
    }
}
