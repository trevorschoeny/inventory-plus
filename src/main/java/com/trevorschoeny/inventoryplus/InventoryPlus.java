package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.menukit.MenuKit;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory Plus — a set of features that enhance the player's inventory.
 *
 * <p><b>Equipment:</b> Two passive equipment slots (elytra + totem) that provide
 * their effects without occupying armor or hand slots.
 *
 * <p><b>Pockets:</b> Each hotbar slot can store up to 3 extra items that cycle
 * via keybind, extending the hotbar's effective capacity.
 *
 * <p>All UI is built on the MenuKit API — panels, slots, buttons, and HUD
 * elements are declared via builder chains. Persistence, sync, and creative
 * mode support are handled automatically by MenuKit.
 */
public class InventoryPlus implements ModInitializer {

    public static final String MOD_ID = "inventory-plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Load config from disk
        InventoryPlusConfig.load();

        // Container: 2-slot storage for passive equipment (elytra + totem)
        // Registered before the panel so the panel can reference it by name.
        MenuKit.container("equipment").playerBound().size(2).register();

        // Feature 1: Equipment panel (UI for the equipment container)
        EquipmentPanel.register();

        // Feature 2: Pockets (hotbar extension — extra items per hotbar slot)
        PocketsPanel.register();

        LOGGER.info("[InventoryPlus] Initialized");
    }
}
