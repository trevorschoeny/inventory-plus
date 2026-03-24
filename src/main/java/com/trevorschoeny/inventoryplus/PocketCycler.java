package com.trevorschoeny.inventoryplus;


import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MenuKit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
 * The swap happens on the server thread via {@code server.execute()}
 * for authoritative item movement.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class PocketCycler {

    static final int POCKET_SIZE = 3;

    private static KeyMapping cycleRightKey;
    private static KeyMapping cycleLeftKey;

    /**
     * Registers cycling keybinds. Called from {@link InventoryPlus#initClient}.
     *
     * @param category the shared "Trev's Mod" keybind category
     */
    public static void registerKeybinds(KeyMapping.Category category) {
        cycleRightKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trevs-mod.pocket_cycle_right",
                GLFW.GLFW_KEY_RIGHT,    // default: Right Arrow
                category
        ));

        cycleLeftKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trevs-mod.pocket_cycle_left",
                GLFW.GLFW_KEY_LEFT,     // default: Left Arrow
                category
        ));

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

    // ── Cycling ─────────────────────────────────────────────────────────────

    /**
     * Initiates a cycle on the selected hotbar slot's pocket.
     * Delegates to the server thread for authoritative item movement.
     */
    private static void cycle(Minecraft mc, boolean forward) {
        if (mc.player == null) return;

        int selectedSlot = mc.player.getInventory().getSelectedSlot(); // 0-8
        String containerName = "pocket_" + selectedSlot;

        // Execute the actual swap on the server thread (authoritative)
        var server = mc.getSingleplayerServer();
        if (server == null) return;

        final int slot = selectedSlot;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp == null) return;

            Inventory inventory = sp.getInventory();
            MKContainer pocket = MenuKit.getContainerForPlayer(
                    containerName, sp.getUUID(), true);
            if (pocket == null) return;

            Set<Integer> disabled = PocketsPanel.getDisabledSlots(slot);
            int maxSlots = InventoryPlusConfig.get().pocketSlotCount;

            // Collect the indices of enabled positions.
            // Position 0 = hotbar (always enabled), positions 1-3 = pocket slots.
            // Slots beyond the configured count are treated as disabled.
            List<Integer> enabledIndices = new ArrayList<>();
            enabledIndices.add(0); // hotbar is always in the rotation
            for (int i = 0; i < POCKET_SIZE; i++) {
                if (i < maxSlots && !disabled.contains(i)) {
                    enabledIndices.add(i + 1); // pocket index i → position i+1
                }
            }

            // Need at least 2 enabled positions to cycle
            if (enabledIndices.size() < 2) return;

            // Read items at enabled positions (copy to avoid reference issues)
            ItemStack[] allItems = new ItemStack[1 + POCKET_SIZE];
            allItems[0] = inventory.getItem(slot).copy();
            for (int i = 0; i < POCKET_SIZE; i++) {
                allItems[i + 1] = pocket.getItem(i).copy();
            }

            // Extract the enabled items into a list, rotate, write back
            List<ItemStack> enabledItems = new ArrayList<>();
            for (int idx : enabledIndices) {
                enabledItems.add(allItems[idx]);
            }

            // Rotate the enabled items
            if (forward) {
                // Forward: first item wraps to end
                ItemStack first = enabledItems.remove(0);
                enabledItems.add(first);
            } else {
                // Backward: last item wraps to front
                ItemStack last = enabledItems.remove(enabledItems.size() - 1);
                enabledItems.add(0, last);
            }

            // Write rotated items back to their original enabled positions
            for (int i = 0; i < enabledIndices.size(); i++) {
                int pos = enabledIndices.get(i);
                ItemStack item = enabledItems.get(i).copy();
                if (pos == 0) {
                    inventory.setItem(slot, item);
                } else {
                    pocket.setItem(pos - 1, item);
                }
            }

        });
    }
}
