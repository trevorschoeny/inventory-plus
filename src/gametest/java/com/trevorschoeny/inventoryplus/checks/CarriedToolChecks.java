package com.trevorschoeny.inventoryplus.checks;

import com.mojang.blaze3d.platform.InputConstants;

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
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

/**
 * Auto Tool Switch reaching into carried containers (Emerald, step 6). The player stands on
 * stone holding a stick and looks down; each case sets the inventory on the server, holds
 * the attack button, and reads what the server says is in the hand while still digging.
 */
public class CarriedToolChecks implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            onServer(world, p -> p.setGameMode(GameType.SURVIVAL));
            ctx.waitTicks(5);
            ctx.getInput().lookAt(0f, 90f);
            ctx.runOnClient(mc -> {
                IPConfig.setAutoToolSwitchEnabled(true);
                IPConfig.setAutoToolSwitchFromShulkers(true);
                IPConfig.setAutoToolSwitchFromBundles(true);
            });

            // No pickaxe in the inventory; a shulker holds a wooden one and an iron one. The
            // iron one, the better for stone, comes out and is switched to while digging.
            dig(ctx, world, p -> p.getInventory().setItem(20, shulker(4, new ItemStack(Items.IRON_PICKAXE),
                    5, new ItemStack(Items.WOODEN_PICKAXE))), p -> {
                expect(p.getInventory().getSelectedItem().is(Items.IRON_PICKAXE),
                        "digging stone with no pickaxe switches to the shulker's iron one ("
                                + p.getInventory().getSelectedItem() + ")");
                expect(contents(p.getInventory().getItem(20)).get(4).isEmpty()
                                && contents(p.getInventory().getItem(20)).get(5).is(Items.WOODEN_PICKAXE),
                        "only the iron pickaxe left the shulker");
            });

            // A pickaxe in the inventory: the shulker is left alone.
            dig(ctx, world, p -> {
                p.getInventory().setItem(25, new ItemStack(Items.STONE_PICKAXE));
                p.getInventory().setItem(20, shulker(4, new ItemStack(Items.IRON_PICKAXE)));
            }, p -> expect(p.getInventory().getSelectedItem().is(Items.STONE_PICKAXE)
                            && contents(p.getInventory().getItem(20)).get(4).is(Items.IRON_PICKAXE),
                    "with a pickaxe in the inventory, that one is used and the shulker kept ("
                            + p.getInventory().getSelectedItem() + ")"));

            // The boxes off: no reach into the shulker.
            ctx.runOnClient(mc -> IPConfig.setAutoToolSwitchFromShulkers(false));
            dig(ctx, world, p -> p.getInventory().setItem(20, shulker(4, new ItemStack(Items.IRON_PICKAXE))),
                    p -> expect(count(p, Items.IRON_PICKAXE) == 0,
                            "with Take tools from Shulker Boxes off, the shulker is kept"));
            ctx.runOnClient(mc -> IPConfig.setAutoToolSwitchFromShulkers(true));

            // The server never chooses a tool from the ender chest.
            onServer(world, p -> {
                clear(p);
                p.getEnderChestInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> ClientPlayNetworking.send(new CarriedTake.FromEnderChest(
                    new ItemStack(Items.IRON_PICKAXE), 3, CarriedTake.Purpose.TOOL, -1, false, false, 0)));
            ctx.waitTicks(5);
            onServer(world, p -> expect(p.getEnderChestInventory().getItem(0).is(Items.IRON_PICKAXE)
                            && p.getInventory().getItem(3).isEmpty(),
                    "a tool request for the ender chest does nothing"));
        }
    }

    /**
     * Clears the player, puts stone back under them, gives them a stick in hotbar 0, runs
     * {@code setup}, then holds the attack button on the stone below and runs {@code check}
     * on the server while still digging; releases after.
     */
    private static void dig(ClientGameTestContext ctx, TestSingleplayerContext world, Consumer<ServerPlayer> setup,
                            Consumer<ServerPlayer> check) {
        onServer(world, p -> {
            clear(p);
            // Fresh stone each time: a good pickaxe breaks the last case's in a few ticks.
            p.level().setBlockAndUpdate(p.blockPosition().below(), Blocks.STONE.defaultBlockState());
            p.getInventory().setItem(0, new ItemStack(Items.STICK));
            setup.accept(p);
        });
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        ctx.waitTicks(10);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(15);
        onServer(world, check);
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(40);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Carried tool: " + what);
    }
}
