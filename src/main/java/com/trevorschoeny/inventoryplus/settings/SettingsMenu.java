package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelPosition;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.core.Tabs;
import com.trevlar.menukit.screen.MKScreen;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Inventory Plus and Inventory Max settings menu: full screen, a sidebar
 * of tabs down the left, a tab per feature (plan: Leadership drive,
 * {@code mods/inventory-plus/plans/settings-menu.md}).
 *
 * <p>The menu is named {@link #MENU}, so other mods add tabs to it with
 * MenuKit's {@code Tabs.addTo}. Inventory Plus sets the order, and holds the
 * places of Inventory Max's three tabs with stand-ins that Inventory Max
 * replaces when it is installed.
 *
 * <p>Opened from the dot button on the inventory toolbar, the Open settings
 * key, and Mod Menu. Closing returns to the screen it was opened from.
 *
 * <p>Scaffold stage: the tab bodies are placeholders ({@link SettingsTabs}).
 */
public final class SettingsMenu extends MKScreen {

    /** The menu's name, for other mods' {@code Tabs.addTo}. */
    public static final Identifier MENU = Identifier.fromNamespaceAndPath("inventoryplus", "settings");

    /** The tab the menu opens on the first time. */
    static final String GENERAL = "general";
    static final String LOCKS = "locks";

    /**
     * The open tab, kept across reopens for the rest of the session. MenuKit's
     * Tabs reads it every frame and writes the tab the player picks.
     */
    private static String selectedTab = GENERAL;

    private SettingsMenu(@Nullable Screen parent) {
        super(title(), List.of(main()));
        // Escape goes back to wherever the menu was opened from: the inventory,
        // Mod Menu, or the game. A container stays open underneath, because
        // swapping screens does not close it; returning reuses the same screen.
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    /** Opens the menu over {@code parent}, which it returns to on close. */
    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(create(parent));
    }

    /** The menu as a screen, for Mod Menu's factory. */
    public static Screen create(@Nullable Screen parent) {
        return new SettingsMenu(parent);
    }

    private static boolean maxInstalled() {
        return FabricLoader.getInstance().isModLoaded("inventorymax");
    }

    /** "Inventory Plus", or both names when Inventory Max is here to be configured too. */
    private static Component title() {
        return Component.literal(maxInstalled() ? "Inventory Plus and Inventory Max" : "Inventory Plus");
    }

    private static Panel main() {
        boolean max = maxInstalled();
        Consumer<String> openTab = id -> selectedTab = id;
        Tabs tabs = Tabs.builder()
                .menu(MENU)
                .mode(Tabs.Mode.SIDEBAR)
                .align(Tabs.Align.LEFT)
                .selected(() -> selectedTab, id -> selectedTab = id)
                .tab(tab(GENERAL, "General").body(() -> SettingsTabs.general(max)))
                .tab(tab("moving_items", "Moving Items").body(SettingsTabs::movingItems))
                .tab(tab(LOCKS, "Locks").body(() -> SettingsTabs.locks(max)))
                .tab(tab("sort", "Sort").body(() -> SettingsTabs.sort(openTab)))
                .tab(tab("move_matching", "Move Matching").body(() -> SettingsTabs.moveMatching(openTab)))
                .tab(tab("restock", "Restock").body(() -> SettingsTabs.restock(openTab)))
                .tab(tab("auto_tool_switch", "Auto Tool Switch").body(() -> SettingsTabs.autoToolSwitch(openTab)))
                .tab(tab("column_cycler", "Column Cycler").body(() -> SettingsTabs.columnCycler(openTab)))
                .tab(tab("hotbar_cycler", "Hotbar Cycler").body(() -> SettingsTabs.hotbarCycler(openTab)))
                // Inventory Max's places. Its real tabs, added under these ids,
                // take them over; without it these show dimmed, greyed bodies.
                .tab(tab("inventorymax:pockets", "Pockets").standIn()
                        .body(SettingsTabs::pocketsStandIn))
                .tab(tab("inventorymax:equipment_slots", "Equipment Slots").standIn()
                        .body(SettingsTabs::equipmentSlotsStandIn))
                .tab(tab("inventorymax:mend_anywhere", "Mend Anywhere").standIn()
                        .body(SettingsTabs::mendAnywhereStandIn))
                .build();
        return Panel.builder("inventoryplus:settings")
                .style(PanelStyle.RAISED)
                .position(PanelPosition.main())
                .add(tabs)
                .build();
    }

    private static Tabs.TabSpec tab(String id, String label) {
        return Tabs.tab(id).label(Component.literal(label));
    }
}
