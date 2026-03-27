package com.trevorschoeny.inventoryplus;


import com.trevorschoeny.inventoryplus.network.PocketCycleC2SPayload;
import com.trevorschoeny.menukit.MKKeybindSync;
import com.trevorschoeny.menukit.MKKeyMapping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Handles pocket cycling — rotating items through the hotbar slot from
 * the pocket storage. Left/right arrow keys (default, rebindable) cycle
 * the currently selected hotbar slot's pocket items.
 *
 * <p>Cycling rotates only the <b>enabled</b> positions (hotbar + non-disabled
 * pocket slots). Disabled slots are skipped entirely — their items stay put.
 * The hotbar position is always enabled.
 *
 * <p>Cycling only works when no screen is open (in-game HUD mode).
 * The client sends a C2S packet; the server handler performs the
 * authoritative item rotation.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class PocketCycler {

    static final int POCKET_SIZE = 3;

    private static MKKeyMapping cycleRightKey;
    private static MKKeyMapping cycleLeftKey;

    /** Returns the cycle-right key mapping for YACL gear icon scroll target. */
    public static MKKeyMapping getCycleRightKey() { return cycleRightKey; }

    /** Returns the cycle-left key mapping for YACL gear icon scroll target. */
    public static MKKeyMapping getCycleLeftKey() { return cycleLeftKey; }

    /**
     * Registers cycling keybinds. Called from {@link InventoryPlusClient#onInitializeClient}.
     * Creates MKKeyMapping instances from config values so modifier keys work correctly.
     *
     * @param category the shared "Trev's Mod" keybind category
     * @param cfg      the config containing persisted keybind values
     */
    public static void registerKeybinds(KeyMapping.Category category, InventoryPlusConfig cfg) {
        cycleRightKey = (MKKeyMapping) KeyBindingHelper.registerKeyBinding(
                MKKeyMapping.fromKeybind(cfg.pocketCycleRightKeybind,
                        "key.trevs-mod.pocket_cycle_right", category));

        cycleLeftKey = (MKKeyMapping) KeyBindingHelper.registerKeyBinding(
                MKKeyMapping.fromKeybind(cfg.pocketCycleLeftKeybind,
                        "key.trevs-mod.pocket_cycle_left", category));

        // Register tick handler that listens for keybinds
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Only cycle when no screen is open (gameplay mode)
            if (client.screen != null) return;
            if (client.player == null) return;

            // Pockets disabled — consume clicks silently so they don't queue up
            if (!InventoryPlusConfig.get().enablePockets) {
                while (cycleRightKey.consumeClick()) {}
                while (cycleLeftKey.consumeClick()) {}
                return;
            }

            while (cycleRightKey.consumeClick()) {
                cycle(client, false);  // cycle right (backward)
                PocketHud.triggerAnimation(false);
            }

            while (cycleLeftKey.consumeClick()) {
                cycle(client, true); // cycle left (forward)
                PocketHud.triggerAnimation(true);
            }
        });

        InventoryPlus.LOGGER.info("[PocketCycler] Registered keybinds");
    }

    // ── Config Sync ────────────────────────────────────────────────────────

    /**
     * Syncs runtime MKKeyMapping instances with current config values.
     * Called from the YACL save callback after the user changes keybinds.
     */
    public static void syncKeybinds(InventoryPlusConfig cfg) {
        cycleRightKey.updateFromKeybind(cfg.pocketCycleRightKeybind);
        cycleLeftKey.updateFromKeybind(cfg.pocketCycleLeftKeybind);
    }

    /**
     * Registers pocket cycling keybinds for Controls -> config sync.
     * Called from {@link InventoryPlusClient#onInitializeClient} after
     * keybind registration. Ensures changes in vanilla Controls are
     * persisted to config on screen close.
     */
    public static void registerKeybindSync() {
        MKKeybindSync.register(cycleRightKey, combo -> {
            InventoryPlusConfig.get().pocketCycleRightKeybind = combo;
            InventoryPlusConfig.save();
        });
        MKKeybindSync.register(cycleLeftKey, combo -> {
            InventoryPlusConfig.get().pocketCycleLeftKeybind = combo;
            InventoryPlusConfig.save();
        });
    }

    // ── Cycling ─────────────────────────────────────────────────────────────

    /**
     * Initiates a cycle on the selected hotbar slot's pocket.
     * Sends a C2S packet to the server for authoritative item movement.
     * The animation is triggered separately by the caller (client-side).
     */
    private static void cycle(Minecraft mc, boolean forward) {
        if (mc.player == null) return;

        int selectedSlot = mc.player.getInventory().getSelectedSlot(); // 0-8

        // Send the cycle request to the server. The server handler in
        // InventoryPlus.registerPocketCyclePacket() performs the actual
        // item rotation on the authoritative inventory. Works correctly
        // on both singleplayer and multiplayer — no server reference needed.
        ClientPlayNetworking.send(new PocketCycleC2SPayload(selectedSlot, forward));
    }
}
