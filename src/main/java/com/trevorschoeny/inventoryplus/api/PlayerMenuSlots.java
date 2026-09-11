package com.trevorschoeny.inventoryplus.api;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.UUID;

/**
 * Where one of the player's own inventory slots sits on a menu: the single
 * conversion from the index a search speaks to the index a click speaks.
 *
 * <h3>Two numberings for one slot</h3>
 *
 * <p>A <b>container index</b> is the slot's place in the player's
 * {@link Inventory}, what {@code Slot.getContainerSlot()} and
 * {@code Inventory.getItem(i)} use: 0-8 hotbar, 9-35 main, 36-39 armour,
 * 40 offhand. Every Inventory Plus search (restock, Auto Tool Switch, the
 * cyclers) scans and returns this.
 *
 * <p>A <b>menu index</b> is the slot's place in a menu's slot list,
 * {@code Slot.index}, and it is what {@code handleContainerInput} names. It
 * depends on the menu. On the player's own {@code InventoryMenu}, which is
 * open even with no screen up, 0 is the crafting result, 1-4 the crafting
 * grid, 5-8 armour, 9-35 main, 36-44 hotbar and 45 the offhand. The main grid
 * happens to match, so passing a container index straight through works for
 * 9-35 and silently clicks the crafting grid or an armour slot for 0-8.
 *
 * <p>That is exactly the defect 1.6.0 fixes: every offhand and armour restock
 * sent the search's container index as the click's menu index, so a spare
 * found on the hotbar made restock click the wrong slot. Auto Tool Switch and
 * the Column Cycler each carried a private copy of this scan and were right;
 * restock had none. There is now one, and every click that names one of the
 * player's own slots goes through it.
 *
 * <h3>Why a scan and not arithmetic</h3>
 *
 * <p>Reading the menu's own slots is correct for any menu that shows the
 * player's inventory, not just {@code InventoryMenu}: a chest, a furnace, the
 * creative screen. The match is by the inventory's player UUID rather than by
 * reference so it holds on either side of the single-player thread divide,
 * the same rule {@code LockedSlots.isLockable} follows.
 *
 * <p>Public because Inventory Max's totem restock converts the same index
 * and should not keep its own copy. Only for code that runs on the client:
 * Inventory Plus is client-only, so anything on a server path must not name
 * this class.
 */
public final class PlayerMenuSlots {

    private PlayerMenuSlots() {}

    /**
     * The menu index of {@code player}'s inventory slot at container index
     * {@code containerSlot} on {@code menu}, or {@code -1} when that slot is
     * not on this menu. A caller that gets {@code -1} should not click at all:
     * there is no slot it could safely name.
     */
    public static int menuIndexOf(AbstractContainerMenu menu, Player player, int containerSlot) {
        UUID owner = player.getUUID();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory inv)) continue;
            if (!inv.player.getUUID().equals(owner)) continue;
            if (slot.getContainerSlot() == containerSlot) return slot.index;
        }
        return -1;
    }
}
