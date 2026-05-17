package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.autorestock.AutoRestockTicker;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;
import com.trevorschoeny.inventoryplus.movematching.MoveMatchingKeybind;

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
        // Auto-restock — tick-driven detection of empty active hotbar /
        // offhand / armor slots, with refill from main inventory. Pure
        // client-side; sends vanilla slot-click packets so any vanilla
        // server accepts the operation. See AutoRestockTicker class
        // javadoc for the watch + refill model.
        ClientTickEvents.END_CLIENT_TICK.register(AutoRestockTicker::tick);

        // Move Matching — inventory-centric, IN + OUT widgets above the
        // player's 3×9 main inv when a supported container is open.
        // Plus a screen-scoped I / O keybind pair that fires anywhere
        // in the inventory screen under the same condition. Per Lead /
        // Trev's 2026-05-16 simplification: no per-container cycle, no
        // persistence, no stale-key pruner — Locked Slots / Locked
        // Items become the protection mechanism in a later round.
        MoveMatchingButtons.register();
        MoveMatchingKeybind.register();

        LOGGER.info("[inventoryplus] Client initialized — auto-restock + "
                + "move-matching active (sorting still pending).");
    }
}
