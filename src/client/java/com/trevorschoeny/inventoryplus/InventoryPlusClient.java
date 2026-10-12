package com.trevorschoeny.inventoryplus;


import com.trevorschoeny.inventoryplus.api.HotbarCyclable;
import com.trevorschoeny.inventoryplus.autorestock.AutoRestockTicker;
import com.trevorschoeny.inventoryplus.autotoolswitch.AutoToolSwitch;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerCyclable;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerDragController;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerKeybind;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotationKeybind;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarCycler;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarCyclerHud;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarCyclerKeybind;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarCyclerRowButtons;
import com.trevorschoeny.inventoryplus.columncycler.hud.ColumnCyclerHudSource;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.CycleHud;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.tooltips.Tooltips;
import com.trevorschoeny.inventoryplus.lockgroups.LockTooltips;
import com.trevorschoeny.inventoryplus.lockgroups.Locks;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsDragController;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotKeybind;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingKeybind;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingModes;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;
import com.trevorschoeny.inventoryplus.sort.SortKeybind;
import com.trevorschoeny.inventoryplus.sort.SortState;
import com.trevorschoeny.inventoryplus.toolbar.PowerUsersToolbar;
import com.trevorschoeny.inventoryplus.settings.SettingsKeybind;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Inventory Plus. It works on any vanilla server.
 * It uses only SlotKit's client tier (Amethyst spec 2.3): it builds no slots
 * or menus, so it needs nothing on the server and never kicks anyone.
 */
public class InventoryPlusClient implements ClientModInitializer {

    public static final String MOD_ID = "inventoryplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // Loaded once at startup; toggles persist on change through the
        // IPConfig setters. Must load before any feature reads a toggle.
        IPConfig.load();

        // Slot operations are defined by the common initializer (InventoryPlus),
        // so a server knows them too. Every feature asks SlotOperations.allows
        // before it picks a slot and sends its clicks under SlotOperations.as.

        // Vanilla KeyMapping registration, so L, I, O and S show up in the
        // Controls menu and can be rebound. The per-feature keybind classes
        // check them with KeyMapping.matches(key, scancode) in their
        // ScreenKeyboardEvents.afterKeyPress handlers.
        IPKeybinds.register();

        // Restock watches the active hotbar, offhand and armor slots each tick
        // and refills them from the main inventory. It sends vanilla
        // slot-click packets, so any vanilla server accepts the move.
        ClientTickEvents.END_CLIENT_TICK.register(AutoRestockTicker::tick);

        // Auto Tool Switch swaps in the right tool or weapon on hit, using the
        // HotbarCyclableRegistry for the cyclable tier. Off by default.
        AutoToolSwitch.register();

        // Move Matching's in and out buttons live in the toolbar, and its
        // screen-scoped I and O keys register separately. Each button's
        // showWhen hides it on screens without an external container.
        MoveMatchingModes.load();
        MoveMatchingKeybind.register();

        // A lock is a lock group on a slot (LockedSlots, per world) or on an item (LockedItems,
        // per world); what each group stops is its choice in the reach record
        // (Reach, in config.json, loaded with the config above). The lock
        // button selects a group and L applies it. The button lives in the
        // toolbar (Toolbar.register below).
        //
        // Enforcement is one MenuKit veto (Locks): every operation MenuKit asks
        // about, vanilla's or a mod's, is refused on a slot whose group the
        // operation may not use, or whose lock groups stop it. Inventory Plus's
        // own features ask MenuKit before they act, so they get the same
        // answer. Manual cursor moves are operations too (click take and put),
        // allowed by both default groups.
        // Column Cycler's membership loads first: upgrading a 1.5.x lock file
        // drops the locks its old pairing wrote (LockedSlots.load).
        ColumnCycler.load();
        LockedSlots.load(ColumnCycler::pairedSlotsIn);
        LockedSlotKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(LockedSlotsDragController::tick);
        // Item locks: the file is read here; the stacks decode against the
        // level's registries on first use inside a world.
        LockedItems.load();
        Locks.registerVeto();
        LockTooltips.register();
        // Tooltips: every line Inventory Plus adds or changes, and the hide key.
        Tooltips.register();
        ClientTickEvents.END_CLIENT_TICK.register(LockedSlotKeybind::tick);

        // One right-aligned MenuKit panel above the player's 3x9 grid, holding
        // the action buttons. Each button's showWhen decides its own
        // visibility within the panel.
        Toolbar.register();

        // Settings menu: the dot button lives in the toolbar above; the Open
        // settings key (unbound by default) is registered here.
        SettingsKeybind.register();

        // Column Cycler is off by default (columnCyclerEnabled). Slot
        // membership is per world (config/inventoryplus/column-cycler.json).
        // The C key toggles membership, and its inventory button turns the
        // feature on and off. The cycle slots are a feature reach group:
        // every move Slot lock stops by default leaves them alone, per move.
        Reach.registerFeature(Reach.COLUMN_CYCLER, "Column Cycler", "inventoryplus:column_cycle",
                ColumnCycler::inReachGroup);
        ColumnCyclerKeybind.register();
        ColumnCyclerRotationKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerDragController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerRotationKeybind::tick);

        // Hotbar Cycler rotates whole inventory rows through the hotbar.
        // Membership is per row (buttons, not a per-slot overlay), and the
        // rotation reuses ColumnCyclerRotator's engine across all nine
        // columns, which is what keeps items in their own column.
        //
        // The cycled rows are a feature reach group, asked per slot at veto
        // time: the protection belongs to the position, stays put while items
        // rotate through it, and follows a row's toggle at once.
        HotbarCycler.load();
        Reach.registerFeature(Reach.HOTBAR_CYCLER, "Hotbar Cycler", "inventoryplus:hotbar_cycle",
                HotbarCycler::inReachGroup);
        // A player who had either old cycle-lock switch off keeps that answer
        // as explicit reach entries, once.
        IPConfig.LegacyCycleLocks legacyCycleLocks = IPConfig.legacyCycleLocks();
        if (!legacyCycleLocks.cycleSlotsLocked()) Reach.allowEverywhere(Reach.COLUMN_CYCLER, Reach.HOTBAR_CYCLER);
        else if (!legacyCycleLocks.lockCycledRows()) Reach.allowEverywhere(Reach.HOTBAR_CYCLER);
        HotbarCyclerRowButtons.register();
        HotbarCyclerKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(HotbarCyclerKeybind::tick);
        // The hotbar slide and the change-gated preview slides. Init order
        // relative to the HUD sources below is irrelevant: this only adds a
        // rotation listener now, and consults the source registry at rotation
        // time. That is also what lets Pocket Cycler's source, registered by
        // Inventory Max at its own init, take part without this mod referencing it.
        HotbarCyclerHud.register();

        // Column Cycler implements the "bring this slot's item to the hotbar"
        // contract that Auto Tool Switch uses. Going through the registry
        // keeps consumers independent of any one cycler.
        HotbarCyclableRegistry.register(ColumnCyclerCyclable.INSTANCE);

        // The shared cycle HUD: a mini-hotbar strip right of the vanilla
        // hotbar, showing whichever cycler is active on the selected hotbar
        // slot. Pocket Cycler in Inventory Max registers its own source into
        // the same registry. CycleHud reads CycleHudRegistry and knows no
        // particular cycler. Column Cycler's contents and animation bridge
        // live in ColumnCyclerHudSource.
        ColumnCyclerHudSource.register();
        CycleHud.register();

        // The right column: Settings, then each feature's on/off switch,
        // companions' after ours (built at client start).
        PowerUsersToolbar.register();

        // Sort: the S key sorts the container under the cursor, and the sort
        // type per container is stored in sort-state.json.
        //
        // ContainerOpenTracker captures the BlockPos via UseBlockCallback
        // when the player right-clicks to open a container, then
        // associates it with the menu's containerId on AFTER_INIT.
        // Needed because client-side AbstractContainerMenu slots wrap a
        // SimpleContainer for chests/barrels/hoppers/etc., not the
        // backing BlockEntity, so block-pos identity isn't directly
        // derivable from the menu.
        ContainerOpenTracker.register();
        SortState.load();
        SortKeybind.register();

        LOGGER.info("[inventoryplus] Client initialized, auto-restock + "
                + "move-matching + locked-slots + locked-items + sort + column-cycler + "
                + "hotbar-cycler active.");
    }
}
