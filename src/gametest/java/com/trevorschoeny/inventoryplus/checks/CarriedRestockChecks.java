package com.trevorschoeny.inventoryplus.checks;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;

import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.nested.CarriedTake;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundle;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundleItems;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.menuSlot;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

import static com.trevorschoeny.inventoryplus.nested.CarriedTake.Purpose.RESTOCK;

/**
 * Restock's "ran out" from inside carried containers (Emerald, steps 1 and 2). Each case
 * sets the inventory on the server, empties the hand the way using its last item would,
 * lets Restock's client tick react, and reads the server's inventory: the server is where
 * the item has to have landed.
 */
public class CarriedRestockChecks implements FabricClientGameTest {

    private static final String NO_SERVER_MOD = "inventoryplus.checks.noServerMod";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> {
                IPConfig.setRestockEnabled(true);
                IPConfig.setAutoRestockItem(true);
                IPConfig.setRestockItemsFromShulkers(true);
                IPConfig.setRestockItemsFromBundles(true);
                IPConfig.setRestockItemsFromEnderChest(false);
            });

            // ── With Inventory Plus on the server (path 1) ──

            // A shulker in slot 20 holds 30 cobblestone in its slot 4.
            ranOut(ctx, world, 0, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.COBBLESTONE, 30)));
            });
            onServer(world, p -> {
                expect(p.getInventory().getItem(0).is(Items.COBBLESTONE) && p.getInventory().getItem(0).getCount() == 30,
                        "the hand is refilled with the shulker's 30 cobblestone (" + p.getInventory().getItem(0) + ")");
                expect(contents(p.getInventory().getItem(20)).get(4).isEmpty(), "and the shulker's slot 4 is empty");
            });

            // Two deep: dirt in a bundle in a shulker; the bundle keeps its stone.
            ranOut(ctx, world, 0, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.DIRT, 2));
                p.getInventory().setItem(10, shulker(3, bundle(new ItemStack(Items.STONE, 2), new ItemStack(Items.DIRT, 9))));
            });
            onServer(world, p -> {
                expect(p.getInventory().getItem(0).getCount() == 9 && p.getInventory().getItem(0).is(Items.DIRT),
                        "dirt two containers deep refills the hand (" + p.getInventory().getItem(0) + ")");
                List<ItemStack> left = bundleItems(contents(p.getInventory().getItem(10)).get(3));
                expect(left.size() == 1 && left.get(0).is(Items.STONE), "the bundle keeps its stone");
            });

            // The offhand runs out too.
            ranOut(ctx, world, Inventory.SLOT_OFFHAND, p -> {
                p.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.TORCH, 3));
                p.getInventory().setItem(30, shulker(0, new ItemStack(Items.TORCH, 40)));
            });
            onServer(world, p -> expect(p.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount() == 40,
                    "the offhand is refilled from a shulker (" + p.getInventory().getItem(Inventory.SLOT_OFFHAND) + ")"));

            // A locked slot stops Restock reaching into the shulker in it: the same setup
            // as the first case, which refills, but slot 20 is locked.
            ctx.runOnClient(mc -> LockedSlots.setLockedSlot(menuSlot(mc.player, 20), Reach.defaultOf(LockKind.SLOT)));
            ranOut(ctx, world, 0, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.COBBLESTONE, 30)));
            });
            onServer(world, p -> expect(p.getInventory().getItem(0).isEmpty()
                            && contents(p.getInventory().getItem(20)).get(4).getCount() == 30,
                    "a shulker in a locked slot is left alone (" + p.getInventory().getItem(0) + ")"));
            ctx.runOnClient(mc -> LockedSlots.setLockedSlot(menuSlot(mc.player, 20), null));

            // ── The server's own checks, by sending requests directly ──

            ItemPath shulkerSlot4 = new ItemPath(ItemRef.Source.INVENTORY, 20, List.of(4));
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 60));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.COBBLESTONE, 30)));
            });
            ctx.waitTicks(5);
            ItemStack renamed = new ItemStack(Items.COBBLESTONE, 30);
            renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Not what's there"));
            ctx.runOnClient(mc -> {
                ClientPlayNetworking.send(new CarriedTake.Take(shulkerSlot4, renamed, 0, RESTOCK));          // wrong components
                ClientPlayNetworking.send(new CarriedTake.Take(shulkerSlot4,
                        new ItemStack(Items.COBBLESTONE, 30), 21, RESTOCK));                                  // not a hotbar slot
            });
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getInventory().getItem(0).getCount() == 60
                            && p.getInventory().getItem(21).isEmpty()
                            && contents(p.getInventory().getItem(20)).get(4).getCount() == 30,
                    "a request with the wrong item, or for a slot that isn't the hotbar or offhand, does nothing"));
            ctx.runOnClient(mc -> ClientPlayNetworking.send(
                    new CarriedTake.Take(shulkerSlot4, new ItemStack(Items.COBBLESTONE, 30), 0, RESTOCK)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getInventory().getItem(0).getCount() == 64
                            && contents(p.getInventory().getItem(20)).get(4).getCount() == 26,
                    "a take is capped at what fits: 4 onto 60, 26 left in the shulker"));

            // The ender chest: the server chooses, passing over slots the client says are locked.
            onServer(world, p -> {
                clear(p);
                p.getEnderChestInventory().setItem(2, new ItemStack(Items.COBBLESTONE, 5));
                p.getEnderChestInventory().setItem(5, new ItemStack(Items.COBBLESTONE, 20));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new CarriedTake.FromEnderChest(
                    new ItemStack(Items.COBBLESTONE), 0, RESTOCK, false, false, 1 << 2)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getInventory().getItem(0).getCount() == 20
                            && p.getEnderChestInventory().getItem(2).getCount() == 5,
                    "the ender chest fills the hand from slot 5, passing over locked slot 2 ("
                            + p.getInventory().getItem(0) + ")"));

            // ── A server without Inventory Plus (path 2, plain clicks) ──

            ctx.runOnClient(mc -> System.setProperty(NO_SERVER_MOD, "true"));

            // A bundle in slot 15 with dirt, stone, cobblestone: the middle one is taken by
            // clicks, the others stay in order. This is 26.3's bundle index: the path's
            // index is the item vanilla's selection takes.
            ranOut(ctx, world, 0, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
                p.getInventory().setItem(15, bundle(new ItemStack(Items.DIRT, 4), new ItemStack(Items.STONE, 6),
                        new ItemStack(Items.COBBLESTONE, 2)));
            });
            onServer(world, p -> {
                expect(p.getInventory().getItem(0).is(Items.STONE) && p.getInventory().getItem(0).getCount() == 6,
                        "plain clicks take the bundle's stone, its second item (" + p.getInventory().getItem(0) + ")");
                List<ItemStack> left = bundleItems(p.getInventory().getItem(15));
                expect(left.size() == 2 && left.get(0).is(Items.DIRT) && left.get(1).is(Items.COBBLESTONE),
                        "the bundle keeps dirt then cobblestone");
                expect(p.containerMenu.getCarried().isEmpty(), "nothing is left on the cursor");
            });

            // Selecting is a toggle: with the stone already selected, the clicks still take
            // the stone, not the first item.
            ranOut(ctx, world, 0, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.STONE, 3));
                ItemStack b = bundle(new ItemStack(Items.DIRT, 4), new ItemStack(Items.STONE, 6));
                BundleItem.toggleSelectedItem(b, 1);
                p.getInventory().setItem(15, b);
            });
            onServer(world, p -> expect(p.getInventory().getItem(0).is(Items.STONE)
                            && bundleItems(p.getInventory().getItem(15)).get(0).is(Items.DIRT),
                    "an already-selected item is the one taken (" + p.getInventory().getItem(0) + ")"));

            // A shulker can't be opened with clicks, so without the server mod it's passed over.
            ranOut(ctx, world, 0, p -> {
                p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.COBBLESTONE, 30)));
            });
            onServer(world, p -> expect(p.getInventory().getItem(0).isEmpty(),
                    "without the server mod a shulker's item is left where it is"));

            ctx.runOnClient(mc -> System.clearProperty(NO_SERVER_MOD));
        }
    }

    /**
     * Clears the player, runs {@code setup}, lets the client settle, then empties
     * {@code slot} on the server as using its last item would, and lets Restock react.
     */
    private static void ranOut(ClientGameTestContext ctx, TestSingleplayerContext world, int slot,
                               Consumer<ServerPlayer> setup) {
        onServer(world, p -> {
            clear(p);
            setup.accept(p);
        });
        ctx.waitTicks(10);
        onServer(world, p -> p.getInventory().setItem(slot, ItemStack.EMPTY));
        ctx.waitTicks(15);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Carried restock: " + what);
    }
}
