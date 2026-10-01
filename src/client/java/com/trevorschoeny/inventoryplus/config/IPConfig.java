package com.trevorschoeny.inventoryplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global IP config — toggle state for default-scope features. Loaded
 * once at client init; mutated in-memory by the config screen; persisted
 * on every setter call. Same JSON pattern as
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
 * All current toggles default ON (per Trev's spec 2026-05-17). Missing
 * file or missing field = default ON. The config file is only written
 * after a setter changes a value.
 *
 * <h3>Scope</h3>
 *
 * Default-scope toggles only. Power-user toggles (Pull from Bundles,
 * Pull from Ender Chest, Include Hotbar) and toggles for not-yet-built
 * mechanics (Restock Before Break, Pull from Shulker Boxes, Pull Ammo
 * When Shooting) are deliberately absent — they ship when their
 * mechanics ship.
 *
 * <h3>Access</h3>
 *
 * Static getters/setters; no instance handles. Call
 * {@link #load} once at client init.
 */
public final class IPConfig {

    private IPConfig() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();   // reach groups write "key": null

    // ─── Auto-Restock ────────────────────────────────────────────────
    // Offhand restock is unconditionally on per Trev 2026-05-18 — the
    // offhand is just another active slot; no reason to gate it.
    private static boolean autoRestockArmor = true;
    private static boolean autoRestockArmorBeforeBreak = false; // sub of Armor
    private static boolean autoRestockTool = true;
    private static boolean autoRestockToolBeforeBreak = false;  // sub of Tool
    private static boolean autoRestockItem = true;
    private static boolean autoRestockShulker = false;          // parent
    private static boolean autoRestockShulkerAmmo = false;      // sub of Shulker
    // The four "use locked items" settings (Trev 2026-09-06) are gone: what a
    // lock stops is its lock group's choice in the reach record now
    // (`plans/lock-groups.md`). Their old values are still read from the file
    // once, for the 1.5.x migration of item locks (LockedItems).
    // Durability floor for the two Before-Break swaps (armor + tool share it) —
    // a swap fires when remaining durability sits at or below this. Was the
    // hardcoded AutoRestockTicker.BEFORE_BREAK_THRESHOLD = 10.
    private static int autoRestockBeforeBreakThreshold = 10;

    // NOTE: the Shulker / ShulkerAmmo flags above are STATE-ONLY today —
    // the mechanics behind them aren't implemented yet, so toggling them in
    // the UI persists the choice but no in-game behavior changes until those
    // mechanics ship. ArmorBeforeBreak / ToolBeforeBreak were state-only
    // through 2026-05-17 and are now live (AutoRestockTicker section 4).

    // ─── Auto Tool Switch (default OFF — opt-in master toggle) ───────
    // When ON, hitting a block (or attacking a mob, with Weapons sub-
    // toggle) auto-swaps the right tool into the active hand. See the
    // Auto Tool Switch spec + AutoToolSwitch class javadoc for the
    // three-tier resolution and auto-return mechanics.
    private static boolean autoToolSwitchEnabled = false;
    // Return mode + window: how/when the hotbar snaps back after a switch.
    // Replaces the old boolean autoToolSwitchReturn (migrated in load()).
    private static AutoSwitchReturnMode autoToolSwitchReturnMode = AutoSwitchReturnMode.OFF;
    private static int autoToolSwitchReturnCooldownSeconds = 3;  // window for SNEAK / HOTKEY_TIMED
    private static boolean autoToolSwitchWeapons = false;    // sub of Enabled
    private static boolean autoToolSwitchAllMobs = false;    // sub of Weapons; false = hostile only
    // Preferred weapon type — wins regardless of material when the
    // player has multiple weapon kinds available. Default SWORD.
    // Persisted as the enum name.
    private static WeaponPreference autoToolSwitchWeaponPreference = WeaponPreference.SWORD;

    // ─── Show Buttons ────────────────────────────────────────────────
    private static boolean sortShowButton = true;
    private static boolean moveMatchingShowButtons = true;
    private static boolean lockedSlotsShowButton = true;

    // ─── Column Cycler (Power Users) ─────────────────────────────────
    // Master toggle defaults OFF (Power Users category opt-in). When OFF,
    // the PU toolbar button hides and the C keybind is a no-op.
    // showButton controls whether the PU toolbar button is visible even
    // when the feature is enabled — power users who prefer keybind-only
    // can hide the button while keeping C functional.
    // The cycle slots' protection is Reach's since 2026-09-30: Column
    // Cycler's slots are a feature reach group (reach.md), and the global
    // cycleSlotsLocked is gone (read once below, for the upgrade).
    private static boolean columnCyclerEnabled = false;
    private static boolean columnCyclerShowButton = true;
    // Scroll wheel as alternative trigger for forward/backward cycle.
    // Default OFF; opt-in trade-off — scrolling on a cycle-active column
    // rotates the cycle instead of switching the hotbar slot.
    private static boolean columnCyclerScrollToCycle = false;

    // ── Hotbar Cycler (Power Users) ─────────────────────────────────────
    // Rotates whole inventory rows through the hotbar. Off by default like
    // every Power Users feature. Its rows are a feature reach group since
    // 2026-09-30; lockCycledRows is gone (read once below, for the upgrade).
    private static boolean hotbarCyclerEnabled = false;
    private static boolean hotbarCyclerShowButtons = true;
    private static boolean hotbarCyclerScrollToCycle = false;
    // Column Cycler HUD overlay mode. MINI_HOTBAR is the default per
    // Trev's spec — when Column Cycler is enabled, the HUD strip shows
    // automatically. NONE disables the HUD without disabling the
    // feature. Future Diamond Indicators mode will become a third
    // value (per column-cycler.md). Persisted as the enum name.
    private static HudMode columnCyclerHudMode = HudMode.MINI_HOTBAR;

    // ─── Master switches and buttons (settings menu, Reach build stage 3) ──
    // Every feature tab has an on/off and a button toggle (Trev, 2026-09-27).
    // Sort, Move Matching and Restock had no master switch; Restock and Auto
    // Tool Switch had no button. lockGroupsEnabled pauses every lock without
    // deleting any: nothing blocked, nothing drawn (settings-menu.md).
    private static boolean sortEnabled = true;
    private static boolean moveMatchingEnabled = true;
    private static boolean restockEnabled = true;
    private static boolean restockShowButton = true;
    private static boolean autoToolSwitchShowButton = true;
    private static boolean lockGroupsEnabled = true;
    // "Also keep container locks on the server" (Lock groups tab; Reach build
    // stage 4, replacing Inventory Max's containerLocksEnabled). Container
    // locks are always kept on this computer; with this on, each one placed
    // from now on is also written to Inventory Max's shared channel, where the
    // server keeps and enforces it as a plain lock. Default off: container
    // locks are the player's own (Trev 2026-09-07). Needs Inventory Max.
    private static boolean containerLocksOnServer = false;
    // Tooltips: everything Inventory Plus does to tooltips (Trev, 2026-09-30).
    // tooltipsEnabled off means Inventory Plus leaves every tooltip alone.
    // Item tips (durability and food lines) moved from MenuKit in 6.0.0, on
    // by default as players had it there. Hold Ctrl to hide tooltips is on by
    // default too: holding Ctrl over a slot otherwise does nothing.
    private static boolean tooltipsEnabled = true;
    private static boolean itemTipsEnabled = true;
    private static boolean hideTooltipsOnCtrl = true;

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
        // The reach record rides in this file (`plans/reach.md`, "Where it
        // lives"); every change to it saves the whole file, like any setting.
        Reach.onChange(IPConfig::save);
        Path path = filePath();
        if (!Files.exists(path)) {
            InventoryPlusClient.LOGGER.info(
                    "[config] no config file at {} — using defaults", path);
            return;
        }
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            apply(root);
            legacyUsesLockedItems = new LegacyUsesLockedItems(
                    readBool(root, "autoRestockArmorUsesLockedItems", true),
                    readBool(root, "autoRestockToolUsesLockedItems", true),
                    readBool(root, "autoRestockItemUsesLockedItems", true),
                    readBool(root, "autoToolSwitchUsesLockedItems", true));
            legacyCycleLocks = new LegacyCycleLocks(
                    readBool(root, "cycleSlotsLocked", true), readBool(root, "lockCycledRows", true));
            Reach.read(root.has("reach") && root.get("reach").isJsonObject() ? root.getAsJsonObject("reach") : null);
            InventoryPlusClient.LOGGER.info("[config] loaded from {}", path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[config] failed to read {} — using defaults", path, e);
        }
    }

    /** Reads every setting {@code root} has; the rest keep their values. */
    private static void apply(JsonObject root) {
        autoRestockArmor             = readBool(root, "autoRestockArmor",             autoRestockArmor);
        autoRestockArmorBeforeBreak  = readBool(root, "autoRestockArmorBeforeBreak",  autoRestockArmorBeforeBreak);
        autoRestockTool              = readBool(root, "autoRestockTool",              autoRestockTool);
        autoRestockToolBeforeBreak   = readBool(root, "autoRestockToolBeforeBreak",   autoRestockToolBeforeBreak);
        autoRestockItem              = readBool(root, "autoRestockItem",              autoRestockItem);
        autoRestockShulker           = readBool(root, "autoRestockShulker",           autoRestockShulker);
        autoRestockShulkerAmmo       = readBool(root, "autoRestockShulkerAmmo",       autoRestockShulkerAmmo);
        autoRestockBeforeBreakThreshold = readInt(root, "autoRestockBeforeBreakThreshold",
                autoRestockBeforeBreakThreshold);
        autoToolSwitchEnabled  = readBool(root, "autoToolSwitchEnabled",  autoToolSwitchEnabled);
        // Migrate the old boolean (true → AUTOMATIC, false → OFF), then let a
        // present mode key override it.
        if (root.has("autoToolSwitchReturn")) {
            autoToolSwitchReturnMode = readBool(root, "autoToolSwitchReturn", false)
                    ? AutoSwitchReturnMode.AUTOMATIC : AutoSwitchReturnMode.OFF;
        }
        autoToolSwitchReturnMode = AutoSwitchReturnMode.fromName(
                readString(root, "autoToolSwitchReturnMode", null), autoToolSwitchReturnMode);
        autoToolSwitchReturnCooldownSeconds = readInt(root, "autoToolSwitchReturnCooldownSeconds",
                autoToolSwitchReturnCooldownSeconds);
        autoToolSwitchWeapons  = readBool(root, "autoToolSwitchWeapons",  autoToolSwitchWeapons);
        autoToolSwitchAllMobs  = readBool(root, "autoToolSwitchAllMobs",  autoToolSwitchAllMobs);
        autoToolSwitchWeaponPreference = WeaponPreference.fromName(readString(root, "autoToolSwitchWeaponPreference", null), autoToolSwitchWeaponPreference);
        sortShowButton         = readBool(root, "sortShowButton",         sortShowButton);
        moveMatchingShowButtons= readBool(root, "moveMatchingShowButtons",moveMatchingShowButtons);
        lockedSlotsShowButton  = readBool(root, "lockedSlotsShowButton",  lockedSlotsShowButton);
        columnCyclerEnabled       = readBool(root, "columnCyclerEnabled",       columnCyclerEnabled);
        columnCyclerShowButton    = readBool(root, "columnCyclerShowButton",    columnCyclerShowButton);
        columnCyclerScrollToCycle = readBool(root, "columnCyclerScrollToCycle", columnCyclerScrollToCycle);
        hotbarCyclerEnabled       = readBool(root, "hotbarCyclerEnabled",       hotbarCyclerEnabled);
        hotbarCyclerShowButtons   = readBool(root, "hotbarCyclerShowButtons",   hotbarCyclerShowButtons);
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
        itemTipsEnabled           = readBool(root, "itemTipsEnabled",           itemTipsEnabled);
        hideTooltipsOnCtrl        = readBool(root, "hideTooltipsOnCtrl",        hideTooltipsOnCtrl);
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
        root.addProperty("autoRestockBeforeBreakThreshold", autoRestockBeforeBreakThreshold);
        root.addProperty("autoToolSwitchEnabled",   autoToolSwitchEnabled);
        root.addProperty("autoToolSwitchReturnMode", autoToolSwitchReturnMode.name());
        root.addProperty("autoToolSwitchReturnCooldownSeconds", autoToolSwitchReturnCooldownSeconds);
        root.addProperty("autoToolSwitchWeapons",   autoToolSwitchWeapons);
        root.addProperty("autoToolSwitchAllMobs",   autoToolSwitchAllMobs);
        root.addProperty("autoToolSwitchWeaponPreference", autoToolSwitchWeaponPreference.name());
        root.addProperty("sortShowButton",          sortShowButton);
        root.addProperty("moveMatchingShowButtons", moveMatchingShowButtons);
        root.addProperty("lockedSlotsShowButton",   lockedSlotsShowButton);
        root.addProperty("columnCyclerEnabled",       columnCyclerEnabled);
        root.addProperty("columnCyclerShowButton",    columnCyclerShowButton);
        root.addProperty("columnCyclerScrollToCycle", columnCyclerScrollToCycle);
        root.addProperty("hotbarCyclerEnabled",       hotbarCyclerEnabled);
        root.addProperty("hotbarCyclerShowButtons",   hotbarCyclerShowButtons);
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
        root.addProperty("itemTipsEnabled",           itemTipsEnabled);
        root.addProperty("hideTooltipsOnCtrl",        hideTooltipsOnCtrl);
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
                    "[config] failed to write {} — changes won't survive restart",
                    path, e);
        }
    }

    /**
     * The four removed "use locked items" settings as the file last held them
     * (true when absent, their old default). Read by the 1.5.x migration only.
     */
    public record LegacyUsesLockedItems(boolean restockArmor, boolean restockTool, boolean restockItem,
                                        boolean autoToolSwitch) {}

    private static LegacyUsesLockedItems legacyUsesLockedItems = new LegacyUsesLockedItems(true, true, true, true);

    public static LegacyUsesLockedItems legacyUsesLockedItems() { return legacyUsesLockedItems; }

    /**
     * The two removed cycle-lock switches as the file last held them (true
     * when absent, their default): "Lock the slots the cyclers use" and
     * Hotbar Cycler's "Lock cycled rows". Read by the upgrade to feature reach
     * groups only (Trev, 2026-09-30).
     */
    public record LegacyCycleLocks(boolean cycleSlotsLocked, boolean lockCycledRows) {}

    private static LegacyCycleLocks legacyCycleLocks = new LegacyCycleLocks(true, true);

    public static LegacyCycleLocks legacyCycleLocks() { return legacyCycleLocks; }

    /** The old global switch as the file held it, for the 1.5.x pairing drop in LockedSlots. */
    public static boolean legacyCycleSlotsLocked() { return legacyCycleLocks.cycleSlotsLocked(); }

    public static boolean sortEnabled()              { return sortEnabled; }
    public static boolean moveMatchingEnabled()      { return moveMatchingEnabled; }
    public static boolean restockEnabled()           { return restockEnabled; }
    public static boolean restockShowButton()        { return restockShowButton; }
    public static boolean autoToolSwitchShowButton() { return autoToolSwitchShowButton; }
    public static boolean lockGroupsEnabled()        { return lockGroupsEnabled; }
    public static boolean containerLocksOnServer()   { return containerLocksOnServer; }
    public static boolean tooltipsEnabled()          { return tooltipsEnabled; }
    public static boolean itemTipsEnabled()          { return itemTipsEnabled; }
    public static boolean hideTooltipsOnCtrl()       { return hideTooltipsOnCtrl; }
    public static void setSortEnabled(boolean v)              { sortEnabled = v; save(); }
    public static void setMoveMatchingEnabled(boolean v)      { moveMatchingEnabled = v; save(); }
    public static void setRestockEnabled(boolean v)           { restockEnabled = v; save(); }
    public static void setRestockShowButton(boolean v)        { restockShowButton = v; save(); }
    public static void setAutoToolSwitchShowButton(boolean v) { autoToolSwitchShowButton = v; save(); }
    public static void setLockGroupsEnabled(boolean v)        { lockGroupsEnabled = v; save(); }
    public static void setContainerLocksOnServer(boolean v)   { containerLocksOnServer = v; save(); }
    public static void setTooltipsEnabled(boolean v)          { tooltipsEnabled = v; save(); }
    public static void setItemTipsEnabled(boolean v)          { itemTipsEnabled = v; save(); }
    public static void setHideTooltipsOnCtrl(boolean v)       { hideTooltipsOnCtrl = v; save(); }

    // ─── Getters ─────────────────────────────────────────────────────
    public static boolean autoRestockArmor()            { return autoRestockArmor; }
    public static boolean autoRestockArmorBeforeBreak() { return autoRestockArmorBeforeBreak; }
    public static boolean autoRestockTool()             { return autoRestockTool; }
    public static boolean autoRestockToolBeforeBreak()  { return autoRestockToolBeforeBreak; }
    public static boolean autoRestockItem()             { return autoRestockItem; }
    public static boolean autoRestockShulker()          { return autoRestockShulker; }
    public static boolean autoRestockShulkerAmmo()      { return autoRestockShulkerAmmo; }
    public static int autoRestockBeforeBreakThreshold() { return autoRestockBeforeBreakThreshold; }
    public static boolean autoToolSwitchEnabled()       { return autoToolSwitchEnabled; }
    public static AutoSwitchReturnMode autoToolSwitchReturnMode() { return autoToolSwitchReturnMode; }
    public static int autoToolSwitchReturnCooldownSeconds()       { return autoToolSwitchReturnCooldownSeconds; }
    public static boolean autoToolSwitchWeapons()       { return autoToolSwitchWeapons; }
    public static boolean autoToolSwitchAllMobs()       { return autoToolSwitchAllMobs; }
    public static WeaponPreference autoToolSwitchWeaponPreference() { return autoToolSwitchWeaponPreference; }
    public static boolean sortShowButton()              { return sortShowButton; }
    public static boolean moveMatchingShowButtons()     { return moveMatchingShowButtons; }
    public static boolean lockedSlotsShowButton()       { return lockedSlotsShowButton; }
    public static boolean columnCyclerEnabled()         { return columnCyclerEnabled; }
    public static boolean columnCyclerShowButton()      { return columnCyclerShowButton; }
    public static boolean columnCyclerScrollToCycle()   { return columnCyclerScrollToCycle; }
    public static boolean hotbarCyclerEnabled()         { return hotbarCyclerEnabled; }
    public static boolean hotbarCyclerShowButtons()     { return hotbarCyclerShowButtons; }
    public static boolean hotbarCyclerScrollToCycle()   { return hotbarCyclerScrollToCycle; }
    public static HudMode columnCyclerHudMode()         { return columnCyclerHudMode; }

    // ─── Setters ─────────────────────────────────────────────────────
    public static void setAutoRestockArmor(boolean v)            { autoRestockArmor = v; save(); }
    public static void setAutoRestockArmorBeforeBreak(boolean v) { autoRestockArmorBeforeBreak = v; save(); }
    public static void setAutoRestockTool(boolean v)             { autoRestockTool = v; save(); }
    public static void setAutoRestockToolBeforeBreak(boolean v)  { autoRestockToolBeforeBreak = v; save(); }
    public static void setAutoRestockItem(boolean v)             { autoRestockItem = v; save(); }
    public static void setAutoRestockShulker(boolean v)          { autoRestockShulker = v; save(); }
    public static void setAutoRestockShulkerAmmo(boolean v)      { autoRestockShulkerAmmo = v; save(); }
    public static void setAutoRestockBeforeBreakThreshold(int v) { autoRestockBeforeBreakThreshold = v; save(); }
    public static void setAutoToolSwitchEnabled(boolean v)       { autoToolSwitchEnabled = v; save(); }
    public static void setAutoToolSwitchReturnMode(AutoSwitchReturnMode v) { autoToolSwitchReturnMode = v; save(); }
    public static void setAutoToolSwitchReturnCooldownSeconds(int v)       { autoToolSwitchReturnCooldownSeconds = v; save(); }
    public static void setAutoToolSwitchWeapons(boolean v)       { autoToolSwitchWeapons = v; save(); }
    public static void setAutoToolSwitchAllMobs(boolean v)       { autoToolSwitchAllMobs = v; save(); }
    public static void setAutoToolSwitchWeaponPreference(WeaponPreference v) { autoToolSwitchWeaponPreference = v; save(); }
    public static void setSortShowButton(boolean v)              { sortShowButton = v; save(); }
    public static void setMoveMatchingShowButtons(boolean v)     { moveMatchingShowButtons = v; save(); }
    public static void setLockedSlotsShowButton(boolean v)       { lockedSlotsShowButton = v; save(); }
    public static void setColumnCyclerEnabled(boolean v)         { columnCyclerEnabled = v; save(); }
    public static void setColumnCyclerShowButton(boolean v)      { columnCyclerShowButton = v; save(); }
    // Scroll-to-cycle has a single owner: the wheel is one input, so turning
    // it on for one cycler turns it off for the other (cycle-modes.md). Both
    // setters enforce it, so the rule holds wherever it's set from.
    public static void setColumnCyclerScrollToCycle(boolean v) {
        columnCyclerScrollToCycle = v;
        if (v) hotbarCyclerScrollToCycle = false;
        save();
    }
    public static void setHotbarCyclerEnabled(boolean v)         { hotbarCyclerEnabled = v; save(); }
    public static void setHotbarCyclerShowButtons(boolean v)     { hotbarCyclerShowButtons = v; save(); }
    public static void setHotbarCyclerScrollToCycle(boolean v) {
        hotbarCyclerScrollToCycle = v;
        if (v) columnCyclerScrollToCycle = false;
        save();
    }
    public static void setColumnCyclerHudMode(HudMode v)         { columnCyclerHudMode = v; save(); }
}
