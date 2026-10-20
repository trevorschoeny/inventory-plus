package com.trevorschoeny.inventoryplus.carried;

import com.trevlar.slotkit.api.window.BehaviorKeys;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedTake.Purpose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Pick block from carried containers (Emerald): the middle click on a block, or an
 * entity, whose item the inventory doesn't have takes it from a shulker box or bundle
 * the player carries, or the ender chest (the General tab's "Pick block" boxes).
 *
 * <p>It works alongside vanilla's pick rather than replacing it. Vanilla's pick, on the
 * server, looks for the item in the inventory and brings it to the hand; in survival,
 * with none there, it does nothing. So before vanilla's packet is sent, this asks for the
 * item in an empty inventory slot ({@link Purpose#PICK}): the request reaches the server
 * first, the item lands, and vanilla's pick then finds it and brings it to the hand the
 * usual way, selecting it in the hotbar or swapping it in. The order holds because the
 * server runs both on its own thread, in the order they arrive.
 *
 * <p>The item asked for is the one vanilla's pick asks for (the block's clone stack, or
 * the entity's pick result), matched as vanilla matches it, item and components. Creative
 * is left alone: vanilla's pick there makes the item from nothing.
 */
public final class CarriedPick {

    private CarriedPick() {}

    /** Called at the head of vanilla's pick, before it sends anything. Never stops it. */
    public static void beforePick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;
        if (player.hasInfiniteMaterials()) return;
        CarriedFetch.Reach reach = new CarriedFetch.Reach(IPConfig.pickFromShulkers(),
                IPConfig.pickFromBundles(), IPConfig.pickFromEnderChest());
        if (!reach.any()) return;
        ItemStack wanted = pickResult(mc);
        if (wanted.isEmpty() || !wanted.isItemEnabled(mc.level.enabledFeatures())) return;
        // The inventory has it: vanilla's pick does the rest.
        if (player.getInventory().findSlotMatchingItem(wanted) != -1) return;
        int target = CarriedFetch.emptySlot(player, BehaviorKeys.INVENTORY_INSERT);
        if (target < 0) return;
        CarriedFetch.fetch(mc.gameMode, player, reach, Purpose.PICK,
                s -> ItemStack.isSameItemSameComponents(s, wanted), null, wanted, target);
    }

    /** What vanilla's pick will ask for: the block's clone stack, or the entity's pick result. */
    private static ItemStack pickResult(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            return mc.level.getBlockState(block.getBlockPos()).getCloneItemStack(mc.level, block.getBlockPos(), false);
        }
        if (hit instanceof EntityHitResult entity) {
            ItemStack result = entity.getEntity().getPickResult();
            return result == null ? ItemStack.EMPTY : result;
        }
        return ItemStack.EMPTY;
    }
}
