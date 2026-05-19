package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.autorestock.AutoRestockTicker;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerButtons;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerClickInterceptor;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerDragController;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerKeybind;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotationKeybind;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsButtons;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsClickInterceptor;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsDragController;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlotKeybind;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingKeybind;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;
import com.trevorschoeny.inventoryplus.sort.SortKeybind;
import com.trevorschoeny.inventoryplus.sort.SortState;
import com.trevorschoeny.inventoryplus.toolbar.PowerUsersToolbar;
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
 *   <li>Move-matching — MK Button + keybind, registered against every
 *       simplecontainer screen via {@link com.trevorschoeny.menukit.inject.ScreenPanelAdapter}.</li>
 *   <li>Sorting — same shape as move-matching; second button stacked
 *       below at {@code MenuRegion.RIGHT_ALIGN_TOP}.</li>
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
        // a toggle. See IPConfig + IPConfigScreen javadoc.
        IPConfig.load();

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

        // Move Matching — inventory-centric, IN + OUT buttons live in
        // the IP toolbar. Screen-scoped I / O keybinds register
        // separately. Per-button visibility (.showWhen on the buttons)
        // hides MM IN/OUT on screens without an external container.
        MoveMatchingKeybind.register();

        // Locked Slots — per-world client-side persistence + L keybind
        // toggle + edit-mode click interceptor + drag-to-toggle.
        //
        // Protection is mixin-driven (no post-hoc corrector):
        //   - AbstractContainerMenuMoveItemStackToMixin blocks shift-click
        //     destination into locked slots (both client + server threads
        //     in single-player via UUID-equality check in isLockable).
        //   - InventoryGetFreeSlotMixin blocks auto-pickup destination
        //     into empty locked slots (same UUID-equality pattern).
        // Manual cursor placement passes through unmodified — neither
        // mixin blocks the PICKUP click-type path. See package javadocs.
        //
        // The lock-edit toggle button lives in the IP toolbar (see
        // Toolbar.register below); registerLifecycle here handles the
        // edit-mode auto-reset on screen open.
        LockedSlots.load();
        LockedSlotsButtons.registerLifecycle();
        LockedSlotsClickInterceptor.register();
        LockedSlotKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(LockedSlotsDragController::tick);

        // IP toolbar — one right-aligned MK panel above the player 3×9
        // grid, holding lock-edit toggle + MM IN/OUT (and future
        // feature buttons). Per-button .showWhen gates per-feature
        // visibility within the same panel.
        Toolbar.register();

        // Column Cycler (Power Users) — opt-in feature gated by
        // columnCyclerEnabled. Slot membership state is per-world
        // (config/inventoryplus/column-cycler.json), with a parallel
        // tiedLocks set for the Lock-pairing rule. The C keybind toggles
        // membership outside edit mode; the cycle-edit toolbar toggle
        // enters edit mode (where clicks become toggles). Mutual
        // exclusion with Lock Slots' edit mode is enforced inside the
        // edit-mode setters.
        ColumnCycler.load();
        ColumnCyclerButtons.registerLifecycle();
        ColumnCyclerClickInterceptor.register();
        ColumnCyclerKeybind.register();
        ColumnCyclerRotationKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerDragController::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ColumnCyclerRotationKeybind::tick);

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
                + "move-matching + locked-slots + sort + column-cycler active.");
    }
}
