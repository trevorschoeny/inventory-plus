package com.trevorschoeny.inventoryplus.operations;

import static com.trevlar.menukit.api.slot.SlotGroupCategory.CHEST_STORAGE;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.DISPENSER_STORAGE;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.HOPPER_STORAGE;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.MOUNT_STORAGE;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.PLAYER_ARMOR;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.PLAYER_HOTBAR;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.PLAYER_INVENTORY;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.PLAYER_OFFHAND;
import static com.trevlar.menukit.api.slot.SlotGroupCategory.SHULKER_STORAGE;

import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.*;

import com.trevlar.menukit.api.slot.SlotGroupSet;
import com.trevlar.menukit.api.window.BehaviorKey;
import com.trevlar.menukit.api.window.SlotOperations.AppliesTo;
import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.SlotOperations.Role;
import com.trevlar.menukit.api.window.TriBool;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Inventory Plus's side of MenuKit's slot operations: defining its eight
 * operations, and asking about the player's own slots.
 *
 * <p>The keys themselves are public, in
 * {@link com.trevorschoeny.inventoryplus.api.InventoryPlusOperations}. This
 * class is internal: defining them is Inventory Plus's job alone, and the
 * helpers below are shaped around how Inventory Plus's own features address
 * slots (by container index, the way the restock and tool searches scan).
 */
public final class IPSlotOperations {

    private IPSlotOperations() {}

    /**
     * Defines the eight operations with their roles, so MenuKit lists them and a
     * settings screen can show only the ones that could move an item. From the
     * common initializer, so a server knows them too. Idempotent.
     */
    public static void define() {
        // Which vanilla groups each can act on (MenuKit's AppliesTo), so the
        // Reach tab offers only boxes that mean something (Trev, 2026-09-27:
        // "only what applies is offered"). Groups a mod adds apply too unless
        // excepted; one that declared a vanilla category follows that category.
        AppliesTo storage = AppliesTo.vanilla(PLAYER_INVENTORY, CHEST_STORAGE, SHULKER_STORAGE,
                DISPENSER_STORAGE, HOPPER_STORAGE, MOUNT_STORAGE);
        AppliesTo storageAndHotbar = AppliesTo.vanilla(PLAYER_INVENTORY, PLAYER_HOTBAR, CHEST_STORAGE,
                SHULKER_STORAGE, DISPENSER_STORAGE, HOPPER_STORAGE, MOUNT_STORAGE);
        AppliesTo carried = AppliesTo.vanilla(PLAYER_INVENTORY, PLAYER_HOTBAR);
        // The cyclers move only through their own inventory and hotbar slots,
        // never a pocket (the Pocket Cycler owns those).
        AppliesTo cycled = carried.except(new SlotGroupSet("inventorymax", "pockets"));
        // Sort works on the main inventory and on containers, never the hotbar.
        SlotOperations.define(SORT, Role.BOTH, storage);
        SlotOperations.define(MOVE_MATCHING_OUT, Role.TAKE, storageAndHotbar);
        SlotOperations.define(MOVE_MATCHING_IN, Role.PUT, storageAndHotbar);
        SlotOperations.define(RESTOCK_TAKE, Role.TAKE, carried);
        SlotOperations.define(RESTOCK_PUT, Role.PUT, AppliesTo.vanilla(PLAYER_HOTBAR, PLAYER_ARMOR, PLAYER_OFFHAND));
        SlotOperations.define(AUTO_TOOL_SWITCH, Role.BOTH, carried);
        SlotOperations.define(COLUMN_CYCLE, Role.BOTH, cycled);
        SlotOperations.define(HOTBAR_CYCLE, Role.BOTH, cycled);
    }

    /**
     * Whether {@code op} may act on the player's own inventory slot at container
     * index {@code containerSlot} (0-8 hotbar, 9-35 main, 36-39 armour, 40
     * offhand).
     *
     * <p>Asked through the player's inventory menu, not the bare container,
     * because MenuKit reads a vanilla slot's category from its menu: only that
     * way does a category-wide rule such as
     * {@code inherent(PLAYER_HOTBAR, op, FALSE)} reach the slot. The inventory
     * menu is always present, screen or not, so this works in the world too.
     * Falls back to the container form if the slot is somehow not on the menu.
     */
    public static boolean allowsPlayerSlot(Player player, int containerSlot, BehaviorKey<TriBool> op) {
        AbstractContainerMenu menu = player.inventoryMenu;
        // The same container-to-menu conversion every click uses (PlayerMenuSlots),
        // so a judgement and the click it guards can never name different slots.
        int menuIndex = PlayerMenuSlots.menuIndexOf(menu, player, containerSlot);
        if (menuIndex >= 0) return SlotOperations.allows(menu, menu.getSlot(menuIndex), player, op);
        return SlotOperations.allows(player.getInventory(), containerSlot, player, op);
    }

    /**
     * Whether {@code op} may act on the inventory-menu slot at {@code menuIndex}
     * (for example {@code InventoryMenu.SHIELD_SLOT}). An index that is not on
     * the menu is refused: a click there could not land anyway, and a feature
     * should not send one.
     */
    public static boolean allowsMenuSlot(Player player, int menuIndex, BehaviorKey<TriBool> op) {
        AbstractContainerMenu menu = player.inventoryMenu;
        if (menuIndex < 0 || menuIndex >= menu.slots.size()) return false;
        return SlotOperations.allows(menu, menu.getSlot(menuIndex), player, op);
    }
}
