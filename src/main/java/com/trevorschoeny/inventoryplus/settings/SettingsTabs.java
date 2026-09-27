package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPConfigScreen;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.settings.SettingsBody.Bool;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Divider;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.TextLabel;
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
import java.util.Set;
import java.util.function.Supplier;

/**
 * The body of every settings tab, as placeholders (plan: Leadership drive,
 * {@code mods/inventory-plus/plans/settings-menu.md}).
 *
 * <p>Controls with a real setting behind them read and write it live;
 * the rest are greyed placeholders showing their planned default (see
 * {@link SettingsBody}). The feature tabs share one section order: on/off, reach (slot
 * groups, then lock groups), button, keys, then the feature's own options.
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
    // Written out until Lock Groups is built. Used for the Locks tab's checklist
    // and for each feature's "Stopped by" line, so the two always agree.

    private static final Set<String> SLOT_LOCK_STOPS = Set.of(
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "menukit:shift_click_in", "menukit:shift_click_out", "menukit:collect",
            "menukit:world_pickup", "menukit:drop", "menukit:drop_stack", "menukit:drag_fill",
            "menukit:hotbar_swap", "menukit:offhand_swap", "inventoryplus:restock_take");

    private static final Set<String> EXACT_ITEM_STOPS = Set.of(
            "menukit:drop", "menukit:drop_stack", "menukit:shift_click_out", "menukit:collect",
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "inventoryplus:column_cycle", "inventoryplus:hotbar_cycle");

    /**
     * Containers the player carries, which every feature's reach offers after
     * the slot and lock groups (Trev, 2026-09-27). Not slot groups: they are
     * items in the inventory whose contents a feature may reach.
     */
    private static final List<Component> IN_INVENTORY = List.of(
            Component.literal("Shulker Boxes (in inventory)"),
            Component.literal("Bundles (in inventory)"),
            Component.literal("Ender Chest (in inventory)"));

    /** Grey, the colour both default groups start with. */
    private static final int GROUP_GREY = 0xFF8B8B8B;

    /**
     * A lock group as the scaffold shows it: its name and kind, the operations
     * it stops, and its key. Every group, item kinds included, is listed on
     * every operation (Trev, 2026-09-27).
     */
    private record LockGroup(String name, String kind, Set<String> stops, @Nullable KeyMapping key) {}

    private static List<LockGroup> lockGroupList() {
        return List.of(
                new LockGroup("Slot lock", "Slot", SLOT_LOCK_STOPS, IPKeybinds.LOCK_SLOT),
                // A group's own key needs Keybindery's runtime bindings (agreed,
                // not built), so the Exact item group has none yet.
                new LockGroup("Exact item", "Exact item", EXACT_ITEM_STOPS, null));
    }

    // ── General ─────────────────────────────────────────────────────────

    static List<PanelElement> general(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .line("Greyed settings aren't built yet.");
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

    // ── Lock groups ─────────────────────────────────────────────────────

    /**
     * The lock groups themselves: one collapsible section per group, adding a
     * new one, the lock button toggle, the global cycler-lock switch, and
     * Container locks. Split out of Moving Items (Designer/Trev, 2026-09-27)
     * once that tab held five kinds of thing; what a group *stops* stays in
     * each operation's reach below, in Moving Items — this tab only manages
     * the groups.
     */
    static List<PanelElement> lockGroups(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .line("Groups you fill yourself: press a group's key on a slot to add it or take it out.");
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

    // ── Moving Items ────────────────────────────────────────────────────

    /**
     * One collapsible section per vanilla operation: the slot groups and lock
     * groups it may use, a revert to vanilla's default, and for shift-click
     * in, the order it fills them in. Defaults are vanilla's own behaviour;
     * the tab can only narrow it (Trev, 2026-09-26), so a box vanilla never
     * allows will show greyed once MenuKit publishes vanilla's per-category
     * rules. Lock groups themselves live in their own tab; clearing one's box
     * here is how it stops a move.
     */
    static List<PanelElement> movingItems() {
        List<Component> groups = slotGroups();
        SettingsBody b = new SettingsBody()
                .line("Choose which slot groups and lock groups each of vanilla's item moves may use. A cleared box means the move never touches that group.");

        b.heading("Moves");
        for (BehaviorKey<?> op : SlotOperations.all()) {
            String ns = op.id().getNamespace();
            // Inventory Plus's and Inventory Max's own operations live in their feature tabs.
            if (ns.equals("inventoryplus") || ns.equals("inventorymax")) continue;
            String summary = summary(op, groups);
            b.section(SlotOperations.name(op).getString(), () -> Component.literal(summary), null, c -> {
                c.line(0, SlotOperations.description(op)).reach(null, places(op.id().toString()));
                // Shift-click in alone has a fill order to set.
                if (op == BehaviorKeys.SHIFT_CLICK_IN) {
                    c.buttons("Revert to default").row(y -> List.of(new Button(0, y, 70, 16,
                            Component.literal("Priority…"),
                            btn -> PriorityMenu.open(Minecraft.getInstance().gui.screen(),
                                    "Shift-click in priority", slotGroups()))), 20);
                } else {
                    c.buttons("Revert to default");
                }
            });
        }
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

    // Every feature tab: one wrapping row of on/off, the button toggle and
    // the keys; then the options; then the reach, closed (Trev, 2026-09-27).

    static List<PanelElement> sort() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Sort", Bool.placeholder(true),
                        "Show the Sort button", Bool.of(IPConfig::sortShowButton, IPConfig::setSortShowButton),
                        IPKeybinds.SORT);
        b.heading("Options").line("Sort has nothing else to set yet.");
        b.reachSection("Reach", places("inventoryplus:sort", IN_INVENTORY));
        return b.build();
    }

    static List<PanelElement> moveMatching() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Move Matching", Bool.placeholder(true), "Show the Move Matching buttons",
                        Bool.of(IPConfig::moveMatchingShowButtons, IPConfig::setMoveMatchingShowButtons),
                        IPKeybinds.MOVE_MATCHING_OUT, IPKeybinds.MOVE_MATCHING_IN);
        // Where Move Matching puts items first: the places it may put into,
        // in-inventory containers included.
        List<Component> fillPlaces = new ArrayList<>(slotGroups());
        fillPlaces.addAll(IN_INVENTORY);
        b.heading("Options").row(y -> List.of(
                new TextLabel(0, y + 4, Component.literal("Fill order"), b.textColor(), false),
                new Button(Minecraft.getInstance().font.width("Fill order") + 8, y, 70, 16,
                        Component.literal("Priority…"),
                        btn -> PriorityMenu.open(Minecraft.getInstance().gui.screen(),
                                "Move Matching priority", fillPlaces))), 20);
        b.reachSection("Reach: out", places("inventoryplus:move_matching_out", IN_INVENTORY))
                .reachSection("Reach: in", places("inventoryplus:move_matching_in", IN_INVENTORY));
        return b.build();
    }

    static List<PanelElement> restock() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Restock", Bool.placeholder(true), "Show the Restock button", Bool.placeholder(true));
        // Restock has no master switch: each kind is its own, and its sub-options
        // grey while it is off, as in the old settings screen.
        Bool armor = Bool.of(IPConfig::autoRestockArmor, IPConfig::setAutoRestockArmor);
        Bool tool = Bool.of(IPConfig::autoRestockTool, IPConfig::setAutoRestockTool);
        Bool item = Bool.of(IPConfig::autoRestockItem, IPConfig::setAutoRestockItem);
        Bool shulker = Bool.of(IPConfig::autoRestockShulker, IPConfig::setAutoRestockShulker);
        b.heading("Options")
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
                // autoRestockShulker also shows in the reach below as its
                // shulker box entry; that entry takes over when reach opens.
                .checkbox("Pull from Shulker Boxes (in inventory)", shulker)
                .subCheckbox("Pull ammo when shooting", Bool.of(IPConfig::autoRestockShulkerAmmo,
                        IPConfig::setAutoRestockShulkerAmmo).onlyWhen(shulker.get()))
                .slider("Swap before breaking at durability", 2, 50,
                        IPConfig::autoRestockBeforeBreakThreshold, IPConfig::setAutoRestockBeforeBreakThreshold,
                        () -> !((IPConfig.autoRestockArmor() && IPConfig.autoRestockArmorBeforeBreak())
                                || (IPConfig.autoRestockTool() && IPConfig.autoRestockToolBeforeBreak())));
        List<SettingsBody.Place> takesFromExtras = List.of(
                new SettingsBody.Place(IN_INVENTORY.get(0), IPConfig.autoRestockShulker()),
                new SettingsBody.Place(IN_INVENTORY.get(1), true),
                new SettingsBody.Place(IN_INVENTORY.get(2), true));
        b.reachSection("Reach: takes from", placesWith("inventoryplus:restock_take", takesFromExtras))
                .reachSection("Reach: fills", places("inventoryplus:restock_put", IN_INVENTORY));
        return b.build();
    }

    static List<PanelElement> autoToolSwitch() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Auto Tool Switch", Bool.of(IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled),
                        "Show the Auto Tool Switch button", Bool.placeholder(true),
                        IPKeybinds.AUTO_SWITCH_RETURN);
        b.heading("Options")
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
                        () -> !(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons()));
        b.reachSection("Reach", places("inventoryplus:auto_tool_switch", IN_INVENTORY));
        return b.build();
    }

    // The cyclers pick their slots in game, so their reach is the lock groups alone.

    static List<PanelElement> columnCycler() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Column Cycler", Bool.of(IPConfig::columnCyclerEnabled, IPConfig::setColumnCyclerEnabled),
                        "Show the Column Cycler button",
                        Bool.of(IPConfig::columnCyclerShowButton, IPConfig::setColumnCyclerShowButton),
                        IPKeybinds.CYCLE_SLOT, IPKeybinds.CYCLE_FORWARD, IPKeybinds.CYCLE_BACKWARD);
        b.heading("Options")
                .choice("Beside the hotbar", Arrays.asList(HudMode.values()), SettingsTabs::hudName,
                        IPConfig::columnCyclerHudMode, IPConfig::setColumnCyclerHudMode,
                        () -> !IPConfig.columnCyclerEnabled())
                // Turning this on turns Hotbar Cycler's off (one wheel); the
                // setter enforces it and both checkboxes read live.
                .checkbox("Scroll to cycle", Bool.of(IPConfig::columnCyclerScrollToCycle,
                        IPConfig::setColumnCyclerScrollToCycle).onlyWhen(IPConfig::columnCyclerEnabled));
        b.reachSection("Reach", lockPlaces("inventoryplus:column_cycle"));
        return b.build();
    }

    static List<PanelElement> hotbarCycler() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Hotbar Cycler", Bool.of(IPConfig::hotbarCyclerEnabled, IPConfig::setHotbarCyclerEnabled),
                        "Show the row buttons",
                        Bool.of(IPConfig::hotbarCyclerShowButtons, IPConfig::setHotbarCyclerShowButtons),
                        IPKeybinds.HOTBAR_CYCLE_FORWARD, IPKeybinds.HOTBAR_CYCLE_BACKWARD);
        b.heading("Options")
                // Rows lock only while "Lock the slots the cyclers use" is on too.
                .checkbox("Lock cycled rows", Bool.of(IPConfig::lockCycledRows, IPConfig::setLockCycledRows)
                        .onlyWhen(() -> IPConfig.hotbarCyclerEnabled() && IPConfig.cycleSlotsLocked()))
                .checkbox("Scroll to cycle", Bool.of(IPConfig::hotbarCyclerScrollToCycle,
                        IPConfig::setHotbarCyclerScrollToCycle).onlyWhen(IPConfig::hotbarCyclerEnabled));
        b.reachSection("Reach", lockPlaces("inventoryplus:hotbar_cycle"));
        return b.build();
    }

    // ── Inventory Max stand-ins (greyed; Inventory Max replaces them) ─────

    private static SettingsBody standIn() {
        return new SettingsBody(true).line("Install Inventory Max to use these features.");
    }

    static List<PanelElement> pocketsStandIn() {
        SettingsBody b = standIn()
                .standInTopRow("Use Pockets", true, "Show the Pockets button",
                        Component.literal("Pocket Cycle Forward: Right Arrow"),
                        Component.literal("Pocket Cycle Backward: Left Arrow"));
        b.heading("Options")
                .choice("Beside the hotbar", List.of("Mini hotbar"), v -> v, "Mini hotbar")
                .checkbox("Restock and Auto Tool Switch may take from pockets", true);
        b.reachSection("Reach", places("inventorymax:pocket_cycle", IN_INVENTORY));
        return b.build();
    }

    static List<PanelElement> equipmentSlotsStandIn() {
        SettingsBody b = standIn()
                .standInTopRow("Use Equipment Slots", true, "Show the Equipment Slots button");
        b.heading("Options")
                .checkbox("Show elytra and totem icons beside the hotbar", true);
        return b.build();
    }

    static List<PanelElement> mendAnywhereStandIn() {
        SettingsBody b = standIn()
                .standInTopRow("Use Mend Anywhere", true, "Show the Mend Anywhere button");
        b.heading("Options")
                .line("Mending items repair from XP anywhere in your inventory, not only in your hands and armor.");
        return b.build();
    }

    // ── Shared sections ─────────────────────────────────────────────────

    /**
     * One operation's reach list, in the order Trev set (2026-09-27): vanilla's
     * slot groups, then the lock groups, then groups other mods added
     * (Inventory Max's included), then any {@code extras} the feature reaches
     * beyond slot groups. Slot groups start on (vanilla's default, until
     * MenuKit publishes vanilla's rules); a lock group starts on unless it
     * stops the operation. A slot is reached only if every group it is in is
     * checked, so a lock group's cleared box is the lock.
     */
    private static List<SettingsBody.Place> places(String operation, List<Component> extras) {
        return placesWith(operation, extras.stream().map(c -> new SettingsBody.Place(c, true)).toList());
    }

    /** As {@link #places(String, List)}, with each extra's own starting state. */
    private static List<SettingsBody.Place> placesWith(String operation, List<SettingsBody.Place> extras) {
        List<SlotGroups.Entry> listing = SlotGroups.listing();
        List<SettingsBody.Place> out = new ArrayList<>();
        for (SlotGroups.Entry e : listing) {
            if (e.source() == null) out.add(new SettingsBody.Place(e.name(), true));
        }
        out.addAll(lockPlaces(operation));
        for (SlotGroups.Entry e : listing) {
            if (e.source() != null) out.add(new SettingsBody.Place(groupLabel(e), true));
        }
        out.addAll(extras);
        return out;
    }

    private static List<SettingsBody.Place> places(String operation) {
        return places(operation, List.of());
    }

    /** The lock groups that apply to {@code operation}, each on unless it stops it. */
    private static List<SettingsBody.Place> lockPlaces(String operation) {
        List<SettingsBody.Place> out = new ArrayList<>();
        for (LockGroup g : lockGroupList()) {
            out.add(new SettingsBody.Place(Component.literal(g.name() + " (lock group)"),
                    !g.stops().contains(operation)));
        }
        return out;
    }

    /** A closed section's summary: how many slot groups it reaches, and which lock groups stop it. */
    private static String summary(BehaviorKey<?> op, List<Component> slotGroups) {
        String reach = slotGroups.size() + " of " + slotGroups.size() + " slot groups";
        List<String> stoppedBy = new ArrayList<>();
        for (LockGroup g : lockGroupList()) {
            if (g.stops().contains(op.id().toString())) stoppedBy.add(g.name());
        }
        return stoppedBy.isEmpty() ? reach : reach + " · stopped by " + String.join(", ", stoppedBy);
    }

    /** Every slot group a player can name, from MenuKit's listing. */
    private static List<Component> slotGroups() {
        List<Component> names = new ArrayList<>();
        for (SlotGroups.Entry e : SlotGroups.listing()) names.add(groupLabel(e));
        return names;
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
