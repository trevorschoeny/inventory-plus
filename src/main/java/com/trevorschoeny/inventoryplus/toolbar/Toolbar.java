package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsButtons;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;
import com.trevorschoeny.inventoryplus.sort.SortButton;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.core.SlotGroupRegion;
import com.trevorschoeny.menukit.inject.SlotGroupPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.List;

/**
 * The Inventory Plus toolbar system — per-container right-aligned MK
 * panels anchored above each sortable container's slot group. Holds
 * IP/IPP feature buttons specific to that container.
 *
 * <h3>Per-container, not single</h3>
 *
 * Trev 2026-05-17: every sortable container on screen gets its own
 * toolbar. In a chest screen, that means TWO toolbars — one above the
 * chest (Sort-external) and one above the player inv (Lock + Sort-inv
 * + MM). In a pure inventory screen, just the inventory toolbar.
 *
 * <p>Earlier iteration had a single inv-anchored toolbar that
 * "smart-targeted" the external container when present. Rejected
 * because it didn't generalize — sort-type cycler and other future
 * per-container buttons need to live next to their container, not
 * hide-and-target.
 *
 * <h3>Button placement rules</h3>
 *
 * <ul>
 *   <li><b>Inventory-tied buttons</b> (Lock-edit toggle) — only on the
 *       inventory toolbar. Locked slots are player-state, not
 *       container-state.</li>
 *   <li><b>Per-container action buttons</b> (Sort) — one instance on
 *       each container's toolbar, each acting on the container it
 *       sits above.</li>
 *   <li><b>Cross-container buttons</b> (MM In / MM Out) — only on the
 *       inventory toolbar, since MM is fundamentally inv-centric
 *       (move stuff from inv to external, or from external to inv).
 *       Per the {@code IP features/move-matching.md} spec.</li>
 * </ul>
 *
 * <h3>Layout</h3>
 *
 * Both toolbars use {@link SlotGroupRegion#TOP_ALIGN_RIGHT} +
 * {@link PanelStyle#NONE} (zero padding via {@link
 * Panel#interiorPadding}). The right edge anchors flush with the
 * slot group's right edge. Children laid out left-to-right at
 * explicit panel-local x offsets, 1px button gap.
 *
 * <h3>Per-screen scope</h3>
 *
 * <ul>
 *   <li>Inventory toolbar: panel-level showWhen excludes Creative.
 *       Anchored to {@link SlotGroupCategory#PLAYER_INVENTORY} so it
 *       appears on every container screen with a 3×9 main inv slot
 *       group (which is essentially all non-creative ones).</li>
 *   <li>External toolbar: anchored to the four sortable storage
 *       categories ({@link SlotGroupCategory#CHEST_STORAGE},
 *       {@link SlotGroupCategory#SHULKER_STORAGE},
 *       {@link SlotGroupCategory#DISPENSER_STORAGE},
 *       {@link SlotGroupCategory#HOPPER_STORAGE}). The category filter
 *       handles visibility — no .showWhen needed. Naturally hides on
 *       specialized UIs (furnace/anvil/enchanting/etc.) because those
 *       slot groups aren't in the filter list.</li>
 * </ul>
 *
 * <h3>Adding new buttons</h3>
 *
 * Feature-side: expose a factory method per toolbar variant the
 * button belongs on. For container-action buttons that mirror across
 * inv + external, expose two factories (see {@link SortButton}).
 *
 * <p>Toolbar-side: append the factory output to the appropriate
 * {@code build*Children} method with the next x offset.
 */
public final class Toolbar {

    private Toolbar() {}

    /** 1-px gap between adjacent buttons in the row. */
    public static final int BUTTON_GAP = 1;

    public static void register() {
        registerInventoryToolbar();
        registerExternalToolbar();
    }

    /**
     * Inventory toolbar — anchored above the player's 3×9 main inv.
     * Holds Lock-edit + Sort-inv + MM Out + MM In. Panel-level
     * showWhen excludes Creative (the creative item picker isn't a
     * real inventory).
     */
    private static void registerInventoryToolbar() {
        Panel panel = new Panel(
                "inventoryplus.toolbar.inventory",
                buildInventoryChildren(),
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);
        panel.showWhen(Toolbar::isToolbarScope);
        new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
                .on(SlotGroupCategory.PLAYER_INVENTORY);
    }

    /**
     * External-container toolbar — anchored above the external
     * container's slot group (chest/shulker/dispenser/hopper). Holds
     * Sort-external. Visibility entirely controlled by the category
     * filter — no panel-level showWhen needed.
     */
    private static void registerExternalToolbar() {
        Panel panel = new Panel(
                "inventoryplus.toolbar.external",
                buildExternalChildren(),
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);
        new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT)
                .on(SlotGroupCategory.CHEST_STORAGE,
                    SlotGroupCategory.SHULKER_STORAGE,
                    SlotGroupCategory.DISPENSER_STORAGE,
                    SlotGroupCategory.HOPPER_STORAGE);
    }

    private static List<PanelElement> buildInventoryChildren() {
        int x = 0;
        // Lock-edit toggle (leftmost, always visible — inventory-tied).
        PanelElement lockEdit = LockedSlotsButtons.toolbarToggle(x, 0);
        x += LockedSlotsButtons.SIZE + BUTTON_GAP;
        // Sort — targets the player main inv from this toolbar.
        PanelElement sort = SortButton.inventoryToolbarButton(x, 0);
        x += SortButton.SIZE + BUTTON_GAP;
        // Move Matching OUT.
        PanelElement mmOut = MoveMatchingButtons.toolbarOutButton(x, 0);
        x += MoveMatchingButtons.SIZE + BUTTON_GAP;
        // Move Matching IN (rightmost when MM is visible).
        PanelElement mmIn = MoveMatchingButtons.toolbarInButton(x, 0);
        return List.of(lockEdit, sort, mmOut, mmIn);
    }

    private static List<PanelElement> buildExternalChildren() {
        // Single button (for now) — sort the external container.
        PanelElement sort = SortButton.externalToolbarButton(0, 0);
        return List.of(sort);
    }

    /**
     * Panel-level scope filter for the inventory toolbar. Hides
     * entirely in Creative.
     */
    private static boolean isToolbarScope() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof CreativeModeInventoryScreen);
    }
}
