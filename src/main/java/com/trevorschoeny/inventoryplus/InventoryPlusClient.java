package com.trevorschoeny.inventoryplus;

import net.fabricmc.api.ClientModInitializer;

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
        // Scaffold-only smoke pass: no features wired yet. Subsequent
        // 18b deltas register feature modules here in dependency order
        // (auto-restock first since it has the fewest cross-cutting
        // concerns, then move-matching, then sorting).
        LOGGER.info("[inventoryplus] Client initialized — Phase 18b scaffold, no features wired yet.");
    }
}
