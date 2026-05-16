package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * A "slot group" within an open container menu — a contiguous block of
 * slots sharing a {@link SlotRole} and backing {@link net.minecraft.world.Container}.
 *
 * <h3>Targetability decision</h3>
 *
 * A group "hosts a move-matching button" when {@link #targetable} returns
 * true, which is a join of (role, screen class):
 *
 * <ul>
 *   <li>{@link SlotRole#PLAYER_MAIN_INV} → always targetable.</li>
 *   <li>{@link SlotRole#EXTERNAL} → targetable iff the screen is one of
 *       the vanilla simplecontainer screens (ContainerScreen — chest /
 *       trapped chest / barrel / ender chest, ShulkerBoxScreen,
 *       HopperScreen, DispenserScreen which also covers dropper). Not
 *       targetable on InventoryScreen (the crafting input/result, etc.
 *       are technically EXTERNAL but the spec excludes them).</li>
 *   <li>Everything else → never targetable.</li>
 * </ul>
 *
 * <p>Future "Shulker Peek"-style features that surface a real shulker
 * container inside the inventory screen would need to mark their screen
 * (or extend InventoryScreen) so {@link #isSimplecontainerScreen} returns
 * true for it. The partitioning itself works automatically — only the
 * decision changes.
 *
 * <h3>Bounds</h3>
 *
 * Slot positions are local to the screen's {@code leftPos}/{@code topPos}.
 * {@link #localTopY} / {@link #localRightX} recompute live each render
 * because vanilla can technically reposition slots on resize (rare for
 * container screens, but cheap to honor).
 */
public record SlotGroup(
        ContainerKey key,
        List<Slot> slots,
        SlotRole role
) {

    /** Decides targetability given the active screen — see class javadoc. */
    public boolean targetable(Screen screen) {
        return switch (role) {
            case PLAYER_MAIN_INV -> true;
            case EXTERNAL -> isSimplecontainerScreen(screen);
            case PLAYER_HOTBAR, PLAYER_EQUIPMENT -> false;
        };
    }

    /**
     * Vanilla simplecontainer screen classes. {@code InventoryScreen} and
     * {@code CreativeModeInventoryScreen} are deliberately excluded — their
     * EXTERNAL groups (crafting, etc.) aren't traditional containers per
     * spec.
     */
    public static boolean isSimplecontainerScreen(Screen screen) {
        return screen instanceof ContainerScreen
                || screen instanceof ShulkerBoxScreen
                || screen instanceof HopperScreen
                || screen instanceof DispenserScreen;
    }

    public int localTopY() {
        int top = Integer.MAX_VALUE;
        for (Slot slot : slots) if (slot.y < top) top = slot.y;
        return top == Integer.MAX_VALUE ? 0 : top;
    }

    public int localRightX() {
        int right = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            int slotRight = slot.x + 16;
            if (slotRight > right) right = slotRight;
        }
        return right == Integer.MIN_VALUE ? 0 : right;
    }

    public boolean containsMenuIndex(int menuIndex) {
        for (Slot slot : slots) if (slot.index == menuIndex) return true;
        return false;
    }
}
