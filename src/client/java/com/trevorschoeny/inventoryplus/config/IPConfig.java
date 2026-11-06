package com.trevorschoeny.inventoryplus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;
import com.trevorschoeny.inventoryplus.hotbarsets.HotbarSets;

import com.mojang.serialization.JsonOps;

import org.jetbrains.annotations.Nullable;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

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
 *   "autoRestockGear":     true,
 *   "autoRestockItem":     true,
 *   "sortShowButton":      true,
 *   "moveMatchingShowButtons": true
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Auto-Restock. Offhand restock is always on: the offhand is just another
    // active slot, so there is no reason to gate it.
    // Gear: tools, weapons, shields and armor, one switch with one "Swap before it
    // breaks" (once separate for tools and armor, merged in load()).
    private static boolean autoRestockGear = true;
    private static boolean autoRestockGearBeforeBreak = false; // sub of Gear
    private static boolean autoRestockItem = true;
    private static boolean autoRestockShulker = false;          // parent
    private static boolean autoRestockShulkerAmmo = false;      // sub of Shulker
    // "Also restock from shulker boxes" (Restock): every restock type may take
    // from a shulker box carried in the inventory, never a bundle or the ender
    // chest. Off by default. Auto Tool Switch and pick block follow it too: one
    // setting for everything that takes from a carried box.
    private static boolean restockFromShulkers = false;
    // Durability floor for the two Before-Break swaps (armor and tool share
    // it): a swap fires when remaining durability sits at or below this.
    private static int autoRestockBeforeBreakThreshold = 10;

    // The Shulker and ShulkerAmmo flags above are state-only: the mechanics
    // behind them aren't implemented yet, so toggling them persists the choice
    // but changes nothing in game. ArmorBeforeBreak and ToolBeforeBreak are
    // live (AutoRestockTicker section 4).

    // Auto Tool Switch, off by default. When on, hitting a block or attacking a
    // hostile mob swaps the right tool or weapon into the active hand. See the
    // AutoToolSwitch class javadoc for the three-tier resolution and the switch
    // back.
    private static boolean autoToolSwitchEnabled = false;
    // The only two choices besides on/off: Sneak pauses it (the player's
    // always-available override), and it leaves a held weapon alone when a
    // block is hit, so a missed swing in a fight doesn't swap the sword for a
    // shovel. Everything else is fixed behavior, see AutoToolSwitch.
    private static boolean autoToolSwitchSneakPauses = true;
    private static boolean autoToolSwitchKeepWeapon = true;

    // Show Buttons
    private static boolean sortShowButton = true;
    private static boolean moveMatchingShowButtons = true;

    // Column Cycler (Power Users). Off by default, like every Power Users
    // feature. When off, the toolbar button hides and the C keybind is a
    // no-op. showButton hides the button while the feature stays enabled, for
    // players who prefer the keybind alone (off by default: the inventory had
    // too many buttons). The global cycleSlotsLocked is gone (read once below,
    // for the upgrade).
    private static boolean columnCyclerEnabled = false;
    private static boolean columnCyclerShowButton = false;
    // Scroll wheel as an alternative trigger for forward and backward cycle.
    // Off by default because it is a trade-off: scrolling on a cycle-active
    // column rotates the cycle instead of switching the hotbar slot.
    private static boolean columnCyclerScrollToCycle = false;

    // Hotbar Sets: hold a key, scroll to a row or a saved set, let go. It replaced
    // Hotbar Cycler (off by default); this one is on, since nothing happens until
    // the key is held. Its switch in the inventory's right column keeps Hotbar
    // Cycler's setting. Where the inventory rows go in creative's list.
    private static boolean hotbarSetsEnabled = true;
    private static boolean hotbarSetsShowButton = false;
    private static HotbarSets.CreativeRows hotbarSetsCreativeRows = HotbarSets.CreativeRows.TOP;
    // Column Cycler HUD overlay mode. MINI_HOTBAR by default, so the HUD strip
    // shows as soon as Column Cycler is enabled. NONE disables the HUD without
    // disabling the feature. Persisted as the enum name.
    private static HudMode columnCyclerHudMode = HudMode.MINI_HOTBAR;

    // Master switches and buttons. Every feature tab has an on/off and a
    // button toggle. Locks have neither: a lock always holds, and edit mode's
    // button is on every container toolbar.
    private static boolean sortEnabled = true;
    private static boolean moveMatchingEnabled = true;
    private static boolean restockEnabled = true;
    private static boolean restockShowButton = true;
    private static boolean autoToolSwitchShowButton = true;
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
    // default. Replaces tooltipCursorAboveSlots, which moved the cursor
    // even with the game window in the background;
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
            // Reach's pickup choice, kept as an Intake until every world has had it.
            legacyPickupIntake = root.has("legacyPickupIntake")
                    ? Intake.CODEC.parse(JsonOps.INSTANCE, root.get("legacyPickupIntake"))
                            .result().orElse(null)
                    : pickupIntakeOf(root.has("reach") && root.get("reach").isJsonObject()
                            ? root.getAsJsonObject("reach") : null);
            // The file is written without "reach" from now on; its pickup choice
            // lives on in config until every world has had it (IntakeClient).
            if (legacyPickupIntake != null && root.has("intakeMigratedWorlds")) {
                for (var w : root.getAsJsonArray("intakeMigratedWorlds")) intakeMigratedWorlds.add(w.getAsString());
            }
            InventoryPlusClient.LOGGER.info("[config] loaded from {}", path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[config] failed to read {}, using defaults", path, e);
        }
    }

    /** Reads every setting {@code root} has. The rest keep their values. */
    private static void apply(JsonObject root) {
        // The old tools and armor switches merge: on if either was.
        autoRestockGear = root.has("autoRestockGear") ? readBool(root, "autoRestockGear", autoRestockGear)
                : readBool(root, "autoRestockTool", true) || readBool(root, "autoRestockArmor", true);
        autoRestockGearBeforeBreak = root.has("autoRestockGearBeforeBreak")
                ? readBool(root, "autoRestockGearBeforeBreak", autoRestockGearBeforeBreak)
                : readBool(root, "autoRestockToolBeforeBreak", false) || readBool(root, "autoRestockArmorBeforeBreak", false);
        autoRestockItem              = readBool(root, "autoRestockItem",              autoRestockItem);
        autoRestockShulker           = readBool(root, "autoRestockShulker",           autoRestockShulker);
        autoRestockShulkerAmmo       = readBool(root, "autoRestockShulkerAmmo",       autoRestockShulkerAmmo);
        restockFromShulkers = restockFromShulkersOf(root, restockFromShulkers);
        autoRestockBeforeBreakThreshold = readInt(root, "autoRestockBeforeBreakThreshold",
                autoRestockBeforeBreakThreshold);
        autoToolSwitchEnabled  = readBool(root, "autoToolSwitchEnabled",  autoToolSwitchEnabled);
        autoToolSwitchSneakPauses = readBool(root, "autoToolSwitchSneakPauses", autoToolSwitchSneakPauses);
        autoToolSwitchKeepWeapon = readBool(root, "autoToolSwitchKeepWeapon", autoToolSwitchKeepWeapon);
        sortShowButton         = readBool(root, "sortShowButton",         sortShowButton);
        moveMatchingShowButtons= readBool(root, "moveMatchingShowButtons",moveMatchingShowButtons);
        columnCyclerEnabled       = readBool(root, "columnCyclerEnabled",       columnCyclerEnabled);
        columnCyclerShowButton    = readBool(root, "columnCyclerShowButton",    columnCyclerShowButton);
        columnCyclerScrollToCycle = readBool(root, "columnCyclerScrollToCycle", columnCyclerScrollToCycle);
        hotbarSetsEnabled         = readBool(root, "hotbarSetsEnabled",         hotbarSetsEnabled);
        hotbarSetsShowButton      = readBool(root, "hotbarSetsShowButton",
                readBool(root, "hotbarCyclerShowButton", hotbarSetsShowButton));
        hotbarSetsCreativeRows    = creativeRows(readString(root, "hotbarSetsCreativeRows", null), hotbarSetsCreativeRows);
        columnCyclerHudMode       = HudMode.fromName(readString(root, "columnCyclerHudMode", null), columnCyclerHudMode);
        sortEnabled               = readBool(root, "sortEnabled",               sortEnabled);
        moveMatchingEnabled       = readBool(root, "moveMatchingEnabled",       moveMatchingEnabled);
        restockEnabled            = readBool(root, "restockEnabled",            restockEnabled);
        restockShowButton         = readBool(root, "restockShowButton",         restockShowButton);
        autoToolSwitchShowButton  = readBool(root, "autoToolSwitchShowButton",  autoToolSwitchShowButton);
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

    /** Every setting as the file writes it (without the version or the Intake migration record). */
    private static JsonObject settingsJson() {
        JsonObject root = new JsonObject();
        root.addProperty("autoRestockGear",             autoRestockGear);
        root.addProperty("autoRestockGearBeforeBreak",  autoRestockGearBeforeBreak);
        root.addProperty("autoRestockItem",             autoRestockItem);
        root.addProperty("autoRestockShulker",          autoRestockShulker);
        root.addProperty("autoRestockShulkerAmmo",      autoRestockShulkerAmmo);
        root.addProperty("restockFromShulkers",         restockFromShulkers);
        root.addProperty("autoRestockBeforeBreakThreshold", autoRestockBeforeBreakThreshold);
        root.addProperty("autoToolSwitchEnabled",   autoToolSwitchEnabled);
        root.addProperty("autoToolSwitchSneakPauses", autoToolSwitchSneakPauses);
        root.addProperty("autoToolSwitchKeepWeapon", autoToolSwitchKeepWeapon);
        root.addProperty("sortShowButton",          sortShowButton);
        root.addProperty("moveMatchingShowButtons", moveMatchingShowButtons);
        root.addProperty("columnCyclerEnabled",       columnCyclerEnabled);
        root.addProperty("columnCyclerShowButton",    columnCyclerShowButton);
        root.addProperty("columnCyclerScrollToCycle", columnCyclerScrollToCycle);
        root.addProperty("hotbarSetsEnabled",         hotbarSetsEnabled);
        root.addProperty("hotbarSetsShowButton",      hotbarSetsShowButton);
        root.addProperty("hotbarSetsCreativeRows",    hotbarSetsCreativeRows.name());
        root.addProperty("columnCyclerHudMode",       columnCyclerHudMode.name());
        root.addProperty("sortEnabled",               sortEnabled);
        root.addProperty("moveMatchingEnabled",       moveMatchingEnabled);
        root.addProperty("restockEnabled",            restockEnabled);
        root.addProperty("restockShowButton",         restockShowButton);
        root.addProperty("autoToolSwitchShowButton",  autoToolSwitchShowButton);
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
            if (legacyPickupIntake != null) {
                JsonArray worlds = new JsonArray();
                for (String w : intakeMigratedWorlds) worlds.add(w);
                root.add("intakeMigratedWorlds", worlds);
                root.add("legacyPickupIntake", Intake.CODEC.encodeStart(JsonOps.INSTANCE,
                        legacyPickupIntake).getOrThrow());
            }
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

    // Reach's pickup choice, for the Intake migration

    /**
     * The Intake that stands closest to what Reach's pickups did with carried shulker
     * boxes before 4.0.0, or {@code null} when they left them alone (the default).
     * IntakeClient puts it on the boxes the player carries, once per world.
     */
    private static @Nullable Intake legacyPickupIntake;
    private static final Set<String> intakeMigratedWorlds = new TreeSet<>();

    public static @Nullable Intake legacyPickupIntake() { return legacyPickupIntake; }

    /** Whether world {@code worldId}'s carried boxes still need {@link #legacyPickupIntake}. */
    public static boolean needsIntakeMigration(String worldId) {
        return legacyPickupIntake != null && !intakeMigratedWorlds.contains(worldId);
    }

    public static void markIntakeMigrated(String worldId) {
        if (intakeMigratedWorlds.add(worldId)) save();
    }

    /**
     * Reach's version 3 record, read for its one choice that survives: pickups with
     * Shulker Boxes ticked under Empty slots took anything into carried boxes before
     * the inventory (Before inventory); ticked only under Matching, only what a box
     * held (Before inventory, Matching only). An untouched operation left boxes alone.
     * Bundles and the ender chest have no Intake, so their boxes are dropped.
     */
    public static @Nullable Intake pickupIntakeOf(@Nullable JsonObject reach) {
        if (reach == null) return null;
        int version = reach.has("version") ? reach.get("version").getAsInt() : 0;
        if (version < 3) return null;   // version 2 had no carried places
        String op = "menukit:world_pickup", shulkers = "inventoryplus:place/shulker_boxes";
        if (ticks(reach, "denied", op, shulkers)) return new Intake(Intake.When.BEFORE, false);
        if (ticks(reach, "matching", op, shulkers)) return new Intake(Intake.When.BEFORE, true);
        return null;
    }

    /**
     * "Also restock from shulker boxes" as {@code root} has it. Before 4.0.0 each restock
     * type had its own Shulker Boxes, Bundles and Your Ender Chest switches: a file from
     * then turns it on if any type reached into shulker boxes, and so does Auto Tool
     * Switch's or pick block's own shulker box switch, which it now covers too. Bundles
     * and the ender chest are no longer taken from. {@code current} when {@code root}
     * says nothing.
     */
    public static boolean restockFromShulkersOf(JsonObject root, boolean current) {
        if (root.has("restockFromShulkers")) return readBool(root, "restockFromShulkers", current);
        boolean any = false;
        for (String kind : new String[] {"Items", "Tools", "Armor", "Ammo"}) {
            any |= readBool(root, "restock" + kind + "FromShulkers", false);
        }
        any |= readBool(root, "pickFromShulkers", false) || readBool(root, "autoToolSwitchFromShulkers", false);
        return current || any;
    }

    /** Whether {@code list}'s saved entry for {@code op} leaves {@code key} ticked. No entry: the default, unticked. */
    private static boolean ticks(JsonObject reach, String list, String op, String key) {
        if (!reach.has(list) || !reach.get(list).isJsonObject()) return false;
        JsonObject lists = reach.getAsJsonObject(list);
        if (!lists.has(op) || !lists.get(op).isJsonArray()) return false;
        for (var k : lists.getAsJsonArray(op)) if (k.getAsString().equals(key)) return false;
        return true;
    }

    public static boolean sortEnabled()              { return sortEnabled; }
    public static boolean moveMatchingEnabled()      { return moveMatchingEnabled; }
    public static boolean restockEnabled()           { return restockEnabled; }
    public static boolean restockShowButton()        { return restockShowButton; }
    public static boolean autoToolSwitchShowButton() { return autoToolSwitchShowButton; }
    public static boolean restockFromShulkers()      { return restockFromShulkers; }
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
    public static void setRestockFromShulkers(boolean v)      { restockFromShulkers = v; save(); }
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
    public static boolean autoRestockGear()             { return autoRestockGear; }
    public static boolean autoRestockGearBeforeBreak()  { return autoRestockGearBeforeBreak; }
    public static boolean autoRestockItem()             { return autoRestockItem; }
    public static boolean autoRestockShulker()          { return autoRestockShulker; }
    public static boolean autoRestockShulkerAmmo()      { return autoRestockShulkerAmmo; }
    public static int autoRestockBeforeBreakThreshold() { return autoRestockBeforeBreakThreshold; }
    public static boolean autoToolSwitchEnabled()       { return autoToolSwitchEnabled; }
    public static boolean autoToolSwitchSneakPauses()   { return autoToolSwitchSneakPauses; }
    public static boolean autoToolSwitchKeepWeapon()    { return autoToolSwitchKeepWeapon; }
    public static boolean sortShowButton()              { return sortShowButton; }
    public static boolean moveMatchingShowButtons()     { return moveMatchingShowButtons; }
    public static boolean columnCyclerEnabled()         { return columnCyclerEnabled; }
    public static boolean columnCyclerShowButton()      { return columnCyclerShowButton; }
    public static boolean columnCyclerScrollToCycle()   { return columnCyclerScrollToCycle; }
    public static boolean hotbarSetsEnabled()           { return hotbarSetsEnabled; }
    public static boolean hotbarSetsShowButton()        { return hotbarSetsShowButton; }
    public static HotbarSets.CreativeRows hotbarSetsCreativeRows() { return hotbarSetsCreativeRows; }
    public static HudMode columnCyclerHudMode()         { return columnCyclerHudMode; }

    // Setters
    public static void setAutoRestockGear(boolean v)             { autoRestockGear = v; save(); }
    public static void setAutoRestockGearBeforeBreak(boolean v)  { autoRestockGearBeforeBreak = v; save(); }
    public static void setAutoRestockItem(boolean v)             { autoRestockItem = v; save(); }
    public static void setAutoRestockShulker(boolean v)          { autoRestockShulker = v; save(); }
    public static void setAutoRestockShulkerAmmo(boolean v)      { autoRestockShulkerAmmo = v; save(); }
    public static void setAutoRestockBeforeBreakThreshold(int v) { autoRestockBeforeBreakThreshold = v; save(); }
    public static void setAutoToolSwitchEnabled(boolean v)       { autoToolSwitchEnabled = v; save(); }
    public static void setAutoToolSwitchSneakPauses(boolean v)   { autoToolSwitchSneakPauses = v; save(); }
    public static void setAutoToolSwitchKeepWeapon(boolean v)    { autoToolSwitchKeepWeapon = v; save(); }
    public static void setSortShowButton(boolean v)              { sortShowButton = v; save(); }
    public static void setMoveMatchingShowButtons(boolean v)     { moveMatchingShowButtons = v; save(); }
    public static void setColumnCyclerEnabled(boolean v)         { columnCyclerEnabled = v; save(); }
    public static void setColumnCyclerShowButton(boolean v)      { columnCyclerShowButton = v; save(); }
    public static void setColumnCyclerScrollToCycle(boolean v)   { columnCyclerScrollToCycle = v; save(); }
    public static void setHotbarSetsEnabled(boolean v)           { hotbarSetsEnabled = v; save(); }
    public static void setHotbarSetsShowButton(boolean v)        { hotbarSetsShowButton = v; save(); }
    public static void setHotbarSetsCreativeRows(HotbarSets.CreativeRows v) { hotbarSetsCreativeRows = v; save(); }

    private static HotbarSets.CreativeRows creativeRows(@Nullable String name, HotbarSets.CreativeRows fallback) {
        if (name == null) return fallback;
        try {
            return HotbarSets.CreativeRows.valueOf(name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
    public static void setColumnCyclerHudMode(HudMode v)         { columnCyclerHudMode = v; save(); }
}
