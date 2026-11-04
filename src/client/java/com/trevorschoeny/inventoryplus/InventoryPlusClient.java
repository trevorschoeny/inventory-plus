package com.trevorschoeny.inventoryplus;


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
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.tooltips.Tooltips;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.OwnLocks;
import com.trevorschoeny.inventoryplus.lockedslots.SlotMarks;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingKeybind;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingModes;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;
import com.trevorschoeny.inventoryplus.sort.SortKeybind;
import com.trevorschoeny.inventoryplus.sort.SortState;
import com.trevorschoeny.inventoryplus.toolbar.PowerUsersToolbar;
import com.trevorschoeny.inventoryplus.settings.SettingsKeybind;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenFrames;
import com.trevlar.menukit.api.panel.ScreenMatcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint for Inventory Plus. It works on any vanilla server.
 * It uses only SlotKit's client tier: it builds no slots
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

        // Lets a vanilla chest's own rows scroll inside its frame on a short
        // window, instead of the frame overflowing it. Every slot-position
        // read in Inventory Plus already goes through slot.x/slot.y
        // (ScreenLayout, SortKeybind, the lock/cycle drag controllers, the
        // overlay mixin) and every button through MenuKit/SlotKit's own
        // panel placement, so nothing here computes a chest's grid position
        // itself; it follows the scroll with no further change.
        ScreenFrames.scrollsWhenShort(ModeScope.BOTH, ScreenMatcher.only(ContainerScreen.class));

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
        // Intake: a carried shulker box's pickups setting, set through the server,
        // and the one-time move of Reach's pickup choice onto the boxes carried.
        com.trevorschoeny.inventoryplus.carried.IntakeClient.register();

        // Auto Tool Switch swaps in the right tool or weapon on hit, using the
        // HotbarCyclableRegistry for the cyclable tier. Off by default.
        AutoToolSwitch.register();

        // Move Matching's in and out buttons live in the toolbar, and its
        // screen-scoped I and O keys register separately. Each button's
        // showWhen hides it on screens without an external container.
        MoveMatchingModes.load();
        MoveMatchingKeybind.register();

        // A lock is a slot that takes only deliberate moves (rules.SlotLocks). The
        // player's own are kept here per world (LockedSlots) and judged by this
        // client's veto (OwnLocks), which also sends them to a server with
        // Inventory Plus; a chest's or box's are SHARED slot state the common
        // veto judges on both sides. L locks the hovered slot; the lock button
        // in the toolbar (Toolbar.register below) pauses the player's own.
        // Column Cycler's membership loads first: upgrading a 1.5.x lock file
        // drops the locks its old pairing wrote (LockedSlots.load).
        ColumnCycler.load();
        LockedSlots.load(ColumnCycler::pairedSlotsIn);
        OwnLocks.register();
        // Padlock and cycle icon draw as one MenuKit
        // slot decoration (7.5.0), in place of the old extractSlot mixin.
        SlotMarks.register();
        // Tooltips: every line Inventory Plus adds or changes, and the hide key.
        Tooltips.register();

        // One right-aligned MenuKit panel above the player's 3x9 grid, holding
        // the action buttons. Each button's showWhen decides its own
        // visibility within the panel.
        Toolbar.register();
        // Edit mode: its buttons are on the toolbars above; its dialogs and its look here.
        com.trevorschoeny.inventoryplus.editmode.EditMode.register();
        com.trevorschoeny.inventoryplus.editmode.BlueprintDialogs.register();

        // Settings menu: the dot button lives in the toolbar above; the Open
        // settings key (unbound by default) is registered here.
        SettingsKeybind.register();

        // Column Cycler is off by default (columnCyclerEnabled). Slot
        // membership is per world (config/inventoryplus/column-cycler.json).
        // The C key toggles membership, and its inventory button turns the
        // feature on and off.
        ColumnCyclerKeybind.register();
        ColumnCyclerRotationKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerDragController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerRotationKeybind::tick);

        // Hotbar Cycler rotates whole inventory rows through the hotbar.
        // Membership is per row (buttons, not a per-slot overlay), and the
        // rotation reuses ColumnCyclerRotator's engine across all nine
        // columns, which is what keeps items in their own column.
        HotbarCycler.load();
        HotbarCyclerRowButtons.register();
        HotbarCyclerKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(HotbarCyclerKeybind::tick);
        // The hotbar slide and the change-gated preview slides. Init order
        // relative to the HUD sources below is irrelevant: this only adds a
        // rotation listener now, and snapshots MenuKit's cycle strip at rotation
        // time, which is also what lets another mod's ring take part.
        HotbarCyclerHud.register();

        // Column Cycler implements the "bring this slot's item to the hotbar"
        // contract that Auto Tool Switch uses. Going through the registry
        // keeps consumers independent of any one cycler.
        HotbarCyclableRegistry.register(ColumnCyclerCyclable.INSTANCE);

        // Column Cycler's ring on MenuKit's cycle strip (7.1.0), right of the
        // vanilla hotbar. MenuKit draws it; ColumnCyclerHudSource says what
        // the column holds and when it turned.
        ColumnCyclerHudSource.register();

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
                + "move-matching + locked-slots + sort + column-cycler + "
                + "hotbar-cycler active.");
    }
}
