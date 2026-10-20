package com.trevorschoeny.inventoryplus.autorestock;

import com.trevorschoeny.inventoryplus.carried.CarriedFetch;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedTake.Purpose;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Restock's last resort when an item runs out and the inventory has no more of it: the
 * same item from inside a shulker box or bundle the player carries, or from the ender
 * chest (the Restock tab's "Pull from" boxes under Items). How the item is fetched, and
 * what the server checks, is {@link CarriedFetch}'s.
 */
final class CarriedRestock {

    private CarriedRestock() {}

    /**
     * Fills {@code target} (a hotbar slot, or the offhand) with an item of {@code wanted}'s
     * kind from a carried container. Returns true when it sent a request or clicks.
     */
    static boolean refill(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack wanted, int target) {
        CarriedFetch.Reach reach = new CarriedFetch.Reach(IPConfig.restockItemsFromShulkers(),
                IPConfig.restockItemsFromBundles(), IPConfig.restockItemsFromEnderChest());
        if (!reach.any()) return false;
        return CarriedFetch.fetch(gameMode, player, reach, Purpose.RESTOCK, s -> s.is(wanted.getItem()), null,
                wanted, target);
    }
}
