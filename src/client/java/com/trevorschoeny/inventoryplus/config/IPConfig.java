package com.trevorschoeny.inventoryplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global IP config: toggle state for the features. Loaded once at client
 * init, changed in memory by the settings menu, and persisted on every
 * setter call. Same JSON pattern as
 * {@link com.trevorschoeny.inventoryplus.sort.SortState}.
 *
 * <h3>File layout</h3>
 *
 * <pre>{@code
 * config/inventoryplus/config.json
 *
 * {
 *   "version": 1,
 *   "autoRestockOffhand":  true,
 *   "autoRestockArmor":    true,
 *   "autoRestockTool":     true,
 *   "autoRestockItem":     true,
 *   "reach": { ... },          (lockgroups.Reach)
 *   "sortShowButton":      true,
 *   "moveMatchingShowButtons": true,
 *   "lockedSlotsShowButton":   true
 * }
 * }</pre>
 *
 * <h3>Defaults</h3>
 *
 * A missing file or missing field means the field's default. The config
 * file is only written after a setter changes a value.
 *
 * <h3>Scope</h3>
 *
 * Default-scope toggles only. Power-user toggles (Pull from Bundles,
 * Pull from Ender Chest, Include Hotbar) and toggles for not-yet-built
 * mechanics (Restock Before Break, Pull from Shulker Boxes, Pull Ammo
 * When Shooting) are deliberately absent. They ship when their
 * mechanics ship.
 *
 * <h3>Access</h3>
 *
 * Static getters and setters, no instances. Call
 * {@link #load} once at client init.
 */
public final class IPConfig {

    private IPConfig() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();   // reach groups write "key": null

    // Auto-Restock. Offhand restock is always on: the offhand is just another
    // active slot, so there is no reason to gate it.
    private static boolean autoRestockArmor = true;
    private static boolean autoRestockArmorBeforeBreak = false; // sub of Armor
    private static boolean autoRestockTool = true;
    private static boolean autoRestockToolBeforeBreak = false;  // sub of Tool
    private static boolean autoRestockItem = true;
    private static boolean autoRestockShulker = false;          // parent
    private static boolean autoRestockShulkerAmmo = false;      // sub of Shulker
    // Restock items: where an item that ran out may come from besides the
    // inventory itself (Restock tab, Items). Off until Trev picks the defaults.
    private static boolean restockItemsFromShulkers = false;
    private static boolean restockItemsFromBundles = false;
    private static boolean restockItemsFromEnderChest = false;
    // The same three sources for Tools (broke and before break), Armor (the same)
    // and Ammo (arrows and the like for the ranged weapon in hand). Off until
    // Trev picks the defaults.
    private static boolean restockToolsFromShulkers = false;
    private static boolean restockToolsFromBundles = false;
    private static boolean restockToolsFromEnderChest = false;
    private static boolean restockArmorFromShulkers = false;
    private static boolean restockArmorFromBundles = false;
    private static boolean restockArmorFromEnderChest = false;
    private static boolean restockAmmoFromShulkers = false;
    private static boolean restockAmmoFromBundles = false;
    private static boolean restockAmmoFromEnderChest = false;
    // Pick block from carried containers (Emerald): when the inventory has none of
    // the picked block, take it from a shulker box, a bundle or the ender chest
    // (General tab). Off until Trev picks the defaults.
    private static boolean pickFromShulkers = false;
    private static boolean pickFromBundles = false;
    private static boolean pickFromEnderChest = false;
    // The four "use locked items" settings are gone: what a lock stops is its
    // lock group's choice in the reach record now. Their old values are still
    // read from the file once, for the 1.5.x migration of item locks
    // (LockedItems).
    // Durability floor for the two Before-Break swaps (armor and tool share
    // it): a swap fires when remaining durability sits at or below this.
    private static int autoRestockBeforeBreakThreshold = 10;

    // The Shulker and ShulkerAmmo flags above are state-only: the mechanics
    // behind them aren't implemented yet, so toggling them persists the choice
    // but changes nothing in game. ArmorBeforeBreak and ToolBeforeBreak are
    // live (AutoRestockTicker section 4).

    // Auto Tool Switch, off by default. When on, hitting a block (or attacking
    // a mob, with the Weapons sub-toggle) swaps the right tool into the active
    // hand. See the AutoToolSwitch class javadoc for the three-tier resolution
    // and the switch back.
    private static boolean autoToolSwitchEnabled = false;
    // Switching back: by default the hotbar snaps back on its own once the
    // player has stopped for the window. The key to the previous tool is
    // opt-in, and returns early (or, with automatic off, any time). These
    // replace the old four-way autoToolSwitchReturnMode (migrated in load()).
    private static boolean autoToolSwitchReturnAuto = true;
    private static boolean autoToolSwitchReturnKey = false;
    private static int autoToolSwitchReturnCooldownSeconds = 1;  // 0 = back as soon as you stop
    private static boolean autoToolSwitchWeapons = false;    // sub of Enabled
    private static boolean autoToolSwitchAllMobs = false;    // sub of Weapons; false = hostile only
    // Preferred weapon type: wins regardless of material when the player has
    // multiple weapon kinds available. Persisted as the enum name.
    private static WeaponPreference autoToolSwitchWeaponPreference = WeaponPreference.SWORD;
    // When the inventory has no tool or weapon that fits, take one from a carried
    // shulker box or bundle (Emerald). Off until Trev picks the defaults.
    private static boolean autoToolSwitchFromShulkers = false;
    private static boolean autoToolSwitchFromBundles = false;

    // Show Buttons
    private static boolean sortShowButton = true;
    private static boolean moveMatchingShowButtons = true;
    private static boolean lockedSlotsShowButton = true;

    // Column Cycler (Power Users). Off by default, like every Power Users
    // feature. When off, the toolbar button hides and the C keybind is a
    // no-op. showButton hides the button while the feature stays enabled, for
    // players who prefer the keybind alone (off by default: the inventory had
    // too many buttons). The cycle slots' protection belongs to Reach: Column
    // Cycler's slots are a feature reach group, and the global
    // cycleSlotsLocked is gone (read once below, for the upgrade).
    private static boolean columnCyclerEnabled = false;
    private static boolean columnCyclerShowButton = false;
    // Scroll wheel as an alternative trigger for forward and backward cycle.
    // Off by default because it is a trade-off: scrolling on a cycle-active
    // column rotates the cycle instead of switching the hotbar slot.
    private static boolean columnCyclerScrollToCycle = false;

    // Hotbar Cycler (Power Users). Rotates whole inventory rows through the
    // hotbar. Off by default like every Power Users feature. Its rows are a
    // feature reach group, and lockCycledRows is gone (read once below, for
    // the upgrade).
    private static boolean hotbarCyclerEnabled = false;
    private static boolean hotbarCyclerShowButtons = true;
    // The on/off switch in the inventory's right column, off by default.
    // hotbarCyclerShowButtons is the row buttons beside the grid.
    private static boolean hotbarCyclerShowButton = false;
    private static boolean hotbarCyclerScrollToCycle = false;
    // Column Cycler HUD overlay mode. MINI_HOTBAR by default, so the HUD strip
    // shows as soon as Column Cycler is enabled. NONE disables the HUD without
    // disabling the feature. Persisted as the enum name.
    private static HudMode columnCyclerHudMode = HudMode.MINI_HOTBAR;

    // Master switches and buttons. Every feature tab has an on/off and a
    // button toggle. lockGroupsEnabled pauses every lock without deleting any:
    // nothing blocked, nothing drawn.
    private static boolean sortEnabled = true;
    private static boolean moveMatchingEnabled = true;
    private static boolean restockEnabled = true;
    private static boolean restockShowButton = true;
    private static boolean autoToolSwitchShowButton = true;
    private static boolean lockGroupsEnabled = true;
    // "Also keep container locks on the server" (Lock groups tab; replaced
    // Inventory Max's containerLocksEnabled). Container locks are always kept
    // on this computer. The option wrote each new one to Inventory Max's shared
    // channel as well, where the server kept and enforced it as a plain lock.
    // With Inventory Max retired nothing provides that channel, so the row is
    // hidden (SettingsTabs) and the value is only carried over. Off by default,
    // because container locks are the player's own.
    private static boolean containerLocksOnServer = false;
    // Tooltips: everything Inventory Plus does to tooltips. tooltipsEnabled off
    // means Inventory Plus leaves every tooltip alone. One boolean per line
    // Inventory Plus adds or changes (tooltips/, one provider each). Durability
    // and Food were the one Item tips switch (moved from MenuKit in 6.0.0), and
    // both read the old itemTipsEnabled as a fallback. Mod name is off by
    // default: noise for a vanilla-only player. Holding the hide-tooltips key
    // (IPKeybinds.HIDE_TOOLTIPS, Space by default) hides every tooltip, on by
    // default. Saved as hideTooltipsOnKey. It was hideTooltipsOnCtrl while the
    // key was fixed to Ctrl (read as a fallback in load()).
    private static boolean tooltipsEnabled = true;
    private static boolean tooltipDurability = true;
    private static boolean tooltipFood = true;
    private static boolean tooltipFoodEffects = true;
    private static boolean tooltipEnchantmentDescriptions = true;
    private static boolean tooltipSortEnchantments = true;
    private static boolean tooltipToolStats = true;
    private static boolean tooltipCompare = true;
    private static boolean tooltipCondenseTrims = true;
    private static boolean tooltipModName = false;
    // On the Tooltips tab, under its switch.
    private static boolean hideTooltipsOnKey = true;
    // Reverses the key: item tooltips stay hidden, and holding it shows them.
    private static boolean tooltipKeyShows = false;
    // Compact tooltips: the inventory buttons' tooltips keep only their
    // essential lines (what it does, its state), no hints.
    private static boolean compactTooltips = false;
    // Scrolling a long tooltip and keeping it on screen are no longer
    // settings: always on under the Tooltips switch (tooltips/Tooltips). An
    // old file's tooltipScrollLong and tooltipKeepOnScreen are never read, and
    // the next save drops them.
    // Tooltips vanilla would place beside the cursor dock at the bottom center
    // of the screen, or the top center when the cursor would be inside them
    // there (MenuKit 6.2.0 MKTooltip.avoidCoverWhen). A new key: the old
    // tooltipAvoidCover meant "keep out of the way", a different behavior,
    // so an upgrader's saved true there must not turn this on. Everyone starts
    // at off; the old key is read by nobody and the next save drops it.
    private static boolean tooltipDock = false;
    // When a screen opens, tooltips stay hidden until the mouse moves, so the
    // slot under the cursor doesn't pop one up the moment it opens. On by
    // default. Replaces tooltipCursorAboveSlots, which moved the cursor (Trev,
    // 2026-10-15: it moved it even with the game window in the background);
    // that old key is read by nobody and the next save drops it.
    private static boolean tooltipHideUntilMouseMoves = true;

    // Settings menu. On, the pause-menu look (MenuKit's options screen). Off,
    // the inventory look it had before.
    private static boolean settingsOptionsLook = true;

    /**
     * Every setting at its default, taken before the file is read. A tab's
     * Reset to Defaults applies the part of this it owns ({@link #reset}).
     * Declared after every field, so each is at its default here.
     */
    private static final JsonObject DEFAULTS = settingsJson();

    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("config.json");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        // The reach record lives in this file, so every change to it saves the
        // whole file, like any setting.
        Reach.onChange(IPConfig::save);
        Path path = filePath();
        if (!Files.exists(path)) {
            InventoryPlusClient.LOGGER.info(
                    "[config] no config file at {}, using defaults", path);
            return;
        }
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            apply(root);
            legacyCycleSlotsLocked = readBool(root, "cycleSlotsLocked", true);
            Reach.read(root.has("reach") && root.get("reach").isJsonObject() ? root.getAsJsonObject("reach") : null);
            InventoryPlusClient.LOGGER.info("[config] loaded from {}", path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[config] failed to read {}, using defaults", path, e);
        }
    }

    /** Reads every setting {@code root} has. The rest keep their values. */
    private static void apply(JsonObject root) {
        autoRestockArmor             = readBool(root, "autoRestockArmor",             autoRestockArmor);
        autoRestockArmorBeforeBreak  = readBool(root, "autoRestockArmorBeforeBreak",  autoRestockArmorBeforeBreak);
        autoRestockTool              = readBool(root, "autoRestockTool",              autoRestockTool);
        autoRestockToolBeforeBreak   = readBool(root, "autoRestockToolBeforeBreak",   autoRestockToolBeforeBreak);
        autoRestockItem              = readBool(root, "autoRestockItem",              autoRestockItem);
        autoRestockShulker           = readBool(root, "autoRestockShulker",           autoRestockShulker);
        autoRestockShulkerAmmo       = readBool(root, "autoRestockShulkerAmmo",       autoRestockShulkerAmmo);
        restockItemsFromShulkers     = readBool(root, "restockItemsFromShulkers",     restockItemsFromShulkers);
        restockItemsFromBundles      = readBool(root, "restockItemsFromBundles",      restockItemsFromBundles);
        restockItemsFromEnderChest   = readBool(root, "restockItemsFromEnderChest",   restockItemsFromEnderChest);
        restockToolsFromShulkers = readBool(root, "restockToolsFromShulkers", restockToolsFromShulkers);
        restockToolsFromBundles = readBool(root, "restockToolsFromBundles", restockToolsFromBundles);
        restockToolsFromEnderChest = readBool(root, "restockToolsFromEnderChest", restockToolsFromEnderChest);
        restockArmorFromShulkers = readBool(root, "restockArmorFromShulkers", restockArmorFromShulkers);
        restockArmorFromBundles = readBool(root, "restockArmorFromBundles", restockArmorFromBundles);
        restockArmorFromEnderChest = readBool(root, "restockArmorFromEnderChest", restockArmorFromEnderChest);
        restockAmmoFromShulkers = readBool(root, "restockAmmoFromShulkers", restockAmmoFromShulkers);
        restockAmmoFromBundles = readBool(root, "restockAmmoFromBundles", restockAmmoFromBundles);
        restockAmmoFromEnderChest = readBool(root, "restockAmmoFromEnderChest", restockAmmoFromEnderChest);
        pickFromShulkers             = readBool(root, "pickFromShulkers",             pickFromShulkers);
        pickFromBundles              = readBool(root, "pickFromBundles",              pickFromBundles);
        pickFromEnderChest           = readBool(root, "pickFromEnderChest",           pickFromEnderChest);
        autoRestockBeforeBreakThreshold = readInt(root, "autoRestockBeforeBreakThreshold",
                autoRestockBeforeBreakThreshold);
        autoToolSwitchEnabled  = readBool(root, "autoToolSwitchEnabled",  autoToolSwitchEnabled);
        // Migrate the old four-way mode, or the boolean before it: AUTOMATIC
        // returns on its own, either hotkey mode keeps the key, OFF neither.
        // The timed hotkey (key only within the window) has no new form; it
        // becomes the key any time.
        String oldMode = root.has("autoToolSwitchReturn")
                ? (readBool(root, "autoToolSwitchReturn", false) ? "AUTOMATIC" : "OFF")
                : readString(root, "autoToolSwitchReturnMode", null);
        if (oldMode != null) {
            autoToolSwitchReturnAuto = oldMode.equals("AUTOMATIC");
            autoToolSwitchReturnKey = oldMode.startsWith("HOTKEY");
        }
        autoToolSwitchReturnAuto = readBool(root, "autoToolSwitchReturnAuto", autoToolSwitchReturnAuto);
        autoToolSwitchReturnKey = readBool(root, "autoToolSwitchReturnKey", autoToolSwitchReturnKey);
        autoToolSwitchReturnCooldownSeconds = readInt(root, "autoToolSwitchReturnCooldownSeconds",
                autoToolSwitchReturnCooldownSeconds);
        autoToolSwitchWeapons  = readBool(root, "autoToolSwitchWeapons",  autoToolSwitchWeapons);
        autoToolSwitchAllMobs  = readBool(root, "autoToolSwitchAllMobs",  autoToolSwitchAllMobs);
        autoToolSwitchWeaponPreference = WeaponPreference.fromName(readString(root, "autoToolSwitchWeaponPreference", null), autoToolSwitchWeaponPreference);
        autoToolSwitchFromShulkers = readBool(root, "autoToolSwitchFromShulkers", autoToolSwitchFromShulkers);
        autoToolSwitchFromBundles = readBool(root, "autoToolSwitchFromBundles", autoToolSwitchFromBundles);
        sortShowButton         = readBool(root, "sortShowButton",         sortShowButton);
        moveMatchingShowButtons= readBool(root, "moveMatchingShowButtons",moveMatchingShowButtons);
        lockedSlotsShowButton  = readBool(root, "lockedSlotsShowButton",  lockedSlotsShowButton);
        columnCyclerEnabled       = readBool(root, "columnCyclerEnabled",       columnCyclerEnabled);
        columnCyclerShowButton    = readBool(root, "columnCyclerShowButton",    columnCyclerShowButton);
        columnCyclerScrollToCycle = readBool(root, "columnCyclerScrollToCycle", columnCyclerScrollToCycle);
        hotbarCyclerEnabled       = readBool(root, "hotbarCyclerEnabled",       hotbarCyclerEnabled);
        hotbarCyclerShowButtons   = readBool(root, "hotbarCyclerShowButtons",   hotbarCyclerShowButtons);
        hotbarCyclerShowButton    = readBool(root, "hotbarCyclerShowButton",    hotbarCyclerShowButton);
        hotbarCyclerScrollToCycle = readBool(root, "hotbarCyclerScrollToCycle", hotbarCyclerScrollToCycle);
        columnCyclerHudMode       = HudMode.fromName(readString(root, "columnCyclerHudMode", null), columnCyclerHudMode);
        sortEnabled               = readBool(root, "sortEnabled",               sortEnabled);
        moveMatchingEnabled       = readBool(root, "moveMatchingEnabled",       moveMatchingEnabled);
        restockEnabled            = readBool(root, "restockEnabled",            restockEnabled);
        restockShowButton         = readBool(root, "restockShowButton",         restockShowButton);
        autoToolSwitchShowButton  = readBool(root, "autoToolSwitchShowButton",  autoToolSwitchShowButton);
        lockGroupsEnabled         = readBool(root, "lockGroupsEnabled",         lockGroupsEnabled);
        containerLocksOnServer    = readBool(root, "containerLocksOnServer",    containerLocksOnServer);
        tooltipsEnabled           = readBool(root, "tooltipsEnabled",           tooltipsEnabled);
        tooltipDurability = readBool(root, "tooltipDurability", readBool(root, "itemTipsEnabled", tooltipDurability));
        tooltipFood = readBool(root, "tooltipFood", readBool(root, "itemTipsEnabled", tooltipFood));
        tooltipFoodEffects = readBool(root, "tooltipFoodEffects", tooltipFoodEffects);
        tooltipEnchantmentDescriptions = readBool(root, "tooltipEnchantmentDescriptions", tooltipEnchantmentDescriptions);
        tooltipSortEnchantments = readBool(root, "tooltipSortEnchantments", tooltipSortEnchantments);
        tooltipToolStats = readBool(root, "tooltipToolStats", tooltipToolStats);
        tooltipCompare = readBool(root, "tooltipCompare", tooltipCompare);
        tooltipCondenseTrims = readBool(root, "tooltipCondenseTrims", tooltipCondenseTrims);
        tooltipModName = readBool(root, "tooltipModName", tooltipModName);
        hideTooltipsOnKey         = readBool(root, "hideTooltipsOnKey",
                readBool(root, "hideTooltipsOnCtrl", hideTooltipsOnKey));
        tooltipKeyShows           = readBool(root, "tooltipKeyShows",           tooltipKeyShows);
        compactTooltips           = readBool(root, "compactTooltips",           compactTooltips);
        // Old key ignored on purpose (see the field doc): no migration.
        tooltipDock = readBool(root, "tooltipDock", tooltipDock);
        tooltipHideUntilMouseMoves = readBool(root, "tooltipHideUntilMouseMoves", tooltipHideUntilMouseMoves);
        settingsOptionsLook = readBool(root, "settingsOptionsLook", settingsOptionsLook);
    }

    /**
     * Puts the settings named in {@code keys} back to their defaults and
     * saves: a tab's Reset to Defaults. Keys are the file's own names.
     */
    public static void reset(String... keys) {
        JsonObject part = new JsonObject();
        for (String key : keys) {
            if (!DEFAULTS.has(key)) throw new IllegalArgumentException("[config] no setting " + key);
            part.add(key, DEFAULTS.get(key));
        }
        apply(part);
        save();
    }

    /** Every setting back to its default, and saves: General's Reset everything. */
    public static void resetAll() {
        apply(DEFAULTS);
        save();
    }

    private static boolean readBool(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive()
                ? root.get(key).getAsBoolean()
                : fallback;
    }

    /**
     * Read a string field from the config JSON, returning {@code fallback}
     * when the key is absent or not a primitive. Used for enum-typed
     * fields (serialized as their {@code name()} string).
     */
    private static String readString(JsonObject root, String key, String fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive()
                ? root.get(key).getAsString()
                : fallback;
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive()
                ? root.get(key).getAsInt()
                : fallback;
    }

    /** Every setting as the file writes it (without the version or the reach record). */
    private static JsonObject settingsJson() {
        JsonObject root = new JsonObject();
        root.addProperty("autoRestockArmor",            autoRestockArmor);
        root.addProperty("autoRestockArmorBeforeBreak", autoRestockArmorBeforeBreak);
        root.addProperty("autoRestockTool",             autoRestockTool);
        root.addProperty("autoRestockToolBeforeBreak",  autoRestockToolBeforeBreak);
        root.addProperty("autoRestockItem",             autoRestockItem);
        root.addProperty("autoRestockShulker",          autoRestockShulker);
        root.addProperty("autoRestockShulkerAmmo",      autoRestockShulkerAmmo);
        root.addProperty("restockItemsFromShulkers",    restockItemsFromShulkers);
        root.addProperty("restockItemsFromBundles",     restockItemsFromBundles);
        root.addProperty("restockItemsFromEnderChest",  restockItemsFromEnderChest);
        root.addProperty("restockToolsFromShulkers", restockToolsFromShulkers);
        root.addProperty("restockToolsFromBundles", restockToolsFromBundles);
        root.addProperty("restockToolsFromEnderChest", restockToolsFromEnderChest);
        root.addProperty("restockArmorFromShulkers", restockArmorFromShulkers);
        root.addProperty("restockArmorFromBundles", restockArmorFromBundles);
        root.addProperty("restockArmorFromEnderChest", restockArmorFromEnderChest);
        root.addProperty("restockAmmoFromShulkers", restockAmmoFromShulkers);
        root.addProperty("restockAmmoFromBundles", restockAmmoFromBundles);
        root.addProperty("restockAmmoFromEnderChest", restockAmmoFromEnderChest);
        root.addProperty("pickFromShulkers",            pickFromShulkers);
        root.addProperty("pickFromBundles",             pickFromBundles);
        root.addProperty("pickFromEnderChest",          pickFromEnderChest);
        root.addProperty("autoRestockBeforeBreakThreshold", autoRestockBeforeBreakThreshold);
        root.addProperty("autoToolSwitchEnabled",   autoToolSwitchEnabled);
        root.addProperty("autoToolSwitchReturnAuto", autoToolSwitchReturnAuto);
        root.addProperty("autoToolSwitchReturnKey", autoToolSwitchReturnKey);
        root.addProperty("autoToolSwitchReturnCooldownSeconds", autoToolSwitchReturnCooldownSeconds);
        root.addProperty("autoToolSwitchWeapons",   autoToolSwitchWeapons);
        root.addProperty("autoToolSwitchAllMobs",   autoToolSwitchAllMobs);
        root.addProperty("autoToolSwitchWeaponPreference", autoToolSwitchWeaponPreference.name());
        root.addProperty("autoToolSwitchFromShulkers", autoToolSwitchFromShulkers);
        root.addProperty("autoToolSwitchFromBundles", autoToolSwitchFromBundles);
        root.addProperty("sortShowButton",          sortShowButton);
        root.addProperty("moveMatchingShowButtons", moveMatchingShowButtons);
        root.addProperty("lockedSlotsShowButton",   lockedSlotsShowButton);
        root.addProperty("columnCyclerEnabled",       columnCyclerEnabled);
        root.addProperty("columnCyclerShowButton",    columnCyclerShowButton);
        root.addProperty("columnCyclerScrollToCycle", columnCyclerScrollToCycle);
        root.addProperty("hotbarCyclerEnabled",       hotbarCyclerEnabled);
        root.addProperty("hotbarCyclerShowButtons",   hotbarCyclerShowButtons);
        root.addProperty("hotbarCyclerShowButton",    hotbarCyclerShowButton);
        root.addProperty("hotbarCyclerScrollToCycle", hotbarCyclerScrollToCycle);
        root.addProperty("columnCyclerHudMode",       columnCyclerHudMode.name());
        root.addProperty("sortEnabled",               sortEnabled);
        root.addProperty("moveMatchingEnabled",       moveMatchingEnabled);
        root.addProperty("restockEnabled",            restockEnabled);
        root.addProperty("restockShowButton",         restockShowButton);
        root.addProperty("autoToolSwitchShowButton",  autoToolSwitchShowButton);
        root.addProperty("lockGroupsEnabled",         lockGroupsEnabled);
        root.addProperty("containerLocksOnServer",    containerLocksOnServer);
        root.addProperty("tooltipsEnabled",           tooltipsEnabled);
        root.addProperty("tooltipDurability", tooltipDurability);
        root.addProperty("tooltipFood", tooltipFood);
        root.addProperty("tooltipFoodEffects", tooltipFoodEffects);
        root.addProperty("tooltipEnchantmentDescriptions", tooltipEnchantmentDescriptions);
        root.addProperty("tooltipSortEnchantments", tooltipSortEnchantments);
        root.addProperty("tooltipToolStats", tooltipToolStats);
        root.addProperty("tooltipCompare", tooltipCompare);
        root.addProperty("tooltipCondenseTrims", tooltipCondenseTrims);
        root.addProperty("tooltipModName", tooltipModName);
        root.addProperty("hideTooltipsOnKey",         hideTooltipsOnKey);
        root.addProperty("tooltipKeyShows",           tooltipKeyShows);
        root.addProperty("compactTooltips",           compactTooltips);
        root.addProperty("tooltipDock", tooltipDock);
        root.addProperty("tooltipHideUntilMouseMoves", tooltipHideUntilMouseMoves);
        root.addProperty("settingsOptionsLook", settingsOptionsLook);
        return root;
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            settingsJson().entrySet().forEach(e -> root.add(e.getKey(), e.getValue()));
            root.add("reach", Reach.write());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[config] failed to write {}, changes won't survive restart",
                    path, e);
        }
    }

    /**
     * The removed "Lock the slots the cyclers use" switch as the file last held
     * it (true when absent, its default), for the 1.5.x pairing drop in LockedSlots.
     */
    private static boolean legacyCycleSlotsLocked = true;

    public static boolean legacyCycleSlotsLocked() { return legacyCycleSlotsLocked; }

    public static boolean sortEnabled()              { return sortEnabled; }
    public static boolean moveMatchingEnabled()      { return moveMatchingEnabled; }
    public static boolean restockEnabled()           { return restockEnabled; }
    public static boolean restockShowButton()        { return restockShowButton; }
    public static boolean autoToolSwitchShowButton() { return autoToolSwitchShowButton; }
    public static boolean lockGroupsEnabled()        { return lockGroupsEnabled; }
    public static boolean containerLocksOnServer()   { return containerLocksOnServer; }
    public static boolean tooltipsEnabled()          { return tooltipsEnabled; }
    public static boolean tooltipDurability() { return tooltipDurability; }
    public static boolean tooltipFood() { return tooltipFood; }
    public static boolean tooltipFoodEffects() { return tooltipFoodEffects; }
    public static boolean tooltipEnchantmentDescriptions() { return tooltipEnchantmentDescriptions; }
    public static boolean tooltipSortEnchantments() { return tooltipSortEnchantments; }
    public static boolean tooltipToolStats() { return tooltipToolStats; }
    public static boolean tooltipCompare() { return tooltipCompare; }
    public static boolean tooltipCondenseTrims() { return tooltipCondenseTrims; }
    public static boolean tooltipModName() { return tooltipModName; }
    public static boolean hideTooltipsOnKey()        { return hideTooltipsOnKey; }
    public static boolean tooltipKeyShows()          { return tooltipKeyShows; }
    public static boolean compactTooltips()          { return compactTooltips; }
    public static boolean tooltipDock() { return tooltipDock; }
    public static boolean tooltipHideUntilMouseMoves() { return tooltipHideUntilMouseMoves; }
    public static boolean settingsOptionsLook() { return settingsOptionsLook; }
    public static void setSortEnabled(boolean v)              { sortEnabled = v; save(); }
    public static void setMoveMatchingEnabled(boolean v)      { moveMatchingEnabled = v; save(); }
    public static void setRestockEnabled(boolean v)           { restockEnabled = v; save(); }
    public static void setRestockShowButton(boolean v)        { restockShowButton = v; save(); }
    public static void setAutoToolSwitchShowButton(boolean v) { autoToolSwitchShowButton = v; save(); }
    public static void setLockGroupsEnabled(boolean v)        { lockGroupsEnabled = v; save(); }
    public static void setContainerLocksOnServer(boolean v)   { containerLocksOnServer = v; save(); }
    public static void setTooltipsEnabled(boolean v)          { tooltipsEnabled = v; save(); }
    public static void setTooltipDurability(boolean v) { tooltipDurability = v; save(); }
    public static void setTooltipFood(boolean v) { tooltipFood = v; save(); }
    public static void setTooltipFoodEffects(boolean v) { tooltipFoodEffects = v; save(); }
    public static void setTooltipEnchantmentDescriptions(boolean v) { tooltipEnchantmentDescriptions = v; save(); }
    public static void setTooltipSortEnchantments(boolean v) { tooltipSortEnchantments = v; save(); }
    public static void setTooltipToolStats(boolean v) { tooltipToolStats = v; save(); }
    public static void setTooltipCompare(boolean v) { tooltipCompare = v; save(); }
    public static void setTooltipCondenseTrims(boolean v) { tooltipCondenseTrims = v; save(); }
    public static void setTooltipModName(boolean v) { tooltipModName = v; save(); }
    public static void setHideTooltipsOnKey(boolean v)        { hideTooltipsOnKey = v; save(); }
    public static void setTooltipKeyShows(boolean v)          { tooltipKeyShows = v; save(); }
    public static void setCompactTooltips(boolean v)          { compactTooltips = v; save(); }
    public static void setTooltipDock(boolean v) { tooltipDock = v; save(); }
    public static void setTooltipHideUntilMouseMoves(boolean v) { tooltipHideUntilMouseMoves = v; save(); }
    public static void setSettingsOptionsLook(boolean v) { settingsOptionsLook = v; save(); }

    // Getters
    public static boolean autoRestockArmor()            { return autoRestockArmor; }
    public static boolean autoRestockArmorBeforeBreak() { return autoRestockArmorBeforeBreak; }
    public static boolean autoRestockTool()             { return autoRestockTool; }
    public static boolean autoRestockToolBeforeBreak()  { return autoRestockToolBeforeBreak; }
    public static boolean autoRestockItem()             { return autoRestockItem; }
    public static boolean autoRestockShulker()          { return autoRestockShulker; }
    public static boolean autoRestockShulkerAmmo()      { return autoRestockShulkerAmmo; }
    public static boolean restockItemsFromShulkers()    { return restockItemsFromShulkers; }
    public static boolean restockItemsFromBundles()     { return restockItemsFromBundles; }
    public static boolean restockItemsFromEnderChest()  { return restockItemsFromEnderChest; }
    public static boolean restockToolsFromShulkers() { return restockToolsFromShulkers; }
    public static boolean restockToolsFromBundles() { return restockToolsFromBundles; }
    public static boolean restockToolsFromEnderChest() { return restockToolsFromEnderChest; }
    public static boolean restockArmorFromShulkers() { return restockArmorFromShulkers; }
    public static boolean restockArmorFromBundles() { return restockArmorFromBundles; }
    public static boolean restockArmorFromEnderChest() { return restockArmorFromEnderChest; }
    public static boolean restockAmmoFromShulkers() { return restockAmmoFromShulkers; }
    public static boolean restockAmmoFromBundles() { return restockAmmoFromBundles; }
    public static boolean restockAmmoFromEnderChest() { return restockAmmoFromEnderChest; }
    public static boolean pickFromShulkers()            { return pickFromShulkers; }
    public static boolean pickFromBundles()             { return pickFromBundles; }
    public static boolean pickFromEnderChest()          { return pickFromEnderChest; }
    public static int autoRestockBeforeBreakThreshold() { return autoRestockBeforeBreakThreshold; }
    public static boolean autoToolSwitchEnabled()       { return autoToolSwitchEnabled; }
    public static boolean autoToolSwitchReturnAuto() { return autoToolSwitchReturnAuto; }
    public static boolean autoToolSwitchReturnKey()  { return autoToolSwitchReturnKey; }
    public static int autoToolSwitchReturnCooldownSeconds()       { return autoToolSwitchReturnCooldownSeconds; }
    public static boolean autoToolSwitchWeapons()       { return autoToolSwitchWeapons; }
    public static boolean autoToolSwitchAllMobs()       { return autoToolSwitchAllMobs; }
    public static WeaponPreference autoToolSwitchWeaponPreference() { return autoToolSwitchWeaponPreference; }
    public static boolean autoToolSwitchFromShulkers() { return autoToolSwitchFromShulkers; }
    public static boolean autoToolSwitchFromBundles() { return autoToolSwitchFromBundles; }
    public static boolean sortShowButton()              { return sortShowButton; }
    public static boolean moveMatchingShowButtons()     { return moveMatchingShowButtons; }
    public static boolean lockedSlotsShowButton()       { return lockedSlotsShowButton; }
    public static boolean columnCyclerEnabled()         { return columnCyclerEnabled; }
    public static boolean columnCyclerShowButton()      { return columnCyclerShowButton; }
    public static boolean columnCyclerScrollToCycle()   { return columnCyclerScrollToCycle; }
    public static boolean hotbarCyclerEnabled()         { return hotbarCyclerEnabled; }
    public static boolean hotbarCyclerShowButtons()     { return hotbarCyclerShowButtons; }
    public static boolean hotbarCyclerShowButton()      { return hotbarCyclerShowButton; }
    public static boolean hotbarCyclerScrollToCycle()   { return hotbarCyclerScrollToCycle; }
    public static HudMode columnCyclerHudMode()         { return columnCyclerHudMode; }

    // Setters
    public static void setAutoRestockArmor(boolean v)            { autoRestockArmor = v; save(); }
    public static void setAutoRestockArmorBeforeBreak(boolean v) { autoRestockArmorBeforeBreak = v; save(); }
    public static void setAutoRestockTool(boolean v)             { autoRestockTool = v; save(); }
    public static void setAutoRestockToolBeforeBreak(boolean v)  { autoRestockToolBeforeBreak = v; save(); }
    public static void setAutoRestockItem(boolean v)             { autoRestockItem = v; save(); }
    public static void setAutoRestockShulker(boolean v)          { autoRestockShulker = v; save(); }
    public static void setAutoRestockShulkerAmmo(boolean v)      { autoRestockShulkerAmmo = v; save(); }
    public static void setRestockItemsFromShulkers(boolean v)    { restockItemsFromShulkers = v; save(); }
    public static void setRestockItemsFromBundles(boolean v)     { restockItemsFromBundles = v; save(); }
    public static void setRestockItemsFromEnderChest(boolean v)  { restockItemsFromEnderChest = v; save(); }
    public static void setRestockToolsFromShulkers(boolean v) { restockToolsFromShulkers = v; save(); }
    public static void setRestockToolsFromBundles(boolean v) { restockToolsFromBundles = v; save(); }
    public static void setRestockToolsFromEnderChest(boolean v) { restockToolsFromEnderChest = v; save(); }
    public static void setRestockArmorFromShulkers(boolean v) { restockArmorFromShulkers = v; save(); }
    public static void setRestockArmorFromBundles(boolean v) { restockArmorFromBundles = v; save(); }
    public static void setRestockArmorFromEnderChest(boolean v) { restockArmorFromEnderChest = v; save(); }
    public static void setRestockAmmoFromShulkers(boolean v) { restockAmmoFromShulkers = v; save(); }
    public static void setRestockAmmoFromBundles(boolean v) { restockAmmoFromBundles = v; save(); }
    public static void setRestockAmmoFromEnderChest(boolean v) { restockAmmoFromEnderChest = v; save(); }
    public static void setPickFromShulkers(boolean v)            { pickFromShulkers = v; save(); }
    public static void setPickFromBundles(boolean v)             { pickFromBundles = v; save(); }
    public static void setPickFromEnderChest(boolean v)          { pickFromEnderChest = v; save(); }
    public static void setAutoRestockBeforeBreakThreshold(int v) { autoRestockBeforeBreakThreshold = v; save(); }
    public static void setAutoToolSwitchEnabled(boolean v)       { autoToolSwitchEnabled = v; save(); }
    public static void setAutoToolSwitchReturnAuto(boolean v) { autoToolSwitchReturnAuto = v; save(); }
    public static void setAutoToolSwitchReturnKey(boolean v)  { autoToolSwitchReturnKey = v; save(); }
    public static void setAutoToolSwitchReturnCooldownSeconds(int v)       { autoToolSwitchReturnCooldownSeconds = v; save(); }
    public static void setAutoToolSwitchWeapons(boolean v)       { autoToolSwitchWeapons = v; save(); }
    public static void setAutoToolSwitchAllMobs(boolean v)       { autoToolSwitchAllMobs = v; save(); }
    public static void setAutoToolSwitchWeaponPreference(WeaponPreference v) { autoToolSwitchWeaponPreference = v; save(); }
    public static void setAutoToolSwitchFromShulkers(boolean v) { autoToolSwitchFromShulkers = v; save(); }
    public static void setAutoToolSwitchFromBundles(boolean v) { autoToolSwitchFromBundles = v; save(); }
    public static void setSortShowButton(boolean v)              { sortShowButton = v; save(); }
    public static void setMoveMatchingShowButtons(boolean v)     { moveMatchingShowButtons = v; save(); }
    public static void setLockedSlotsShowButton(boolean v)       { lockedSlotsShowButton = v; save(); }
    public static void setColumnCyclerEnabled(boolean v)         { columnCyclerEnabled = v; save(); }
    public static void setColumnCyclerShowButton(boolean v)      { columnCyclerShowButton = v; save(); }
    // Scroll-to-cycle has a single owner: the wheel is one input, so turning
    // it on for one cycler turns it off for the other. Both
    // setters enforce it, so the rule holds wherever it's set from.
    public static void setColumnCyclerScrollToCycle(boolean v) {
        columnCyclerScrollToCycle = v;
        if (v) hotbarCyclerScrollToCycle = false;
        save();
    }
    public static void setHotbarCyclerEnabled(boolean v)         { hotbarCyclerEnabled = v; save(); }
    public static void setHotbarCyclerShowButtons(boolean v)     { hotbarCyclerShowButtons = v; save(); }
    public static void setHotbarCyclerShowButton(boolean v)      { hotbarCyclerShowButton = v; save(); }
    public static void setHotbarCyclerScrollToCycle(boolean v) {
        hotbarCyclerScrollToCycle = v;
        if (v) columnCyclerScrollToCycle = false;
        save();
    }
    public static void setColumnCyclerHudMode(HudMode v)         { columnCyclerHudMode = v; save(); }
}
