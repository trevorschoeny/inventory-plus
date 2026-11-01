package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * {@link com.trevorschoeny.inventoryplus.operations.CursorShelf} never parks
 * a held stack on armor or offhand: a PICKUP there equips the item instead
 * of merely storing it, which could curse the player with whatever they
 * happened to be holding when they pressed Sort or a Move Matching key. It
 * also never loses or drops the held stack when the main inventory and
 * hotbar have no empty slot to park it on.
 */
public class CursorShelfChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);

            cursedHelmetCheck(world);
            fullInventoryCheck(world);

            ctx.setScreen(() -> null);
        }
    }

    /**
     * A Curse of Binding helmet on the cursor, the head slot empty and the
     * main inventory open to park on: Sort must park it in the main
     * inventory, never the empty head slot, or the player is stuck wearing
     * it.
     */
    private void cursedHelmetCheck(TestSingleplayerContext world) {
        onServer(world, p -> {
            clear(p);
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            p.getInventory().setItem(30, new ItemStack(Items.DIRT, 5));
            p.getInventory().setItem(12, new ItemStack(Items.DIRT, 7));
            Holder<Enchantment> binding = p.registryAccess().getOrThrow(Enchantments.BINDING_CURSE);
            ItemStack helmet = new ItemStack(Items.IRON_HELMET);
            helmet.enchant(binding, 1);
            p.containerMenu.setCarried(helmet);
            p.containerMenu.broadcastChanges();
        });
        ctx.waitTicks(10);
        ctx.getInput().setCursorPos(1, 1);   // off every slot: the key sorts the inventory
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        onServer(world, p -> {
            expect(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "sorting with a cursed helmet on the cursor "
                    + "never equips it (" + p.getItemBySlot(EquipmentSlot.HEAD) + ")");
            expect(p.containerMenu.getCarried().is(Items.IRON_HELMET), "and hands the helmet back to the cursor ("
                    + p.containerMenu.getCarried() + ")");
            p.containerMenu.setCarried(ItemStack.EMPTY);
        });
    }

    /**
     * The hotbar and main inventory both full, stone and dirt alternating
     * slot to slot (so a real sort would regroup them), a diamond on the
     * cursor: there is nowhere to park it, so Sort must not run at all. Every
     * slot stays exactly as placed and the diamond stays on the cursor.
     */
    private void fullInventoryCheck(TestSingleplayerContext world) {
        onServer(world, p -> {
            clear(p);
            for (int i = 0; i < 36; i++) {
                p.getInventory().setItem(i, new ItemStack(i % 2 == 0 ? Items.STONE : Items.DIRT, 64));
            }
            p.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
            p.containerMenu.broadcastChanges();
        });
        ctx.waitTicks(10);
        ctx.getInput().setCursorPos(1, 1);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        onServer(world, p -> {
            boolean unchanged = true;
            for (int i = 0; i < 36; i++) {
                ItemStack s = p.getInventory().getItem(i);
                Item want = i % 2 == 0 ? Items.STONE : Items.DIRT;
                if (!s.is(want) || s.getCount() != 64) unchanged = false;
            }
            expect(unchanged, "sorting a full inventory with no shelf spot leaves every slot as it was");
            expect(p.containerMenu.getCarried().is(Items.DIAMOND), "and leaves the diamond on the cursor ("
                    + p.containerMenu.getCarried() + ")");
            p.containerMenu.setCarried(ItemStack.EMPTY);
        });
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("CursorShelf: " + what);
    }
}
