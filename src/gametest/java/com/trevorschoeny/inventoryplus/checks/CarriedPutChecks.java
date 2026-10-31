package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundle;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundleItems;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedPut;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Container first ({@link CarriedPut}): an item given to the inventory, shift-clicked
 * out of a chest or taken by Move Matching in goes into a carried shulker box, bundle
 * or the ender chest that already holds it, and the inventory takes only the rest. Off,
 * or with the container's slot refused, vanilla. The client's choices reach the server.
 */
public class CarriedPutChecks implements FabricClientGameTest {

    /** Every place for every move, every slot open. */
    private static final CarriedPut.Prefs ALL = new CarriedPut.Prefs(0b111_111_111,
            List.of(-1L, -1L, -1L), 0);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");

            // The client's choice reaches the server: pickups into shulkers only.
            ctx.runOnClient(mc -> IPConfig.setPickupIntoShulkers(true));
            stock(world);
            ctx.waitTicks(25);
            onServer(world, p -> {
                ItemStack give = new ItemStack(Items.COBBLESTONE, 30);
                p.getInventory().add(give);
                expect(give.isEmpty() && contents(p.getInventory().getItem(20)).get(4).getCount() == 40
                                && count(p, Items.COBBLESTONE) == 0,
                        "with the client's Shulker Boxes box on, a pickup goes into the shulker holding it");
                ItemStack dirt = new ItemStack(Items.DIRT, 4);
                p.getInventory().add(dirt);
                expect(count(p, Items.DIRT) == 4 && p.getEnderChestInventory().getItem(0).getCount() == 5,
                        "the ender chest box off: dirt stays in the inventory");
            });
            ctx.runOnClient(mc -> IPConfig.setPickupIntoShulkers(false));
            ctx.waitTicks(25);
            stock(world);
            onServer(world, p -> {
                p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
                expect(count(p, Items.COBBLESTONE) == 5 && contents(p.getInventory().getItem(20)).get(4).getCount() == 10,
                        "box off: a pickup lands in the inventory as vanilla");
            });

            // Every place, set directly.
            stock(world);
            onServer(world, p -> {
                CarriedPut.setPrefs(p.getUUID(), ALL);
                p.getInventory().add(new ItemStack(Items.DANDELION, 3));
                expect(bundleItems(p.getInventory().getItem(21)).stream().mapToInt(ItemStack::getCount).sum() == 4
                                && count(p, Items.DANDELION) == 0,
                        "a bundle holding dandelions takes picked-up dandelions");
                p.getInventory().add(new ItemStack(Items.DIRT, 10));
                expect(p.getEnderChestInventory().getItem(0).getCount() == 15 && count(p, Items.DIRT) == 0,
                        "the ender chest holding dirt takes picked-up dirt");
                ItemStack lots = new ItemStack(Items.COBBLESTONE, 64);
                p.getInventory().getItem(20).set(net.minecraft.core.component.DataComponents.CONTAINER,
                        net.minecraft.world.item.component.ItemContainerContents.fromItems(full(Items.COBBLESTONE, 62)));
                p.getInventory().add(lots);
                expect(count(p, Items.COBBLESTONE) == 64 - 27 * 2,
                        "what doesn't fit goes on into the inventory (" + count(p, Items.COBBLESTONE) + ")");
            });

            // A refused slot keeps its container closed.
            stock(world);
            onServer(world, p -> {
                long notTwenty = ~(1L << 20);
                CarriedPut.setPrefs(p.getUUID(), new CarriedPut.Prefs(0b111_111_111, List.of(notTwenty, -1L, -1L), 0));
                p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
                expect(count(p, Items.COBBLESTONE) == 5, "a slot the pickup may not use keeps the shulker in it closed");
                CarriedPut.setPrefs(p.getUUID(), ALL);
            });

            // Shift-click in and Move Matching in, from a chest.
            stock(world);
            onServer(world, p -> {
                BlockPos at = p.blockPosition().offset(2, 0, 0);
                p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                Container chest = (Container) p.level().getBlockEntity(at);
                chest.setItem(0, new ItemStack(Items.COBBLESTONE, 16));
                chest.setItem(1, new ItemStack(Items.COBBLESTONE, 8));
                chest.setItem(2, new ItemStack(Items.STONE, 8));
                p.openMenu((MenuProvider) chest);
            });
            ctx.waitTicks(8);
            onServer(world, p -> {
                var menu = p.containerMenu;
                menu.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
                expect(!menu.getSlot(0).hasItem() && contents(p.getInventory().getItem(20)).get(4).getCount() == 26
                                && count(p, Items.COBBLESTONE) == 0,
                        "a shift-click on a chest's cobblestone fills the shulker holding it");
                menu.clicked(2, 0, ContainerInput.QUICK_MOVE, p);
                expect(count(p, Items.STONE) == 8, "a kind no container holds shift-clicks into the inventory as vanilla");
                int moved = CarriedPut.fromSlot(p, menu, menu.getSlot(1), 5, CarriedPut.Move.MOVE_MATCHING,
                        InventoryPlusOperations.MOVE_MATCHING_OUT);
                expect(moved == 5 && menu.getSlot(1).getItem().getCount() == 3,
                        "Move Matching in's first pass takes what it was asked for into the shulker");
                p.closeContainer();
            });

            // Move Matching in end to end, from the client: cobblestone, which the shulker
            // holds, goes into it on the server; stone, which only the inventory holds,
            // follows into the inventory once the server has answered.
            stock(world);
            ctx.runOnClient(mc -> IPConfig.setMoveMatchingIntoShulkers(true));
            BlockPos[] chestAt = {null};
            onServer(world, p -> {
                p.getInventory().setItem(9, new ItemStack(Items.STONE, 1));
                BlockPos at = p.blockPosition().offset(-2, 0, 0);
                p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                Container chest = (Container) p.level().getBlockEntity(at);
                chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
                chest.setItem(2, new ItemStack(Items.STONE, 16));
                chestAt[0] = at;
                p.openMenu((MenuProvider) chest);
            });
            ctx.waitTicks(25);
            ctx.runOnClient(mc -> {
                var groups = com.trevorschoeny.inventoryplus.movematching.SlotGroupDetector.detect(mc.gui.screen());
                var inv = com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons.findPlayerMainInv(groups);
                expect(inv != null, "the chest screen has the player's main inventory");
                com.trevorschoeny.inventoryplus.movematching.MoveMatchingExecutor.execute(mc, inv,
                        com.trevorschoeny.inventoryplus.movematching.Direction.IN,
                        com.trevorschoeny.inventoryplus.movematching.MoveMatchingMode.ALL);
            });
            ctx.waitTicks(20);
            onServer(world, p -> {
                Container chest = (Container) p.level().getBlockEntity(chestAt[0]);
                int inShulker = contents(p.getInventory().getItem(20)).stream()
                        .filter(x -> x.is(Items.COBBLESTONE)).mapToInt(ItemStack::getCount).sum();
                expect(chest.isEmpty() && inShulker == 138 && count(p, Items.COBBLESTONE) == 0
                                && count(p, Items.STONE) == 17,
                        "Move Matching in fills the shulker first and the inventory with the rest (shulker "
                                + inShulker + ", loose cobblestone " + count(p, Items.COBBLESTONE) + ", stone "
                                + count(p, Items.STONE) + ", chest empty " + chest.isEmpty() + ")");
                p.closeContainer();
            });
            ctx.runOnClient(mc -> IPConfig.setMoveMatchingIntoShulkers(false));
        }
    }

    /** A shulker in slot 20 with 10 cobblestone, a bundle in 21 with a dandelion, 5 dirt in the ender chest. */
    private static void stock(TestSingleplayerContext world) {
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(20, shulker(4, new ItemStack(Items.COBBLESTONE, 10)));
            p.getInventory().setItem(21, bundle(new ItemStack(Items.DANDELION)));
            p.getEnderChestInventory().setItem(0, new ItemStack(Items.DIRT, 5));
        });
    }

    /** 27 stacks of {@code n} {@code item}: a shulker with 2 room per slot when n is 62. */
    private static List<ItemStack> full(net.minecraft.world.item.Item item, int n) {
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < 27; i++) out.add(new ItemStack(item, n));
        return out;
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Carried put: " + what);
    }
}
