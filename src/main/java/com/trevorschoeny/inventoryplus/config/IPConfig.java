package com.trevorschoeny.inventoryplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
    private static boolean autoToolSwitchReturn = false;     // sub of Enabled
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
    // cycleSlotsLocked binds cycle ⇔ lock under ON (default): a slot's
    // cycle membership and lock state are toggled as one unit. L is a
    // no-op on cycle slots; the lock icon is suppressed (cycle icon
    // represents both). Under OFF, cycle and lock are fully independent.
    private static boolean columnCyclerEnabled = false;
    private static boolean columnCyclerShowButton = true;
    private static boolean cycleSlotsLocked = true;
    // Scroll wheel as alternative trigger for forward/backward cycle.
    // Default OFF; opt-in trade-off — scrolling on a cycle-active column
    // rotates the cycle instead of switching the hotbar slot.
    private static boolean columnCyclerScrollToCycle = false;
    // Column Cycler HUD overlay mode. MINI_HOTBAR is the default per
    // Trev's spec — when Column Cycler is enabled, the HUD strip shows
    // automatically. NONE disables the HUD without disabling the
    // feature. Future Diamond Indicators mode will become a third
    // value (per column-cycler.md). Persisted as the enum name.
    private static HudMode columnCyclerHudMode = HudMode.MINI_HOTBAR;

    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("config.json");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) {
            InventoryPlusClient.LOGGER.info(
                    "[config] no config file at {} — using defaults", path);
            return;
        }
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            autoRestockArmor             = readBool(root, "autoRestockArmor",             autoRestockArmor);
            autoRestockArmorBeforeBreak  = readBool(root, "autoRestockArmorBeforeBreak",  autoRestockArmorBeforeBreak);
            autoRestockTool              = readBool(root, "autoRestockTool",              autoRestockTool);
            autoRestockToolBeforeBreak   = readBool(root, "autoRestockToolBeforeBreak",   autoRestockToolBeforeBreak);
            autoRestockItem              = readBool(root, "autoRestockItem",              autoRestockItem);
            autoRestockShulker           = readBool(root, "autoRestockShulker",           autoRestockShulker);
            autoRestockShulkerAmmo       = readBool(root, "autoRestockShulkerAmmo",       autoRestockShulkerAmmo);
            autoToolSwitchEnabled  = readBool(root, "autoToolSwitchEnabled",  autoToolSwitchEnabled);
            autoToolSwitchReturn   = readBool(root, "autoToolSwitchReturn",   autoToolSwitchReturn);
            autoToolSwitchWeapons  = readBool(root, "autoToolSwitchWeapons",  autoToolSwitchWeapons);
            autoToolSwitchAllMobs  = readBool(root, "autoToolSwitchAllMobs",  autoToolSwitchAllMobs);
            autoToolSwitchWeaponPreference = WeaponPreference.fromName(readString(root, "autoToolSwitchWeaponPreference", null), autoToolSwitchWeaponPreference);
            sortShowButton         = readBool(root, "sortShowButton",         sortShowButton);
            moveMatchingShowButtons= readBool(root, "moveMatchingShowButtons",moveMatchingShowButtons);
            lockedSlotsShowButton  = readBool(root, "lockedSlotsShowButton",  lockedSlotsShowButton);
            columnCyclerEnabled       = readBool(root, "columnCyclerEnabled",       columnCyclerEnabled);
            columnCyclerShowButton    = readBool(root, "columnCyclerShowButton",    columnCyclerShowButton);
            cycleSlotsLocked          = readBool(root, "cycleSlotsLocked",          cycleSlotsLocked);
            columnCyclerScrollToCycle = readBool(root, "columnCyclerScrollToCycle", columnCyclerScrollToCycle);
            columnCyclerHudMode       = HudMode.fromName(readString(root, "columnCyclerHudMode", null), columnCyclerHudMode);
            InventoryPlusClient.LOGGER.info("[config] loaded from {}", path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[config] failed to read {} — using defaults", path, e);
        }
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

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.addProperty("autoRestockArmor",            autoRestockArmor);
            root.addProperty("autoRestockArmorBeforeBreak", autoRestockArmorBeforeBreak);
            root.addProperty("autoRestockTool",             autoRestockTool);
            root.addProperty("autoRestockToolBeforeBreak",  autoRestockToolBeforeBreak);
            root.addProperty("autoRestockItem",             autoRestockItem);
            root.addProperty("autoRestockShulker",          autoRestockShulker);
            root.addProperty("autoRestockShulkerAmmo",      autoRestockShulkerAmmo);
            root.addProperty("autoToolSwitchEnabled",   autoToolSwitchEnabled);
            root.addProperty("autoToolSwitchReturn",    autoToolSwitchReturn);
            root.addProperty("autoToolSwitchWeapons",   autoToolSwitchWeapons);
            root.addProperty("autoToolSwitchAllMobs",   autoToolSwitchAllMobs);
            root.addProperty("autoToolSwitchWeaponPreference", autoToolSwitchWeaponPreference.name());
            root.addProperty("sortShowButton",          sortShowButton);
            root.addProperty("moveMatchingShowButtons", moveMatchingShowButtons);
            root.addProperty("lockedSlotsShowButton",   lockedSlotsShowButton);
            root.addProperty("columnCyclerEnabled",       columnCyclerEnabled);
            root.addProperty("columnCyclerShowButton",    columnCyclerShowButton);
            root.addProperty("cycleSlotsLocked",          cycleSlotsLocked);
            root.addProperty("columnCyclerScrollToCycle", columnCyclerScrollToCycle);
            root.addProperty("columnCyclerHudMode",       columnCyclerHudMode.name());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[config] failed to write {} — changes won't survive restart",
                    path, e);
        }
    }

    // ─── Getters ─────────────────────────────────────────────────────
    public static boolean autoRestockArmor()            { return autoRestockArmor; }
    public static boolean autoRestockArmorBeforeBreak() { return autoRestockArmorBeforeBreak; }
    public static boolean autoRestockTool()             { return autoRestockTool; }
    public static boolean autoRestockToolBeforeBreak()  { return autoRestockToolBeforeBreak; }
    public static boolean autoRestockItem()             { return autoRestockItem; }
    public static boolean autoRestockShulker()          { return autoRestockShulker; }
    public static boolean autoRestockShulkerAmmo()      { return autoRestockShulkerAmmo; }
    public static boolean autoToolSwitchEnabled()       { return autoToolSwitchEnabled; }
    public static boolean autoToolSwitchReturn()        { return autoToolSwitchReturn; }
    public static boolean autoToolSwitchWeapons()       { return autoToolSwitchWeapons; }
    public static boolean autoToolSwitchAllMobs()       { return autoToolSwitchAllMobs; }
    public static WeaponPreference autoToolSwitchWeaponPreference() { return autoToolSwitchWeaponPreference; }
    public static boolean sortShowButton()              { return sortShowButton; }
    public static boolean moveMatchingShowButtons()     { return moveMatchingShowButtons; }
    public static boolean lockedSlotsShowButton()       { return lockedSlotsShowButton; }
    public static boolean columnCyclerEnabled()         { return columnCyclerEnabled; }
    public static boolean columnCyclerShowButton()      { return columnCyclerShowButton; }
    public static boolean cycleSlotsLocked()            { return cycleSlotsLocked; }
    public static boolean columnCyclerScrollToCycle()   { return columnCyclerScrollToCycle; }
    public static HudMode columnCyclerHudMode()         { return columnCyclerHudMode; }

    // ─── Setters ─────────────────────────────────────────────────────
    public static void setAutoRestockArmor(boolean v)            { autoRestockArmor = v; save(); }
    public static void setAutoRestockArmorBeforeBreak(boolean v) { autoRestockArmorBeforeBreak = v; save(); }
    public static void setAutoRestockTool(boolean v)             { autoRestockTool = v; save(); }
    public static void setAutoRestockToolBeforeBreak(boolean v)  { autoRestockToolBeforeBreak = v; save(); }
    public static void setAutoRestockItem(boolean v)             { autoRestockItem = v; save(); }
    public static void setAutoRestockShulker(boolean v)          { autoRestockShulker = v; save(); }
    public static void setAutoRestockShulkerAmmo(boolean v)      { autoRestockShulkerAmmo = v; save(); }
    public static void setAutoToolSwitchEnabled(boolean v)       { autoToolSwitchEnabled = v; save(); }
    public static void setAutoToolSwitchReturn(boolean v)        { autoToolSwitchReturn = v; save(); }
    public static void setAutoToolSwitchWeapons(boolean v)       { autoToolSwitchWeapons = v; save(); }
    public static void setAutoToolSwitchAllMobs(boolean v)       { autoToolSwitchAllMobs = v; save(); }
    public static void setAutoToolSwitchWeaponPreference(WeaponPreference v) { autoToolSwitchWeaponPreference = v; save(); }
    public static void setSortShowButton(boolean v)              { sortShowButton = v; save(); }
    public static void setMoveMatchingShowButtons(boolean v)     { moveMatchingShowButtons = v; save(); }
    public static void setLockedSlotsShowButton(boolean v)       { lockedSlotsShowButton = v; save(); }
    public static void setColumnCyclerEnabled(boolean v)         { columnCyclerEnabled = v; save(); }
    public static void setColumnCyclerShowButton(boolean v)      { columnCyclerShowButton = v; save(); }
    public static void setCycleSlotsLocked(boolean v)            { cycleSlotsLocked = v; save(); }
    public static void setColumnCyclerScrollToCycle(boolean v)   { columnCyclerScrollToCycle = v; save(); }
    public static void setColumnCyclerHudMode(HudMode v)         { columnCyclerHudMode = v; save(); }
}
