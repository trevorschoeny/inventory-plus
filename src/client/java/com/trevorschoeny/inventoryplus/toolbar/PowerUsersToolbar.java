package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.slotkit.api.group.SlotGroupPanelAdapter;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The right column: a vertical stack beside the inventory grid, in
 * vanilla's dead space, holding Settings and the feature switches. A
 * separate panel from {@link Toolbar}, which holds the action buttons above
 * the grid.
 *
 * <h3>Layout</h3>
 *
 * {@link OutsideRegion#RIGHT_ALIGN_TOP} with {@link PanelStyle#NONE}: the
 * panel sits flush to the right of the player inventory's slot group,
 * top-aligned. Children stack downward with a 1 px gap, the same as the
 * horizontal toolbar.
 *
 * <h3>Dynamic stacking</h3>
 *
 * Each button keeps its own {@code visibleWhen} gate. One vertical Flow
 * lays them out, so a hidden button is skipped and the ones below move up,
 * and the column wraps into a second at the bottom of the inventory frame.
 *
 * <h3>Per-screen scope</h3>
 *
 * Anchored to {@link SlotGroupCategory#PLAYER_INVENTORY} like the
 * inventory toolbar. The panel's {@code visibleWhen} excludes Creative,
 * because the creative item picker isn't a real inventory.
 */
public final class PowerUsersToolbar {

    private PowerUsersToolbar() {}

    public static final int BUTTON_GAP = 1;

    /**
     * Builds the column when the client starts, after every mod's init (so a
     * companion's {@code addFeatureSwitch} is in) and before MenuKit freezes
     * declarations (its freeze runs in a later phase of the same event).
     */
    public static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> registerPanel());
    }

    private static void registerPanel() {
        Panel panel = Panel.builder("inventoryplus.toolbar.power-users")
                .elements(buildChildren())
                .style(PanelStyle.NONE)
                .position(PanelPosition.region(OutsideRegion.RIGHT_ALIGN_TOP))
                .visibleWhen(PowerUsersToolbar::isToolbarScope)
                .build();
        new SlotGroupPanelAdapter(panel)
                .on(SlotGroupCategory.PLAYER_INVENTORY);
    }

    /**
     * Top to bottom: Settings, then one on/off switch per
     * feature in the settings menu's tab order (Lock groups, Restock, Auto
     * Tool Switch, Column Cycler, Hotbar Cycler), then companions' (Inventory
     * Max: Pockets, Equipment Slots, Mend Anywhere). In one vertical Flow a
     * hidden button is skipped, and at the bottom of the inventory's frame
     * the column wraps into a second one to the right.
     */
    private static List<PanelElement> buildChildren() {
        Flow.Builder column = Flow.builder().vertical().gap(BUTTON_GAP, BUTTON_GAP)
                .add(settingsButton(0, 0))
                .add(FeatureToggleButtons.lockGroups(0, 0))
                .add(FeatureToggleButtons.restock(0, 0))
                .add(FeatureToggleButtons.autoToolSwitch(0, 0))
                .add(FeatureToggleButtons.columnCycler(0, 0))
                .add(FeatureToggleButtons.hotbarCycler(0, 0));
        for (PanelElement c : FeatureToggleButtons.contributed(0, 0, 0)) column.add(c);
        return List.of(column.build());
    }

    private static final Identifier SETTINGS_TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "settings_button");

    /** Opens the settings menu over the current screen, which it returns to on close. */
    private static Button settingsButton(int x, int y) {
        return Button.builder().sprite(SETTINGS_TEXTURE).at(x, y)
                .size(FeatureToggleButtons.SIZE, FeatureToggleButtons.SIZE)
                .onClick(() -> SettingsMenu.open(Minecraft.getInstance().gui.screen()))
                .tooltip(Component.literal("Settings"))
                .build();
    }

    private static boolean isToolbarScope() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof CreativeModeInventoryScreen);
    }
}
