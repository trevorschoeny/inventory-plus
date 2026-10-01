package com.trevorschoeny.inventoryplus.settings;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.LockColours;
import com.trevorschoeny.inventoryplus.lockgroups.LockGroup;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.settings.SettingsBody.Bool;
import com.trevorschoeny.inventoryplus.settings.SettingsBody.Place;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.slot.SlotGroupCategory;
import com.trevlar.menukit.api.slot.SlotGroupId;
import com.trevlar.menukit.api.slot.SlotGroups;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.BehaviorKeys;
import com.trevlar.menukit.api.window.SlotOperations;

import com.trevorschoeny.keybindery.api.KeybinderyAPI;
import com.trevorschoeny.keybindery.chord.IChordKeyMapping;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;


/**
 * The body of every settings tab (plan: Leadership drive,
 * {@code mods/inventory-plus/plans/settings-menu.md}).
 *
 * <p>Every tab has one frame (Trev, 2026-09-27): the title, a description,
 * Reset to Defaults, the feature's on/off checkbox (not on General or
 * Reach), a line, then its settings one per line, every one of them greyed
 * while the feature is off. Back to game floats above the menu panel, not in
 * a tab. Every reach lives in the Reach tab; feature tabs have none.
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

    // ── Resets ──────────────────────────────────────────────────────────
    //
    // Every tab's Reset to Defaults asks first (settings-menu.md, "Every tab"),
    // then puts that tab's settings and keys back. General's Reset everything
    // runs every tab's, and Inventory Max's through the hook it registers.

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

    private static void resetGeneral() {
        resetKeys(IPKeybinds.OPEN_SETTINGS);
    }

    private static void resetSort() {
        IPConfig.reset("sortEnabled", "sortShowButton");
        resetKeys(IPKeybinds.SORT);
    }

    private static void resetTooltips() {
        IPConfig.reset("tooltipsEnabled", "itemTipsEnabled", "hideTooltipsOnKey");
        resetKeys(IPKeybinds.HIDE_TOOLTIPS);
    }

    private static void resetMoveMatching() {
        IPConfig.reset("moveMatchingEnabled", "moveMatchingShowButtons");
        resetKeys(IPKeybinds.MOVE_MATCHING_OUT, IPKeybinds.MOVE_MATCHING_IN);
    }

    private static void resetRestock() {
        IPConfig.reset("restockEnabled", "restockShowButton", "autoRestockArmor", "autoRestockArmorBeforeBreak",
                "autoRestockTool", "autoRestockToolBeforeBreak", "autoRestockItem", "autoRestockShulker",
                "autoRestockShulkerAmmo", "autoRestockBeforeBreakThreshold");
    }

    private static void resetAutoToolSwitch() {
        IPConfig.reset("autoToolSwitchEnabled", "autoToolSwitchShowButton", "autoToolSwitchReturnMode",
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
        IPConfig.reset("hotbarCyclerEnabled", "hotbarCyclerShowButtons", "hotbarCyclerScrollToCycle");
        resetKeys(IPKeybinds.HOTBAR_CYCLE_FORWARD, IPKeybinds.HOTBAR_CYCLE_BACKWARD);
    }

    /**
     * The Lock groups tab's Reset (settings-menu.md, "Every tab"): the two
     * default groups as a fresh install has them, every custom group gone, and
     * every lock kept on this computer removed, in every world. What each
     * group stops is the Reach tab's and stays.
     */
    private static void resetLockGroups() {
        LockedSlots.reassign(null, null);
        LockedItems.reassign(null, null);
        Reach.resetGroups();
        IPConfig.reset("lockGroupsEnabled", "lockedSlotsShowButton", "containerLocksOnServer");
        resetKeys(IPKeybinds.LOCK_SLOT);
        SettingsMenu.rebuild();
    }

    /** "12 locks in 3 worlds", every lock of {@code groupId}, or every lock when {@code null}. */
    private static String lockCount(@Nullable String groupId) {
        Set<String> worlds = new HashSet<>();
        int n = LockedSlots.count(groupId, worlds) + LockedItems.count(groupId, worlds);
        return n + (n == 1 ? " lock" : " locks") + " in " + worlds.size() + (worlds.size() == 1 ? " world" : " worlds");
    }

    // ── General ─────────────────────────────────────────────────────────

    static List<PanelElement> general(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .frame("General", "Settings for the whole menu. Greyed settings aren't built yet.",
                        confirmReset("General", SettingsTabs::resetGeneral));
        b.heading("Misc.").key(IPKeybinds.OPEN_SETTINGS);
        // The advert for Inventory Max's tabs; with Inventory Max installed the
        // stand-ins are gone, so there is nothing for this to hide.
        if (!maxInstalled) b.checkbox("Show Inventory Max tabs", true);
        b.heading("Presets")
                .actions("Save or load a file", "Save to file", "Load from file")
                .actions("Share by pasting", "Copy to clipboard", "Paste from clipboard");
        // Its own category, not Misc.: it resets every tab.
        b.heading("Reset")
                .actions(new SettingsBody.Action("Reset everything", () -> SettingsMenu.confirm("Reset everything?",
                        "Every Inventory Plus and Inventory Max setting goes back to its default, and "
                                + lockCount(null) + " are removed.",
                        SettingsTabs::resetEverything)));
        return b.build();
    }

    /** General's Reset everything: every tab's reset, Inventory Max's included. */
    private static void resetEverything() {
        IPConfig.resetAll();
        Reach.revertAll();
        resetGeneral();
        resetSort();
        resetMoveMatching();
        resetAutoToolSwitch();
        resetColumnCycler();
        resetHotbarCycler();
        resetLockGroups();
        SettingsMenu.runExternalResets();
    }

    // ── Reach ───────────────────────────────────────────────────────────

    /**
     * Every operation MenuKit knows, under three headings (Vanilla, Inventory
     * Plus and Inventory Max, Other mods; an empty heading is left out), one
     * collapsible section each, every box read from and written to the reach
     * record. A slot is reached only if every group it is in is checked, so a
     * lock group's cleared box is the lock (settings-menu.md, "Reach").
     */
    static List<PanelElement> reach() {
        SettingsBody b = new SettingsBody().frame("Reach",
                "Choose where each move may take items from and put them. A cleared box means the "
                        + "move never touches that group. Clear a lock group's box to make it stop that move.",
                confirmReset("Reach", Reach::revertAll));
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
        // Shown now: from the next build on, none of these reads as new.
        List<String> shown = new ArrayList<>();
        for (BehaviorKey<?> op : SlotOperations.all()) shown.add(op.id().toString());
        Reach.markSeen(shown);
        return b.build();
    }

    private static void operations(SettingsBody b, String heading, List<BehaviorKey<?>> ops) {
        if (ops.isEmpty()) return;
        b.heading(heading);
        for (BehaviorKey<?> op : ops) operationSection(b, op);
    }

    /**
     * One operation's section. Only what applies is offered (Trev,
     * 2026-09-27): the slot groups MenuKit says the operation can act on
     * ({@code SlotOperations.groups}), the lock groups whose kind fits its
     * role, and the places Inventory Plus declares for it. Closed, a live count
     * and what stops it; open, its blocks.
     */
    private static void operationSection(SettingsBody b, BehaviorKey<?> op) {
        String id = op.id().toString();
        boolean putOnly = SlotOperations.role(op) == SlotOperations.Role.PUT;
        Set<String> applies = new HashSet<>();
        for (SlotGroupId g : SlotOperations.groups(op)) applies.add(g.asString());

        List<Place> slotGroups = new ArrayList<>();
        List<Place> ourGroups = new ArrayList<>();
        List<Place> otherGroups = new ArrayList<>();
        for (SlotGroups.Entry e : SlotGroups.listing()) {
            // A set (Inventory Max's 27 pockets) is one box that writes each member.
            List<String> keys = new ArrayList<>();
            for (SlotGroupId g : e.groups()) if (applies.contains(g.asString())) keys.add(g.asString());
            if (keys.isEmpty()) continue;
            Place box = new Place(e.source() == null || isOurs(namespaceOf(e)) ? e.name() : groupLabel(e),
                    () -> keys.stream().noneMatch(k -> Reach.denies(id, k)),
                    v -> Reach.setDenied(id, keys, !v), () -> false);
            if (e.source() == null) slotGroups.add(box);
            else if (isOurs(namespaceOf(e))) ourGroups.add(box);
            else otherGroups.add(box);
        }
        // The cyclers' slots, as reach options (reach.md, "Feature reach
        // groups"): offered by every group that applies to the player's
        // inventory or hotbar, since they are subsets of those, first in the
        // Inventory Plus and Inventory Max row. A cycler's own group keeps its
        // own option checked and fixed: it must rotate its own slots.
        boolean carried = applies.contains(new SlotGroupId.Category(SlotGroupCategory.PLAYER_INVENTORY).asString())
                || applies.contains(new SlotGroupId.Category(SlotGroupCategory.PLAYER_HOTBAR).asString());
        if (carried) {
            List<Place> features = new ArrayList<>();
            for (Reach.Feature f : Reach.features()) {
                features.add(f.owner().equals(id)
                        ? Place.fixed(Component.literal(f.label()), true)
                        : new Place(Component.literal(f.label()), () -> !Reach.featureDenies(f.key(), id),
                                v -> Reach.setDenied(id, List.of(f.key()), !v), () -> false));
            }
            ourGroups.addAll(0, features);
        }
        List<Place> lockGroups = new ArrayList<>();
        List<LockGroup> offered = new ArrayList<>();
        for (LockGroup g : Reach.groups()) {
            // An item lock protects an item already there; put-only moves never apply.
            if (g.kind().locksItems() && putOnly) continue;
            offered.add(g);
            lockGroups.add(new Place(Component.literal(g.name()), () -> !Reach.lockDenies(g.id(), id),
                    v -> Reach.setLockDenies(g.id(), id, !v), () -> false));
        }
        List<Place> places = placesOf(id);

        List<Place> all = new ArrayList<>(slotGroups);
        all.addAll(lockGroups);
        all.addAll(places);
        all.addAll(ourGroups);
        all.addAll(otherGroups);
        boolean isNew = !Reach.seen(id);
        Supplier<Component> summary = () -> {
            long on = all.stream().filter(p -> p.on().getAsBoolean()).count();
            String count = on == all.size() ? "all " + on + " on" : on + " of " + all.size() + " on";
            List<String> stoppedBy = new ArrayList<>();
            for (LockGroup g : offered) if (Reach.lockDenies(g.id(), id)) stoppedBy.add(g.name());
            String text = stoppedBy.isEmpty() ? count : count + " · stopped by " + String.join(", ", stoppedBy);
            return Component.literal((isNew ? "new · " : "") + text + (Reach.hasEntry(id) ? " · changed" : ""));
        };

        b.section(SlotOperations.name(op).getString(), summary, null, c -> {
            c.line(0, SlotOperations.description(op));
            // The move's own settings sit above its checkboxes (Trev, 2026-09-27).
            c.actions(new SettingsBody.Action("Revert to default", () -> Reach.revert(id)));
            // Shift-click in's fill order waits on MenuKit (reach.md); greyed.
            if (op == BehaviorKeys.SHIFT_CLICK_IN) c.buttons("Priority…");
            block(c, "Slot groups", slotGroups);
            block(c, "Lock groups", lockGroups);
            block(c, "Places", places);
            block(c, "Inventory Plus and Inventory Max", ourGroups);
            block(c, "Other mods", otherGroups);
        });
    }

    /**
     * The places Inventory Plus declares for an operation: things carried
     * that aren't slots (`plans/reach.md`, "Places"). Only Restock's takes from
     * reaches into one, Shulker Boxes, and that mechanic is still to come, so
     * its box is greyed and shows the old "Pull from Shulker Boxes" setting.
     */
    private static List<Place> placesOf(String operation) {
        return operation.equals("inventoryplus:restock_take")
                ? List.of(Place.fixed(Component.literal("Shulker Boxes"), IPConfig.autoRestockShulker()))
                : List.of();
    }

    /** One labelled block of checkboxes, left out when nothing in it applies. */
    private static void block(SettingsBody c, String label, List<Place> places) {
        if (!places.isEmpty()) c.reach(label, places);
    }

    // ── Lock groups ─────────────────────────────────────────────────────

    /**
     * The lock groups themselves: the switch that pauses every lock, Misc.,
     * then one section per group and + New group. What a group stops is set in
     * each operation's section in Reach; this tab manages the groups.
     */
    static List<PanelElement> lockGroups(boolean maxInstalled) {
        SettingsBody b = new SettingsBody()
                .frame("Lock groups", "Lock slots and items so moves leave them alone. Press a group's key "
                        + "on a slot to add it to the group or take it out. What each group stops is set in Reach.",
                        // Pauses every lock without deleting any: nothing blocked, nothing drawn.
                        Bool.of(IPConfig::lockGroupsEnabled, IPConfig::setLockGroupsEnabled),
                        () -> SettingsMenu.confirm("Reset Lock groups to defaults?",
                                "Every custom group goes, and " + lockCount(null) + " kept on this computer are removed.",
                                SettingsTabs::resetLockGroups));
        b.heading("Misc.")
                // What the cyclers' slots are protected from is Reach's, per move
                // (reach.md, "Feature reach groups"); no switch here since 2026-09-30.
                .checkbox("Show the lock button", Bool.of(IPConfig::lockedSlotsShowButton, IPConfig::setLockedSlotsShowButton));
        // Container locks are kept on this computer always; the server copy
        // goes through Inventory Max's shared channel, so it needs Inventory Max.
        if (!maxInstalled) b.line("Install Inventory Max to also keep container locks on the server.");
        b.checkbox("Also keep container locks on the server (needs Inventory Max there). "
                + "They are always kept on your computer.",
                new Bool(IPConfig::containerLocksOnServer, IPConfig::setContainerLocksOnServer, () -> !maxInstalled));
        // The groups go last (Trev, 2026-09-27).
        b.heading("Groups");
        for (LockGroup g : Reach.groups()) lockGroupSection(b, g);
        // A new group picks its kind up front and starts with its kind's
        // default's choices (reach.md); Item (every item of a type) is only
        // ever custom (lock-groups.md).
        b.actions(new SettingsBody.Action("+ Slot group", () -> newGroup(LockKind.SLOT, "New slot group")),
                new SettingsBody.Action("+ Item group", () -> newGroup(LockKind.ITEM, "New item group")),
                new SettingsBody.Action("+ Exact item group", () -> newGroup(LockKind.EXACT, "New exact item group")));
        return b.build();
    }

    private static void newGroup(LockKind kind, String name) {
        Reach.createGroup(kind, name, Reach.nextColour());
        SettingsMenu.rebuild();
    }

    /**
     * One lock group as a collapsible section. Closed: its colour, its name,
     * and a grey summary (its kind when the name does not say it, and its key).
     * Open: kind, key, colour, name, and Delete for a custom group; opening
     * the section is how a group is edited.
     */
    private static void lockGroupSection(SettingsBody b, LockGroup g) {
        KeyMapping key = g.key() == null ? null : keyNamed(g.key());
        Supplier<Component> summary = () -> {
            String keyText = key == null ? "no key yet" : "key " + key.getTranslatedKeyMessage().getString();
            return Component.literal(g.name().equals(g.kind().displayName()) ? keyText
                    : g.kind().displayName() + " · " + keyText);
        };
        b.section(g.name(), summary, LockColours.argb(g.colour(), 0xFF), c -> {
            c.line(0, Component.literal("Kind: " + g.kind().displayName()
                    + (g.isDefault() ? " (a default group; it cannot be deleted)" : "")));
            if (key != null) c.key(key, Component.literal("Key"));
            // A key for a group made in game needs Keybindery's runtime bindings.
            else c.line(0, Component.literal("Key: none yet"));
            c.choice("Colour", Reach.COLOURS, LockColours::displayName, g::colour, colour -> {
                Reach.recolour(g.id(), colour);
                SettingsMenu.rebuild();
            }, () -> false);
            c.textSetting("Name", g.name(), "Rename", name -> {
                Reach.rename(g.id(), name);
                SettingsMenu.rebuild();
            });
            if (!g.isDefault()) c.actions(new SettingsBody.Action("Delete", () -> confirmDelete(g)));
        });
    }

    /**
     * Deleting a group moves its locks to the default of its kind
     * (lock-groups.md). An Item group has no default of its kind, so its locks
     * are removed; the confirm says which.
     */
    private static void confirmDelete(LockGroup g) {
        String count = lockCount(g.id());
        String body = g.kind() == LockKind.ITEM
                ? "This removes its " + count + "."
                : "This moves its " + count + " to " + Reach.resolve(Reach.defaultOf(g.kind()), g.kind()).name() + ".";
        SettingsMenu.confirm("Delete " + g.name() + "?", body, () -> {
            String to = g.kind() == LockKind.ITEM ? null : Reach.defaultOf(g.kind());
            LockedSlots.reassign(g.id(), to);
            LockedItems.reassign(g.id(), to);
            Reach.deleteGroup(g.id());
            SettingsMenu.rebuild();
        });
    }

    /** The registered key named {@code name}, or {@code null}. */
    private static @Nullable KeyMapping keyNamed(String name) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k.getName().equals(name)) return k;
        return null;
    }

    // ── Feature tabs ────────────────────────────────────────────────────
    //
    // The frame, the on/off switch, then one setting per line: the button
    // toggle, each key, each option.

    static List<PanelElement> sort() {
        return new SettingsBody()
                .frame("Sort", "Sorts your inventory or the open container with one click. "
                        + "Right-click the Sort button to change how it sorts.",
                        Bool.of(IPConfig::sortEnabled, IPConfig::setSortEnabled),
                        confirmReset("Sort", SettingsTabs::resetSort))
                .heading("Controls")
                .checkbox("Show the Sort button", Bool.of(IPConfig::sortShowButton, IPConfig::setSortShowButton))
                .key(IPKeybinds.SORT)
                .build();
    }

    /**
     * Tooltips (was Item Tips; Trev, 2026-09-30): everything Inventory Plus
     * does to tooltips. Off, Inventory Plus leaves every tooltip alone.
     */
    static List<PanelElement> tooltips() {
        return new SettingsBody()
                .frame("Tooltips", "Changes Inventory Plus makes to tooltips: extra lines on items, "
                        + "and a key to hide every tooltip while you hold it.",
                        Bool.of(IPConfig::tooltipsEnabled, IPConfig::setTooltipsEnabled),
                        confirmReset("Tooltips", SettingsTabs::resetTooltips))
                .heading("Misc.")
                .checkbox("Item tips: a tool's durability, a food's nutrition and saturation",
                        Bool.of(IPConfig::itemTipsEnabled, IPConfig::setItemTipsEnabled))
                // Hidden by MenuKit's tooltip seam, through a predicate Inventory
                // Plus registers at client init (InventoryPlusClient).
                .checkbox("Hold a key to hide tooltips",
                        Bool.of(IPConfig::hideTooltipsOnKey, IPConfig::setHideTooltipsOnKey))
                .key(IPKeybinds.HIDE_TOOLTIPS)
                .build();
    }

    static List<PanelElement> moveMatching() {
        return new SettingsBody()
                .frame("Move Matching", "Moves items between your inventory and the open container, "
                        + "but only items the other side already has. One button moves them in, the other out.",
                        Bool.of(IPConfig::moveMatchingEnabled, IPConfig::setMoveMatchingEnabled),
                        confirmReset("Move Matching", SettingsTabs::resetMoveMatching))
                // Where Move Matching puts items first waits on MenuKit (reach.md); greyed.
                .heading("Misc.")
                .actions("Fill order", "Priority…")
                .heading("Controls")
                .checkbox("Show the Move Matching buttons",
                        Bool.of(IPConfig::moveMatchingShowButtons, IPConfig::setMoveMatchingShowButtons))
                .key(IPKeybinds.MOVE_MATCHING_OUT)
                .key(IPKeybinds.MOVE_MATCHING_IN)
                .build();
    }

    static List<PanelElement> restock() {
        // Each kind is its own switch too, and its sub-options grey while it
        // is off, as in the old settings screen.
        Bool armor = Bool.of(IPConfig::autoRestockArmor, IPConfig::setAutoRestockArmor);
        Bool tool = Bool.of(IPConfig::autoRestockTool, IPConfig::setAutoRestockTool);
        Bool item = Bool.of(IPConfig::autoRestockItem, IPConfig::setAutoRestockItem);
        Bool shulker = Bool.of(IPConfig::autoRestockShulker, IPConfig::setAutoRestockShulker);
        return new SettingsBody()
                .frame("Restock", "Refills your hand, hotbar and armor from your inventory when "
                        + "something runs out or breaks. It can also swap armor and tools just before they break.",
                        Bool.of(IPConfig::restockEnabled, IPConfig::setRestockEnabled),
                        confirmReset("Restock", SettingsTabs::resetRestock))
                .heading("Misc.")
                .checkbox("Show the Restock button", Bool.of(IPConfig::restockShowButton, IPConfig::setRestockShowButton))
                // Shared by armor's and tools' "Swap before it breaks".
                .slider("Swap before breaking at durability", 2, 50,
                        IPConfig::autoRestockBeforeBreakThreshold, IPConfig::setAutoRestockBeforeBreakThreshold,
                        () -> !((IPConfig.autoRestockArmor() && IPConfig.autoRestockArmorBeforeBreak())
                                || (IPConfig.autoRestockTool() && IPConfig.autoRestockToolBeforeBreak())))
                .heading("Armor")
                .checkbox("Restock armor", armor)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockArmorBeforeBreak,
                        IPConfig::setAutoRestockArmorBeforeBreak).onlyWhen(armor.get()))
                .heading("Tools")
                .checkbox("Restock tools", tool)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockToolBeforeBreak,
                        IPConfig::setAutoRestockToolBeforeBreak).onlyWhen(tool.get()))
                .heading("Items")
                .checkbox("Restock items", item)
                // Also Restock's Shulker Boxes place in Reach; the two read one setting.
                .heading("Shulker Boxes")
                .checkbox("Pull from Shulker Boxes in your inventory", shulker)
                .subCheckbox("Pull ammo when shooting", Bool.of(IPConfig::autoRestockShulkerAmmo,
                        IPConfig::setAutoRestockShulkerAmmo).onlyWhen(shulker.get()))
                .build();
    }

    static List<PanelElement> autoToolSwitch() {
        return new SettingsBody()
                .frame("Auto Tool Switch", "Switches to the right tool for the block you're mining, "
                        + "and to a weapon when you attack. It can switch back to what you were holding afterwards.",
                        Bool.of(IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled),
                        confirmReset("Auto Tool Switch", SettingsTabs::resetAutoToolSwitch))
                .heading("Misc.")
                .checkbox("Show the Auto Tool Switch button",
                        Bool.of(IPConfig::autoToolSwitchShowButton, IPConfig::setAutoToolSwitchShowButton))
                .heading("Switching back")
                .choice("Return to the previous tool", Arrays.asList(AutoSwitchReturnMode.values()),
                        AutoSwitchReturnMode::displayName,
                        IPConfig::autoToolSwitchReturnMode, IPConfig::setAutoToolSwitchReturnMode,
                        () -> false)
                .slider("Return window, seconds", 1, 10,
                        IPConfig::autoToolSwitchReturnCooldownSeconds, IPConfig::setAutoToolSwitchReturnCooldownSeconds,
                        () -> !IPConfig.autoToolSwitchReturnMode().isWindowed())
                // The return key only matters to the return modes, so it sits with them.
                .key(IPKeybinds.AUTO_SWITCH_RETURN)
                .heading("Weapons")
                .checkbox("Switch weapons too", Bool.of(IPConfig::autoToolSwitchWeapons,
                        IPConfig::setAutoToolSwitchWeapons))
                .subCheckbox("All mobs, not just hostile ones", Bool.of(IPConfig::autoToolSwitchAllMobs,
                        IPConfig::setAutoToolSwitchAllMobs).onlyWhen(IPConfig::autoToolSwitchWeapons))
                .choice("Preferred weapon", Arrays.asList(WeaponPreference.values()),
                        SettingsTabs::titleCase,
                        IPConfig::autoToolSwitchWeaponPreference, IPConfig::setAutoToolSwitchWeaponPreference,
                        () -> !IPConfig.autoToolSwitchWeapons())
                .build();
    }

    static List<PanelElement> columnCycler() {
        return new SettingsBody()
                .frame("Column Cycler", "Rotates the items in an inventory column through a hotbar slot. "
                        + "Press the cycle key on a slot to add its column, then cycle it with the forward and backward keys.",
                        Bool.of(IPConfig::columnCyclerEnabled, IPConfig::setColumnCyclerEnabled),
                        confirmReset("Column Cycler", SettingsTabs::resetColumnCycler))
                .heading("Misc.")
                .choice("Beside the hotbar", Arrays.asList(HudMode.values()), SettingsTabs::hudName,
                        IPConfig::columnCyclerHudMode, IPConfig::setColumnCyclerHudMode, () -> false)
                .heading("Controls")
                .checkbox("Show the Column Cycler button",
                        Bool.of(IPConfig::columnCyclerShowButton, IPConfig::setColumnCyclerShowButton))
                .key(IPKeybinds.CYCLE_SLOT)
                .heading("Cycling")
                .key(IPKeybinds.CYCLE_FORWARD)
                .key(IPKeybinds.CYCLE_BACKWARD)
                // Turning this on turns Hotbar Cycler's off (one wheel); the
                // setter enforces it and both checkboxes read live.
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
                .heading("Misc.")
                .checkbox("Show the row buttons",
                        Bool.of(IPConfig::hotbarCyclerShowButtons, IPConfig::setHotbarCyclerShowButtons))
                .heading("Cycling")
                .key(IPKeybinds.HOTBAR_CYCLE_FORWARD)
                .key(IPKeybinds.HOTBAR_CYCLE_BACKWARD)
                .checkbox("Scroll to cycle", Bool.of(IPConfig::hotbarCyclerScrollToCycle,
                        IPConfig::setHotbarCyclerScrollToCycle))
                .build();
    }

    // ── Inventory Max stand-ins (greyed; Inventory Max replaces them) ─────

    private static SettingsBody standIn(String title, String description) {
        return new SettingsBody(true)
                .frame(title, description + " Install Inventory Max to use it.", Bool.placeholder(true), null);
    }

    static List<PanelElement> pocketsStandIn() {
        return standIn("Pockets", "Adds up to three extra slots behind each hotbar slot, "
                        + "and keys to cycle through them.")
                .heading("Misc.")
                .checkbox("Show the Pockets button", true)
                .choice("Beside the hotbar", List.of("Mini hotbar"), v -> v, "Mini hotbar")
                .heading("Cycling")
                .key(Component.literal("Pocket Cycle Forward"), Component.literal("Right Arrow"))
                .key(Component.literal("Pocket Cycle Backward"), Component.literal("Left Arrow"))
                .build();
    }

    static List<PanelElement> equipmentSlotsStandIn() {
        return standIn("Equipment Slots", "Adds an elytra slot and a totem slot to your inventory.")
                .heading("Misc.")
                .checkbox("Show the Equipment Slots button", true)
                .checkbox("Show elytra and totem icons beside the hotbar", true)
                .build();
    }

    static List<PanelElement> mendAnywhereStandIn() {
        return standIn("Mend Anywhere", "Mending items repair from XP anywhere in your inventory, "
                        + "not only in your hands and armor.")
                .heading("Misc.")
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
            case SlotGroupId.Category v -> v.category().namespace();
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
