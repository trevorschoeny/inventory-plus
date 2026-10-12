package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;
import com.trevorschoeny.inventoryplus.sort.SortButton;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.slotkit.api.group.SlotGroupPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.List;

/**
 * The action toolbars: right-aligned MenuKit panels anchored above each
 * sortable container's slot group, holding the buttons that act on that
 * container.
 *
 * <h3>One toolbar per container</h3>
 *
 * Every sortable container on screen gets its own toolbar. A chest screen
 * has two, one above the chest (its Sort) and one above the player
 * inventory. The inventory screen alone has just the inventory toolbar.
 *
 * <p>An earlier version had one inventory-anchored toolbar that targeted
 * the external container when present. It didn't generalize: per-container
 * buttons need to sit next to their container.
 *
 * <h3>Button placement</h3>
 *
 * <ul>
 *   <li><b>Per-container action buttons</b> (Sort): one on each container's
 *       toolbar, acting on the container it sits above.</li>
 *   <li><b>Cross-container buttons</b> (Move Matching in and out): only on
 *       the inventory toolbar, since Move Matching always moves between the
 *       inventory and a container.</li>
 * </ul>
 *
 * <h3>Layout</h3>
 *
 * Both toolbars use {@link OutsideRegion#TOP_ALIGN_RIGHT} with
 * {@link PanelStyle#NONE} (zero padding through {@link
 * Panel#interiorPadding}). The right edge sits flush with the slot group's
 * right edge, and children run left to right with a 1 px gap.
 *
 * <h3>Per-screen scope</h3>
 *
 * <ul>
 *   <li>Inventory toolbar: the panel's visibleWhen excludes Creative. It is
 *       anchored to {@link SlotGroupCategory#PLAYER_INVENTORY}, so it
 *       appears on every container screen with a 3x9 main inventory group,
 *       which is nearly all non-creative ones.</li>
 *   <li>External toolbar: anchored to the four sortable storage
 *       categories ({@link SlotGroupCategory#CHEST_STORAGE},
 *       {@link SlotGroupCategory#SHULKER_STORAGE},
 *       {@link SlotGroupCategory#DISPENSER_STORAGE},
 *       {@link SlotGroupCategory#HOPPER_STORAGE}). The category filter
 *       handles visibility, so it hides on specialized UIs (furnace, anvil,
 *       enchanting table) whose slot groups aren't in the list.</li>
 * </ul>
 *
 * <h3>Adding buttons</h3>
 *
 * A feature exposes a factory per toolbar its button belongs on. A
 * container-action button that appears on both the inventory and external
 * toolbars exposes two (see {@link SortButton}). The toolbar adds the
 * factory output to the matching {@code build*Children} method.
 */
public final class Toolbar {

    private Toolbar() {}

    public static final int BUTTON_GAP = 1;

    public static void register() {
        registerInventoryToolbar();
        registerExternalToolbar();
    }

    /**
     * Anchored above the player's 3x9 main inventory. Holds Move Matching out
     * and in, and Sort. Hidden in Creative, because the creative item picker
     * isn't a real inventory.
     */
    private static void registerInventoryToolbar() {
        Panel panel = Panel.builder("inventoryplus.toolbar.inventory")
                .elements(buildInventoryChildren())
                .style(PanelStyle.NONE)
                .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
                .visibleWhen(Toolbar::isToolbarScope)
                .build();
        new SlotGroupPanelAdapter(panel)
                .on(SlotGroupCategory.PLAYER_INVENTORY);
    }

    /**
     * Anchored above the external container's slot group (chest, shulker,
     * dispenser, hopper). Holds that container's Sort. The category filter
     * alone controls visibility.
     */
    private static void registerExternalToolbar() {
        Panel panel = Panel.builder("inventoryplus.toolbar.external")
                .elements(buildExternalChildren())
                .style(PanelStyle.NONE)
                .position(PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT))
                .build();
        new SlotGroupPanelAdapter(panel)
                .on(SlotGroupCategory.CHEST_STORAGE,
                    SlotGroupCategory.SHULKER_STORAGE,
                    SlotGroupCategory.DISPENSER_STORAGE,
                    SlotGroupCategory.HOPPER_STORAGE);
    }

    /**
     * The top row is for acting, the right column for switching features.
     * Left to right: Move Matching out, Move Matching
     * in, Sort at the right end. One Flow, flush right with no holes: a
     * hidden button is skipped in layout, and the leading spacer takes what
     * the hidden ones leave, so the right edge never moves.
     */
    private static List<PanelElement> buildInventoryChildren() {
        return List.of(Flow.builder().gap(BUTTON_GAP, BUTTON_GAP)
                .add(Flow.spacer())
                .add(MoveMatchingButtons.toolbarOutButton(0, 0))
                .add(MoveMatchingButtons.toolbarInButton(0, 0))
                // Sort targets the player's main inventory from this toolbar.
                .add(SortButton.inventoryToolbarButton(0, 0))
                .build());
    }

    private static List<PanelElement> buildExternalChildren() {
        PanelElement sort = SortButton.externalToolbarButton(0, 0);
        return List.of(sort);
    }

    private static boolean isToolbarScope() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof CreativeModeInventoryScreen);
    }
}
