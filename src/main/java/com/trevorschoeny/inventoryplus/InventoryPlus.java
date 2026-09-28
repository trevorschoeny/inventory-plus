package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import net.fabricmc.api.ModInitializer;

/**
 * Inventory Plus on both sides (§0062). Everything a player sees and does is
 * the client's ({@link InventoryPlusClient}, in the client source set); what
 * runs here is only what a server needs to know about Inventory Plus: its slot
 * operations, so a server can answer "may Sort take from this slot?" for a
 * companion like Inventory Max, and name the operation a packet carries.
 *
 * <p>Runs before the client initializer on a client, and MenuKit freezes
 * declarations after both, so the operations are defined in time either way.
 */
public final class InventoryPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        IPSlotOperations.define();
    }
}
