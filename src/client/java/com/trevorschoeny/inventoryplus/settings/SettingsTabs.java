package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.settings.SettingsBody.Bool;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Toggle;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;


import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;


import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * The body of every settings tab.
 *
 * <p>Every tab has one frame: the title, a description,
 * Reset to Defaults, the feature's on/off checkbox (not on General), a
 * line, then its settings one per line, every one of them grayed while the
 * feature is off. Done sits below the menu panel, not in a tab.
 *
 * <p>Controls with a real setting behind them read and write it live. The
 * rest are grayed placeholders showing their planned default (see
 * {@link SettingsBody}).
 */
final class SettingsTabs {

    private SettingsTabs() {}

    // Resets
    //
    // Every tab's Reset to Defaults asks first, then puts that tab's settings and keys back. General's Reset everything
    // runs every tab's, and other mods' through the hook they register.

    /** Opens a confirm for resetting tab {@code title}, then runs {@code reset}. */
    private static Runnable confirmReset(String title, Runnable reset) {
        return () -> SettingsMenu.confirm("Reset " + title + " to defaults?",
                "Every setting and key on this tab goes back to how a fresh install has it.", reset);
    }

    /** Puts {@code keys} back to their default binding, through Keybindery as the key buttons do. */
    private static void resetKeys(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            KeybinderyAPI.getInstance().setChord(key, IChordKeyMapping.defaultChord(key));
        }
    }

    /** General's own settings back to default. Only Reset everything runs it. */
    private static void resetGeneral() {
        IPConfig.reset("settingsOptionsLook", "compactTooltips");
        resetKeys(IPKeybinds.OPEN_SETTINGS);
        // The look may have just gone back to the Default look, so reopen in it.
        SettingsMenu.reopen();
    }

    private static void resetSort() {
        IPConfig.reset("sortEnabled", "sortShowButton");
        resetKeys(IPKeybinds.SORT);
    }

    private static void resetTooltips() {
        IPConfig.reset("tooltipsEnabled", "tooltipDurability", "tooltipFood", "tooltipFoodEffects",
                "tooltipEnchantmentDescriptions", "tooltipSortEnchantments", "tooltipToolStats",
                "tooltipCompare", "tooltipCondenseTrims", "tooltipModName", "hideTooltipsOnKey", "tooltipKeyShows",
                "tooltipDock", "tooltipHideUntilMouseMoves");
        resetKeys(IPKeybinds.HIDE_TOOLTIPS);
    }

    private static void resetMoveMatching() {
        IPConfig.reset("moveMatchingEnabled", "moveMatchingShowButtons");
        resetKeys(IPKeybinds.MOVE_MATCHING_OUT, IPKeybinds.MOVE_MATCHING_IN);
    }

    private static void resetRestock() {
        IPConfig.reset("restockEnabled", "restockShowButton", "autoRestockArmor", "autoRestockArmorBeforeBreak",
                "autoRestockTool", "autoRestockToolBeforeBreak", "autoRestockItem", "autoRestockShulker",
                "autoRestockShulkerAmmo", "autoRestockBeforeBreakThreshold", "restockFromShulkers");
    }

    private static void resetAutoToolSwitch() {
        IPConfig.reset("autoToolSwitchEnabled", "autoToolSwitchShowButton", "autoToolSwitchReturnAuto",
                "autoToolSwitchReturnKey",
                "autoToolSwitchReturnCooldownSeconds", "autoToolSwitchWeapons", "autoToolSwitchAllMobs",
                "autoToolSwitchWeaponPreference");
        resetKeys(IPKeybinds.AUTO_SWITCH_RETURN);
    }

    private static void resetColumnCycler() {
        IPConfig.reset("columnCyclerEnabled", "columnCyclerShowButton", "columnCyclerScrollToCycle",
                "columnCyclerHudMode");
        resetKeys(IPKeybinds.CYCLE_SLOT, IPKeybinds.CYCLE_FORWARD, IPKeybinds.CYCLE_BACKWARD);
    }

    private static void resetHotbarCycler() {
        IPConfig.reset("hotbarCyclerEnabled", "hotbarCyclerShowButton", "hotbarCyclerShowButtons",
                "hotbarCyclerScrollToCycle");
        resetKeys(IPKeybinds.HOTBAR_CYCLE_FORWARD, IPKeybinds.HOTBAR_CYCLE_BACKWARD);
    }

    /** Locks' reset: the switch, the button and the key, and every lock kept on this computer, in every world. */
    private static void resetLocks() {
        LockedSlots.clearAll();
        IPConfig.reset("lockGroupsEnabled", "lockedSlotsShowButton");
        resetKeys(IPKeybinds.LOCK_SLOT);
    }

    /** "12 locks in 3 worlds": every lock kept on this computer. */
    private static String lockCount() {
        Set<String> worlds = new HashSet<>();
        int n = LockedSlots.count(worlds);
        return n + (n == 1 ? " lock" : " locks") + " in " + worlds.size() + (worlds.size() == 1 ? " world" : " worlds");
    }

    // General

    static List<PanelElement> general() {
        SettingsBody b = new SettingsBody()
                .frame("General", "Settings for the whole menu. Grayed settings aren't built yet.");
        // No category header on the first settings.
        b.key(IPKeybinds.OPEN_SETTINGS)
                // The inventory buttons' tooltips in one short line each, to
                // take less room. Off by default.
                .checkbox("Compact tooltips: short, one line on inventory buttons",
                        Bool.of(IPConfig::compactTooltips, IPConfig::setCompactTooltips));
        // A toggle between the two looks, named for the one in use: Default
        // (Minecraft's own screens) or Panel (a container-style panel); MenuKit's
        // convention for a mod's look switch (2026-10-17), replacing the old
        // "Classic"/"Panel" cycle button. The menu is made as one kind of
        // screen or the other, so a click reopens it on General in the new
        // look, which also rebuilds this toggle with the new label.
        b.row(y -> List.of(Toggle.builder().at(0, y).size(0, 16)
                .label(() -> Component.literal("Look: " + (IPConfig.settingsOptionsLook() ? "Default" : "Panel")))
                .state(IPConfig::settingsOptionsLook, v -> {
                    IPConfig.setSettingsOptionsLook(v);
                    SettingsMenu.reopen();
                })
                .build()), 20);
        // Pick block's carried containers (IPConfig.pickFrom*) have no control
        // while their Reach rows are gone; their GUI is to be settled with Trev.
        b.heading("Presets")
                .actions("Save or load a file", "Save to file", "Load from file")
                .actions("Share by pasting", "Copy to clipboard", "Paste from clipboard");
        // Its own category: it resets every tab.
        b.heading("Reset")
                .actions(new SettingsBody.Action("Reset everything", () -> SettingsMenu.confirm("Reset everything?",
                        "Every setting in this menu goes back to its default, and "
                                + lockCount() + " are removed.",
                        SettingsTabs::resetEverything)));
        return b.build();
    }

    /** General's Reset everything: every tab's reset, other mods' tabs included. */
    private static void resetEverything() {
        IPConfig.resetAll();
        resetGeneral();
        resetSort();
        resetMoveMatching();
        resetAutoToolSwitch();
        resetColumnCycler();
        resetHotbarCycler();
        resetLocks();
        SettingsMenu.runExternalResets();
    }

    // Locks

    /** The switch that pauses the player's own locks, and the button. */
    static List<PanelElement> locks() {
        return new SettingsBody()
                .frame("Locks", "Lock slots so moves leave them alone. Press the lock key on a slot "
                        + "to lock it or take its lock off.",
                        // Pauses the player's own locks without deleting any: nothing blocked, nothing drawn.
                        Bool.of(IPConfig::locksEnabled, IPConfig::setLocksEnabled),
                        () -> SettingsMenu.confirm("Reset Locks to defaults?",
                                lockCount() + " kept on this computer are removed.", SettingsTabs::resetLocks))
                .checkbox("Show the lock button", Bool.of(IPConfig::lockedSlotsShowButton, IPConfig::setLockedSlotsShowButton))
                .build();
    }

    // Feature tabs
    //
    // The frame, the on/off switch, then one setting per line: the button
    // toggle, each key, each option.

    static List<PanelElement> sort() {
        return new SettingsBody()
                .frame("Sort", "Sorts your inventory or the open container with one click. "
                        + "Right-click the Sort button to change how it sorts.",
                        Bool.of(IPConfig::sortEnabled, IPConfig::setSortEnabled),
                        confirmReset("Sort", SettingsTabs::resetSort))
                .checkbox("Show the Sort button", Bool.of(IPConfig::sortShowButton, IPConfig::setSortShowButton))
                .key(IPKeybinds.SORT, Component.translatable(IPKeybinds.SORT.getName()),
                        Component.literal("Sorts the container under your mouse. With only your inventory "
                                + "open, it sorts your inventory wherever your mouse is."))
                .build();
    }

    /**
     * Tooltips (formerly Item Tips): everything Inventory Plus
     * does to tooltips. Off, Inventory Plus leaves every tooltip alone.
     */
    static List<PanelElement> tooltips() {
        return new SettingsBody()
                .frame("Tooltips", "Changes Inventory Plus makes to tooltips: extra lines on items, "
                        + "and where they sit on screen.",
                        Bool.of(IPConfig::tooltipsEnabled, IPConfig::setTooltipsEnabled),
                        confirmReset("Tooltips", SettingsTabs::resetTooltips))
                // Display first, then Item Details. Scrolling a long tooltip and
                // keeping it on screen have no switch here, because they are
                // always on.
                .heading("Display")
                // MenuKit 6.2.0's avoidCoverWhen: a tooltip vanilla would place
                // beside the cursor docks at the bottom center of the screen, or
                // the top center when the cursor would be inside it there.
                .checkbox("Dock tooltips at top or bottom",
                        Bool.of(IPConfig::tooltipDock, IPConfig::setTooltipDock))
                // When a screen opens, the slot under the cursor shows no tooltip
                // until the mouse moves (tooltips/Tooltips).
                .checkbox("Hide tooltips until the mouse moves",
                        Bool.of(IPConfig::tooltipHideUntilMouseMoves, IPConfig::setTooltipHideUntilMouseMoves))
                // Hidden by MenuKit's tooltip seam, through a predicate Inventory
                // Plus registers at client init (tooltips/Tooltips).
                .checkbox("Hold a key to hide tooltips",
                        Bool.of(IPConfig::hideTooltipsOnKey, IPConfig::setHideTooltipsOnKey))
                .key(IPKeybinds.HIDE_TOOLTIPS, Component.translatable(IPKeybinds.HIDE_TOOLTIPS.getName()), null,
                        () -> !IPConfig.hideTooltipsOnKey())
                // Reverses the key: item tooltips hidden until it is held.
                .subCheckbox("Instead, hide item tooltips and show them while the key is held",
                        new Bool(IPConfig::tooltipKeyShows, IPConfig::setTooltipKeyShows,
                                () -> !IPConfig.hideTooltipsOnKey()))
                // One checkbox per provider in tooltips/, in the order they apply.
                .heading("Item Details")
                .checkbox("Durability", Bool.of(IPConfig::tooltipDurability, IPConfig::setTooltipDurability))
                .checkbox("Food: nutrition and saturation", Bool.of(IPConfig::tooltipFood, IPConfig::setTooltipFood))
                .checkbox("Food effects", Bool.of(IPConfig::tooltipFoodEffects, IPConfig::setTooltipFoodEffects))
                .checkbox("Enchantment descriptions", Bool.of(IPConfig::tooltipEnchantmentDescriptions,
                        IPConfig::setTooltipEnchantmentDescriptions))
                .checkbox("Sort enchantments alphabetically", Bool.of(IPConfig::tooltipSortEnchantments,
                        IPConfig::setTooltipSortEnchantments))
                .checkbox("Tool stats: mining speed, tier and enchantability",
                        Bool.of(IPConfig::tooltipToolStats, IPConfig::setTooltipToolStats))
                .checkbox("Compare with what you're wearing or holding",
                        Bool.of(IPConfig::tooltipCompare, IPConfig::setTooltipCompare))
                .checkbox("Condense armor trim lines", Bool.of(IPConfig::tooltipCondenseTrims,
                        IPConfig::setTooltipCondenseTrims))
                .checkbox("Mod name", Bool.of(IPConfig::tooltipModName, IPConfig::setTooltipModName))
                .build();
    }

    static List<PanelElement> moveMatching() {
        return new SettingsBody()
                .frame("Move Matching", "Moves items between your inventory and the open container, "
                        + "but only items the other side already has. One button moves them in, the other out.",
                        Bool.of(IPConfig::moveMatchingEnabled, IPConfig::setMoveMatchingEnabled),
                        confirmReset("Move Matching", SettingsTabs::resetMoveMatching))
                // Where Move Matching puts items first waits on MenuKit, so it is grayed.
                .actions("Fill order", "Priority…")
                .checkbox("Show the Move Matching buttons",
                        Bool.of(IPConfig::moveMatchingShowButtons, IPConfig::setMoveMatchingShowButtons))
                .key(IPKeybinds.MOVE_MATCHING_OUT)
                .key(IPKeybinds.MOVE_MATCHING_IN)
                .build();
    }

    static List<PanelElement> restock() {
        // Each kind is its own switch too, and its sub-options gray while it
        // is off, as in the old settings screen.
        Bool armor = Bool.of(IPConfig::autoRestockArmor, IPConfig::setAutoRestockArmor);
        Bool tool = Bool.of(IPConfig::autoRestockTool, IPConfig::setAutoRestockTool);
        Bool item = Bool.of(IPConfig::autoRestockItem, IPConfig::setAutoRestockItem);
        SettingsBody b = new SettingsBody()
                .frame("Restock", "Refills your hand, hotbar and armor from your inventory when "
                        + "something runs out or breaks. It can also swap armor and tools just before they break.",
                        Bool.of(IPConfig::restockEnabled, IPConfig::setRestockEnabled),
                        confirmReset("Restock", SettingsTabs::resetRestock))
                .checkbox("Show the Restock button", Bool.of(IPConfig::restockShowButton, IPConfig::setRestockShowButton))
                // Shared by armor's and tools' "Swap before it breaks".
                .slider("Swap before breaking at durability", 2, 50,
                        IPConfig::autoRestockBeforeBreakThreshold, IPConfig::setAutoRestockBeforeBreakThreshold,
                        () -> !((IPConfig.autoRestockArmor() && IPConfig.autoRestockArmorBeforeBreak())
                                || (IPConfig.autoRestockTool() && IPConfig.autoRestockToolBeforeBreak())));
        // Items, Tools, Armor. Whether every kind may also pull from carried
        // shulker boxes is one setting (IPConfig.restockFromShulkers), with no
        // control here until its GUI is settled with Trev.
        b.heading("Items").checkbox("Restock items", item);
        b.heading("Tools")
                .checkbox("Restock tools", tool)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockToolBeforeBreak,
                        IPConfig::setAutoRestockToolBeforeBreak).onlyWhen(tool.get()));
        b.heading("Armor")
                .checkbox("Restock armor", armor)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockArmorBeforeBreak,
                        IPConfig::setAutoRestockArmorBeforeBreak).onlyWhen(armor.get()));
        // Ammo for the bow or crossbow in hand comes from carried shulker boxes
        // only, so it follows restockFromShulkers and has no heading of its own.
        return b.build();
    }

    static List<PanelElement> autoToolSwitch() {
        return new SettingsBody()
                .frame("Auto Tool Switch", "Switches to the right tool for the block you're mining, "
                        + "and to a weapon when you attack. It can switch back to what you were holding afterwards.",
                        Bool.of(IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled),
                        confirmReset("Auto Tool Switch", SettingsTabs::resetAutoToolSwitch))
                .checkbox("Show the Auto Tool Switch button",
                        Bool.of(IPConfig::autoToolSwitchShowButton, IPConfig::setAutoToolSwitchShowButton))
                // By default it switches back on its own a second after you
                // stop. The key is opt-in and returns early.
                .heading("Switching back")
                .checkbox("Switch back automatically", Bool.of(IPConfig::autoToolSwitchReturnAuto,
                        IPConfig::setAutoToolSwitchReturnAuto))
                .slider("Seconds after you stop", 0, 10,
                        IPConfig::autoToolSwitchReturnCooldownSeconds, IPConfig::setAutoToolSwitchReturnCooldownSeconds,
                        () -> !IPConfig.autoToolSwitchReturnAuto())
                .checkbox("Key to previous tool", Bool.of(IPConfig::autoToolSwitchReturnKey,
                        IPConfig::setAutoToolSwitchReturnKey))
                .key(IPKeybinds.AUTO_SWITCH_RETURN, Component.literal("Key"), null,
                        () -> !IPConfig.autoToolSwitchReturnKey())
                .heading("Weapons")
                .checkbox("Switch weapons too", Bool.of(IPConfig::autoToolSwitchWeapons,
                        IPConfig::setAutoToolSwitchWeapons))
                .subCheckbox("All mobs, not just hostile ones", Bool.of(IPConfig::autoToolSwitchAllMobs,
                        IPConfig::setAutoToolSwitchAllMobs).onlyWhen(IPConfig::autoToolSwitchWeapons))
                .choice("Preferred weapon", Arrays.asList(WeaponPreference.values()),
                        SettingsTabs::titleCase,
                        IPConfig::autoToolSwitchWeaponPreference, IPConfig::setAutoToolSwitchWeaponPreference,
                        () -> !IPConfig.autoToolSwitchWeapons())
                // The carried containers it may take a tool from
                // (IPConfig.autoToolSwitchFrom*) have no control while their Reach
                // rows are gone; their GUI is to be settled with Trev.
                .build();
    }

    static List<PanelElement> columnCycler() {
        return new SettingsBody()
                .frame("Column Cycler", "Rotates the items in an inventory column through a hotbar slot. "
                        + "Press the cycle key on a slot to add its column, then cycle it with the forward and backward keys.",
                        Bool.of(IPConfig::columnCyclerEnabled, IPConfig::setColumnCyclerEnabled),
                        confirmReset("Column Cycler", SettingsTabs::resetColumnCycler))
                .choice("Beside the hotbar", Arrays.asList(HudMode.values()), SettingsTabs::hudName,
                        IPConfig::columnCyclerHudMode, IPConfig::setColumnCyclerHudMode, () -> false)
                .checkbox("Show the Column Cycler button",
                        Bool.of(IPConfig::columnCyclerShowButton, IPConfig::setColumnCyclerShowButton))
                .key(IPKeybinds.CYCLE_SLOT)
                .heading("Cycling")
                .key(IPKeybinds.CYCLE_FORWARD)
                .key(IPKeybinds.CYCLE_BACKWARD)
                // Turning this on turns Hotbar Cycler's off, since there is one
                // wheel. The setter enforces it and both checkboxes read live.
                .checkbox("Scroll to cycle", Bool.of(IPConfig::columnCyclerScrollToCycle,
                        IPConfig::setColumnCyclerScrollToCycle))
                .build();
    }

    static List<PanelElement> hotbarCycler() {
        return new SettingsBody()
                .frame("Hotbar Cycler", "Rotates whole inventory rows through your hotbar. "
                        + "Add rows with the buttons beside them, then cycle with the forward and backward keys.",
                        Bool.of(IPConfig::hotbarCyclerEnabled, IPConfig::setHotbarCyclerEnabled),
                        confirmReset("Hotbar Cycler", SettingsTabs::resetHotbarCycler))
                .checkbox("Show the Hotbar Cycler button",
                        Bool.of(IPConfig::hotbarCyclerShowButton, IPConfig::setHotbarCyclerShowButton))
                .checkbox("Show the row buttons",
                        Bool.of(IPConfig::hotbarCyclerShowButtons, IPConfig::setHotbarCyclerShowButtons))
                .heading("Cycling")
                .key(IPKeybinds.HOTBAR_CYCLE_FORWARD)
                .key(IPKeybinds.HOTBAR_CYCLE_BACKWARD)
                .checkbox("Scroll to cycle", Bool.of(IPConfig::hotbarCyclerScrollToCycle,
                        IPConfig::setHotbarCyclerScrollToCycle))
                .build();
    }

    // Shared

    /** A HUD mode's name: NONE reads "Off", the rest in sentence case. */
    private static String hudName(Enum<?> e) {
        return e.name().equals("NONE") ? "Off" : titleCase(e);
    }

    private static String titleCase(Enum<?> e) {
        String s = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
