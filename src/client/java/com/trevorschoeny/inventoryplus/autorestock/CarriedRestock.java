package com.trevorschoeny.inventoryplus.autorestock;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.window.BehaviorKeys;

import com.trevorschoeny.inventoryplus.carried.CarriedFetch;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedTake.Purpose;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.Comparator;

/**
 * Restock's last resort when the inventory has nothing that fits: the item from inside a
 * shulker box the player carries, when "Also restock from shulker boxes" is on (one
 * setting for every kind: items, tools, armor, ammo). How the item is
 * fetched, and what the server checks, is {@link CarriedFetch}'s.
 */
final class CarriedRestock {

    private CarriedRestock() {}

    /** Fresher first: less damage, so more durability left. */
    private static final Comparator<ItemStack> FRESHER = Comparator.comparingInt(s -> -s.getDamageValue());

    /**
     * Where every restock kind may pull from: carried shulker boxes, under the one
     * "Also restock from shulker boxes" setting. Never bundles or the ender chest.
     */
    private static CarriedFetch.Reach shulkers() {
        return new CarriedFetch.Reach(IPConfig.restockFromShulkers(), false, false);
    }

    /**
     * An item ran out: fills {@code target} (a hotbar slot, or the offhand) with more of
     * {@code wanted}. Returns true when it sent a request or clicks.
     */
    static boolean refill(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack wanted, int target) {
        CarriedFetch.Reach reach = shulkers();
        if (!reach.any()) return false;
        return CarriedFetch.fetch(gameMode, player, reach, Purpose.RESTOCK, s -> s.is(wanted.getItem()), null,
                wanted, target);
    }

    /**
     * A tool (or shield) in a hand broke: fills its now-empty {@code target} (a hotbar
     * slot, or the offhand) with the freshest of the same item.
     */
    static boolean toolBroke(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack broken, int target) {
        CarriedFetch.Reach reach = shulkers();
        if (!reach.any()) return false;
        return CarriedFetch.fetch(gameMode, player, reach, Purpose.RESTOCK, s -> s.is(broken.getItem()), FRESHER,
                broken, target);
    }

    /**
     * An armour piece broke: fills its now-empty armour slot ({@code target}, 36-39) with
     * the freshest of the same item.
     */
    static boolean armorBroke(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack broken, int target) {
        CarriedFetch.Reach reach = shulkers();
        if (!reach.any()) return false;
        return CarriedFetch.fetch(gameMode, player, reach, Purpose.RESTOCK, s -> s.is(broken.getItem()), FRESHER,
                broken, target);
    }

    /**
     * A tool or armour piece in {@code target} is about to break: swaps in the freshest of
     * the same item with less damage, and the worn one goes to an empty inventory slot
     * ({@link Purpose#RESTOCK_SWAP}). Armour uses the Armor boxes, everything else Tools'.
     * Nothing when no slot is free, or the worn piece has Curse of Binding.
     */
    static boolean beforeBreak(MultiPlayerGameMode gameMode, LocalPlayer player, ItemStack worn, int target) {
        boolean armour = target >= 36 && target <= 39;
        CarriedFetch.Reach reach = shulkers();
        if (!reach.any()) return false;
        if (armour && EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) return false;
        int spare = CarriedFetch.emptySlot(player, BehaviorKeys.INVENTORY_INSERT, target);
        if (spare < 0) return false;
        return CarriedFetch.fetch(gameMode, player, reach, Purpose.RESTOCK_SWAP,
                s -> s.is(worn.getItem()) && s.getDamageValue() < worn.getDamageValue(), FRESHER, worn, target, spare);
    }

    /** Ticks until ammo may be asked for again, so an empty search isn't repeated every tick. */
    private static int ammoCooldown;

    private static final int AMMO_RETRY_TICKS = 40;

    /**
     * Each tick: a bow, crossbow or other ranged weapon in either hand with nothing to
     * fire anywhere in the inventory gets a stack of what it fires from a carried
     * container, in an empty inventory slot, where the weapon finds it. Asked before the
     * player draws, so the first shot already has ammo.
     */
    static void ammoTick(MultiPlayerGameMode gameMode, LocalPlayer player) {
        if (ammoCooldown > 0) {
            ammoCooldown--;
            return;
        }
        CarriedFetch.Reach reach = shulkers();
        if (!reach.any() || !ModeScope.SURVIVAL.includes(player)) return;
        ItemStack weapon = player.getMainHandItem().getItem() instanceof ProjectileWeaponItem
                ? player.getMainHandItem() : player.getOffhandItem();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem ranged)) return;
        if (!player.getProjectile(weapon).isEmpty()) return;
        int target = CarriedFetch.emptySlot(player, BehaviorKeys.INVENTORY_INSERT);
        if (target < 0) return;
        ammoCooldown = AMMO_RETRY_TICKS;
        CarriedFetch.fetch(gameMode, player, reach, Purpose.AMMO, ranged.getAllSupportedProjectiles(), null,
                weapon, target);
    }
}
