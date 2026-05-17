package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-scoped {@code S} keybind — sorts the simplecontainer
 * containing the slot under the cursor, per the spec at
 * {@code IP features/sorting.md}.
 *
 * <h3>Trigger flow</h3>
 *
 * <ol>
 *   <li>S pressed inside a container screen.</li>
 *   <li>Find the slot under the cursor. If none, no-op.</li>
 *   <li>Resolve a {@link ContainerIdentity} from that slot. If
 *       {@code null} (specialized UI, hotbar, armor, etc.), no-op.</li>
 *   <li>Read sort type from {@link SortState}. If
 *       {@link SortType#DISABLED}, no-op.</li>
 *   <li>Collect the sortable region (slots in the same container,
 *       with player-inv main-only filter), call {@link Sorter}.</li>
 * </ol>
 *
 * <h3>Scope</h3>
 *
 * Sortable regions per spec:
 * <ul>
 *   <li>Player main inv (container-slot 9-35) — regardless of which
 *       screen is open.</li>
 *   <li>External simple containers — chest, shulker block, ender
 *       chest, barrel, hopper, dispenser, dropper.</li>
 * </ul>
 *
 * Excluded (S no-op): hotbar (0-8), armor (36-39), offhand (40),
 * crafting input/result, anvil/furnace/enchanting/etc. specialized
 * slots.
 *
 * <h3>Auto-repeat</h3>
 *
 * Holding S would re-fire {@code afterKeyPress} via GLFW auto-repeat,
 * causing repeated sorts. Each re-sort on an already-sorted container
 * is a no-op (the algorithm exits without issuing clicks if no slot
 * mismatches the target), so the worst case is harmless wasted CPU.
 * If users hammer S enough to notice, add a debounce.
 */
public final class SortKeybind {

    private SortKeybind() {}

    private static final int KEY_S = GLFW.GLFW_KEY_S;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((innerScreen, event) -> {
                if (event.key() != KEY_S) return;
                if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;
                handleSortKey(currentAcs);
            });
        });
    }

    private static void handleSortKey(AbstractContainerScreen<?> acs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;

        Slot hovered = slotUnderMouse(acs, mc);
        if (hovered == null) return;

        ContainerIdentity identity = ContainerIdentity.fromHoveredSlot(hovered, acs.getMenu());
        if (identity == null) {
            // Hotbar / armor / specialized UI — not sortable per spec.
            return;
        }

        SortType type = SortState.getType(identity);
        if (type == SortType.DISABLED) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] {} is DISABLED — no-op", identity.key());
            return;
        }

        List<Slot> region = collectRegion(acs.getMenu(), hovered);
        if (region.size() < 2) return;

        try {
            Sorter.sort(acs.getMenu(), mc.gameMode, mc.player, region, type);
        } catch (UnsupportedOperationException e) {
            InventoryPlusClient.LOGGER.warn("[sort] {}", e.getMessage());
        }
    }

    /**
     * Collects the slots that belong to the same sortable region as
     * the hovered slot.
     *
     * <p>For player-inv hover: all slots where container is the
     * player {@link Inventory} AND container-slot is in main-inv range
     * (9-35). Hotbar and armor are excluded by the identity layer
     * upstream; this collector still filters in case the menu mixes
     * them in arbitrary order.
     *
     * <p>For block-anchored / ender-chest hover: all slots whose
     * {@code container} reference equals the hovered slot's container.
     */
    private static List<Slot> collectRegion(AbstractContainerMenu menu, Slot hovered) {
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
