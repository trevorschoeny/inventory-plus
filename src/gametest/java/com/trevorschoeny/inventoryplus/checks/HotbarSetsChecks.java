package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.menuSlot;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.hotbarsets.HotbarSets;
import com.trevorschoeny.inventoryplus.hotbarsets.HotbarSetsInput;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Hotbar Sets: hold the key, scroll, let go. A row swaps with the hotbar column by column; a
 * locked column stays; a column where a reserved slot would get an item it doesn't take
 * doesn't move, while one whose reservation takes the item does. Right-click and Escape
 * cancel. In creative a saved set loads under the same rules, and survives a reload.
 *
 * <p>Every column is stocked with an item of its own count, so each assert names exactly
 * the item it expects and can only pass if that column did what the rule says.
 */
public class HotbarSetsChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;

    // Column 2: the hotbar slot is locked. Column 5: the row slot is reserved for diamonds, so
    // the hotbar's dirt can't go there. Column 7: the hotbar slot is reserved for cobblestone,
    // which is what the row brings it, so it swaps.
    private static final int LOCKED = 2, ROW_REFUSES = 5, HOTBAR_ACCEPTS = 7;
    private static final int BOTTOM_ROW = 2;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            // Locks and reservations first: a reservation set on a slot holding something else
            // moves that item out, so the stock goes in after.
            ctx.runOnClient(mc -> {
                LockedSlots.setLocked(menuSlot(mc.player, LOCKED), true);
                Reservations.reserve(mc.player, menuSlot(mc.player, HotbarSets.firstSlotOf(BOTTOM_ROW) + ROW_REFUSES),
                        Reservation.of(new ItemStack(Items.DIAMOND)));
                Reservations.reserve(mc.player, menuSlot(mc.player, HOTBAR_ACCEPTS),
                        Reservation.of(new ItemStack(Items.COBBLESTONE)));
            });
            ctx.waitTicks(5);
            stock(world);

            // The HUD: hold, one notch up (the bottom row), let go.
            ctx.getInput().holdKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(3);
            expect(ctx.computeOnClient(mc -> HotbarSetsInput.isOpen()), "holding the key opens the list");
            expect(ctx.computeOnClient(mc -> HotbarSetsInput.selected() == 0), "the list opens on the hotbar itself");
            ctx.getInput().scroll(1);
            ctx.waitTicks(2);
            expect(ctx.computeOnClient(mc -> HotbarSetsInput.selected() == 1), "a notch up selects the bottom row");
            ctx.getInput().releaseKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(10);
            onServer(world, p -> {
                for (int c = 0; c < 9; c++) {
                    boolean stays = c == LOCKED || c == ROW_REFUSES;
                    expectAt(p, c, stays ? Items.DIRT : Items.COBBLESTONE, c + 1,
                            "after the swap, hotbar column " + c);
                    expectAt(p, HotbarSets.firstSlotOf(BOTTOM_ROW) + c, stays ? Items.COBBLESTONE : Items.DIRT, c + 1,
                            "after the swap, bottom row column " + c);
                }
            });

            // Right-click while holding cancels: nothing moves, even after letting go.
            ctx.getInput().holdKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(3);
            ctx.getInput().scroll(1);
            ctx.waitTicks(2);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            ctx.waitTicks(3);
            expect(ctx.computeOnClient(mc -> !HotbarSetsInput.isOpen()), "right-click closes the list");
            ctx.getInput().releaseKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(10);
            onServer(world, p -> expectAt(p, 0, Items.COBBLESTONE, 1, "a cancelled list moves nothing"));

            // The survival inventory: the same, over the real rows. Three notches is the top row.
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);
            ctx.getInput().holdKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(3);
            for (int i = 0; i < 3; i++) ctx.getInput().scroll(1);
            ctx.waitTicks(2);
            expect(ctx.computeOnClient(mc -> HotbarSetsInput.selected() == 3), "three notches up selects the top row");
            ctx.getInput().releaseKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(10);
            onServer(world, p -> {
                expectAt(p, 0, Items.STONE, 1, "the inventory screen: the top row's item is in the hotbar");
                expectAt(p, HotbarSets.firstSlotOf(0), Items.COBBLESTONE, 1, "the inventory screen: the hotbar's is in the top row");
            });
            // Escape while holding cancels and keeps the screen open.
            ctx.getInput().holdKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(3);
            ctx.getInput().scroll(1);
            ctx.waitTicks(2);
            ctx.getInput().pressKey(InputConstants.getKey("key.keyboard.escape"));
            ctx.waitTicks(3);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof InventoryScreen && !HotbarSetsInput.isOpen()),
                    "Escape closes the list and the screen stays open");
            ctx.getInput().releaseKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(10);
            onServer(world, p -> expectAt(p, 0, Items.STONE, 1, "an Escaped list moves nothing"));
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);

            // Creative: save the hotbar, change it, load the set back.
            world.getServer().runCommand("gamemode creative @a");
            ctx.waitTicks(6);
            onServer(world, p -> {
                for (int c = 0; c < 9; c++) p.getInventory().setItem(c, new ItemStack(Items.OAK_PLANKS, c + 1));
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> HotbarSets.saveHotbar());
            onServer(world, p -> {
                for (int c = 0; c < 9; c++) p.getInventory().setItem(c, new ItemStack(Items.GLASS, c + 1));
            });
            ctx.waitTicks(5);
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof CreativeModeInventoryScreen), "creative's screen is open");
            // Creative's list, above the hotbar: the saved set first, then the rows.
            ctx.getInput().holdKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(3);
            ctx.getInput().scroll(1);
            ctx.waitTicks(2);
            ctx.getInput().releaseKey(IPKeybinds.HOTBAR_SETS);
            ctx.waitTicks(10);
            onServer(world, p -> {
                for (int c = 0; c < 9; c++) {
                    // The locked slot keeps its glass; the slot reserved for cobblestone won't take planks.
                    boolean stays = c == LOCKED || c == HOTBAR_ACCEPTS;
                    expectAt(p, c, stays ? Items.GLASS : Items.OAK_PLANKS, c + 1, "creative: after loading the set, hotbar column " + c);
                }
            });
            ctx.runOnClient(mc -> {
                HotbarSets.reloadForChecks();
                List<List<ItemStack>> sets = HotbarSets.savedSets();
                expect(sets.size() == 1 && sets.getFirst().get(4).is(Items.OAK_PLANKS) && sets.getFirst().get(4).getCount() == 5,
                        "the saved set reads back from the file (" + sets + ")");
                HotbarSets.delete(0);
                expect(HotbarSets.savedSets().isEmpty(), "deleting the set empties the list");
                LockedSlots.setLocked(menuSlot(mc.player, LOCKED), false);
                Reservations.reserve(mc.player, menuSlot(mc.player, HotbarSets.firstSlotOf(BOTTOM_ROW) + ROW_REFUSES), Reservation.NONE);
                Reservations.reserve(mc.player, menuSlot(mc.player, HOTBAR_ACCEPTS), Reservation.NONE);
            });
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        }
    }

    /** Hotbar: dirt, one more in each column. Bottom row: cobblestone the same. Top row: stone, one each. */
    private void stock(TestSingleplayerContext world) {
        onServer(world, p -> {
            clear(p);
            for (int c = 0; c < 9; c++) {
                p.getInventory().setItem(c, new ItemStack(Items.DIRT, c + 1));
                p.getInventory().setItem(HotbarSets.firstSlotOf(BOTTOM_ROW) + c, new ItemStack(Items.COBBLESTONE, c + 1));
                p.getInventory().setItem(HotbarSets.firstSlotOf(0) + c, new ItemStack(Items.STONE));
            }
        });
        ctx.waitTicks(5);
    }

    private static void expectAt(ServerPlayer p, int slot, Item item, int count, String what) {
        ItemStack stack = p.getInventory().getItem(slot);
        if (stack.is(item) && stack.getCount() == count) return;
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < 36; i++) all.append(i).append('=').append(p.getInventory().getItem(i)).append(' ');
        expect(false, what + ": expected " + count + " " + item + ", found " + stack + " [" + all + "]");
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Hotbar Sets: " + what);
    }
}
