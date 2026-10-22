package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.api.dialog.ConfirmDialog;
import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.Tabs;
import com.trevlar.menukit.api.panel.MKScreen;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.api.element.TextRole;
import com.trevorschoeny.inventoryplus.config.IPConfig;


import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Inventory Plus settings menu: full screen, a tab per
 * feature.
 *
 * <p><b>Two looks, one menu.</b> The player picks the look on
 * General ({@code IPConfig.settingsOptionsLook}), and the menu is made as that
 * kind of MenuKit screen, fixed for its life:
 * <ul>
 *   <li>{@link ScreenKind#DEFAULT}, the default: the pause-menu look. The name is
 *       the screen's title, centered in white. The tabs run down a sidebar, the
 *       page sits in the list band over the blurred world, and Done is at the
 *       bottom.</li>
 *   <li>{@link ScreenKind#PANEL}: the inventory look the menu had before. A
 *       gray panel from the top of the screen with the name bar, the tabs down
 *       a sidebar, Done below it.</li>
 * </ul>
 * Every tab and body is the same on both: their text is colored by role
 * ({@code TextRole}), so the kind decides how it reads. Changing the look
 * reopens the menu on General in the other kind ({@link #reopen}).
 *
 * <p>The menu is named {@link #MENU}, so other mods add tabs to it with
 * MenuKit's {@code Tabs.addTo}. Inventory Plus sets the order of its own tabs.
 *
 * <p>Opened from the dot button on the inventory toolbar, the Open settings
 * key, Mod Menu, and Ctrl-click on any feature's inventory button. Closing
 * returns to the screen it was opened from.
 *
 * <p>Controls with a real setting behind them work, and the rest are grayed
 * placeholders ({@link SettingsBody}).
 */
public final class SettingsMenu extends MKScreen {

    /** The menu's name, for other mods' {@code Tabs.addTo}. */
    public static final Identifier MENU = Identifier.fromNamespaceAndPath("inventoryplus", "settings");

    // Tab ids. The inventory buttons open their feature's tab on Ctrl-click
    // (Cmd on a Mac), so the ids they need are public.

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
    public static final String TOOLTIPS = "tooltips";

    /**
     * The open tab, kept across reopens for the rest of the session. MenuKit's
     * Tabs reads it every frame and writes the tab the player picks.
     */
    private static String selectedTab = GENERAL;

    /**
     * Each tab's body scroll, by tab id, for the session (MenuKit's
     * Tabs.bodyScroll). A rebuild (a reset, or a tab switch after the groups changed) opens
     * the body where the player was, not at the top.
     */
    private static final Map<String, Double> bodyScroll = new HashMap<>();

    /** The screen the menu returns to on close, kept so a rebuild returns there too. */
    private final @Nullable Screen parent;

    private SettingsMenu(@Nullable Screen parent, ScreenKind kind) {
        super(title(), kind, panels(kind));
        this.parent = parent;
        // The inventory look has its own name bar inside the panel, so no title
        // band. The Default look's title is the name.
        if (kind == ScreenKind.PANEL) hideTitle();
        // Escape goes back to wherever the menu was opened from: the inventory,
        // Mod Menu, or the game. A container stays open underneath, because
        // swapping screens does not close it, and returning reuses the same screen.
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
     * For an inventory button's click handler: on Ctrl-click (Cmd on a Mac,
     * where a real Ctrl-click arrives as a right-click) opens the menu on
     * {@code tabId} and answers true, and the button does nothing else.
     * Otherwise answers false and the click is the button's.
     */
    public static boolean ctrlClickOpens(String tabId) {
        if (!Click.of(Click.LEFT).ctrl()) return false;
        openOn(tabId);
        return true;
    }

    /**
     * An inventory button's tooltip: {@code tooltip}, or "Open settings for
     * {@code feature}" while Ctrl (Cmd on a Mac) is held, which is what a
     * click would then do. "Settings" with Compact tooltips on.
     */
    public static Supplier<Component> tooltip(String feature, Supplier<Component> tooltip) {
        Component open = Component.literal("Open settings for " + feature);
        Component compact = Component.literal("Settings");
        return () -> !Click.of(Click.LEFT).ctrl() ? tooltip.get()
                : IPConfig.compactTooltips() ? compact : open;
    }

    /**
     * Builds the menu again on the same tab, over the same parent. The tab
     * bodies are built once per menu; the lock groups list updates itself, so
     * what rebuilds is a reset, and a tab switch after the groups changed (so
     * Reach lists them as they are). Deferred to the next tick, so a button
     * never swaps the screen out from under its own click.
     */
    static void rebuild() {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.gui.screen() instanceof SettingsMenu menu) mc.gui.setScreen(new SettingsMenu(menu.parent, kind()));
        });
    }

    /**
     * Opens the menu again on General, in the look the setting now asks for:
     * after the look checkbox changes, and after a reset that may have put it
     * back. Same parent, so Escape still returns where the player came from.
     */
    static void reopen() {
        selectedTab = GENERAL;
        rebuild();
    }

    /** The kind of screen the look setting asks for. */
    private static ScreenKind kind() {
        return IPConfig.settingsOptionsLook() ? ScreenKind.DEFAULT : ScreenKind.PANEL;
    }

    /** The lock groups the open menu's tab bodies were built with. */
    private static List<com.trevorschoeny.inventoryplus.lockgroups.LockGroup> builtWithGroups = List.of();

    /**
     * Switches tab. The Lock groups tab adds and deletes groups in place, but
     * Reach lists the groups too and its body is built once per menu, so
     * leaving for another tab after the groups changed rebuilds the menu on
     * that tab.
     */
    private static void selectTab(String id) {
        selectedTab = id;
        if (!com.trevorschoeny.inventoryplus.lockgroups.Reach.groups().equals(builtWithGroups)) rebuild();
    }

    /** The menu's panels for {@code kind}: the confirm panel is the same on both. */
    private static List<Panel> panels(ScreenKind kind) {
        // Both looks end in Done at the bottom. The Panel look has no Back to
        // game button, so its menu panel runs to the top.
        return List.of(main(kind), donePanel(), confirmPanel());
    }

    // Resets other mods add

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

    // Confirms
    //
    // One MenuKit ConfirmDialog for every confirm the menu asks (the resets).
    // Its title and body read the pending confirm while it shows (MenuKit
    // 7.3.0), so a count it states is the count when it opened. Deleting a
    // lock group asks through the group list's own confirm.

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
        return ConfirmDialog.builder()
                .id("inventoryplus:settings_confirm")
                .title(() -> Component.literal(pending == null ? "" : pending.title()))
                .body(() -> Component.literal(pending == null ? "" : pending.body()))
                .onCancel(() -> pending = null)
                .onConfirm(() -> {
                    Confirm c = pending;
                    pending = null;
                    if (c != null) c.action().run();
                })
                .build()
                .visibleWhen(() -> pending != null);
    }

    /** The menu as a screen, for Mod Menu's factory. */
    public static Screen create(@Nullable Screen parent) {
        return new SettingsMenu(parent, kind());
    }

    /** The menu as a screen on {@code tabId}, for a companion's Mod Menu factory. */
    public static Screen create(@Nullable Screen parent, String tabId) {
        selectedTab = tabId;
        return create(parent);
    }

    /** The menu's name. */
    static String modName() {
        return "Inventory Plus";
    }

    /**
     * The inventory look's name: one bar across the whole menu panel, above the sidebar and
     * the body, centered on the panel: the name at twice
     * size, bold, between two spacers that share the row's leftover evenly.
     * The menu panel's first row; the Tabs are the second (see {@link #main}).
     */
    static PanelElement nameBar() {
        return Flow.builder().at(0, 0).gap(0, 0)
                .add(Flow.spacer())
                .add(TextLabel.builder()
                        .text(Component.literal(modName()).withStyle(ChatFormatting.BOLD))
                        .role(TextRole.HEADING).scale(2f).build())
                .add(Flow.spacer())
                .build();
    }

    /** The screen's title: the menu's name. */
    private static Component title() {
        return Component.literal(modName());
    }

    /**
     * Done, at the bottom center at vanilla's width, the way every Options
     * screen ends, on both looks. {@code onClose} runs the return action, so Done
     * and Escape agree.
     */
    private static Panel donePanel() {
        return Panel.builder("inventoryplus:settings_done")
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.region(OutsideRegion.BOTTOM_CENTER)))
                .add(Button.builder().label(CommonComponents.GUI_DONE).size(200, 20)
                        .onClick(() -> Minecraft.getInstance().gui.screen().onClose()).build())
                .build();
    }

    /** The name bar's height (the name at twice size), and the gap under it. */
    private static final int NAME_BAR_HEIGHT = 18;
    private static final int NAME_BAR_GAP = 6;

    /**
     * The menu panel. Default look: no box, so the blurred world shows, and the
     * Tabs alone, as a sidebar (Create World's tab bar turned on its side).
     * Inventory look: the gray panel with two rows, the name bar, then the
     * Tabs as a sidebar below it.
     *
     * <p>The Tabs set no size, so they fill: the panel hands a filling element
     * the viewport height less its own y (Panel's height pass), which puts
     * their bottom on the panel's. Other mods' tabs come in through
     * {@code Tabs.addTo}, so this layout is the whole of it.
     */
    private static Panel main(ScreenKind kind) {
        builtWithGroups = com.trevorschoeny.inventoryplus.lockgroups.Reach.groups();
        boolean options = kind == ScreenKind.DEFAULT;
        Tabs.Builder builder = Tabs.builder().menu(MENU);
        // Both looks put the tabs in a sidebar. On the Default look MenuKit
        // draws it as Create World's tab bar on its side.
        builder.mode(Tabs.Mode.SIDEBAR).align(Tabs.Align.LEFT);
        if (!options) builder.at(0, NAME_BAR_HEIGHT + NAME_BAR_GAP);
        Tabs tabs = builder
                .state(() -> selectedTab, SettingsMenu::selectTab)
                .bodyScroll(bodyScroll::get, bodyScroll::put)
                .tab(tab(GENERAL, "General").body(SettingsTabs::general))
                // Tooltips right under General.
                .tab(tab(TOOLTIPS, "Tooltips").body(SettingsTabs::tooltips))
                .tab(tab(REACH, "Reach").body(SettingsTabs::reach))
                .tab(tab(LOCK_GROUPS, "Lock groups").body(SettingsTabs::lockGroups))
                .tab(tab(SORT, "Sort").body(SettingsTabs::sort))
                .tab(tab(MOVE_MATCHING, "Move Matching").body(SettingsTabs::moveMatching))
                .tab(tab(RESTOCK, "Restock").body(SettingsTabs::restock))
                .tab(tab(AUTO_TOOL_SWITCH, "Auto Tool Switch").body(SettingsTabs::autoToolSwitch))
                .tab(tab(COLUMN_CYCLER, "Column Cycler").body(SettingsTabs::columnCycler))
                .tab(tab(HOTBAR_CYCLER, "Hotbar Cycler").body(SettingsTabs::hotbarCycler))
                .build();
        Panel.Builder panel = Panel.builder("inventoryplus:settings")
                .style(options ? PanelStyle.NONE : PanelStyle.RAISED)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.main()));
        if (!options) panel.add(nameBar());
        return panel.add(tabs).build();
    }

    private static Tabs.TabSpec tab(String id, String label) {
        return Tabs.tab(id).label(Component.literal(label));
    }
}
