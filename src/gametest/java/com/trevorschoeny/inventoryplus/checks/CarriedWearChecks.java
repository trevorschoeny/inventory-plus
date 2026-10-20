package com.trevorschoeny.inventoryplus.checks;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedTake;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.function.Consumer;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundle;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundleItems;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

/**
 * Restock from carried containers for tools, armour and ammo (Emerald, step 4): a tool or
 * armour piece that broke, one about to break, and ammo for a bow in hand. Each case sets
 * the inventory on the server, makes the change there (a break, wear, a bow with nothing
 * to fire), lets Restock's client tick react, and reads the server's inventory.
 */
public class CarriedWearChecks implements FabricClientGameTest {

    private static final String NO_SERVER_MOD = "inventoryplus.checks.noServerMod";

    /** Iron pickaxe durability is 250; the before-break threshold is set to 10. */
    private static final int IRON_PICK = 250;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            onServer(world, p -> p.setGameMode(GameType.SURVIVAL));
            ctx.runOnClient(mc -> {
                IPConfig.setRestockEnabled(true);
                IPConfig.setAutoRestockTool(true);
                IPConfig.setAutoRestockToolBeforeBreak(true);
                IPConfig.setAutoRestockArmor(true);
                IPConfig.setAutoRestockArmorBeforeBreak(true);
                IPConfig.setAutoRestockBeforeBreakThreshold(10);
                IPConfig.setRestockToolsFromShulkers(true);
                IPConfig.setRestockToolsFromBundles(true);
                IPConfig.setRestockArmorFromShulkers(true);
                IPConfig.setRestockAmmoFromShulkers(true);
            });

            // A pickaxe in the hand breaks; a shulker in slot 20 holds a worn one (slot 1)
            // and a fresh one (slot 4). The fresh one fills the hand.
            change(ctx, world, p -> {
                p.getInventory().setItem(0, pick(200));
                p.getInventory().setItem(20, shulker(1, pick(100), 4, pick(0)));
            }, p -> p.getInventory().setItem(0, ItemStack.EMPTY));
            onServer(world, p -> {
                ItemStack hand = p.getInventory().getItem(0);
                expect(hand.is(Items.IRON_PICKAXE) && hand.getDamageValue() == 0,
                        "a broken pickaxe is replaced by the shulker's freshest (" + hand + ")");
                expect(contents(p.getInventory().getItem(20)).get(1).getDamageValue() == 100,
                        "the worn one stays in the shulker");
            });

            // A chestplate breaks; the shulker's fills the chest slot (38).
            change(ctx, world, p -> {
                p.getInventory().setItem(38, new ItemStack(Items.IRON_CHESTPLATE));
                p.getInventory().setItem(20, shulker(6, new ItemStack(Items.IRON_CHESTPLATE)));
            }, p -> p.getInventory().setItem(38, ItemStack.EMPTY));
            onServer(world, p -> expect(p.getInventory().getItem(38).is(Items.IRON_CHESTPLATE)
                            && contents(p.getInventory().getItem(20)).get(6).isEmpty(),
                    "a broken chestplate is replaced from the shulker (" + p.getInventory().getItem(38) + ")"));

            // Before break: the held pickaxe drops to 5 left; the shulker's fresh one swaps
            // in and the worn one goes to the first empty slot, hotbar 1.
            change(ctx, world, p -> {
                p.getInventory().setItem(0, pick(IRON_PICK - 30));
                p.getInventory().setItem(20, shulker(4, pick(0)));
            }, p -> p.getInventory().getItem(0).setDamageValue(IRON_PICK - 5));
            onServer(world, p -> {
                expect(p.getInventory().getItem(0).getDamageValue() == 0,
                        "a nearly broken pickaxe is swapped for the shulker's fresh one (" + p.getInventory().getItem(0) + ")");
                expect(p.getInventory().getItem(1).getDamageValue() == IRON_PICK - 5,
                        "and the worn one waits in hotbar 1 (" + p.getInventory().getItem(1) + ")");
                expect(contents(p.getInventory().getItem(20)).get(4).isEmpty(), "the shulker's slot 4 is empty");
            });

            // Ammo: a bow in hand and no arrows in the inventory; the shulker's 16 arrows
            // come out into an empty slot.
            change(ctx, world, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.BOW));
                p.getInventory().setItem(20, shulker(2, new ItemStack(Items.ARROW, 16)));
            }, p -> {});
            onServer(world, p -> expect(count(p, Items.ARROW) == 16
                            && contents(p.getInventory().getItem(20)).get(2).isEmpty(),
                    "a bow with nothing to fire gets the shulker's arrows (" + count(p, Items.ARROW) + ")"));

            // ── The server's own checks ──

            ItemPath shulkerSlot4 = new ItemPath(ItemRef.Source.INVENTORY, 20, List.of(4));
            // A swap whose spare slot isn't empty does nothing; the same request with an empty
            // spare swaps. The worn pickaxe sits above the threshold, so Restock's own tick
            // leaves it alone and only these requests act.
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(0, pick(IRON_PICK - 30));
                p.getInventory().setItem(1, new ItemStack(Items.DIRT));
                p.getInventory().setItem(20, shulker(4, pick(0)));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new CarriedTake.Take(shulkerSlot4, pick(0), 0,
                    CarriedTake.Purpose.RESTOCK_SWAP, 1)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getInventory().getItem(0).getDamageValue() == IRON_PICK - 30
                            && p.getInventory().getItem(1).is(Items.DIRT),
                    "a swap into a spare slot that isn't empty does nothing"));
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new CarriedTake.Take(shulkerSlot4, pick(0), 0,
                    CarriedTake.Purpose.RESTOCK_SWAP, 2)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getInventory().getItem(0).getDamageValue() == 0
                            && p.getInventory().getItem(2).getDamageValue() == IRON_PICK - 30,
                    "the same swap into an empty spare goes through (" + p.getInventory().getItem(0) + ")"));
            // Ammo for no ranged weapon does nothing.
            ItemPath shulkerSlot2 = new ItemPath(ItemRef.Source.INVENTORY, 20, List.of(2));
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(0, new ItemStack(Items.STICK));
                p.getInventory().setItem(20, shulker(2, new ItemStack(Items.ARROW, 16)));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new CarriedTake.Take(shulkerSlot2,
                    new ItemStack(Items.ARROW, 16), 5, CarriedTake.Purpose.AMMO)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(count(p, Items.ARROW) == 0,
                    "ammo with no ranged weapon in hand does nothing"));

            // ── A server without Inventory Plus: the swap by clicks, from a bundle ──

            ctx.runOnClient(mc -> System.setProperty(NO_SERVER_MOD, "true"));
            change(ctx, world, p -> {
                p.getInventory().setItem(0, pick(IRON_PICK - 30));
                p.getInventory().setItem(15, bundle(pick(0)));
            }, p -> p.getInventory().getItem(0).setDamageValue(IRON_PICK - 5));
            onServer(world, p -> {
                expect(p.getInventory().getItem(0).getDamageValue() == 0,
                        "plain clicks swap the bundle's fresh pickaxe in (" + p.getInventory().getItem(0) + ")");
                expect(p.getInventory().getItem(1).getDamageValue() == IRON_PICK - 5,
                        "and leave the worn one in hotbar 1 (" + p.getInventory().getItem(1) + ")");
                expect(bundleItems(p.getInventory().getItem(15)).isEmpty(), "the bundle is empty");
                expect(p.containerMenu.getCarried().isEmpty(), "nothing is left on the cursor");
            });
            ctx.runOnClient(mc -> System.clearProperty(NO_SERVER_MOD));
        }
    }

    /** An iron pickaxe with {@code damage} taken. */
    private static ItemStack pick(int damage) {
        ItemStack s = new ItemStack(Items.IRON_PICKAXE);
        s.setDamageValue(damage);
        return s;
    }

    /**
     * Clears the player, runs {@code setup}, lets Restock's snapshot settle, then makes
     * {@code change} on the server (a break, wear) and lets Restock react.
     */
    private static void change(ClientGameTestContext ctx, TestSingleplayerContext world,
                               Consumer<ServerPlayer> setup, Consumer<ServerPlayer> change) {
        onServer(world, p -> {
            clear(p);
            setup.accept(p);
        });
        ctx.waitTicks(10);
        onServer(world, change);
        ctx.waitTicks(15);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Carried wear: " + what);
    }
}
