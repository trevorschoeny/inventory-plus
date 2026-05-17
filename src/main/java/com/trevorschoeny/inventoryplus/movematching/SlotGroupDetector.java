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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Partitions an open container menu's slots into {@link SlotGroup}s by
 * (role, backing container).
 *
 * <h3>Why role-based partitioning</h3>
 *
 * Vanilla 1.21+ unified equipment access through {@link Inventory}'s
 * wrapper — armor and offhand slots share the SAME container reference
 * as the main inventory and hotbar. The distinguishing detail is
 * {@link Slot#getContainerSlot}:
 *
 * <ul>
 *   <li>{@code [0, 9)} — hotbar</li>
 *   <li>{@code [9, 36)} — main inventory 3×9</li>
 *   <li>{@code [36, ∞)} — armor / offhand / body / saddle equipment</li>
 * </ul>
 *
 * <p>An earlier version of this detector partitioned only on container
 * reference + a hotbar/non-hotbar split (boolean: containerSlot &lt; 9).
 * That bucketed armor + main inv + offhand together as one "non-hotbar"
 * group, which made the targetable bounds span from the armor row down
 * to slot 35 — buttons appeared above the helmet row instead of above
 * the main inventory. The role enum + range check is the load-bearing
 * fix.
 *
 * <h3>EXTERNAL targetability</h3>
 *
 * Post-2026-05-16 simplification, ContainerKey-based persistence was
 * dropped — slot groups no longer carry a persistence key. Targetability
 * for EXTERNAL groups (used purely as the "is this a real
 * simplecontainer?" gate now) lives on {@link SlotGroup#targetable}
 * via a container blacklist (crafting input / result are non-targetable).
 */
public final class SlotGroupDetector {

    private SlotGroupDetector() {}

    /** Returns the slot-group partition for the given screen. */
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
        SlotRole currentRole = null;
        Object currentContainer = null;

        for (Slot slot : menu.slots) {
            SlotRole role = roleOf(slot, playerInv);
            Object container = slot.container;

            // Group boundary when (role, container) tuple changes. Using
            // identity equality for the container reference is the right
            // test — vanilla reuses container instances across the slot
            // list within a group.
            boolean boundary = currentRole != null
                    && (currentRole != role || currentContainer != container);

            if (boundary && !current.isEmpty()) {
                groups.add(buildGroup(current, currentRole));
                current = new ArrayList<>();
            }

            current.add(slot);
            currentRole = role;
            currentContainer = container;
        }

        if (!current.isEmpty()) {
            groups.add(buildGroup(current, currentRole));
        }

        return groups;
    }

    /**
     * Screens that might host move-matching buttons. We include
     * InventoryScreen because future features (e.g., Shulker Peek)
     * may surface an external simplecontainer inside it — the
     * activation rule in {@link MoveMatchingButtons} handles the
     * "is a real simplecontainer present?" gate dynamically.
     *
     * <p>CreativeModeInventoryScreen is excluded — the creative item
     * picker isn't a real storage and creative-mode players don't need
     * move-matching.
     */
    public static boolean isMoveMatchingScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen
                || screen instanceof InventoryScreen;
    }

    private static SlotRole roleOf(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) return SlotRole.EXTERNAL;
        int ci = slot.getContainerSlot();
        if (ci < 9) return SlotRole.PLAYER_HOTBAR;
        if (ci < 36) return SlotRole.PLAYER_MAIN_INV;
        return SlotRole.PLAYER_EQUIPMENT;
    }

    private static SlotGroup buildGroup(List<Slot> slots, SlotRole role) {
        Objects.requireNonNull(role, "role");
        return new SlotGroup(List.copyOf(slots), role);
    }
}
