package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
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
import java.util.Objects;

/**
 * Partitions an open container menu's slots into {@link SlotGroup}s by
 * (role, backing container) and tags each with the right
 * {@link ContainerKey} for persistence.
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
 * <h3>Why screen-class-based EXTERNAL targetability</h3>
 *
 * Client-side, the slot's {@code container} field for a chest / shulker /
 * etc. is NOT the server's {@code ChestBlockEntity} — vanilla creates a
 * client-side {@link net.minecraft.world.SimpleContainer} proxy that
 * mirrors slot updates. An instanceof check against
 * {@code ChestBlockEntity} would always fail in multiplayer (and in
 * singleplayer the references are intentionally distinct too). The
 * reliable signal is the active {@link Screen} class — that's the same
 * on both sides and tracks the menu's identity.
 *
 * <p>{@link SlotGroup#targetable} uses
 * {@link SlotGroup#isSimplecontainerScreen} for the EXTERNAL decision.
 * The decision lives on SlotGroup rather than here so it travels with
 * the partition output.
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

        // Resolve the non-player container's key once — every non-player
        // slot group on a single screen maps to the same external block /
        // ender-chest / etc., so we don't need to per-slot look it up.
        ContainerKey nonPlayerKey = resolveNonPlayerContainerKey();

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
                groups.add(buildGroup(current, currentRole, nonPlayerKey));
                current = new ArrayList<>();
            }

            current.add(slot);
            currentRole = role;
            currentContainer = container;
        }

        if (!current.isEmpty()) {
            groups.add(buildGroup(current, currentRole, nonPlayerKey));
        }

        return groups;
    }

    /**
     * Returns true if the screen might host move-matching buttons — i.e.,
     * we should run the detector + 2+-rule check on it. Filters out
     * specialized UIs at the source so the detector doesn't even bother
     * (furnace, brewing stand, anvil, etc. never get the registration
     * pass).
     */
    public static boolean isMoveMatchingScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen
                || screen instanceof InventoryScreen
                || screen instanceof CreativeModeInventoryScreen;
    }

    private static SlotRole roleOf(Slot slot, Inventory playerInv) {
        if (slot.container != playerInv) return SlotRole.EXTERNAL;
        int ci = slot.getContainerSlot();
        if (ci < 9) return SlotRole.PLAYER_HOTBAR;
        if (ci < 36) return SlotRole.PLAYER_MAIN_INV;
        return SlotRole.PLAYER_EQUIPMENT;
    }

    private static SlotGroup buildGroup(List<Slot> slots, SlotRole role,
                                        @Nullable ContainerKey nonPlayerKey) {
        Objects.requireNonNull(role, "role");
        ContainerKey key = switch (role) {
            case PLAYER_MAIN_INV -> ContainerKey.PLAYER_INVENTORY;
            case PLAYER_HOTBAR, PLAYER_EQUIPMENT -> null;
            case EXTERNAL -> nonPlayerKey;
        };
        return new SlotGroup(key, List.copyOf(slots), role);
    }

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
