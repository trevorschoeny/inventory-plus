package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/**
 * Auto Tool Switch's own rules, played with the real attack button.
 * <ul>
 *   <li>Turning to another block while still holding the button switches to that
 *       block's tool: a pickaxe for deepslate, then a shovel for dirt.</li>
 *   <li>A sword in hand stays in hand when a block is hit; with that setting off, the
 *       pickaxe comes in.</li>
 *   <li>A hit that switches to a weapon does the weapon's damage: a switched-in iron
 *       sword with Sharpness V takes as much off a creeper as the same sword held
 *       from the start.</li>
 * </ul>
 */
public class AutoToolSwitchChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;
    private TestSingleplayerContext world;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext w = ctx.worldBuilder().create()) {
            world = w;
            ctx.waitTicks(20);
            onServer(world, p -> p.setGameMode(GameType.SURVIVAL));
            ctx.runOnClient(mc -> IPConfig.setAutoToolSwitchEnabled(true));
            retarget();
            keepWeapon();
            damage();
        }
    }

    /** Deepslate below, dirt in front; dig the deepslate, then turn to the dirt without letting go. */
    private void retarget() {
        setUp(p -> {
            p.level().setBlockAndUpdate(p.blockPosition().below(), Blocks.DEEPSLATE.defaultBlockState());
            p.level().setBlockAndUpdate(p.blockPosition().south(), Blocks.DIRT.defaultBlockState());
            p.getInventory().setItem(0, new ItemStack(Items.STICK));
            p.getInventory().setItem(20, new ItemStack(Items.IRON_PICKAXE));
            p.getInventory().setItem(21, new ItemStack(Items.IRON_SHOVEL));
        });
        ctx.getInput().lookAt(0f, 90f);
        ctx.waitTicks(3);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
        onServer(world, p -> expect(p.getInventory().getSelectedItem().is(Items.IRON_PICKAXE),
                "digging deepslate switches to the pickaxe (" + p.getInventory().getSelectedItem() + ")"));
        // Still holding: turn to the dirt in front, at foot level.
        ctx.getInput().lookAt(0f, 48f);
        ctx.waitTicks(3);
        onServer(world, p -> {
            expect(p.getInventory().getSelectedItem().is(Items.IRON_SHOVEL),
                    "turning to dirt while still digging switches to the shovel ("
                            + p.getInventory().getSelectedItem() + ")");
            expect(p.getInventory().getItem(20).is(Items.IRON_PICKAXE),
                    "the pickaxe went back where it came from (" + p.getInventory().getItem(20) + ")");
        });
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(40);
        onServer(world, p -> expect(p.getInventory().getSelectedItem().is(Items.STICK),
                "a second after stopping, the stick is back in hand (" + p.getInventory().getSelectedItem() + ")"));
    }

    /** A sword in hand hitting stone: kept, then with the setting off, the pickaxe comes in. */
    private void keepWeapon() {
        for (boolean keep : new boolean[] {true, false}) {
            ctx.runOnClient(mc -> IPConfig.setAutoToolSwitchKeepWeapon(keep));
            setUp(p -> {
                p.level().setBlockAndUpdate(p.blockPosition().below(), Blocks.DEEPSLATE.defaultBlockState());
                p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
                p.getInventory().setItem(20, new ItemStack(Items.IRON_PICKAXE));
            });
            ctx.getInput().lookAt(0f, 90f);
            ctx.waitTicks(3);
            ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(4);
            onServer(world, p -> expect(p.getInventory().getSelectedItem().is(keep ? Items.IRON_SWORD : Items.IRON_PICKAXE),
                    (keep ? "a sword in hand stays when a block is hit" : "with Keep a weapon off, the pickaxe comes in")
                            + " (" + p.getInventory().getSelectedItem() + ")"));
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(40);
        }
        ctx.runOnClient(mc -> IPConfig.setAutoToolSwitchKeepWeapon(true));
    }

    /** The same sword, switched in on the hit and held from the start: the creeper loses the same health. */
    private void damage() {
        float switched = hit(p -> {
            p.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
            p.getInventory().setItem(20, sword(p));
        });
        float direct = hit(p -> p.getInventory().setItem(0, sword(p)));
        expect(direct > 6f, "the direct hit carries Sharpness (" + direct + ")");
        expect(Math.abs(switched - direct) < 0.01f,
                "a switched hit does the sword's damage: " + switched + " against " + direct + " held from the start");
    }

    /** A fresh creeper two blocks south; one full-strength hit; the health it lost. */
    private float hit(Consumer<ServerPlayer> setup) {
        int[] id = {0};
        setUp(p -> {
            setup.accept(p);
            Creeper creeper = EntityTypes.CREEPER.create(p.level(), EntitySpawnReason.COMMAND);
            BlockPos at = p.blockPosition().south(2);
            creeper.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
            creeper.setNoAi(true);
            p.level().addFreshEntity(creeper);
            id[0] = creeper.getId();
        });
        ctx.getInput().lookAt(0f, 20f);
        // A full attack cooldown, counted from the setup's slot change.
        ctx.waitTicks(40);
        ctx.runOnClient(mc -> mc.gameMode.attack(mc.player, mc.level.getEntity(id[0])));
        ctx.waitTicks(5);
        float[] lost = {0};
        onServer(world, p -> {
            expect(p.getInventory().getSelectedItem().is(Items.IRON_SWORD),
                    "the sword is in hand for the hit (" + p.getInventory().getSelectedItem() + ")");
            Creeper creeper = (Creeper) p.level().getEntity(id[0]);
            lost[0] = creeper.getMaxHealth() - creeper.getHealth();
            creeper.discard();
        });
        ctx.waitTicks(40);
        return lost[0];
    }

    private static ItemStack sword(ServerPlayer p) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 5);
        return sword;
    }

    /** Clears the player, stands them on stone with air around, stick-free, slot 0 selected; then {@code setup}. */
    private void setUp(Consumer<ServerPlayer> setup) {
        onServer(world, p -> {
            clear(p);
            BlockPos at = p.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, 0, -2), at.offset(2, 2, 3))) {
                p.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            p.level().setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
            setup.accept(p);
        });
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        ctx.waitTicks(10);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Auto Tool Switch: " + what);
    }
}
