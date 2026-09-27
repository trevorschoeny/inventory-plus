package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPConfigScreen;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.settings.SettingsBody.Bool;
import com.trevorschoeny.inventoryplus.settings.SettingsBody.Place;

import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.SlotGroupCategory;
import com.trevlar.menukit.inject.SlotGroupId;
import com.trevlar.menukit.inject.SlotGroups;
import com.trevlar.menukit.window.BehaviorKey;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.SlotOperations;
import com.trevorschoeny.keybindery.chord.ChordButton;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.trevlar.menukit.core.SlotGroupCategory.CHEST_STORAGE;
import static com.trevlar.menukit.core.SlotGroupCategory.DISPENSER_STORAGE;
import static com.trevlar.menukit.core.SlotGroupCategory.HOPPER_STORAGE;
import static com.trevlar.menukit.core.SlotGroupCategory.MOUNT_STORAGE;
import static com.trevlar.menukit.core.SlotGroupCategory.PLAYER_ARMOR;
import static com.trevlar.menukit.core.SlotGroupCategory.PLAYER_HOTBAR;
import static com.trevlar.menukit.core.SlotGroupCategory.PLAYER_INVENTORY;
import static com.trevlar.menukit.core.SlotGroupCategory.PLAYER_OFFHAND;
import static com.trevlar.menukit.core.SlotGroupCategory.SHULKER_STORAGE;

/**
 * The body of every settings tab (plan: Leadership drive,
 * {@code mods/inventory-plus/plans/settings-menu.md}).
 *
 * <p>Every tab has one frame (Trev, 2026-09-27): Back to game and Reset, the
 * title, a description, the feature's on/off switch (feature tabs and Lock
 * groups), then its settings one per line. Every reach lives in the Reach
 * tab; feature tabs have none.
 *
 * <p>Controls with a real setting behind them read and write it live; the
 * rest are greyed placeholders showing their planned default (see
 * {@link SettingsBody}).
 *
 * <p>The three Inventory Max bodies are Inventory Plus's own copy, drawn from
 * {@code IMConfig}'s settings, because Inventory Plus cannot import Inventory
 * Max. They are shown only as stand-ins, greyed, until Inventory Max replaces
 * them with its real tabs.
 */
final class SettingsTabs {

    private SettingsTabs() {}

    // ── The default lock groups (lock-groups.md, "The defaults' operations") ─
    //
    // Written out until Lock Groups is built. Used for each operation's lock
    // group checkboxes and its closed summary in Reach, so the two agree.

    private static final Set<String> SLOT_LOCK_STOPS = Set.of(
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "menukit:shift_click_in", "menukit:shift_click_out", "menukit:collect",
            "menukit:world_pickup", "menukit:drop", "menukit:drop_stack", "menukit:drag_fill",
            "menukit:hotbar_swap", "menukit:offhand_swap", "inventoryplus:restock_take");

    private static final Set<String> EXACT_ITEM_STOPS = Set.of(
            "menukit:drop", "menukit:drop_stack", "menukit:shift_click_out", "menukit:collect",
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "inventoryplus:column_cycle", "inventoryplus:hotbar_cycle");

    /** Grey, the colour both default groups start with. */
    private static final int GROUP_GREY = 0xFF8B8B8B;

    /**
     * A lock group as the scaffold shows it: its name and kind, the operations
     * it stops, and its key. {@code itemKind}: it protects an item already in a
     * slot, so it is left off operations that only put items in
     * (lock-groups.md; settings-menu.md, "Only what applies").
     */
    private record LockGroup(String name, String kind, boolean itemKind, Set<String> stops,
                             @Nullable KeyMapping key) {}

    private static List<LockGroup> lockGroupList() {
        return List.of(
                new LockGroup("Slot lock", "Slot", false, SLOT_LOCK_STOPS, IPKeybinds.LOCK_SLOT),
                // A group's own key needs Keybindery's runtime bindings (agreed,
                // not built), so the Exact item group has none yet.
                new LockGroup("Exact item", "Exact item", true, EXACT_ITEM_STOPS, null));
    }

    // ── What each operation can reach (placeholder, reach.md) ────────────
    //
    // ponytail: best readings written out until MenuKit's declaration of which
    // groups an operation can act on exists. An operation absent from a map
    // takes the default: every vanilla slot group, every mod's group, no place.

    /** Vanilla slot groups (by category) each feature can act on. */
    private static final Map<String, Set<SlotGroupCategory>> VANILLA_REACH = Map.of(
            "inventoryplus:sort", Set.of(PLAYER_INVENTORY, CHEST_STORAGE, SHULKER_STORAGE,
                    DISPENSER_STORAGE, HOPPER_STORAGE, MOUNT_STORAGE),
            "inventoryplus:move_matching_out", Set.of(PLAYER_INVENTORY, PLAYER_HOTBAR, CHEST_STORAGE,
                    SHULKER_STORAGE, DISPENSER_STORAGE, HOPPER_STORAGE, MOUNT_STORAGE),
            "inventoryplus:move_matching_in", Set.of(PLAYER_INVENTORY, PLAYER_HOTBAR, CHEST_STORAGE,
                    SHULKER_STORAGE, DISPENSER_STORAGE, HOPPER_STORAGE, MOUNT_STORAGE),
            "inventoryplus:restock_take", Set.of(PLAYER_INVENTORY, PLAYER_HOTBAR),
            "inventoryplus:restock_put", Set.of(PLAYER_HOTBAR, PLAYER_ARMOR, PLAYER_OFFHAND),
            "inventoryplus:auto_tool_switch", Set.of(PLAYER_INVENTORY, PLAYER_HOTBAR),
            // The cyclers offer the groups their slots come from.
            "inventoryplus:column_cycle", Set.of(PLAYER_INVENTORY, PLAYER_HOTBAR),
            "inventoryplus:hotbar_cycle", Set.of(PLAYER_INVENTORY, PLAYER_HOTBAR),
            "inventorymax:pocket_cycle", Set.of(PLAYER_HOTBAR));

    /**
     * Operations that opt out of mods' slot groups, with the few of those
     * groups they do take (listing keys). The cyclers only move through
     * their own slots.
     */
    private static final Map<String, Set<String>> MOD_GROUPS_ONLY = Map.of(
            "inventoryplus:column_cycle", Set.of(),
            "inventoryplus:hotbar_cycle", Set.of(),
            "inventorymax:pocket_cycle", Set.of("inventorymax:pockets"));

    /** Places (things carried that aren't slots) an operation reaches into today. */
    private static List<Place> placesOf(String operation) {
        // Only Restock reaches into anything today: shulker boxes, when its
        // Pull from Shulker Boxes option is on.
        return operation.equals("inventoryplus:restock_take")
                ? List.of(new Place(Component.literal("Shulker Boxes"), IPConfig.autoRestockShulker()))
                : List.of();
    }

    // ── General ─────────────────────────────────────────────────────────

    static List<PanelElement> general(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .frame("General", "Settings for the whole menu. Greyed settings aren't built yet.")
                .buttons("Reset everything");
        b.heading("Presets")
                .line("Keep your settings in a file, or share them by pasting.")
                .buttons("Save to file", "Load from file")
                .buttons("Copy to clipboard", "Paste from clipboard");
        // The advert for Inventory Max's tabs; with Inventory Max installed the
        // stand-ins are gone, so there is nothing for this to hide.
        if (!maxInstalled) {
            b.heading("Inventory Max")
                    .checkbox("Show Inventory Max tabs", true);
        }
        b.heading("Keys")
                .key(IPKeybinds.OPEN_SETTINGS);
        // ponytail: the one live control, so the settings that work stay reachable
        // until this menu is wired; it goes when the menu does the saving.
        b.heading("Old settings screen")
                .line("The settings that work today, until this menu takes over.")
                .button("Open the old settings screen", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.setScreen(IPConfigScreen.create(mc.gui.screen()));
                });
        return b.build();
    }

    // ── Reach ───────────────────────────────────────────────────────────

    /**
     * Every operation MenuKit knows, under three headings (Vanilla, Inventory
     * Plus and Inventory Max, Other mods; an empty heading is left out), one
     * collapsible section each. Open, a section shows the operation's
     * description, its checkboxes in five labelled blocks, and Revert to
     * default. A slot is reached only if every group it is in is checked, so
     * a lock group's cleared box is the lock (settings-menu.md, "Reach").
     */
    static List<PanelElement> reach() {
        SettingsBody b = new SettingsBody().frame("Reach",
                "Choose where each move may take items from and put them. A cleared box means the "
                        + "move never touches that group. Clear a lock group's box to make it stop that move.");
        List<BehaviorKey<?>> vanilla = new ArrayList<>();
        List<BehaviorKey<?>> ours = new ArrayList<>();
        List<BehaviorKey<?>> others = new ArrayList<>();
        for (BehaviorKey<?> op : SlotOperations.all()) {
            String ns = op.id().getNamespace();
            // Vanilla's operations are defined by MenuKit, under its namespace.
            if (ns.equals("menukit")) vanilla.add(op);
            else if (isOurs(ns)) ours.add(op);
            else others.add(op);
        }
        operations(b, "Vanilla", vanilla);
        operations(b, "Inventory Plus and Inventory Max", ours);
        operations(b, "Other mods", others);
        return b.build();
    }

    private static void operations(SettingsBody b, String heading, List<BehaviorKey<?>> ops) {
        if (ops.isEmpty()) return;
        b.heading(heading);
        for (BehaviorKey<?> op : ops) operationSection(b, op);
    }

    /** One operation's section: closed, a count and what stops it; open, its blocks. */
    private static void operationSection(SettingsBody b, BehaviorKey<?> op) {
        String id = op.id().toString();
        boolean putOnly = SlotOperations.role(op) == SlotOperations.Role.PUT;
        List<SlotGroups.Entry> listing = SlotGroups.listing();

        List<Place> slotGroups = new ArrayList<>();
        List<Place> ourGroups = new ArrayList<>();
        List<Place> otherGroups = new ArrayList<>();
        Set<SlotGroupCategory> vanillaReach = VANILLA_REACH.get(id);
        Set<String> modGroupsOnly = MOD_GROUPS_ONLY.get(id);
        for (SlotGroups.Entry e : listing) {
            if (e.source() == null) {
                if (vanillaReach == null || e.categories().stream().anyMatch(vanillaReach::contains)) {
                    slotGroups.add(new Place(e.name(), true));
                }
            } else if (modGroupsOnly == null || modGroupsOnly.contains(e.key())) {
                // Inside a block labelled with the mods' names, ours need no suffix;
                // other mods' groups keep theirs so each can be told apart.
                if (isOurs(namespaceOf(e))) ourGroups.add(new Place(e.name(), true));
                else otherGroups.add(new Place(groupLabel(e), true));
            }
        }
        List<Place> lockGroups = new ArrayList<>();
        List<String> stoppedBy = new ArrayList<>();
        for (LockGroup g : lockGroupList()) {
            if (g.itemKind() && putOnly) continue;
            boolean stops = g.stops().contains(id);
            lockGroups.add(new Place(Component.literal(g.name()), !stops));
            if (stops) stoppedBy.add(g.name());
        }
        List<Place> places = placesOf(id);

        List<Place> all = new ArrayList<>(slotGroups);
        all.addAll(lockGroups);
        all.addAll(places);
        all.addAll(ourGroups);
        all.addAll(otherGroups);
        long on = all.stream().filter(Place::on).count();
        String count = on == all.size() ? "all " + on + " on" : on + " of " + all.size() + " on";
        String summary = stoppedBy.isEmpty() ? count : count + " · stopped by " + String.join(", ", stoppedBy);

        b.section(SlotOperations.name(op).getString(), () -> Component.literal(summary), null, c -> {
            c.line(0, SlotOperations.description(op));
            block(c, "Slot groups", slotGroups);
            block(c, "Lock groups", lockGroups);
            block(c, "Places", places);
            block(c, "Inventory Plus and Inventory Max", ourGroups);
            block(c, "Other mods", otherGroups);
            // Shift-click in's fill order waits on MenuKit (reach.md); greyed.
            if (op == BehaviorKeys.SHIFT_CLICK_IN) c.buttons("Revert to default", "Priority…");
            else c.buttons("Revert to default");
        });
    }

    /** One labelled block of checkboxes, left out when nothing in it applies. */
    private static void block(SettingsBody c, String label, List<Place> places) {
        if (!places.isEmpty()) c.reach(label, places);
    }

    // ── Lock groups ─────────────────────────────────────────────────────

    /**
     * The lock groups themselves: one collapsible section per group, adding a
     * new one, the lock button toggle, the global cycler-lock switch, and
     * Container locks. What a group stops is set in each operation's section
     * in Reach; this tab only manages the groups.
     */
    static List<PanelElement> lockGroups(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .frame("Lock groups", "Lock slots and items so moves leave them alone. Press a group's key "
                        + "on a slot to add it to the group or take it out. What each group stops is set in Reach.")
                // Pauses every lock without deleting any (settings-menu.md); not built.
                .onOff("Use locks", Bool.placeholder(true));
        for (LockGroup g : lockGroupList()) lockGroupSection(b, g);
        // A new group picks its kind up front; the defaults cover Slot and
        // Exact item, and Item (every item of a type) is only ever custom
        // (lock-groups.md).
        b.buttons("+ New slot group", "+ New item group", "+ New exact item group")
                .checkbox("Show the lock button", Bool.of(IPConfig::lockedSlotsShowButton, IPConfig::setLockedSlotsShowButton))
                // One switch for every cycler (columns, hotbar rows, pockets):
                // cycleSlotsLocked is global, so it lives with the locks, not in
                // one cycler's tab.
                .checkbox("Lock the slots the cyclers use",
                        Bool.of(IPConfig::cycleSlotsLocked, ColumnCycler::setCycleSlotsLocked));

        // Container locks are Inventory Max's. Kept on the client always; the
        // server copy is an option still to be built.
        b.heading("Container locks");
        if (!maxInstalled) b.line("Install Inventory Max to lock slots in chests and other containers.");
        b.line("Locks on chest and other container slots are always kept on your computer.")
                .checkbox("Also keep them on the server (needs Inventory Max there)", false);
        return b.build();
    }

    /**
     * One lock group as a collapsible section. Closed: its colour, its name,
     * and a grey summary (its kind, when the name does not already say it,
     * and its key). Open: a line per thing to change about it; opening the
     * section is how a group is edited.
     */
    private static void lockGroupSection(SettingsBody b, LockGroup g) {
        // Read every frame, so a key rebound in the section shows in its header.
        Supplier<Component> summary = () -> {
            String keyText = g.key() == null ? "no key yet"
                    : "key " + g.key().getTranslatedKeyMessage().getString();
            return Component.literal(g.name().equals(g.kind()) ? keyText : g.kind() + " · " + keyText);
        };
        b.section(g.name(), summary, GROUP_GREY, c -> {
            c.line(0, Component.literal("Kind: " + g.kind()));
            if (g.key() != null) {
                KeyMapping key = g.key();
                c.row(y -> List.of(new ChordButton(key).label(Component.literal("Key")).at(0, y)), 20);
            } else {
                c.line(0, Component.literal("Key: none yet"));
            }
            c.valueRow(Component.literal("Colour: grey"), "Change")
                    .valueRow(Component.literal("Name: " + g.name()), "Rename", "Delete");
        });
    }

    // ── Feature tabs ────────────────────────────────────────────────────
    //
    // The frame, the on/off switch, then one setting per line: the button
    // toggle, each key, each option.

    static List<PanelElement> sort() {
        return new SettingsBody()
                .frame("Sort", "Sorts your inventory or the open container with one click. "
                        + "Right-click the Sort button to change how it sorts.")
                .onOff("Use Sort", Bool.placeholder(true))
                .checkbox("Show the Sort button", Bool.of(IPConfig::sortShowButton, IPConfig::setSortShowButton))
                .key(IPKeybinds.SORT)
                .build();
    }

    static List<PanelElement> moveMatching() {
        return new SettingsBody()
                .frame("Move Matching", "Moves items between your inventory and the open container, "
                        + "but only items the other side already has. One button moves them in, the other out.")
                .onOff("Use Move Matching", Bool.placeholder(true))
                .checkbox("Show the Move Matching buttons",
                        Bool.of(IPConfig::moveMatchingShowButtons, IPConfig::setMoveMatchingShowButtons))
                .key(IPKeybinds.MOVE_MATCHING_OUT)
                .key(IPKeybinds.MOVE_MATCHING_IN)
                // Where Move Matching puts items first waits on MenuKit (reach.md); greyed.
                .valueRow(Component.literal("Fill order"), "Priority…")
                .build();
    }

    static List<PanelElement> restock() {
        // Restock has no master switch yet: each kind is its own, and its
        // sub-options grey while it is off, as in the old settings screen.
        Bool armor = Bool.of(IPConfig::autoRestockArmor, IPConfig::setAutoRestockArmor);
        Bool tool = Bool.of(IPConfig::autoRestockTool, IPConfig::setAutoRestockTool);
        Bool item = Bool.of(IPConfig::autoRestockItem, IPConfig::setAutoRestockItem);
        Bool shulker = Bool.of(IPConfig::autoRestockShulker, IPConfig::setAutoRestockShulker);
        return new SettingsBody()
                .frame("Restock", "Refills your hand, hotbar and armor from your inventory when "
                        + "something runs out or breaks. It can also swap armor and tools just before they break.")
                .onOff("Use Restock", Bool.placeholder(true))
                .checkbox("Show the Restock button", Bool.placeholder(true))
                .checkbox("Armor restock", armor)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockArmorBeforeBreak,
                        IPConfig::setAutoRestockArmorBeforeBreak).onlyWhen(armor.get()))
                .subCheckbox("Use locked items", Bool.of(IPConfig::autoRestockArmorUsesLockedItems,
                        IPConfig::setAutoRestockArmorUsesLockedItems).onlyWhen(armor.get()))
                .checkbox("Tool restock", tool)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockToolBeforeBreak,
                        IPConfig::setAutoRestockToolBeforeBreak).onlyWhen(tool.get()))
                .subCheckbox("Use locked items", Bool.of(IPConfig::autoRestockToolUsesLockedItems,
                        IPConfig::setAutoRestockToolUsesLockedItems).onlyWhen(tool.get()))
                .checkbox("Item restock", item)
                .subCheckbox("Use locked items", Bool.of(IPConfig::autoRestockItemUsesLockedItems,
                        IPConfig::setAutoRestockItemUsesLockedItems).onlyWhen(item.get()))
                // Also Restock's Shulker Boxes place in Reach; the two read one setting.
                .checkbox("Pull from Shulker Boxes (in inventory)", shulker)
                .subCheckbox("Pull ammo when shooting", Bool.of(IPConfig::autoRestockShulkerAmmo,
                        IPConfig::setAutoRestockShulkerAmmo).onlyWhen(shulker.get()))
                .slider("Swap before breaking at durability", 2, 50,
                        IPConfig::autoRestockBeforeBreakThreshold, IPConfig::setAutoRestockBeforeBreakThreshold,
                        () -> !((IPConfig.autoRestockArmor() && IPConfig.autoRestockArmorBeforeBreak())
                                || (IPConfig.autoRestockTool() && IPConfig.autoRestockToolBeforeBreak())))
                .build();
    }

    static List<PanelElement> autoToolSwitch() {
        return new SettingsBody()
                .frame("Auto Tool Switch", "Switches to the right tool for the block you're mining, "
                        + "and to a weapon when you attack. It can switch back to what you were holding afterwards.")
                .onOff("Use Auto Tool Switch", Bool.of(IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled))
                .checkbox("Show the Auto Tool Switch button", Bool.placeholder(true))
                .key(IPKeybinds.AUTO_SWITCH_RETURN)
                .checkbox("Use locked items", Bool.of(IPConfig::autoToolSwitchUsesLockedItems,
                        IPConfig::setAutoToolSwitchUsesLockedItems).onlyWhen(IPConfig::autoToolSwitchEnabled))
                .choice("Return to the previous tool", Arrays.asList(AutoSwitchReturnMode.values()),
                        AutoSwitchReturnMode::displayName,
                        IPConfig::autoToolSwitchReturnMode, IPConfig::setAutoToolSwitchReturnMode,
                        () -> !IPConfig.autoToolSwitchEnabled())
                .slider("Return window, seconds", 1, 10,
                        IPConfig::autoToolSwitchReturnCooldownSeconds, IPConfig::setAutoToolSwitchReturnCooldownSeconds,
                        () -> !(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchReturnMode().isWindowed()))
                .checkbox("Switch weapons too", Bool.of(IPConfig::autoToolSwitchWeapons,
                        IPConfig::setAutoToolSwitchWeapons).onlyWhen(IPConfig::autoToolSwitchEnabled))
                .subCheckbox("All mobs, not just hostile ones", Bool.of(IPConfig::autoToolSwitchAllMobs,
                        IPConfig::setAutoToolSwitchAllMobs)
                        .onlyWhen(() -> IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons()))
                .choice("Preferred weapon", Arrays.asList(WeaponPreference.values()),
                        SettingsTabs::titleCase,
                        IPConfig::autoToolSwitchWeaponPreference, IPConfig::setAutoToolSwitchWeaponPreference,
                        () -> !(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons()))
                .build();
    }

    static List<PanelElement> columnCycler() {
        return new SettingsBody()
                .frame("Column Cycler", "Rotates the items in an inventory column through a hotbar slot. "
                        + "Press the cycle key on a slot to add its column, then cycle it with the forward and backward keys.")
                .onOff("Use Column Cycler", Bool.of(IPConfig::columnCyclerEnabled, IPConfig::setColumnCyclerEnabled))
                .checkbox("Show the Column Cycler button",
                        Bool.of(IPConfig::columnCyclerShowButton, IPConfig::setColumnCyclerShowButton))
                .key(IPKeybinds.CYCLE_SLOT)
                .key(IPKeybinds.CYCLE_FORWARD)
                .key(IPKeybinds.CYCLE_BACKWARD)
                .choice("Beside the hotbar", Arrays.asList(HudMode.values()), SettingsTabs::hudName,
                        IPConfig::columnCyclerHudMode, IPConfig::setColumnCyclerHudMode,
                        () -> !IPConfig.columnCyclerEnabled())
                // Turning this on turns Hotbar Cycler's off (one wheel); the
                // setter enforces it and both checkboxes read live.
                .checkbox("Scroll to cycle", Bool.of(IPConfig::columnCyclerScrollToCycle,
                        IPConfig::setColumnCyclerScrollToCycle).onlyWhen(IPConfig::columnCyclerEnabled))
                .build();
    }

    static List<PanelElement> hotbarCycler() {
        return new SettingsBody()
                .frame("Hotbar Cycler", "Rotates whole inventory rows through your hotbar. "
                        + "Add rows with the buttons beside them, then cycle with the forward and backward keys.")
                .onOff("Use Hotbar Cycler", Bool.of(IPConfig::hotbarCyclerEnabled, IPConfig::setHotbarCyclerEnabled))
                .checkbox("Show the row buttons",
                        Bool.of(IPConfig::hotbarCyclerShowButtons, IPConfig::setHotbarCyclerShowButtons))
                .key(IPKeybinds.HOTBAR_CYCLE_FORWARD)
                .key(IPKeybinds.HOTBAR_CYCLE_BACKWARD)
                // Rows lock only while "Lock the slots the cyclers use" is on too.
                .checkbox("Lock cycled rows", Bool.of(IPConfig::lockCycledRows, IPConfig::setLockCycledRows)
                        .onlyWhen(() -> IPConfig.hotbarCyclerEnabled() && IPConfig.cycleSlotsLocked()))
                .checkbox("Scroll to cycle", Bool.of(IPConfig::hotbarCyclerScrollToCycle,
                        IPConfig::setHotbarCyclerScrollToCycle).onlyWhen(IPConfig::hotbarCyclerEnabled))
                .build();
    }

    // ── Inventory Max stand-ins (greyed; Inventory Max replaces them) ─────

    private static SettingsBody standIn(String title, String description, String use) {
        return new SettingsBody(true)
                .frame(title, description + " Install Inventory Max to use it.")
                .onOff(use, Bool.placeholder(true));
    }

    static List<PanelElement> pocketsStandIn() {
        return standIn("Pockets", "Adds up to three extra slots behind each hotbar slot, "
                        + "and keys to cycle through them.", "Use Pockets")
                .checkbox("Show the Pockets button", true)
                .key(Component.literal("Pocket Cycle Forward"), Component.literal("Right Arrow"))
                .key(Component.literal("Pocket Cycle Backward"), Component.literal("Left Arrow"))
                .choice("Beside the hotbar", List.of("Mini hotbar"), v -> v, "Mini hotbar")
                .checkbox("Restock and Auto Tool Switch may take from pockets", true)
                .build();
    }

    static List<PanelElement> equipmentSlotsStandIn() {
        return standIn("Equipment Slots", "Adds an elytra slot and a totem slot to your inventory.",
                        "Use Equipment Slots")
                .checkbox("Show the Equipment Slots button", true)
                .checkbox("Show elytra and totem icons beside the hotbar", true)
                .build();
    }

    static List<PanelElement> mendAnywhereStandIn() {
        return standIn("Mend Anywhere", "Mending items repair from XP anywhere in your inventory, "
                        + "not only in your hands and armor.", "Use Mend Anywhere")
                .checkbox("Show the Mend Anywhere button", true)
                .build();
    }

    // ── Shared ──────────────────────────────────────────────────────────

    /** Inventory Plus's and Inventory Max's namespaces: "ours" in Reach's headings and blocks. */
    private static boolean isOurs(String namespace) {
        return namespace.equals("inventoryplus") || namespace.equals("inventorymax");
    }

    /**
     * The namespace a listing row is named in, by the rule MenuKit's
     * {@code SlotGroups.source} uses: a set's namespace, a created group's
     * panel-id namespace, or a mod's own category's namespace.
     */
    private static String namespaceOf(SlotGroups.Entry e) {
        if (e.set() != null) return e.set().namespace();
        return switch (e.groups().get(0)) {
            case SlotGroupId.Vanilla v -> v.category().namespace();
            case SlotGroupId.Created c -> {
                int colon = c.panelId().indexOf(':');
                yield colon > 0 ? c.panelId().substring(0, colon) : "";
            }
        };
    }

    /**
     * A listing row as the menu shows it: its name, and for a group a mod
     * added, that mod in parentheses ("Pockets (Inventory Max)"), so a player
     * can tell vanilla's groups from the rest.
     */
    static Component groupLabel(SlotGroups.Entry e) {
        Component source = e.source();
        return source == null ? e.name() : e.name().copy().append(" (").append(source).append(")");
    }

    /** A HUD mode's name: NONE reads "Off", the rest in sentence case. */
    private static String hudName(Enum<?> e) {
        return e.name().equals("NONE") ? "Off" : titleCase(e);
    }

    private static String titleCase(Enum<?> e) {
        String s = e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
