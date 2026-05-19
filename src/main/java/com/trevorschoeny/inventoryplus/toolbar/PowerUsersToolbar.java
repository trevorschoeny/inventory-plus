package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerButtons;

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
 * Power Users toolbar — a right-of-grid vertical stack for opt-in
 * Power Users feature buttons. Separate panel from {@link Toolbar}
 * (which holds Default-feature buttons above the grid) per the
 * {@code IP features/power-users.md} button-placement convention:
 * "off the inventory grid, stacked to the right (in vanilla's
 * dead-space region)."
 *
 * <h3>Layout</h3>
 *
 * {@link SlotGroupRegion#RIGHT_ALIGN_TOP} + {@link PanelStyle#NONE} —
 * panel sits flush to the right of the player inventory's slot group,
 * top-aligned. Children stack vertically downward at explicit
 * panel-local y offsets (1 px button gap, same as the horizontal
 * toolbar).
 *
 * <h3>Dynamic stacking</h3>
 *
 * Each PU button registers itself via its own factory + per-button
 * {@code .showWhen} gate. Buttons whose feature is disabled hide
 * naturally; the panel's vertical layout closes the gap because the
 * y-offset arithmetic is done at build time and the hide-when-empty
 * behavior is automatic. (When more PU features ship, this approach
 * may need to graduate to a stack-with-auto-reflow if hide gaps
 * appear — for the first feature alone, explicit offsets suffice.)
 *
 * <h3>Per-screen scope</h3>
 *
 * Anchored to {@link SlotGroupCategory#PLAYER_INVENTORY} like the
 * default inventory toolbar. Panel-level {@code .showWhen} excludes
 * Creative (the creative item picker isn't a real inventory).
 */
public final class PowerUsersToolbar {

    private PowerUsersToolbar() {}

    /** 1-px gap between adjacent buttons in the vertical stack. */
    public static final int BUTTON_GAP = 1;

    public static void register() {
        Panel panel = new Panel(
                "inventoryplus.toolbar.power-users",
                buildChildren(),
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);
        panel.showWhen(PowerUsersToolbar::isToolbarScope);
        new SlotGroupPanelAdapter(panel, SlotGroupRegion.RIGHT_ALIGN_TOP)
                .on(SlotGroupCategory.PLAYER_INVENTORY);
    }

    private static List<PanelElement> buildChildren() {
        int y = 0;
        // Column Cycler edit toggle — first PU button. Per-button
        // .showWhen handles columnCyclerEnabled + columnCyclerShowButton.
        PanelElement columnCyclerEdit = ColumnCyclerButtons.toolbarToggle(0, y);
        y += ColumnCyclerButtons.SIZE + BUTTON_GAP;
        return List.of(columnCyclerEdit);
    }

    private static boolean isToolbarScope() {
        Screen screen = Minecraft.getInstance().screen;
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof CreativeModeInventoryScreen);
    }
}
