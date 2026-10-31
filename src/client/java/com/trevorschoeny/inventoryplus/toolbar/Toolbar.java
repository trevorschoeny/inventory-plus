package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;
import com.trevorschoeny.inventoryplus.sort.SortButton;

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.CategoryPanels;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.slotkit.api.group.SlotGroupPanelAdapter;
import com.trevlar.slotkit.api.slot.SlotPlacement;
import com.trevlar.menukit.api.panel.Anchor;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.Reference;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

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
 * Panel#interiorPadding}). The inventory toolbar's right edge sits flush with
 * the 3x9's right edge, and each container's lines up with it, however narrow
 * the container's grid ({@link #headerRow}). Children run left to right with
 * a 1 px gap.
 *
 * <h3>Per-screen scope</h3>
 *
 * <p>Every panel is placed in both modes, around a slot group, and shows
 * wherever that group is on the open screen. No screen check.
 *
 * <ul>
 *   <li>Inventory toolbar: around {@link SlotGroupCategory#PLAYER_INVENTORY},
 *       so it appears on every screen with the 3x9 main inventory, creative's
 *       inventory tab included, and not on creative's item tabs, which have
 *       none.</li>
 *   <li>External toolbars: one around every shown group of the sortable
 *       storage categories ({@link SlotGroupCategory#CHEST_STORAGE},
 *       {@link SlotGroupCategory#SHULKER_STORAGE},
 *       {@link SlotGroupCategory#DISPENSER_STORAGE},
 *       {@link SlotGroupCategory#HOPPER_STORAGE},
 *       {@link SlotGroupCategory#BUNDLE_STORAGE}), vanilla or another mod's,
 *       so a shulker box or bundle another mod opens beside a chest gets its
 *       own Sort, and they hide on specialized UIs (furnace, anvil, enchanting
 *       table) whose groups aren't in the list.</li>
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
     * Anchored above the player's 3x9 main inventory, in both modes. Holds
     * Move Matching out and in, and Sort. Shows wherever that group is on the
     * open screen: the survival inventory, any container, and creative's
     * inventory tab, but not creative's item tabs, which have no main
     * inventory (presence comes from the group, not a screen check).
     */
    private static void registerInventoryToolbar() {
        Panel panel = inventoryToolbar = Panel.builder("inventoryplus.toolbar.inventory")
                .elements(buildInventoryChildren())
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, SlotPlacement.group(SlotGroupCategory.PLAYER_INVENTORY,
                        PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT)))
                .build();
        new SlotGroupPanelAdapter(panel);
    }

    /** The containers a Sort button sits above. */
    private static final List<SlotGroupCategory> SORTABLE = List.of(
            SlotGroupCategory.CHEST_STORAGE, SlotGroupCategory.SHULKER_STORAGE,
            SlotGroupCategory.DISPENSER_STORAGE, SlotGroupCategory.HOPPER_STORAGE,
            SlotGroupCategory.BUNDLE_STORAGE);

    /**
     * One toolbar above each shown group of a sortable category, in both
     * modes, holding that group's Sort: a chest's, and the shulker box, ender
     * chest or bundle another mod opens beside it, each its own (SlotKit's
     * {@link CategoryPanels}). The first of each category keeps the id one
     * panel per category had.
     */
    private static void registerExternalToolbar() {
        PanelPosition aboveRight = PanelPosition.region(OutsideRegion.TOP_ALIGN_RIGHT);
        Anchor inventory = SlotPlacement.group(SlotGroupCategory.PLAYER_INVENTORY, aboveRight).anchor();
        for (SlotGroupCategory category : SORTABLE) {
            CategoryPanels.declare(category, on -> {
                String id = "inventoryplus.toolbar.external." + category.path();
                Anchor group = on.around(aboveRight).anchor();
                Anchor header = Anchor.of(Identifier.fromNamespaceAndPath("inventoryplus",
                                "toolbar_header/" + category.path() + "/" + on.index()),
                        screen -> headerRow(group.box(screen), inventory.box(screen)));
                Panel panel = Panel.builder(on.index() == 0 ? id : id + "." + on.index())
                        // The group is read on each click: a tab switch can move the panel to another.
                        .elements(List.of(SortButton.externalToolbarButton(0, 0, on::group)))
                        .style(PanelStyle.NONE)
                        .in(ModeScope.BOTH, Placement.around(header, aboveRight))
                        .build();
                externalToolbars.add(panel);
                return panel;
            });
        }
    }

    /**
     * The container's header row, which its toolbar sits on: the group's box,
     * run right to the player inventory's right edge where the group stops short
     * of it. A group's box is its slots' frames, so a narrow grid (a hopper, a
     * centered dispenser, a bundle showing one slot) would put the toolbar over
     * the container's title; widened, every container's Sort lines up with the
     * inventory toolbar's. A group wider than the inventory keeps its own edge,
     * and a screen without the inventory keeps the group's box.
     */
    static @Nullable Reference headerRow(@Nullable Reference group, @Nullable Reference inventory) {
        if (group == null || inventory == null) return group;
        int right = Math.max(group.leftPos() + group.imageWidth(), inventory.leftPos() + inventory.imageWidth());
        return new Reference(group.leftPos(), group.topPos(), right - group.leftPos(), group.imageHeight());
    }

    // The panels, for the self-checks to ask whether they show (placedBounds).
    private static Panel inventoryToolbar;
    private static final List<Panel> externalToolbars = new java.util.ArrayList<>();

    /** The toolbar above the player inventory. */
    public static Panel inventoryToolbar() {
        return inventoryToolbar;
    }

    /** The toolbars above the sortable containers, {@link CategoryPanels#MAX_SHOWN} per category in {@link #SORTABLE} order. */
    public static List<Panel> externalToolbars() {
        return List.copyOf(externalToolbars);
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

}
