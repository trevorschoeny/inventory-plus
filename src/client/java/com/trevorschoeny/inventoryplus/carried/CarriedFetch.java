package com.trevorschoeny.inventoryplus.carried;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.TriBool;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.nested.CarriedTake;
import com.trevorschoeny.inventoryplus.nested.CarriedTake.Purpose;
import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Predicate;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.RESTOCK_PUT;

/**
 * The client's half of taking an item out of a carried container, shared by every
 * feature that does it (Restock's ran out, broke, before break and ammo; pick block):
 * find, honour locks, then ask.
 *
 * <ul>
 *   <li><b>The server runs Inventory Plus</b> (it answers {@link CarriedTake.Take}): the
 *       client finds the item with SlotKit's {@code ItemSources.find}, from the contents
 *       riding on its own stacks, and asks the server for it by path. Any depth: a bundle
 *       in a shulker works.</li>
 *   <li><b>It doesn't</b>: only a bundle sitting directly in an inventory slot can be
 *       reached, with the clicks a player would make (select the item, right click the
 *       bundle to lift it, left click the target). A shulker's contents can't be taken
 *       with clicks at all, so those are passed over.</li>
 *   <li><b>The ender chest</b>: the client never sees its contents unless it's open, so it
 *       can't choose. It asks the server to choose ({@link CarriedTake.FromEnderChest}),
 *       naming the ender chest slots the player locked so the server passes them over;
 *       with no server mod, nothing.</li>
 * </ul>
 *
 * <p>Locks: a slot the purpose's operation may not take from also stops the reach into a
 * container in it (Trev, 2026-10-20). Locks on items inside containers are not modelled
 * yet.
 */
public final class CarriedFetch {

    private CarriedFetch() {}

    /** Where a feature may reach, from its settings: the three "Pull from" boxes. */
    public record Reach(boolean shulkers, boolean bundles, boolean enderChest) {
        public boolean any() {
            return shulkers || bundles || enderChest;
        }
    }

    /**
     * Puts an item {@code test} accepts into {@code target} (a container index of the
     * player's own inventory), taken from a carried container. Returns true when it sent
     * a request or clicks; the item arrives with the server's next sync.
     *
     * @param prefer     among the containers' matches, the one to take: the greatest by
     *                   this order, the nearest on a tie; {@code null} takes the nearest
     * @param enderProbe what the server should look for in the ender chest ({@link
     *                   CarriedTake.FromEnderChest}); the server matches it by the purpose's
     *                   own rule
     */
    public static boolean fetch(MultiPlayerGameMode gameMode, LocalPlayer player, Reach reach, Purpose purpose,
                                Predicate<ItemStack> test, @Nullable Comparator<ItemStack> prefer,
                                ItemStack enderProbe, int target) {
        return fetch(gameMode, player, reach, purpose, test, prefer, enderProbe, target, -1);
    }

    /**
     * The same, for a before-break swap ({@link Purpose#RESTOCK_SWAP}): the worn item in
     * {@code target} moves to {@code spare}, an empty inventory slot the client chose with
     * its own locks.
     */
    public static boolean fetch(MultiPlayerGameMode gameMode, LocalPlayer player, Reach reach, Purpose purpose,
                                Predicate<ItemStack> test, @Nullable Comparator<ItemStack> prefer,
                                ItemStack enderProbe, int target, int spare) {
        if (reach.shulkers() || reach.bundles()) {
            boolean serverHelps = serverHelps(CarriedTake.Take.TYPE);
            ItemScope scope = ItemScope.inventory().shulkers(reach.shulkers()).bundles(reach.bundles());
            ItemSources.Found chosen = null;
            for (ItemSources.Found found : ItemSources.find(player, scope, test)) {
                ItemPath path = found.path();
                if (path.depth() == 0) continue;   // the inventory's own slots are the feature's to search
                if (!IPSlotOperations.allowsPlayerSlot(player, path.slot(), purpose.take())) continue;
                // Without the server mod only a bundle directly in a slot can be reached.
                if (!serverHelps && !(path.depth() == 1
                        && player.getInventory().getItem(path.slot()).getItem() instanceof BundleItem)) continue;
                if (chosen == null || (prefer != null && prefer.compare(found.stack(), chosen.stack()) > 0)) {
                    chosen = found;
                }
                if (prefer == null) break;
            }
            if (chosen != null) {
                if (serverHelps) {
                    ClientPlayNetworking.send(new CarriedTake.Take(chosen.path(), chosen.stack(), target, purpose, spare));
                    InventoryPlusClient.LOGGER.debug("[carried] {}: asked for {} at {}", purpose, chosen.stack(), chosen.path());
                    return true;
                }
                if (fromBundleByClicks(gameMode, player, chosen.path(), target, spare, purpose.take(), putOf(purpose))) {
                    return true;
                }
            }
        }
        if (reach.enderChest() && serverHelps(CarriedTake.FromEnderChest.TYPE)) {
            int locked = 0;
            for (int i = 0; i < LockedSlots.ENDER_SLOT_COUNT; i++) if (LockedSlots.isEnderLocked(i)) locked |= 1 << i;
            ClientPlayNetworking.send(new CarriedTake.FromEnderChest(enderProbe.copyWithCount(1), target, purpose,
                    spare, reach.shulkers(), reach.bundles(), locked));
            InventoryPlusClient.LOGGER.debug("[carried] {}: asked the ender chest for {}", purpose, enderProbe.getItem());
            return true;
        }
        return false;
    }

    /**
     * Whether the server answers {@code type}: true only on a server running Inventory
     * Plus. The self-checks set {@code inventoryplus.checks.noServerMod} to play a vanilla
     * server from singleplayer.
     */
    static boolean serverHelps(CustomPacketPayload.Type<?> type) {
        return !Boolean.getBoolean("inventoryplus.checks.noServerMod") && ClientPlayNetworking.canSend(type);
    }

    /** The operation the target slot must allow for {@code purpose}, as the server's {@code room} asks it. */
    private static BehaviorKey<TriBool> putOf(Purpose purpose) {
        return switch (purpose) {
            case RESTOCK, RESTOCK_SWAP -> RESTOCK_PUT;
            case AMMO, PICK -> BehaviorKeys.INVENTORY_INSERT;
        };
    }

    /**
     * The no-server-mod path: takes the item at {@code path} (one bundle deep, the bundle in
     * an inventory slot) into {@code target} with vanilla clicks. The target is empty, or,
     * for a swap ({@code spare} set), holds the worn item, which the clicks leave in the
     * empty {@code spare}. False when it can't: the top-level item isn't a bundle, something
     * is on the cursor, the slots aren't as described, or the target refuses {@code put}.
     */
    private static boolean fromBundleByClicks(MultiPlayerGameMode gameMode, LocalPlayer player, ItemPath path,
                                              int target, int spare, BehaviorKey<TriBool> take,
                                              BehaviorKey<TriBool> put) {
        AbstractContainerMenu menu = player.inventoryMenu;
        if (!menu.getCarried().isEmpty()) return false;
        ItemStack bundle = player.getInventory().getItem(path.slot());
        if (!(bundle.getItem() instanceof BundleItem)) return false;   // a shulker needs the server
        boolean swap = spare >= 0;
        if (player.getInventory().getItem(target).isEmpty() == swap) return false;
        if (swap && !player.getInventory().getItem(spare).isEmpty()) return false;
        int bundleSlot = PlayerMenuSlots.menuIndexOf(menu, player, path.slot());
        int targetSlot = PlayerMenuSlots.menuIndexOf(menu, player, target);
        int spareSlot = swap ? PlayerMenuSlots.menuIndexOf(menu, player, spare) : -1;
        if (bundleSlot < 0 || targetSlot < 0 || (swap && spareSlot < 0)) return false;
        if (!IPSlotOperations.allowsMenuSlot(player, targetSlot, put)) return false;
        if (swap && !IPSlotOperations.allowsMenuSlot(player, targetSlot, take)) return false;

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
        SlotOperations.as(take, put, () -> {
            // Right click on the bundle with an empty cursor lifts the selected item;
            // left click on the target sets it down (on a worn item, swaps the two, and a
            // left click on the empty spare sets the worn one down).
            gameMode.handleContainerInput(menu.containerId, bundleSlot, 1, ContainerInput.PICKUP, player);
            gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.PICKUP, player);
            if (swap) gameMode.handleContainerInput(menu.containerId, spareSlot, 0, ContainerInput.PICKUP, player);
        });
        InventoryPlusClient.LOGGER.debug("[carried] took bundle item {} at {} by clicks", index, path);
        return true;
    }

    /**
     * An empty slot of the player's to take an item into, honouring locks: the first empty
     * hotbar slot, else the first empty main inventory slot, that allows {@code put}. -1 when
     * there is none.
     */
    public static int emptySlot(LocalPlayer player, BehaviorKey<TriBool> put) {
        return emptySlot(player, put, -1);
    }

    /** The same, never {@code except}. */
    public static int emptySlot(LocalPlayer player, BehaviorKey<TriBool> put, int except) {
        for (int i = 0; i < 36; i++) {
            if (i == except) continue;
            if (player.getInventory().getItem(i).isEmpty() && IPSlotOperations.allowsPlayerSlot(player, i, put)) return i;
        }
        return -1;
    }
}
