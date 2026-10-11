package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-scoped {@code S} keybind. Sorts the simple container holding the
 * slot under the cursor.
 *
 * <h3>Trigger flow</h3>
 *
 * <ol>
 *   <li>S pressed inside a container screen.</li>
 *   <li>Find the slot under the cursor. If none, and no container but
 *       the inventory is open, the inventory; else no-op.</li>
 *   <li>Resolve a {@link ContainerIdentity} from that slot. If
 *       {@code null} (specialized UI, hotbar, armor, etc.), no-op.</li>
 *   <li>Read the sort type from {@link SortState}.</li>
 *   <li>Collect the sortable region (slots in the same container,
 *       with only the main grid for the player inventory) and call
 *       {@link Sorter}.</li>
 * </ol>
 *
 * <h3>Scope</h3>
 *
 * Sortable regions:
 * <ul>
 *   <li>The player main inventory (container slots 9-35), whichever screen
 *       is open.</li>
 *   <li>External simple containers: chest, shulker box, ender chest,
 *       barrel, hopper, dispenser, dropper.</li>
 * </ul>
 *
 * S does nothing over the hotbar (0-8), armor (36-39), offhand (40),
 * crafting input and result, or specialized slots such as the anvil,
 * furnace and enchanting table.
 *
 * <h3>Auto-repeat</h3>
 *
 * Holding S would re-fire {@code afterKeyPress} via key auto-repeat,
 * causing repeated sorts. Each re-sort on an already-sorted container
 * is a no-op (the algorithm exits without issuing clicks if no slot
 * mismatches the target), so the worst case is some wasted CPU. Add a
 * debounce if holding S ever becomes noticeable.
 */
public final class SortKeybind {

    private SortKeybind() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((innerScreen, event) -> {
                if (!IPKeybinds.SORT.matches(event)) return;
                if (!IPConfig.sortEnabled()) return;
                if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;
                handleSortKey(currentAcs);
            });
        });
    }

    private static void handleSortKey(AbstractContainerScreen<?> acs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        AbstractContainerMenu menu = acs.getMenu();
        Slot hovered = slotUnderMouse(acs, mc);
        ContainerIdentity identity = hovered == null ? null : ContainerIdentity.fromHoveredSlot(hovered, menu);
        // With nothing sortable open but the inventory (the inventory screen, a
        // furnace, a crafting table), the key sorts the inventory wherever the
        // mouse is. With a container open too, it sorts
        // whichever one the mouse is over, and nothing off them.
        if (identity == null && !hasSortableContainer(menu)) {
            hovered = SortButton.findInventoryAnchor(menu);
            identity = hovered == null ? null : ContainerIdentity.fromHoveredSlot(hovered, menu);
        }
        if (identity == null) {
            InventoryPlusClient.LOGGER.debug("[sort] S pressed; nothing sortable under the cursor, no-op");
            return;
        }
        String hoveredDesc = "slot.index=" + hovered.index
                + " containerSlot=" + hovered.getContainerSlot()
                + " container=" + hovered.container.getClass().getSimpleName();

        SortType type = SortState.getType(identity);

        List<Slot> region = collectRegion(menu, hovered);
        if (region.size() < 2) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] S pressed; {} identity={} region only {} slot(s), no-op",
                    hoveredDesc, identity.key(), region.size());
            return;
        }

        InventoryPlusClient.LOGGER.debug(
                "[sort] S pressed; {} identity={} type={} region={} slots, sorting",
                hoveredDesc, identity.key(), type, region.size());

        Sorter.sort(menu, mc.gameMode, mc.player, region, type);
    }

    /** Whether the menu holds a sortable container besides the player's inventory. */
    private static boolean hasSortableContainer(AbstractContainerMenu menu) {
        Slot external = SortButton.findExternalAnchor(menu);
        return external != null && ContainerIdentity.fromHoveredSlot(external, menu) != null;
    }

    /**
     * Collects the slots that belong to the same sortable region as
     * the hovered slot.
     *
     * <p>Over the player inventory: every slot whose container is the player
     * {@link Inventory} and whose container slot is in the main range
     * (9-35). The identity layer already excludes hotbar and armor, but this
     * still filters in case the menu mixes them in any order.
     *
     * <p>Over a block container or ender chest: every slot whose
     * {@code container} reference equals the hovered slot's.
     *
     * <p>Package-visible so the Sort toolbar button can reuse it by passing an
     * anchor slot (any external sortable slot or any main inventory slot).
     */
    static List<Slot> collectRegion(AbstractContainerMenu menu, Slot hovered) {
        List<Slot> region = new ArrayList<>();
        Container hoveredContainer = hovered.container;
        boolean isPlayerInv = hoveredContainer instanceof Inventory;
        for (Slot s : menu.slots) {
            if (s.container != hoveredContainer) continue;
            if (isPlayerInv) {
                int cs = s.getContainerSlot();
                if (cs < 9 || cs > 35) continue;
            }
            region.add(s);
        }
        return region;
    }

    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs, Minecraft mc) {
        double mouseX = mc.mouseHandler.xpos()
                * (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos()
                * (double) mc.getWindow().getGuiScaledHeight()
                / (double) mc.getWindow().getScreenHeight();
        int leftPos = ScreenLayout.leftPos(acs);
        int topPos = ScreenLayout.topPos(acs);
        for (Slot slot : acs.getMenu().slots) {
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16
                    && mouseY >= sy && mouseY < sy + 16) {
                return slot;
            }
        }
        return null;
    }
}
