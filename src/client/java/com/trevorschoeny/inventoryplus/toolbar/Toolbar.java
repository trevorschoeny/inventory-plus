package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerButtons;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsButtons;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;
import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.sort.SortButton;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.SlotGroupPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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
 * Both toolbars use {@link OutsideRegion#TOP_ALIGN_RIGHT} +
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
     * Holds Edit cycle slots, Lock edit, Sort, MM Out, MM In, Settings. Panel-level
     * showWhen excludes Creative (the creative item picker isn't a
     * real inventory).
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
     * External-container toolbar — anchored above the external
     * container's slot group (chest/shulker/dispenser/hopper). Holds
     * Sort-external. Visibility entirely controlled by the category
     * filter — no panel-level showWhen needed.
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
     * Left to right (settings-menu.md, "Opening it", 2026-09-30): Edit cycle
     * slots, Lock edit, Sort, Move Matching out, Move Matching in, Settings at
     * the right end. One Flow, flush right with no holes (Trev, 2026-09-30
     * smoke): a hidden button is skipped in layout, and the leading spacer
     * takes what the hidden ones leave. The Flow's natural width counts every
     * button, so the panel keeps that width whatever is shown and its right
     * edge, with the gear last, never moves.
     */
    private static List<PanelElement> buildInventoryChildren() {
        return List.of(Flow.builder().gap(BUTTON_GAP, BUTTON_GAP)
                .add(Flow.spacer())
                // Column Cycler's edit toggle; its own visibleWhen gates it on
                // the feature and its button.
                .add(ColumnCyclerButtons.toolbarToggle(0, 0))
                .add(LockedSlotsButtons.toolbarToggle(0, 0))
                // Sort targets the player's main inventory from this toolbar.
                .add(SortButton.inventoryToolbarButton(0, 0))
                .add(MoveMatchingButtons.toolbarOutButton(0, 0))
                .add(MoveMatchingButtons.toolbarInButton(0, 0))
                // Settings at the right end: always shown.
                .add(settingsButton(0, 0))
                .build());
    }

    private static final int SETTINGS_SIZE = 9;

    private static final Identifier SETTINGS_TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "settings_button");

    /** Opens the settings menu over the current screen, which it returns to on close. */
    private static Button settingsButton(int x, int y) {
        return Button.builder().sprite(SETTINGS_TEXTURE).at(x, y).size(SETTINGS_SIZE, SETTINGS_SIZE)
                .onClick(() -> SettingsMenu.open(Minecraft.getInstance().gui.screen()))
                .tooltip(Component.literal("Settings"))
                .build();
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
