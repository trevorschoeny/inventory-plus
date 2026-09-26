package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.config.IPConfigScreen;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Checkbox;
import com.trevlar.menukit.core.Divider;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.TextLabel;
import com.trevlar.menukit.inject.SlotGroups;
import com.trevlar.menukit.window.BehaviorKey;
import com.trevlar.menukit.window.SlotOperations;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The body of every settings tab, as placeholders (plan: Leadership drive,
 * {@code mods/inventory-plus/plans/settings-menu.md}).
 *
 * <p>Nothing here reads or writes config. Each control shows its setting's
 * default, so the screen reads true for a fresh install, and every control is
 * disabled. The feature tabs share one section order: on/off, reach, locks,
 * button, keys, then the feature's own options.
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

    // ── Moves ───────────────────────────────────────────────────────────

    static List<PanelElement> moves() {
        SettingsBody b = new SettingsBody()
                .line("Choose where vanilla's inventory moves may go. A cleared box means the move never touches that group.");
        for (BehaviorKey<?> op : SlotOperations.all()) {
            String ns = op.id().getNamespace();
            // Inventory Plus's and Inventory Max's own operations live in their feature tabs.
            if (ns.equals("inventoryplus") || ns.equals("inventorymax")) continue;
            b.heading(SlotOperations.name(op).getString())
                    .line(0, SlotOperations.description(op))
                    .reach(null, slotGroups());
        }
        return b.build();
    }

    // ── Locks ───────────────────────────────────────────────────────────

    static List<PanelElement> locks(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .line("Each lock group decides what it stops. Press L on a slot to apply the active group.");
        b.heading("Lock groups");
        group(b, "Slot lock", "Slot");
        group(b, "Exact item", "Exact item");
        // Expanded under Exact item: the operations an item lock can mean
        // something for. It protects an item already in a slot, so operations
        // that only put items in are left out (SlotOperations.Role.PUT).
        b.line(12, Component.literal("Exact item stops:"));
        for (BehaviorKey<?> op : SlotOperations.all()) {
            if (SlotOperations.role(op) == SlotOperations.Role.PUT) continue;
            boolean stops = EXACT_ITEM_STOPS.contains(op.id().toString());
            b.row(y -> List.of(new Checkbox(24, y, stops, SlotOperations.name(op), v -> {}, () -> true)), 14);
        }
        b.buttons("+ New group");

        // Container Locks has no tab of its own; its switch lives here.
        b.heading("Container locks")
                .checkbox("Lock slots in chests and other placed containers", true);
        if (!maxInstalled) b.line("Install Inventory Max to lock container slots.");

        b.heading("Keys")
                .key(IPKeybinds.LOCK_SLOT);
        return b.build();
    }

    /** One lock group's row: its colour, its name and kind, and an edit button. */
    private static void group(SettingsBody b, String name, String kind) {
        b.row(y -> List.of(
                Divider.horizontal(0, y + 3, 10, GROUP_GREY, 10),
                new TextLabel(16, y + 4, Component.literal(name), TextLabel.COLOR_DARK, false),
                new TextLabel(96, y + 4, Component.literal("Kind: " + kind), b.textColor(), false),
                new Button(180, y, 40, 16, Component.literal("Edit"), btn -> {}, () -> true)), 20);
    }

    // ── Feature tabs ────────────────────────────────────────────────────

    static List<PanelElement> sort(Consumer<String> openTab) {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Sort", true);
        b.heading("Reach").reach(null, slotGroups());
        stoppedBy(b, openTab, "inventoryplus:sort");
        b.heading("Button").checkbox("Show the Sort buttons", true);
        b.heading("Keys").key(IPKeybinds.SORT);
        b.heading("Options").line("Sort has nothing else to set yet.");
        return b.build();
    }

    static List<PanelElement> moveMatching(Consumer<String> openTab) {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Move Matching", true);
        b.heading("Reach")
                .reach("Out", slotGroups())
                .reach("In", slotGroups());
        stoppedBy(b, openTab, "inventoryplus:move_matching_out", "inventoryplus:move_matching_in");
        b.heading("Button").checkbox("Show the Move Matching buttons", true);
        b.heading("Keys")
                .key(IPKeybinds.MOVE_MATCHING_OUT)
                .key(IPKeybinds.MOVE_MATCHING_IN);
        b.heading("Options").line("Move Matching has nothing else to set yet.");
        return b.build();
    }

    static List<PanelElement> restock(Consumer<String> openTab) {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Restock", true);
        List<Component> takesFrom = new ArrayList<>(slotGroups());
        takesFrom.addAll(List.of(Component.literal("Shulker boxes"), Component.literal("Bundles"),
                Component.literal("Ender chest")));
        b.heading("Reach")
                .reach("Takes from", takesFrom)
                .reach("Fills", slotGroups());
        stoppedBy(b, openTab, "inventoryplus:restock_take", "inventoryplus:restock_put");
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

    static List<PanelElement> autoToolSwitch(Consumer<String> openTab) {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Auto Tool Switch", false);
        b.heading("Reach").reach(null, slotGroups());
        stoppedBy(b, openTab, "inventoryplus:auto_tool_switch");
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

    static List<PanelElement> columnCycler(Consumer<String> openTab) {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Column Cycler", false);
        stoppedBy(b, openTab, "inventoryplus:column_cycle");
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

    static List<PanelElement> hotbarCycler(Consumer<String> openTab) {
        SettingsBody b = new SettingsBody();
        onOff(b, "Use Hotbar Cycler", false);
        stoppedBy(b, openTab, "inventoryplus:hotbar_cycle");
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
        b.heading("Reach").reach(null, slotGroups());
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
     * The read-only locks line: which default groups stop any of this
     * feature's operations. Clicking it opens the Locks tab, the only place
     * lock choices are edited.
     */
    private static void stoppedBy(SettingsBody b, Consumer<String> openTab, String... operations) {
        List<String> groups = new ArrayList<>();
        if (Arrays.stream(operations).anyMatch(SLOT_LOCK_STOPS::contains)) groups.add("Slot lock");
        if (Arrays.stream(operations).anyMatch(EXACT_ITEM_STOPS::contains)) groups.add("Exact item");
        String text = groups.isEmpty() ? "Stopped by: no lock group" : "Stopped by: " + String.join(", ", groups);
        b.heading("Locks").row(y -> List.of(new Button(0, y,
                Minecraft.getInstance().font.width(text) + 12, 16, Component.literal(text),
                btn -> openTab.accept(SettingsMenu.LOCKS))), 20);
    }

    /** Every slot group a player can name, from MenuKit's listing. */
    private static List<Component> slotGroups() {
        List<Component> names = new ArrayList<>();
        for (SlotGroups.Entry e : SlotGroups.listing()) names.add(e.name());
        return names;
    }

    private static String titleCase(Enum<?> e) {
        String s = e.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
