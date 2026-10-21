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
 * wrapper, so armor and offhand slots share the same container reference
 * as the main inventory and hotbar. What tells them apart is
 * {@link Slot#getContainerSlot}:
 *
 * <ul>
 *   <li>{@code [0, 9)}: hotbar</li>
 *   <li>{@code [9, 36)}: main inventory 3x9</li>
 *   <li>{@code 36} and up: armor, offhand, body and saddle equipment</li>
 * </ul>
 *
 * <p>An earlier version partitioned only on container reference plus a
 * hotbar or non-hotbar split (containerSlot &lt; 9). That put armor, main
 * inventory and offhand together in one non-hotbar group, so the
 * targetable bounds ran from the armor row down to slot 35 and the buttons
 * appeared above the helmet row instead of above the main inventory. The
 * role enum and range check fix that.
 *
 * <h3>External targetability</h3>
 *
 * Slot groups carry no persistence key. Targetability for external groups,
 * which only answers "is this a real simple container?", lives on
 * {@link SlotGroup#targetable} as a container exclusion list (crafting
 * input and result are not targetable).
 */
public final class SlotGroupDetector {

    private SlotGroupDetector() {}

    /**
     * Returns the slot-group partition for the given screen. Works on any
     * {@link AbstractContainerScreen}. Screen-class filtering belongs at the
     * registration site: the Move Matching buttons show only on chest,
     * shulker, hopper and dispenser screens, while the inventory toolbar runs
     * on every screen with a player main inventory.
     */
    public static List<SlotGroup> detect(Screen screen) {
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

            // A new group starts when the role or container changes. Identity
            // equality on the container is right, because vanilla reuses one
            // container instance across a group's slots.
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
     * Screens that host Move Matching buttons: those that pair the player
     * inventory with an external simple container. InventoryScreen (the
     * standalone survival inventory) is excluded by not being listed, with
     * no dynamic "is a container present?" check. A feature that wants
     * buttons inside the standalone inventory adds its own widgets with
     * their own positioning. Creative's inventory screen isn't one of them:
     * it pairs the inventory with no container.
     */
    public static boolean isMoveMatchingScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen;
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
