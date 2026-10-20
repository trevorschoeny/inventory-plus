package com.trevorschoeny.inventoryplus.checks;

import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
import com.trevorschoeny.inventoryplus.nested.CarriedTake;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.function.Consumer;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundle;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundleItems;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.menuSlot;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

/**
 * Pick block from carried containers (Emerald, step 3). The player stands on granite and
 * looks straight down at it; each case sets the inventory on the server, presses the pick
 * key, and reads where the granite ended up on the server.
 */
public class CarriedPickChecks implements FabricClientGameTest {

    private static final String NO_SERVER_MOD = "inventoryplus.checks.noServerMod";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            onServer(world, p -> {
                p.setGameMode(GameType.SURVIVAL);
                p.level().setBlockAndUpdate(p.blockPosition().below(), Blocks.GRANITE.defaultBlockState());
            });
            ctx.waitTicks(5);
            ctx.getInput().lookAt(0f, 90f);
            ctx.runOnClient(mc -> {
                IPConfig.setPickFromShulkers(true);
                IPConfig.setPickFromBundles(true);
                IPConfig.setPickFromEnderChest(false);
            });

            // The inventory has no granite; a shulker in slot 20 has 10 in its slot 4. The pick
            // takes them into the first empty slot (hotbar 0) and vanilla's pick selects it.
            pick(ctx, world, p -> {
                p.getInventory().setItem(0, ItemStack.EMPTY);
                p.getInventory().setSelectedSlot(4);
                p.getInventory().setItem(4, new ItemStack(Items.STICK));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.GRANITE, 10)));
            });
            onServer(world, p -> {
                ItemStack held = p.getInventory().getSelectedItem();
                expect(held.is(Items.GRANITE) && held.getCount() == 10,
                        "the shulker's 10 granite end up in the hand (" + held + ")");
                expect(contents(p.getInventory().getItem(20)).get(4).isEmpty(), "and the shulker's slot 4 is empty");
            });

            // Granite already in the inventory: the shulker is left alone, vanilla picks the
            // inventory's.
            pick(ctx, world, p -> {
                p.getInventory().setItem(30, new ItemStack(Items.GRANITE, 3));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.GRANITE, 10)));
            });
            onServer(world, p -> expect(contents(p.getInventory().getItem(20)).get(4).getCount() == 10
                            && p.getInventory().getSelectedItem().getCount() == 3,
                    "with granite in the inventory, the shulker keeps its 10 (" + p.getInventory().getSelectedItem() + ")"));

            // A slot lock stops the pick reaching into the shulker in that slot: the same
            // setup as the first case.
            ctx.runOnClient(mc -> LockedSlots.setLockedSlot(menuSlot(mc.player, 20), Reach.defaultOf(LockKind.SLOT)));
            pick(ctx, world, p -> p.getInventory().setItem(20, shulker(4, new ItemStack(Items.GRANITE, 10))));
            onServer(world, p -> expect(contents(p.getInventory().getItem(20)).get(4).getCount() == 10
                            && count(p, Items.GRANITE) == 0,
                    "a shulker in a locked slot is left alone"));
            ctx.runOnClient(mc -> LockedSlots.setLockedSlot(menuSlot(mc.player, 20), null));

            // The boxes off: nothing.
            ctx.runOnClient(mc -> IPConfig.setPickFromShulkers(false));
            pick(ctx, world, p -> p.getInventory().setItem(20, shulker(4, new ItemStack(Items.GRANITE, 10))));
            onServer(world, p -> expect(count(p, Items.GRANITE) == 0,
                    "with Pick from Shulker Boxes off, the pick does nothing"));
            ctx.runOnClient(mc -> IPConfig.setPickFromShulkers(true));

            // The server refuses a pick into a slot that isn't empty.
            ItemPath shulkerSlot4 = new ItemPath(ItemRef.Source.INVENTORY, 20, List.of(4));
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(7, new ItemStack(Items.DIRT));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.GRANITE, 10)));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new CarriedTake.Take(shulkerSlot4,
                    new ItemStack(Items.GRANITE, 10), 7, CarriedTake.Purpose.PICK)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getInventory().getItem(7).is(Items.DIRT)
                            && contents(p.getInventory().getItem(20)).get(4).getCount() == 10,
                    "a pick into a slot that isn't empty does nothing"));

            // The ender chest: the server chooses.
            ctx.runOnClient(mc -> IPConfig.setPickFromEnderChest(true));
            pick(ctx, world, p -> p.getEnderChestInventory().setItem(3, new ItemStack(Items.GRANITE, 12)));
            onServer(world, p -> expect(p.getInventory().getSelectedItem().getCount() == 12
                            && p.getEnderChestInventory().getItem(3).isEmpty(),
                    "the ender chest's granite ends up in the hand (" + p.getInventory().getSelectedItem() + ")"));
            ctx.runOnClient(mc -> IPConfig.setPickFromEnderChest(false));

            // Without the server mod: a bundle in slot 15, taken by clicks, then picked.
            ctx.runOnClient(mc -> System.setProperty(NO_SERVER_MOD, "true"));
            pick(ctx, world, p -> p.getInventory().setItem(15,
                    bundle(new ItemStack(Items.DIRT, 4), new ItemStack(Items.GRANITE, 6))));
            onServer(world, p -> {
                expect(p.getInventory().getSelectedItem().is(Items.GRANITE)
                                && p.getInventory().getSelectedItem().getCount() == 6,
                        "plain clicks take the bundle's granite, then the pick selects it ("
                                + p.getInventory().getSelectedItem() + ")");
                expect(bundleItems(p.getInventory().getItem(15)).size() == 1, "the bundle keeps its dirt");
                expect(p.containerMenu.getCarried().isEmpty(), "nothing is left on the cursor");
            });
            // A shulker can't be opened with clicks, so it's passed over.
            pick(ctx, world, p -> p.getInventory().setItem(20, shulker(4, new ItemStack(Items.GRANITE, 10))));
            onServer(world, p -> expect(count(p, Items.GRANITE) == 0,
                    "without the server mod a shulker's granite is left where it is"));
            ctx.runOnClient(mc -> System.clearProperty(NO_SERVER_MOD));
        }
    }

    /** Clears the player, runs {@code setup}, lets the client settle, presses pick, lets it land. */
    private static void pick(ClientGameTestContext ctx, TestSingleplayerContext world, Consumer<ServerPlayer> setup) {
        onServer(world, p -> {
            clear(p);
            setup.accept(p);
        });
        ctx.waitTicks(10);
        ctx.getInput().pressKey(o -> o.keyPickItem);
        ctx.waitTicks(10);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Carried pick: " + what);
    }
}
