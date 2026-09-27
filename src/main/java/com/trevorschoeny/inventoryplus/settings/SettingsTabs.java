package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.config.IPConfigScreen;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Divider;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.TextLabel;
import com.trevlar.menukit.inject.SlotGroups;
import com.trevlar.menukit.window.BehaviorKey;
import com.trevlar.menukit.window.BehaviorKeys;
import com.trevlar.menukit.window.SlotOperations;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The body of every settings tab, as placeholders (plan: Leadership drive,
 * {@code mods/inventory-plus/plans/settings-menu.md}).
 *
 * <p>Nothing here reads or writes config. Each control shows its setting's
 * default, so the screen reads true for a fresh install, and every control is
 * disabled. The feature tabs share one section order: on/off, reach (slot
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

    /** Grey, the colour both default groups start with. */
    private static final int GROUP_GREY = 0xFF8B8B8B;

    /**
     * A lock group as the scaffold shows it: its name and kind, the operations
     * it stops, and its key. Every group, item kinds included, is listed on
     * every operation (Trev, 2026-09-27).
     */
    private record LockGroup(String name, String kind, Set<String> stops, Component key) {}

    private static List<LockGroup> lockGroups() {
        return List.of(
                new LockGroup("Slot lock", "Slot", SLOT_LOCK_STOPS,
                        IPKeybinds.LOCK_SLOT.getTranslatedKeyMessage()),
                new LockGroup("Exact item", "Exact item", EXACT_ITEM_STOPS,
                        Component.literal("Not bound")));
    }

    // ── General ─────────────────────────────────────────────────────────

    static List<PanelElement> general(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .line("This menu is a first look. Nothing in it saves yet.");
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

    // ── Moving Items ────────────────────────────────────────────────────

    /**
     * One collapsible section per vanilla operation: the slot groups it may
     * use, a revert to vanilla's default, and for shift-click in, the order it
     * fills them in. Defaults are vanilla's own behaviour; the tab can only
     * narrow it (Trev, 2026-09-26), so a box vanilla never allows will show
     * greyed once MenuKit publishes vanilla's per-category rules.
     */
    static List<PanelElement> movingItems(boolean maxInstalled) {
        List<Component> groups = slotGroups();
        SettingsBody b = new SettingsBody()
                .line("Choose which slot groups each of vanilla's item moves may use. A cleared box means the move never touches those groups.");

        // Lock groups are groups the player fills: listed here, then offered
        // in every move's list beside MenuKit's groups (Trev, 2026-09-27). They
        // are not in MenuKit's slot group registry; Inventory Plus owns them.
        b.heading("Lock groups")
                .line("Groups you fill yourself: press a group's key on a slot to add it or take it out.");
        // Slot lock is drawn open, as shift-click in is below (SettingsBody.section).
        for (LockGroup g : lockGroups()) lockGroupSection(b, g, g.name().equals("Slot lock"));
        b.buttons("+ New group")
                .checkbox("Show the lock button", true);

        // Container locks are Inventory Max's. Kept on the client always; the
        // server copy is an option still to be built.
        b.heading("Container locks");
        if (!maxInstalled) b.line("Install Inventory Max to lock slots in chests and other containers.");
        b.line("Locks on chest and other container slots are always kept on your computer.")
                .checkbox("Also keep them on the server (needs Inventory Max there)", false);

        b.heading("Moves");
        for (BehaviorKey<?> op : SlotOperations.all()) {
            String ns = op.id().getNamespace();
            // Inventory Plus's and Inventory Max's own operations live in their feature tabs.
            if (ns.equals("inventoryplus") || ns.equals("inventorymax")) continue;
            // Shift-click in is drawn open: it is the one section with every part.
            boolean open = op == BehaviorKeys.SHIFT_CLICK_IN;
            b.section(SlotOperations.name(op).getString(), summary(op, groups), open);
            if (!open) continue;
            b.line(12, SlotOperations.description(op)).reach(null, places(op.id().toString()));
            b.row(y -> List.of(
                    new Button(12, y, 110, 16, Component.literal("Revert to default"), btn -> {}, () -> true),
                    new Button(126, y, 70, 16, Component.literal("Priority…"),
                            btn -> PriorityMenu.open(Minecraft.getInstance().gui.screen()))), 20);
        }
        return b.build();
    }

    /**
     * One lock group as a collapsible section. Closed: its colour, its name,
     * and a grey summary (its kind, when the name does not already say it,
     * and its key). Open: a line per thing to change about it; opening the
     * section is how a group is edited.
     */
    private static void lockGroupSection(SettingsBody b, LockGroup g, boolean open) {
        String key = g.key().getString();
        String keyText = key.equals("Not bound") ? "key not bound" : "key " + key;
        String summary = g.name().equals(g.kind()) ? keyText : g.kind() + " · " + keyText;
        b.section(g.name(), summary, open, GROUP_GREY);
        if (!open) return;
        b.line(12, Component.literal("Kind: " + g.kind()))
                .valueRow(Component.literal("Key: ").append(g.key()), "Change")
                .valueRow(Component.literal("Colour: grey"), "Change")
                .valueRow(Component.literal("Name: " + g.name()), "Rename", "Delete");
    }

    // ── Feature tabs ────────────────────────────────────────────────────

    static List<PanelElement> sort() {
        SettingsBody b = new SettingsBody()
                .topRow("Use Sort", true, "Show the Sort button", IPKeybinds.SORT);
        b.heading("Reach").reach(null, places("inventoryplus:sort"));
        b.heading("Options").line("Sort has nothing else to set yet.");
        return b.build();
    }

    static List<PanelElement> moveMatching() {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Move Matching", true);
        b.heading("Reach")
                .reach("Out", places("inventoryplus:move_matching_out"))
                .reach("In", places("inventoryplus:move_matching_in"));
        b.heading("Button").checkbox("Show the Move Matching buttons", true);
        b.heading("Keys")
                .key(IPKeybinds.MOVE_MATCHING_OUT)
                .key(IPKeybinds.MOVE_MATCHING_IN);
        b.heading("Options").line("Move Matching has nothing else to set yet.");
        return b.build();
    }

    static List<PanelElement> restock() {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Restock", true);
        List<Component> containers = List.of(Component.literal("Shulker boxes"), Component.literal("Bundles"),
                Component.literal("Ender chest"));
        b.heading("Reach")
                .reach("Takes from", places("inventoryplus:restock_take", containers))
                .reach("Fills", places("inventoryplus:restock_put"));
        b.heading("Keys").line("Restock has no keys.");
        b.heading("Options")
                .checkbox("Armor restock", true)
                .subCheckbox("Swap before it breaks", false)
                .subCheckbox("Use locked items", true)
                .checkbox("Tool restock", true)
                .subCheckbox("Swap before it breaks", false)
                .subCheckbox("Use locked items", true)
                .checkbox("Item restock", true)
                .subCheckbox("Use locked items", true)
                .slider("Swap before breaking at durability", 2, 50, 10);
        return b.build();
    }

    static List<PanelElement> autoToolSwitch() {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Auto Tool Switch", false);
        b.heading("Reach").reach(null, places("inventoryplus:auto_tool_switch"));
        b.heading("Keys").key(IPKeybinds.AUTO_SWITCH_RETURN);
        b.heading("Options")
                .checkbox("Use locked items", true)
                .choice("Return to the previous tool", Arrays.asList(AutoSwitchReturnMode.values()),
                        AutoSwitchReturnMode::displayName, AutoSwitchReturnMode.OFF)
                .slider("Return window, seconds", 1, 10, 3)
                .checkbox("Switch weapons too", false)
                .subCheckbox("All mobs, not just hostile ones", false)
                .choice("Preferred weapon", Arrays.asList(WeaponPreference.values()),
                        SettingsTabs::titleCase, WeaponPreference.SWORD);
        return b.build();
    }

    static List<PanelElement> columnCycler() {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Column Cycler", false);
        b.heading("Reach").reach(null, lockPlaces("inventoryplus:column_cycle"));
        b.heading("Button").checkbox("Show the Column Cycler button", true);
        b.heading("Keys")
                .key(IPKeybinds.CYCLE_SLOT)
                .key(IPKeybinds.CYCLE_FORWARD)
                .key(IPKeybinds.CYCLE_BACKWARD);
        b.heading("Options")
                .checkbox("Show the cycle beside the hotbar", true)
                .checkbox("Lock cycle slots", true)
                .checkbox("Scroll to cycle", false);
        return b.build();
    }

    static List<PanelElement> hotbarCycler() {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Hotbar Cycler", false);
        b.heading("Reach").reach(null, lockPlaces("inventoryplus:hotbar_cycle"));
        b.heading("Button").checkbox("Show the row buttons", true);
        b.heading("Keys")
                .key(IPKeybinds.HOTBAR_CYCLE_FORWARD)
                .key(IPKeybinds.HOTBAR_CYCLE_BACKWARD);
        b.heading("Options")
                .checkbox("Lock cycled rows", true)
                .checkbox("Scroll to cycle", false);
        return b.build();
    }

    // ── Inventory Max stand-ins (greyed; Inventory Max replaces them) ─────

    private static SettingsBody standIn() {
        return new SettingsBody(true).line("Install Inventory Max to use these features.");
    }

    static List<PanelElement> pocketsStandIn() {
        SettingsBody b = standIn();
        onOff(b, "Use Pockets", true);
        b.heading("Reach").reach(null, places("inventorymax:pocket_cycle"));
        b.heading("Keys")
                .key(Component.literal("Pocket Cycle Forward"), Component.literal("Right Arrow"))
                .key(Component.literal("Pocket Cycle Backward"), Component.literal("Left Arrow"));
        b.heading("Options")
                .checkbox("Show the cycle beside the hotbar", true)
                .checkbox("Restock and Auto Tool Switch may take from pockets", true);
        return b.build();
    }

    static List<PanelElement> equipmentSlotsStandIn() {
        SettingsBody b = standIn();
        onOff(b, "Use Equipment Slots", true);
        b.heading("Options")
                .checkbox("Show elytra and totem icons beside the hotbar", true);
        return b.build();
    }

    static List<PanelElement> mendAnywhereStandIn() {
        SettingsBody b = standIn();
        onOff(b, "Use Mend Anywhere", true);
        b.heading("Options")
                .line("Mending items repair from XP anywhere in your inventory, not only in your hands and armor.");
        return b.build();
    }

    // ── Shared sections ─────────────────────────────────────────────────

    private static void onOff(SettingsBody b, String label, boolean on) {
        b.heading("On/off").onOff(label, on);
    }

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
        List<SlotGroups.Entry> listing = SlotGroups.listing();
        List<SettingsBody.Place> out = new ArrayList<>();
        for (SlotGroups.Entry e : listing) {
            if (e.source() == null) out.add(new SettingsBody.Place(e.name(), true));
        }
        out.addAll(lockPlaces(operation));
        for (SlotGroups.Entry e : listing) {
            if (e.source() != null) out.add(new SettingsBody.Place(groupLabel(e), true));
        }
        for (Component extra : extras) out.add(new SettingsBody.Place(extra, true));
        return out;
    }

    private static List<SettingsBody.Place> places(String operation) {
        return places(operation, List.of());
    }

    /** The lock groups that apply to {@code operation}, each on unless it stops it. */
    private static List<SettingsBody.Place> lockPlaces(String operation) {
        List<SettingsBody.Place> out = new ArrayList<>();
        for (LockGroup g : lockGroups()) {
            out.add(new SettingsBody.Place(Component.literal(g.name() + " (lock group)"),
                    !g.stops().contains(operation)));
        }
        return out;
    }

    /** A closed section's summary: how many slot groups it reaches, and which lock groups stop it. */
    private static String summary(BehaviorKey<?> op, List<Component> slotGroups) {
        String reach = slotGroups.size() + " of " + slotGroups.size() + " slot groups";
        List<String> stoppedBy = new ArrayList<>();
        for (LockGroup g : lockGroups()) {
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

    private static String titleCase(Enum<?> e) {
        String s = e.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
