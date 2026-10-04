package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;

import com.trevlar.menukit.api.element.Button;
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
 * Power Users toolbar — a right-of-grid vertical stack for opt-in
 * Power Users feature buttons. Separate panel from {@link Toolbar}
 * (which holds Default-feature buttons above the grid) per the
 * {@code IP features/power-users.md} button-placement convention:
 * "off the inventory grid, stacked to the right (in vanilla's
 * dead-space region)."
 *
 * <h3>Layout</h3>
 *
 * {@link OutsideRegion#RIGHT_ALIGN_TOP} + {@link PanelStyle#NONE} —
 * panel sits flush to the right of the player inventory's slot group,
 * top-aligned. Children stack vertically downward at explicit
 * panel-local y offsets (1 px button gap, same as the horizontal
 * toolbar).
 *
 * <h3>Dynamic stacking</h3>
 *
 * Each button keeps its own {@code visibleWhen} gate. Offsets are fixed
 * at build time, so a hidden button leaves its gap rather than the ones
 * below it moving up.
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
     * Top to bottom (Trev, 2026-10-03): Settings, then one on/off switch per
     * feature in the settings menu's tab order (Lock groups, Restock, Auto
     * Tool Switch, Column Cycler). Positions are fixed at build time, so a
     * hidden button leaves its gap.
     */
    private static List<PanelElement> buildChildren() {
        int step = FeatureToggleButtons.SIZE + BUTTON_GAP;
        return List.of(
                settingsButton(0, 0),
                FeatureToggleButtons.lockGroups(0, step),
                FeatureToggleButtons.restock(0, 2 * step),
                FeatureToggleButtons.autoToolSwitch(0, 3 * step),
                FeatureToggleButtons.columnCycler(0, 4 * step));
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
