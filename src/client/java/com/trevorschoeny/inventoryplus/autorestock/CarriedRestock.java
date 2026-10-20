package com.trevorschoeny.inventoryplus.autorestock;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.window.SlotOperations;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.nested.NestedRestock;
import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_PUT;
import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_TAKE;

/**
 * Restock's last resort when an item runs out and the inventory has no more of it: the
 * same item from inside a shulker box or bundle the player carries, or from the ender
 * chest (the Restock tab's "Pull from" boxes under Items).
 *
 * <ul>
 *   <li><b>The server runs Inventory Plus</b> (it answers {@link NestedRestock.Take}): the
 *       client finds the nearest match with SlotKit's {@code ItemSources.find}, from the
 *       contents riding on its own stacks, and asks the server for it by path. Any depth:
 *       a bundle in a shulker works.</li>
 *   <li><b>It doesn't</b>: only a bundle sitting directly in an inventory slot can be
 *       reached, with the clicks a player would make (select the item, right click the
 *       bundle to lift it, left click the empty slot). A shulker's contents can't be
 *       taken with clicks at all, so those are passed over.</li>
 *   <li><b>The ender chest</b>: the client never sees its contents unless it's open, so it
 *       can't choose. It asks the server to fill the slot from there
 *       ({@link NestedRestock.FromEnderChest}), naming the ender chest slots the player
 *       locked so the server passes them over; with no server mod, nothing.</li>
 * </ul>
 *
 * <p>Locks: a slot Restock may not take from also stops Restock reaching into a container
 * in it (Trev, 2026-10-20). Locks on items inside containers are not modelled yet.
 */
final class CarriedRestock {

    private CarriedRestock() {}

    /**
     * Fills {@code target} (a hotbar slot, or {@link NestedRestock#OFFHAND}) with an item of
     * {@code wanted}'s kind from a carried container. Returns true when it sent a request or
     * clicks; the item arrives with the server's next sync.
     */
    static boolean refill(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack wanted, int target) {
        boolean shulkers = IPConfig.restockItemsFromShulkers();
        boolean bundles = IPConfig.restockItemsFromBundles();
        if (shulkers || bundles) {
            boolean serverHelps = serverHelps(NestedRestock.Take.TYPE);
            ItemScope scope = ItemScope.inventory().shulkers(shulkers).bundles(bundles);
            for (ItemSources.Found found : ItemSources.find(player, scope, s -> s.is(wanted.getItem()))) {
                ItemPath path = found.path();
                if (path.depth() == 0) continue;   // the inventory's own slots were searched already
                if (!IPSlotOperations.allowsPlayerSlot(player, path.slot(), RESTOCK_TAKE)) continue;
                if (serverHelps) {
                    ClientPlayNetworking.send(new NestedRestock.Take(path, found.stack(), target));
                    InventoryPlusClient.LOGGER.debug("[item-restock] asked for {} at {}", found.stack(), path);
                    return true;
                }
                if (path.depth() == 1 && fromBundleByClicks(gameMode, player, path, target)) return true;
            }
        }
        if (IPConfig.restockItemsFromEnderChest() && serverHelps(NestedRestock.FromEnderChest.TYPE)) {
            int locked = 0;
            for (int i = 0; i < LockedSlots.ENDER_SLOT_COUNT; i++) if (LockedSlots.isEnderLocked(i)) locked |= 1 << i;
            ClientPlayNetworking.send(new NestedRestock.FromEnderChest(wanted.copyWithCount(1), target,
                    shulkers, bundles, locked));
            InventoryPlusClient.LOGGER.debug("[item-restock] asked the ender chest for {}", wanted.getItem());
            return true;
        }
        return false;
    }

    /**
     * Whether the server answers {@code type}: true only on a server running Inventory
     * Plus. The self-checks set {@code inventoryplus.checks.noServerMod} to play a vanilla
     * server from singleplayer.
     */
    private static boolean serverHelps(CustomPacketPayload.Type<?> type) {
        return !Boolean.getBoolean("inventoryplus.checks.noServerMod") && ClientPlayNetworking.canSend(type);
    }

    /**
     * The no-server-mod path: takes the item at {@code path} (one bundle deep, the bundle in
     * an inventory slot) into the empty {@code target} with vanilla clicks. False when it
     * can't: the top-level item isn't a bundle, something is on the cursor, or the target
     * refuses Restock.
     */
    private static boolean fromBundleByClicks(MultiPlayerGameMode gameMode, LocalPlayer player,
                                              ItemPath path, int target) {
        AbstractContainerMenu menu = player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return false;
        ItemStack bundle = player.getInventory().getItem(path.slot());
        if (!(bundle.getItem() instanceof BundleItem)) return false;   // a shulker needs the server
        int bundleSlot = PlayerMenuSlots.menuIndexOf(menu, player, path.slot());
        int targetSlot = target == NestedRestock.OFFHAND
                ? InventoryMenu.SHIELD_SLOT : PlayerMenuSlots.menuIndexOf(menu, player, target);
        if (bundleSlot < 0 || targetSlot < 0) return false;
        if (!IPSlotOperations.allowsMenuSlot(player, targetSlot, RESTOCK_PUT)) return false;

        // A bundle path's index is the item's position in the bundle's contents, which is
        // the index vanilla's selection uses (26.3: BundleContents.Mutable.removeOne takes
        // the selected item). Two catches: selecting is a toggle (selecting the item already
        // selected clears it, and removeOne then takes the first item), and the selection
        // isn't synced (BundleContents' stream codec carries only the items), so the client
        // can't know what the server has selected. Clearing first makes it certain: -1 is
        // out of range, which always clears; then the index selects. Both on the client's
        // copy too, as vanilla's own scroll does.
        int index = path.inside().get(0);
        for (int select : new int[] {-1, index}) {
            BundleItem.toggleSelectedItem(bundle, select);
            player.connection.send(new ServerboundSelectBundleItemPacket(bundleSlot, select));
        }
        SlotOperations.as(RESTOCK_TAKE, RESTOCK_PUT, () -> {
            // Right click on the bundle with an empty cursor lifts the selected item;
            // left click on the empty target sets it down.
            gameMode.handleContainerInput(menu.containerId, bundleSlot, 1, ContainerInput.PICKUP, player);
            gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player);
        });
        InventoryPlusClient.LOGGER.debug("[item-restock] took bundle item {} at {} by clicks", index, path);
        return true;
    }
}
