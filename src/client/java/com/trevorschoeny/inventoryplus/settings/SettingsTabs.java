package com.trevorschoeny.inventoryplus.settings;

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

import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.ItemMatch;
import com.trevlar.menukit.api.element.ListView;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Toggle;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.group.SlotGroups;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;

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
 * The body of every settings tab.
 *
 * <p>Every tab has one frame: the title, a description,
 * Reset to Defaults, the feature's on/off checkbox (not on General or
 * Reach), a line, then its settings one per line, every one of them greyed
 * while the feature is off. Done sits below the menu panel, not in
 * a tab. Every reach lives in the Reach tab, and feature tabs have none.
 *
 * <p>Controls with a real setting behind them read and write it live. The
 * rest are greyed placeholders showing their planned default (see
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
        IPConfig.reset("settingsOptionsLook", "compactTooltips",
                "pickFromShulkers", "pickFromBundles", "pickFromEnderChest");
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
                "tooltipCompare", "tooltipCondenseTrims", "tooltipModName", "hideTooltipsOnKey",
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
                "autoRestockShulkerAmmo", "autoRestockBeforeBreakThreshold",
                "restockItemsFromShulkers", "restockItemsFromBundles", "restockItemsFromEnderChest",
                "restockToolsFromShulkers", "restockToolsFromBundles", "restockToolsFromEnderChest",
                "restockArmorFromShulkers", "restockArmorFromBundles", "restockArmorFromEnderChest",
                "restockAmmoFromShulkers", "restockAmmoFromBundles", "restockAmmoFromEnderChest");
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

    /**
     * The Lock groups tab's Reset: the two
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

    // General

    static List<PanelElement> general() {
        SettingsBody b = new SettingsBody()
                .frame("General", "Settings for the whole menu. Greyed settings aren't built yet.");
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
        // Pick block reaches into carried containers when the inventory has none of
        // the block (Emerald). Off until Trev picks the defaults; where these sit
        // (here, or with Reach's places) is his call too.
        b.heading("Pick block")
                .checkbox("Pick from Shulker Boxes in your inventory",
                        Bool.of(IPConfig::pickFromShulkers, IPConfig::setPickFromShulkers))
                .checkbox("Pick from Bundles in your inventory",
                        Bool.of(IPConfig::pickFromBundles, IPConfig::setPickFromBundles))
                .checkbox("Pick from your Ender Chest",
                        Bool.of(IPConfig::pickFromEnderChest, IPConfig::setPickFromEnderChest));
        b.heading("Presets")
                .actions("Save or load a file", "Save to file", "Load from file")
                .actions("Share by pasting", "Copy to clipboard", "Paste from clipboard");
        // Its own category: it resets every tab.
        b.heading("Reset")
                .actions(new SettingsBody.Action("Reset everything", () -> SettingsMenu.confirm("Reset everything?",
                        "Every setting in this menu goes back to its default, and "
                                + lockCount(null) + " are removed.",
                        SettingsTabs::resetEverything)));
        return b.build();
    }

    /** General's Reset everything: every tab's reset, other mods' tabs included. */
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

    // Reach

    /**
     * Every operation MenuKit knows, under three headings (Vanilla, Inventory
     * Plus, Other mods, leaving out an empty heading), one
     * collapsible section each, every box read from and written to the reach
     * record. A slot is reached only if every group it is in is checked, so a
     * lock group's cleared box is the lock.
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
        operations(b, "Inventory Plus", ours);
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
     * One operation's section. Only what applies is offered: the slot groups MenuKit says the operation can act on
     * ({@code SlotOperations.groups}), the lock groups whose kind fits its
     * role, and the places Inventory Plus declares for it. Closed, it shows a
     * live count and what stops it. Open, it shows its blocks.
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
            // A set (Hotbar Pockets' 27 pockets) is one box that writes each member.
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
        // The cyclers' slots, as reach options. They are offered by every group that applies to the player's
        // inventory or hotbar, since they are subsets of those, first in the
        // Inventory Plus row. A cycler's own group keeps its
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
            // No "changed" marker.
            return Component.literal((isNew ? "new · " : "") + text);
        };

        b.section(SlotOperations.name(op).getString(), summary, null, c -> {
            c.line(0, SlotOperations.description(op));
            // The move's own settings sit above its checkboxes.
            c.actions(new SettingsBody.Action("Revert to default", () -> Reach.revert(id)));
            // Shift-click in's fill order waits on MenuKit, so it is greyed.
            if (op == BehaviorKeys.SHIFT_CLICK_IN) c.buttons("Priority…");
            block(c, "Slot groups", slotGroups);
            block(c, "Lock groups", lockGroups);
            block(c, "Places", places);
            block(c, "Inventory Plus", ourGroups);
            block(c, "Other mods", otherGroups);
        });
    }

    /** Containers carried in the inventory, as greyed reach boxes (none is reachable yet). */
    private static List<Place> carriedPlaces(boolean shulker, boolean bundle, boolean enderChest) {
        return List.of(Place.fixed(Component.literal("Shulker Boxes"), shulker),
                Place.fixed(Component.literal("Bundles"), bundle),
                Place.fixed(Component.literal("Ender Chest"), enderChest));
    }

    /**
     * The places Inventory Plus declares for an operation: things carried
     * that aren't slots. Only Restock's takes from
     * reaches into: Shulker Boxes, Bundles and the Ender Chest carried in the
     * inventory. None of those mechanics is built yet, so the boxes are greyed.
     */
    private static List<Place> placesOf(String operation) {
        // Bundles and the Ender Chest beside Shulker Boxes, at the Restock
        // tab's planned defaults.
        return operation.equals("inventoryplus:restock_take")
                ? carriedPlaces(IPConfig.autoRestockShulker(), true, false)
                : List.of();
    }

    /** One labelled block of checkboxes, left out when nothing in it applies. */
    private static void block(SettingsBody c, String label, List<Place> places) {
        if (!places.isEmpty()) c.reach(label, places);
    }

    // Lock groups

    /**
     * The lock groups themselves: the switch that pauses every lock, the
     * button and server settings, then one section per group and + New group. What a group stops is set in
     * each operation's section in Reach; this tab manages the groups.
     */
    static List<PanelElement> lockGroups() {
        SettingsBody b = new SettingsBody()
                .frame("Lock groups", "Lock slots and items so moves leave them alone. Press a group's key "
                        + "on a slot to add it to the group or take it out. What each group stops is set in Reach.",
                        // Pauses every lock without deleting any: nothing blocked, nothing drawn.
                        Bool.of(IPConfig::lockGroupsEnabled, IPConfig::setLockGroupsEnabled),
                        () -> SettingsMenu.confirm("Reset Lock groups to defaults?",
                                "Every custom group goes, and " + lockCount(null) + " kept on this computer are removed.",
                                SettingsTabs::resetLockGroups));
        b
                // What the cyclers' slots are protected from is set per move in
                // Reach, so there is no switch for it here.
                .checkbox("Show the lock button", Bool.of(IPConfig::lockedSlotsShowButton, IPConfig::setLockedSlotsShowButton));
        // The "keep container locks on the server" row is hidden since IP 2.0.0:
        // the server side it needed is gone. containerLocksOnServer is still read
        // and written so the player's choice carries over, and the row comes
        // back with new text once container locks move into Inventory Plus.
        // The groups go last: MenuKit's ListView keyed by group id (7.3.0), each
        // row a card holding the group's section. Adding, deleting, renaming or
        // recolouring a group updates it in place, with no rebuild of the menu.
        // Its toolbar (the heading, the add buttons, delete and the arrows)
        // isn't placed on its own, so MenuKit draws it at the list's top, the
        // heading and the buttons on one line.
        b.element(y -> ListView.<LockGroup>builder()
                .at(0, y)
                .items(Reach::groups)
                .key(LockGroup::id)
                .row(g -> Flow.builder().boxed().add(lockGroupSection(b, g.id())).build())
                .selection(() -> selectedGroup, id -> selectedGroup = id)
                .title(Component.literal("Groups"))
                // A new group picks its kind up front and starts with its kind's
                // default's choices. Item (every item of a type) is only ever
                // custom. Each says on hover how its kind works.
                .add(Component.literal("+ Slot group"), Component.literal(LockKind.SLOT.description()),
                        () -> Reach.createGroup(LockKind.SLOT, "New slot group", Reach.nextColour()).id())
                .add(Component.literal("+ Item group"), Component.literal(LockKind.ITEM.description()),
                        () -> Reach.createGroup(LockKind.ITEM, "New item group", Reach.nextColour()).id())
                .add(Component.literal("+ Exact item group"), Component.literal(LockKind.EXACT.description()),
                        () -> Reach.createGroup(LockKind.EXACT, "New exact item group", Reach.nextColour()).id())
                .remove(Component.literal("- Delete group"), SettingsTabs::deleteGroup)
                // Asked while the dialog shows, so the count is the count then.
                .confirmRemove(g -> Component.literal("Delete " + g.name() + "? " + deleteEffect(g)))
                // The order the lock button cycles through.
                .move(Reach::moveGroup)
                // The two defaults can't be deleted or moved; they stay first.
                .pinned(LockGroup::isDefault)
                .build(), 0);
        return b.build();
    }

    /** The group selected in the list, kept across a rebuild of the menu. */
    private static @Nullable String selectedGroup;

    /**
     * One lock group's section, read by id every frame: its title and swatch
     * too (MenuKit 7.3.0), so a rename or recolour shows on the same row.
     * Closed: its colour, its name, and a grey summary (its kind when the name
     * does not say it, and its key). Open: kind, key, colour (MenuKit's
     * ColorField), name, and for an Item or Exact item group the items it
     * locks in this world (MenuKit's ItemPicker).
     */
    private static PanelElement lockGroupSection(SettingsBody b, String id) {
        LockGroup g = Reach.group(id);
        KeyMapping key = g.key() == null ? null : keyNamed(g.key());
        // A deleted group's row lives a frame past it: read the last one seen.
        LockGroup[] last = {g};
        Supplier<LockGroup> now = () -> {
            LockGroup current = Reach.group(id);
            if (current != null) last[0] = current;
            return last[0];
        };
        Supplier<Component> summary = () -> {
            LockGroup current = now.get();
            String keyText = key == null ? "no key yet" : "key " + key.getTranslatedKeyMessage().getString();
            return Component.literal(current.name().equals(current.kind().displayName()) ? keyText
                    : current.kind().displayName() + " · " + keyText);
        };
        return b.sectionElement("lockgroup:" + id, () -> Component.literal(now.get().name()), summary,
                () -> LockColours.argb(now.get().colour(), 0xFF), c -> {
            c.line(0, Component.literal("Kind: " + g.kind().displayName()
                    + (g.isDefault() ? " (a default group; it cannot be deleted)" : "")),
                    Component.literal(g.kind().description()));
            if (key != null) c.key(key, Component.literal("Key"));
            // A key for a group made in game needs Keybindery's runtime bindings.
            else c.line(0, Component.literal("Key: none yet"));
            // Basic: the 16 dyes; Custom: any colour, kept as #rrggbb.
            c.colour("Colour", () -> LockColours.rgb(now.get().colour()),
                    rgb -> Reach.recolour(id, LockColours.of(rgb)));
            c.textSetting("Name", g.name(), "Rename", name -> Reach.rename(id, name));
            // An Item group locks every item of a type, an Exact item group one
            // item as it is: the picker keeps the kind only, or the whole stack.
            // Item locks are kept per world, so they're chosen in one.
            if (g.kind().locksItems()) {
                boolean item = g.kind() == LockKind.ITEM;
                if (!LockedItems.inWorld()) c.line(0, Component.literal("Join a world to choose its items."));
                c.items(item ? "Items, every one of each type" : "Items, exactly as they are",
                        () -> item ? LockedItems.itemsOf(id) : LockedItems.exactOf(id),
                        stacks -> { if (item) LockedItems.setItems(id, stacks); else LockedItems.setExact(id, stacks); },
                        item ? ItemMatch.Match.ITEM : ItemMatch.Match.EXACT,
                        () -> !LockedItems.inWorld());
            }
        }, 0, 0);
    }

    /**
     * What deleting a group does to its locks, counted now: they move to the
     * default of its kind, or, for an Item group, which has no default of its
     * kind, they are removed.
     */
    private static String deleteEffect(LockGroup g) {
        String count = lockCount(g.id());
        return g.kind() == LockKind.ITEM
                ? "This removes its " + count + "."
                : "This moves its " + count + " to " + Reach.resolve(Reach.defaultOf(g.kind()), g.kind()).name() + ".";
    }

    /** Deletes group {@code id}, moving or removing its locks first (see {@link #deleteEffect}). */
    private static void deleteGroup(String id) {
        LockGroup g = Reach.group(id);
        if (g == null || g.isDefault()) return;
        String to = g.kind() == LockKind.ITEM ? null : Reach.defaultOf(g.kind());
        LockedSlots.reassign(id, to);
        LockedItems.reassign(id, to);
        Reach.deleteGroup(id);
    }

    /** The registered key named {@code name}, or {@code null}. */
    private static @Nullable KeyMapping keyNamed(String name) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k.getName().equals(name)) return k;
        return null;
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
                // beside the cursor docks at the bottom centre of the screen, or
                // the top centre when the cursor would be inside it there.
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
                // Where Move Matching puts items first waits on MenuKit, so it is greyed.
                .actions("Fill order", "Priority…")
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
        // Items, Tools, Armor, Ammo.
        b.heading("Items").checkbox("Restock items", item)
                // Built (Emerald): where an item that ran out may come from.
                .subCheckbox("Pull from Shulker Boxes in your inventory", Bool.of(IPConfig::restockItemsFromShulkers,
                        IPConfig::setRestockItemsFromShulkers).onlyWhen(item.get()))
                .subCheckbox("Pull from Bundles in your inventory", Bool.of(IPConfig::restockItemsFromBundles,
                        IPConfig::setRestockItemsFromBundles).onlyWhen(item.get()))
                .subCheckbox("Pull from your Ender Chest", Bool.of(IPConfig::restockItemsFromEnderChest,
                        IPConfig::setRestockItemsFromEnderChest).onlyWhen(item.get()));
        b.heading("Tools")
                .checkbox("Restock tools", tool)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockToolBeforeBreak,
                        IPConfig::setAutoRestockToolBeforeBreak).onlyWhen(tool.get()));
        pullFrom(b, tool, Bool.of(IPConfig::restockToolsFromShulkers, IPConfig::setRestockToolsFromShulkers),
                Bool.of(IPConfig::restockToolsFromBundles, IPConfig::setRestockToolsFromBundles),
                Bool.of(IPConfig::restockToolsFromEnderChest, IPConfig::setRestockToolsFromEnderChest));
        b.heading("Armor")
                .checkbox("Restock armor", armor)
                .subCheckbox("Swap before it breaks", Bool.of(IPConfig::autoRestockArmorBeforeBreak,
                        IPConfig::setAutoRestockArmorBeforeBreak).onlyWhen(armor.get()));
        pullFrom(b, armor, Bool.of(IPConfig::restockArmorFromShulkers, IPConfig::setRestockArmorFromShulkers),
                Bool.of(IPConfig::restockArmorFromBundles, IPConfig::setRestockArmorFromBundles),
                Bool.of(IPConfig::restockArmorFromEnderChest, IPConfig::setRestockArmorFromEnderChest));
        // Ammo for the bow or crossbow in hand when the inventory has none: a bow
        // finds arrows anywhere in the inventory already, so the only sources
        // are carried containers, and these boxes are the whole switch.
        b.heading("Ammo");
        pullFrom(b, null, Bool.of(IPConfig::restockAmmoFromShulkers, IPConfig::setRestockAmmoFromShulkers),
                Bool.of(IPConfig::restockAmmoFromBundles, IPConfig::setRestockAmmoFromBundles),
                Bool.of(IPConfig::restockAmmoFromEnderChest, IPConfig::setRestockAmmoFromEnderChest));
        return b.build();
    }

    /**
     * A kind's other sources, besides the inventory itself (Emerald): shulker boxes and
     * bundles carried in the inventory, and the ender chest. Under the kind's own switch
     * when it has one ({@code kind}); Ammo has none.
     */
    private static void pullFrom(SettingsBody b, @Nullable Bool kind, Bool shulkers, Bool bundles, Bool enderChest) {
        b.subCheckbox("Pull from Shulker Boxes in your inventory", kind == null ? shulkers : shulkers.onlyWhen(kind.get()))
                .subCheckbox("Pull from Bundles in your inventory", kind == null ? bundles : bundles.onlyWhen(kind.get()))
                .subCheckbox("Pull from your Ender Chest", kind == null ? enderChest : enderChest.onlyWhen(kind.get()));
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

    /** Inventory Plus's namespace: "ours" in Reach's headings and blocks. */
    private static boolean isOurs(String namespace) {
        return namespace.equals("inventoryplus");
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
     * added, that mod in parentheses ("Pockets (Hotbar Pockets)"), so a player
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
