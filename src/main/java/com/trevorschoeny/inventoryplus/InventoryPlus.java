package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.nested.CarriedPut;
import com.trevorschoeny.inventoryplus.nested.CarriedTake;

import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;

import net.fabricmc.api.ModInitializer;

/**
 * Inventory Plus on both sides. Everything a player sees and does is
 * the client's ({@link InventoryPlusClient}, in the client source set); what
 * runs here is only what a server needs to know about Inventory Plus: its slot
 * operations, so a server can answer "may Sort take from this slot?" for a
 * companion like Hotbar Pockets, and name the operation a packet carries; the locks
 * a server holds ({@link SlotLocks}); pickups into carried boxes by their Intake
 * ({@link CarriedPut}); and Restock's requests to take from inside carried containers
 * ({@link CarriedTake}).
 *
 * <p>Runs before the client initializer on a client, and MenuKit freezes
 * declarations after both, so the operations are defined in time either way.
 */
public final class InventoryPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        IPSlotOperations.define();
        // Restock from inside carried shulkers, bundles and the ender chest: the
        // server's half. A server without Inventory Plus has no receiver, which
        // is how the client knows to fall back to plain clicks.
        CarriedTake.register();
        // Locks on placed containers and boxes, and the player's own as their client sends them.
        SlotLocks.register();
        // Pickups into carried shulker boxes by their Intake: the server's half.
        CarriedPut.register();
    }
}
