package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import com.trevorschoeny.inventoryplus.api.HotbarCyclable;
import com.trevorschoeny.inventoryplus.autorestock.AutoRestockTicker;
import com.trevorschoeny.inventoryplus.autotoolswitch.AutoToolSwitch;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerButtons;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerClickInterceptor;
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
import com.trevorschoeny.inventoryplus.lockgroups.LockTooltips;
import com.trevorschoeny.inventoryplus.lockgroups.Locks;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsButtons;
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
 * Client entrypoint for Inventory Plus.
 *
 * <p>IP is a pure client-only Fabric mod per §0005 (IP scope) — installs on
 * any vanilla server. The {@code environment: "client"} declaration in
 * {@code fabric.mod.json} matches; depending only on MK (not MKC) keeps
 * MKC types off the classpath by construction, satisfying §0042's partition
 * test from the consumer side.
 *
 * <p>Phase 18b feature wireup happens here as each feature lands:
 * <ol>
 *   <li>Auto-restock — tick-driven detection + slot-click action; no MK
 *       widgets on the runtime path (config UI is a later concern).</li>
 *   <li>Move-matching — MK Button + keybind, riding the toolbar's
 *       slot-group-anchored panels (explicit {@code SlotGroupCategory}
 *       targeting; see {@code Toolbar}).</li>
 *   <li>Sorting — same shape as move-matching; second button stacked
 *       below at {@code OutsideRegion.RIGHT_ALIGN_TOP}.</li>
 * </ol>
 *
 * <p>Out of scope for 18b: locked-slots, pockets, IPP — deferred per the
 * brief. Code paths that would consult lock state currently treat all
 * slots as unlocked; pocket-slot exclusion is N/A because pockets aren't
 * here yet.
 */
public class InventoryPlusClient implements ClientModInitializer {

    public static final String MOD_ID = "inventoryplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        // Config — loaded once at startup; toggles persist immediately on
        // change via IPConfig setters. Must load before any feature reads
        // a toggle. See the IPConfig javadoc.
        IPConfig.load();

        // Slot operations: Inventory Plus's eight, defined with their roles so
        // MenuKit lists them (SlotOperations.all) and a lock can refuse any one
        // (plans/slot-operations.md). Every feature asks SlotOperations.allows
        // before it picks a slot and sends its clicks under SlotOperations.as.
        IPSlotOperations.define();

        // Keybinds — vanilla KeyMapping registration so L / I / O / S
        // show up in the Controls menu and are user-rebindable. The
        // mappings are referenced by the per-feature keybind classes via
        // KeyMapping.matches(key, scancode) from inside their
        // ScreenKeyboardEvents.afterKeyPress handlers.
        IPKeybinds.register();

        // Auto-restock — tick-driven detection of empty active hotbar /
        // offhand / armor slots, with refill from main inventory. Pure
        // client-side; sends vanilla slot-click packets so any vanilla
        // server accepts the operation. See AutoRestockTicker class
        // javadoc for the watch + refill model.
        ClientTickEvents.END_CLIENT_TICK.register(AutoRestockTicker::tick);

        // Auto Tool Switch — on-hit auto-swap of the right tool/weapon
        // for the target, composing the HotbarCyclableRegistry for
        // cyclable-tier resolution. Default OFF (opt-in via the master
        // toggle in the IP config tab). See AutoToolSwitch javadoc for
        // the three-tier resolution + auto-return mechanics.
        AutoToolSwitch.register();

        // Move Matching — inventory-centric, IN + OUT buttons live in
        // the IP toolbar. Screen-scoped I / O keybinds register
        // separately. Per-button visibility (.showWhen on the buttons)
        // hides MM IN/OUT on screens without an external container.
        MoveMatchingModes.load();
        MoveMatchingKeybind.register();

        // Locks (`plans/lock-groups.md`, `plans/reach.md`). A lock is a lock
        // group on a slot (LockedSlots, per world) or on an item (LockedItems,
        // per world); what each group stops is its choice in the reach record
        // (Reach, in config.json, loaded with the config above). The lock
        // button selects a group and L applies it; the button lives in the IP
        // toolbar (Toolbar.register below).
        //
        // Enforcement is one MenuKit veto (Locks): every operation MenuKit asks
        // about, vanilla's or a mod's, is refused on a slot whose group the
        // operation may not use, or whose lock groups stop it. Inventory Plus's
        // own features ask MenuKit before they act, so they get the same
        // answer. Manual cursor moves are operations too (click take / put),
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
        ClientTickEvents.END_CLIENT_TICK.register(LockedSlotKeybind::tick);

        // IP toolbar — one right-aligned MK panel above the player 3×9
        // grid, holding the lock button + MM IN/OUT (and future
        // feature buttons). Per-button .showWhen gates per-feature
        // visibility within the same panel.
        Toolbar.register();

        // Settings menu: the dot button lives in the toolbar above; the Open
        // settings key (unbound by default) is registered here.
        SettingsKeybind.register();

        // Column Cycler (Power Users) — opt-in feature gated by
        // columnCyclerEnabled. Slot membership state is per-world
        // (config/inventoryplus/column-cycler.json). The C keybind toggles
        // membership outside edit mode; the cycle-edit toolbar toggle
        // enters edit mode (where clicks become toggles). While "Lock the
        // slots the cyclers use" is on, cycle slots carry Slot lock as a
        // derived lock, like the Hotbar Cycler's rows below.
        LockedSlots.registerDerivedPlayerLock(ColumnCycler::pairLockApplies);
        ColumnCyclerButtons.registerLifecycle();
        ColumnCyclerClickInterceptor.register();
        ColumnCyclerKeybind.register();
        ColumnCyclerRotationKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerDragController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerRotationKeybind::tick);

        // Hotbar Cycler — rotates whole inventory rows through the hotbar.
        // Membership is per row (buttons, not a per-slot overlay), and the
        // rotation reuses ColumnCyclerRotator's engine across all nine
        // columns, which is what keeps items in their own column.
        //
        // The row lock is registered as a DERIVED lock rather than written
        // into the stored set: it belongs to the position, has to stay put
        // while items rotate through it, and has to appear and vanish the
        // instant a row or either lock config is toggled. Registering the
        // predicate here means Sort, Move Matching, shift-click and the
        // lock icon all honour it through the one enforcement predicate
        // they already call.
        HotbarCycler.load();
        LockedSlots.registerDerivedPlayerLock(HotbarCycler::rowLockApplies);
        HotbarCyclerRowButtons.register();
        HotbarCyclerKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(HotbarCyclerKeybind::tick);
        // The hotbar slide and the change-gated preview slides. Init order
        // relative to the HUD sources below is irrelevant: this only adds a
        // rotation listener now, and consults the source registry at rotation
        // time. That is also what lets Pocket Cycler's source, registered by
        // Inventory Max at its own init, take part without IP referencing it.
        HotbarCyclerHud.register();

        // HotbarCyclable registration — Column Cycler is the first
        // implementer of the cycler-agnostic "bring this slot's item to
        // the hotbar" contract that Auto Tool Switch (and future
        // Pocket Cycler / Hotbar Swap) consume. The registry is the
        // architectural seam that keeps downstream consumers cycler-
        // agnostic. See the cyclable/ package for the interfaces and
        // the registry's javadoc.
        HotbarCyclableRegistry.register(ColumnCyclerCyclable.INSTANCE);

        // Shared cycle HUD — Mini-hotbar strip to the right of the vanilla
        // hotbar, showing whichever cycler is active on the selected hotbar
        // slot (Column Cycler here; Pocket Cycler in IPP registers its own
        // source into the same registry). Generalized 2026-06-02: the render
        // is cycler-agnostic (CycleHud reads CycleHudRegistry); Column
        // Cycler's contents + animation bridge live in ColumnCyclerHudSource.
        ColumnCyclerHudSource.register();
        CycleHud.register();

        // Power Users toolbar — right-of-grid vertical stack for opt-in
        // PU buttons. Currently holds just the Column Cycler edit
        // toggle. Hides entirely when no PU feature is enabled (each
        // button has its own .showWhen gate).
        PowerUsersToolbar.register();

        // Sort — S keybind sorts the simplecontainer under the cursor.
        // QUANTITY_DESC only for MVP; per-container sort-type persistence
        // wired end-to-end (storage in sort-state.json) but unused until
        // the type-cycle power-user feature lands. No button — gated on
        // MenuKit's render-integration fix (see DEFERRED.md /
        // surface-library-gaps memory).
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

        LOGGER.info("[inventoryplus] Client initialized — auto-restock + "
                + "move-matching + locked-slots + locked-items + sort + column-cycler + "
                + "hotbar-cycler active.");
    }
}
