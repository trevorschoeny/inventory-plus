package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.Tabs;
import com.trevlar.menukit.api.panel.MKScreen;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.List;

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
 * key, Mod Menu, and Ctrl+click on any feature's inventory button. Closing
 * returns to the screen it was opened from.
 *
 * <p>Half wired: controls with a real setting behind them work; the rest are
 * greyed placeholders ({@link SettingsBody}).
 */
public final class SettingsMenu extends MKScreen {

    /** The menu's name, for other mods' {@code Tabs.addTo}. */
    public static final Identifier MENU = Identifier.fromNamespaceAndPath("inventoryplus", "settings");

    // Tab ids. The inventory buttons open their feature's tab on Ctrl+click
    // (Cmd on a Mac), so the ids they need are public. Inventory Max's tabs are
    // "inventorymax:pockets", "inventorymax:equipment_slots" and
    // "inventorymax:mend_anywhere".

    /** The tab the menu opens on the first time. */
    public static final String GENERAL = "general";
    public static final String REACH = "reach";
    public static final String LOCK_GROUPS = "lock_groups";
    public static final String SORT = "sort";
    public static final String MOVE_MATCHING = "move_matching";
    public static final String RESTOCK = "restock";
    public static final String AUTO_TOOL_SWITCH = "auto_tool_switch";
    public static final String COLUMN_CYCLER = "column_cycler";
    public static final String HOTBAR_CYCLER = "hotbar_cycler";
    public static final String ITEM_TIPS = "item_tips";

    /**
     * The open tab, kept across reopens for the rest of the session. MenuKit's
     * Tabs reads it every frame and writes the tab the player picks.
     */
    private static String selectedTab = GENERAL;

    /** The screen the menu returns to on close, kept so a rebuild returns there too. */
    private final @Nullable Screen parent;

    private SettingsMenu(@Nullable Screen parent) {
        super(title(), List.of(main(), confirmPanel()));
        this.parent = parent;
        // No bar across the top (Trev, 2026-09-27): Back to game heads the tab
        // column instead, and the body starts at the top.
        hideTitle();
        // Escape goes back to wherever the menu was opened from: the inventory,
        // Mod Menu, or the game. A container stays open underneath, because
        // swapping screens does not close it; returning reuses the same screen.
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    /** Opens the menu over {@code parent}, which it returns to on close. */
    public static void open(@Nullable Screen parent) {
        Minecraft.getInstance().gui.setScreen(create(parent));
    }

    /** Opens the menu on {@code tabId}, over the screen that is open now. */
    public static void openOn(String tabId) {
        selectedTab = tabId;
        open(Minecraft.getInstance().gui.screen());
    }

    /**
     * For an inventory button's click handler: on Ctrl+click (Cmd on a Mac,
     * where a real Ctrl+click arrives as a right-click) opens the menu on
     * {@code tabId} and answers true, and the button does nothing else.
     * Otherwise answers false and the click is the button's.
     */
    public static boolean ctrlClickOpens(String tabId) {
        if (!Click.of(Click.LEFT).ctrl()) return false;
        openOn(tabId);
        return true;
    }

    /**
     * Builds the menu again on the same tab, over the same parent. The tab
     * bodies are built once per menu, so adding, renaming, recolouring or
     * deleting a lock group (anything that changes which rows exist or what a
     * section's header says) rebuilds. Deferred to the next tick, so a button
     * never swaps the screen out from under its own click.
     */
    static void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui.screen() instanceof SettingsMenu menu) mc.gui.setScreen(new SettingsMenu(menu.parent));
        });
    }

    // ── Resets other mods add (Inventory Max) ───────────────────────────

    private static final List<Runnable> EXTERNAL_RESETS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * A reset General's Reset everything also runs: a mod that adds tabs
     * resets its own settings through this. Inventory Plus cannot import it.
     */
    public static void registerReset(Runnable reset) {
        EXTERNAL_RESETS.add(reset);
    }

    static void runExternalResets() {
        for (Runnable r : EXTERNAL_RESETS) r.run();
    }

    // ── Confirms ────────────────────────────────────────────────────────
    //
    // One modal panel for every confirm the menu asks (the resets, deleting a
    // group). Its text reads the pending confirm every frame, so a count it
    // states is the count when it opened. MenuKit's ConfirmDialog fixes its
    // text when built, and a count has to be taken when the button is pressed.

    private record Confirm(String title, String body, Runnable action) {}

    private static @Nullable Confirm pending;

    /**
     * Asks before {@code action}: a title, one line of what it will do, then
     * Cancel and Confirm. Escape cancels.
     */
    public static void confirm(String title, String body, Runnable action) {
        pending = new Confirm(title, body, action);
    }

    private static Panel confirmPanel() {
        List<PanelElement> elements = List.of(
                TextLabel.builder().at(0, 0)
                        .text(() -> Component.literal(pending == null ? "" : pending.title()))
                        .color(TextLabel.COLOR_DARK).build(),
                TextLabel.builder().at(0, 14)
                        .text(() -> Component.literal(pending == null ? "" : pending.body()))
                        .color(0xFF555555).build(),
                Button.builder().at(0, 32).size(70, 16).label(Component.literal("Cancel"))
                        .onClick(() -> pending = null).build(),
                Button.builder().at(74, 32).size(70, 16).label(Component.literal("Confirm"))
                        .onClick(() -> {
                            Confirm c = pending;
                            pending = null;
                            if (c != null) c.action().run();
                        }).build());
        return Panel.builder("inventoryplus:settings_confirm")
                .elements(elements)
                .style(PanelStyle.RAISED)
                .position(PanelPosition.center())
                .build()
                .modal()
                .onEscape(() -> pending = null)
                .visibleWhen(() -> pending != null);
    }

    /** The menu as a screen, for Mod Menu's factory. */
    public static Screen create(@Nullable Screen parent) {
        return new SettingsMenu(parent);
    }

    /** The menu as a screen on {@code tabId}, for a companion's Mod Menu factory. */
    public static Screen create(@Nullable Screen parent, String tabId) {
        selectedTab = tabId;
        return create(parent);
    }

    /**
     * The menu's name, which heads every tab: "Inventory Plus Max" with
     * Inventory Max installed, "Inventory Plus" without (Trev, 2026-09-30).
     */
    static String modName() {
        return maxInstalled() ? "Inventory Plus Max" : "Inventory Plus";
    }

    private static boolean maxInstalled() {
        return FabricLoader.getInstance().isModLoaded("inventorymax");
    }

    /** "Inventory Plus", or both names when Inventory Max is here to be configured too. */
    private static Component title() {
        return Component.literal(modName());
    }

    private static Panel main() {
        boolean max = maxInstalled();
        Tabs tabs = Tabs.builder()
                .menu(MENU)
                .mode(Tabs.Mode.SIDEBAR)
                .align(Tabs.Align.LEFT)
                .state(() -> selectedTab, id -> selectedTab = id)
                // Back to game tops the left column, above the tabs; it does what Escape does.
                .sidebarHeader(Button.builder().label(Component.literal("Back to game")).size(0, 16)
                        .onClick(() -> Minecraft.getInstance().gui.screen().onClose()).build())
                .tab(tab(GENERAL, "General").body(() -> SettingsTabs.general(max)))
                .tab(tab(REACH, "Reach").body(SettingsTabs::reach))
                .tab(tab(LOCK_GROUPS, "Lock groups").body(() -> SettingsTabs.lockGroups(max)))
                .tab(tab(SORT, "Sort").body(SettingsTabs::sort))
                .tab(tab(MOVE_MATCHING, "Move Matching").body(SettingsTabs::moveMatching))
                .tab(tab(RESTOCK, "Restock").body(SettingsTabs::restock))
                .tab(tab(AUTO_TOOL_SWITCH, "Auto Tool Switch").body(SettingsTabs::autoToolSwitch))
                .tab(tab(COLUMN_CYCLER, "Column Cycler").body(SettingsTabs::columnCycler))
                .tab(tab(HOTBAR_CYCLER, "Hotbar Cycler").body(SettingsTabs::hotbarCycler))
                .tab(tab(ITEM_TIPS, "Item Tips").body(SettingsTabs::itemTips))
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
