package com.trevorschoeny.inventoryplus.autotoolswitch;

import com.trevlar.slotkit.api.window.BehaviorKeys;

import com.trevorschoeny.inventoryplus.carried.CarriedFetch;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedTake.Purpose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.function.Predicate;

/**
 * Auto Tool Switch reaching into carried containers (Emerald), only when the inventory
 * has no tool or weapon that fits: the best one from a shulker box or bundle comes into an
 * empty inventory slot ({@link Purpose#TOOL}), and the switch then finds it there like any
 * inventory tool, so switching back works as it always has. The tool stays in the
 * inventory afterwards.
 *
 * <p>The tool arrives a moment after the swing that asked for it. For mining, the switch
 * follows on its own if the player is still digging the same block ({@link #tick}); for
 * combat, the next swing finds the weapon.
 */
final class CarriedTools {

    private CarriedTools() {}

    /** The block a fetched tool is for, and the tick to give up waiting for it. */
    private static @Nullable BlockPos waitingFor;
    private static int waitUntil;

    private static final int WAIT_TICKS = 20;

    private static CarriedFetch.Reach reach() {
        // No ender chest: which tool is best is the client's call, and it can't see in.
        return new CarriedFetch.Reach(IPConfig.autoToolSwitchFromShulkers(), IPConfig.autoToolSwitchFromBundles(), false);
    }

    /** The inventory has no tool for {@code state}: fetch the best one from a carried container. */
    static void forBlock(LocalPlayer player, BlockPos pos, BlockState state) {
        if (fetch(player, s -> ToolFinder.isMatchingToolForBlock(s, state),
                Comparator.comparingDouble(s -> ToolFinder.scoreToolForBlock(s, state)))) {
            waitingFor = pos;
            waitUntil = player.tickCount + WAIT_TICKS;
        }
    }

    /**
     * The inventory has no weapon and the hand holds none: fetch the best one, the
     * preferred type first, as {@link ToolFinder#findBestForMob} ranks them.
     */
    static void forMob(LocalPlayer player) {
        if (!fetch(player, ToolFinder::isPreferredWeapon, Comparator.comparingDouble(ToolFinder::scoreWeapon))) {
            fetch(player, ToolFinder::isWeapon, Comparator.comparingDouble(ToolFinder::scoreWeapon));
        }
    }

    private static boolean fetch(LocalPlayer player, Predicate<ItemStack> fits, Comparator<ItemStack> better) {
        CarriedFetch.Reach reach = reach();
        Minecraft mc = Minecraft.getInstance();
        if (!reach.any() || mc.gameMode == null) return false;
        int target = CarriedFetch.emptySlot(player, BehaviorKeys.INVENTORY_INSERT);
        if (target < 0) return false;
        return CarriedFetch.fetch(mc.gameMode, player, reach, Purpose.TOOL, fits, better, ItemStack.EMPTY, target);
    }

    /**
     * Each tick, while a tool fetched for a block is on its way: once it has landed, and
     * the player is still digging that block, switch to it. Gives up after a second.
     */
    static void tick(Minecraft mc) {
        if (waitingFor == null || mc.player == null) return;
        if (mc.player.tickCount > waitUntil) {
            waitingFor = null;
            return;
        }
        HitResult hit = mc.hitResult;
        boolean stillDigging = mc.options.keyAttack.isDown() && hit instanceof BlockHitResult block
                && hit.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(waitingFor);
        if (!stillDigging) {
            waitingFor = null;
            return;
        }
        BlockState state = mc.player.level().getBlockState(waitingFor);
        if (ToolFinder.findBestForBlock(mc.player.getInventory(), state) == null) return;   // not landed yet
        BlockPos pos = waitingFor;
        waitingFor = null;
        AutoToolSwitch.preAttackBlock(pos);
    }
}
