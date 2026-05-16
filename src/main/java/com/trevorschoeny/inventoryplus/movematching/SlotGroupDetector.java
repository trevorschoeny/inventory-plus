package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.EnderChestBlock;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Partitions an open container menu's slots into {@link SlotGroup}s with a
 * "targetable" flag — captures both:
 *
 * <ul>
 *   <li>Which slot groups host a move-matching button (targetable = true).
 *       The smoke scope: chest/shulker/barrel/ender-chest containers,
 *       hopper containers, dispenser containers, and the player's main
 *       inventory 3×9.</li>
 *   <li>Which slot groups are sources-only (targetable = false). The
 *       hotbar is always excluded entirely (neither target nor source);
 *       other non-target groups like crafting input, furnace fuel,
 *       brewing-stand bottles, donkey saddle, etc. CAN be sources per
 *       Trev's permissive-source direction but never targets.</li>
 * </ul>
 *
 * <h3>Partitioning rule</h3>
 *
 * Adjacent slots sharing a backing {@code Container} reference (slot 0's
 * container, then slot 1's container if same, etc.) form one group. When
 * the container changes between consecutive menu slots, a new group
 * starts.
 *
 * <p>Special case for the player's inventory: vanilla's {@code Inventory}
 * contains both the 3×9 main grid AND the 9-slot hotbar in one backing
 * {@link Inventory#items} list. Both halves share the SAME container
 * reference. We split them by reading
 * {@link Slot#getContainerSlot()}: indices 0-8 = hotbar (excluded
 * entirely), 9-35 = main inventory (targetable). The slot whose
 * container-index is in [0,8] starts a separate "hotbar" group; [9,35]
 * starts a "main inventory" group.
 *
 * <h3>ContainerKey resolution per group</h3>
 *
 * <ul>
 *   <li><b>Main inventory 3×9</b> → {@link ContainerKey#PLAYER_INVENTORY}.</li>
 *   <li><b>Hotbar</b> → no key (not targetable anyway).</li>
 *   <li><b>Non-player container group</b> → either
 *       {@link ContainerKey#ENDER_CHEST} (when the {@link UseBlockCallback}
 *       capture points at an ender chest block) or
 *       {@link ContainerKey.Block} keyed by the captured position. May
 *       fall back to {@code null} when no position can be captured
 *       (minecart variants); those groups still get a button but read
 *       the global default cycle.</li>
 * </ul>
 */
public final class SlotGroupDetector {

    private SlotGroupDetector() {}

    /**
     * Returns the slot-group partition for the given screen, or an empty
     * list if the screen isn't an eligible move-matching screen.
     */
    public static List<SlotGroup> detect(Screen screen) {
        if (!isMoveMatchingScreen(screen)) return List.of();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return List.of();

        AbstractContainerMenu menu = acs.getMenu();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return List.of();
        Inventory playerInv = player.getInventory();

        List<SlotGroup> groups = new ArrayList<>();
        List<Slot> current = new ArrayList<>();
        Object currentContainer = null;
        Boolean currentIsHotbar = null;

        // Resolve the non-player container's key once — every non-player
        // group in the menu maps to the same external container.
        ContainerKey nonPlayerKey = resolveNonPlayerContainerKey();

        for (Slot slot : menu.slots) {
            Object container = slot.container;
            boolean isPlayer = container == playerInv;
            boolean isHotbar = isPlayer && slot.getContainerSlot() < 9;
            boolean isMainInv = isPlayer && slot.getContainerSlot() >= 9
                    && slot.getContainerSlot() < 36;

            // Group boundary: container changes, OR (within player inv)
            // hotbar/main-inv split changes.
            boolean boundary;
            if (currentContainer == null) {
                boundary = false; // first slot, just start
            } else if (container != currentContainer) {
                boundary = true;
            } else if (isPlayer && (currentIsHotbar != null && currentIsHotbar != isHotbar)) {
                boundary = true;
            } else {
                boundary = false;
            }

            if (boundary && !current.isEmpty()) {
                groups.add(buildGroup(current, currentContainer, currentIsHotbar,
                        playerInv, nonPlayerKey));
                current = new ArrayList<>();
            }

            current.add(slot);
            currentContainer = container;
            currentIsHotbar = isPlayer ? isHotbar : null;
        }

        // Flush trailing group.
        if (!current.isEmpty()) {
            groups.add(buildGroup(current, currentContainer, currentIsHotbar,
                    playerInv, nonPlayerKey));
        }

        return groups;
    }

    /**
     * Tests whether the screen is a move-matching target — i.e., one
     * whose primary container is a "simplecontainer" per spec §"Scope"
     * §"Move-matching applies to": chests, shulkers, ender chests, barrels,
     * hoppers, dispensers, droppers, and the player's main inventory.
     */
    public static boolean isMoveMatchingScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen
                || screen instanceof InventoryScreen;
    }

    private static SlotGroup buildGroup(List<Slot> slots, Object container,
                                        @Nullable Boolean isHotbar,
                                        Inventory playerInv,
                                        @Nullable ContainerKey nonPlayerKey) {
        boolean isPlayer = container == playerInv;
        ContainerKey key;
        boolean targetable;

        if (isPlayer) {
            if (Boolean.TRUE.equals(isHotbar)) {
                // Hotbar — excluded entirely per spec §"Scope" §"The hotbar
                // is excluded entirely as both source and target".
                key = null;
                targetable = false;
            } else {
                key = ContainerKey.PLAYER_INVENTORY;
                targetable = true;
            }
        } else {
            // Non-player container. Targetable iff the screen class is one
            // of the simplecontainer screens — and we already gated by
            // isMoveMatchingScreen() at the entry, so YES for all
            // non-player groups on any of those screens.
            key = nonPlayerKey;
            targetable = true;
        }

        return new SlotGroup(key, List.copyOf(slots), targetable);
    }

    /**
     * Resolves the {@link ContainerKey} for the non-player container in
     * the current screen. Returns {@link ContainerKey#ENDER_CHEST} if
     * the captured block position is an ender chest, otherwise
     * {@link ContainerKey.Block} keyed by the position, otherwise null.
     */
    private static @Nullable ContainerKey resolveNonPlayerContainerKey() {
        var pos = ContainerKeyResolver.lastUsedBlockPosForKey();
        if (pos == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockState(pos).getBlock() instanceof EnderChestBlock) {
            return ContainerKey.ENDER_CHEST;
        }
        return new ContainerKey.Block(pos);
    }
}
