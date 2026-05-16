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
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.EnderChestBlock;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Partitions an open container menu's slots into {@link SlotGroup}s and
 * tags each with a {@code targetable} flag.
 *
 * <h3>Partitioning rule</h3>
 *
 * Adjacent slots sharing a backing {@link Container} reference form one
 * group. The player inventory is split further: container-slot index
 * 0-8 = hotbar (always non-targetable), 9-35 = main inventory (always
 * targetable).
 *
 * <h3>Targetable rule (per Trev's 2026-05-16 redirect)</h3>
 *
 * A slot group is <i>targetable</i> — meaning it hosts a move-matching
 * button — only if it falls into the spec's "traditional simplecontainer"
 * set:
 *
 * <ul>
 *   <li>Player main inventory (3×9 grid above the hotbar).</li>
 *   <li>Containers backed by {@link ChestBlockEntity} (chest, trapped
 *       chest — subclass), {@link BarrelBlockEntity},
 *       {@link ShulkerBoxBlockEntity}, {@link HopperBlockEntity},
 *       {@link DispenserBlockEntity} (dispenser, dropper — subclass), or
 *       {@link PlayerEnderChestContainer}.</li>
 * </ul>
 *
 * <p>Everything else (armor, offhand, crafting input/result, furnace fuel
 * /input/output, brewing-stand bottles, donkey saddle, anvil grid, loom
 * pattern, etc.) is non-targetable. These slots can still be <b>sources</b>
 * for a move-matching trigger fired on a different group — that's Trev's
 * permissive-source direction from 2026-05-15 — but they never host a
 * button.
 *
 * <h3>The 2+ rule</h3>
 *
 * Per Trev's 2026-05-16 direction, move-matching only activates when at
 * least two targetable groups are visible on the same screen. That count
 * decision lives in {@link MoveMatchingButtons} (the registration site).
 * This detector returns all groups; the caller filters.
 *
 * <h3>Future compatibility — Shulker Peek</h3>
 *
 * If a future feature renders shulker contents alongside the player
 * inventory (e.g., a "Shulker Peek" panel inside the inventory screen
 * that exposes a real {@link ShulkerBoxBlockEntity} as a slot group),
 * the detector picks up the second targetable group automatically and
 * the 2+ rule fires — buttons appear without any change to this file.
 * The same applies to any consumer mod that adds a new traditional
 * container into a screen, as long as its slot.container resolves to one
 * of the whitelisted BlockEntity types or the player main inv.
 */
public final class SlotGroupDetector {

    private SlotGroupDetector() {}

    /**
     * Returns the slot-group partition for the given screen.
     */
    public static List<SlotGroup> detect(Screen screen) {
        if (!isMoveMatchingScreen(screen)) return List.of();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return List.of();

        AbstractContainerMenu menu = acs.getMenu();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return List.of();
        Inventory playerInv = player.getInventory();

        ContainerKey nonPlayerKey = resolveNonPlayerContainerKey();

        List<SlotGroup> groups = new ArrayList<>();
        List<Slot> current = new ArrayList<>();
        Object currentContainer = null;
        Boolean currentIsHotbar = null;

        for (Slot slot : menu.slots) {
            Object container = slot.container;
            boolean isPlayer = container == playerInv;
            boolean isHotbar = isPlayer && slot.getContainerSlot() < 9;

            boolean boundary;
            if (currentContainer == null) {
                boundary = false;
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

        if (!current.isEmpty()) {
            groups.add(buildGroup(current, currentContainer, currentIsHotbar,
                    playerInv, nonPlayerKey));
        }

        return groups;
    }

    /**
     * Returns true if the screen MIGHT host move-matching buttons —
     * specifically, if it's a vanilla simplecontainer screen or the
     * player inventory screens (which Shulker Peek-style features would
     * extend). Filters out specialized UIs (furnace, brewing stand,
     * anvil, etc.) at the source; the detector doesn't even run on those.
     */
    public static boolean isMoveMatchingScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen
                || screen instanceof InventoryScreen
                || screen instanceof CreativeModeInventoryScreen;
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
                // Hotbar — never a target, never a source either (Trev's rule).
                key = null;
                targetable = false;
            } else {
                // Player main inventory 3×9.
                key = ContainerKey.PLAYER_INVENTORY;
                targetable = true;
            }
        } else {
            // Non-player container — targetable iff it's in the
            // traditional-simplecontainer whitelist.
            key = nonPlayerKey;
            targetable = isTraditionalContainer(container);
        }

        return new SlotGroup(key, List.copyOf(slots), targetable);
    }

    /**
     * Whitelist for "traditional simplecontainer" backing types — the set
     * of {@link Container} implementations whose slot group should host a
     * move-matching button. See class javadoc §"Targetable rule".
     */
    private static boolean isTraditionalContainer(Object container) {
        return container instanceof ChestBlockEntity        // chest + trapped chest (subclass)
                || container instanceof BarrelBlockEntity
                || container instanceof ShulkerBoxBlockEntity
                || container instanceof HopperBlockEntity   // block hopper only; minecart hopper isn't a BE
                || container instanceof DispenserBlockEntity // dispenser + dropper (subclass)
                || container instanceof PlayerEnderChestContainer;
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
