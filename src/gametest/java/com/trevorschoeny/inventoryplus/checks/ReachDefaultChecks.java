package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.menuSlot;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.trevorschoeny.inventoryplus.api.InventoryPlusApi;
import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.TriBool;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Reach's defaults, asked of the veto directly: an item lock lets the cyclers,
 * Collect and a top-up through but not Sort or Q, and counts only on items the
 * player carries; a Slot lock lets number keys and F through; cycled slots are
 * ordinary slots; Move Matching leaves the hotbar alone until ticked; a
 * companion's protected slots refuse all but what it allows; and a saved
 * record from before these defaults loads without its choices.
 */
public class ReachDefaultChecks implements FabricClientGameTest {

    /** Exact-locked pickaxe, exact-locked torches, a plain main slot. */
    private static final int PICK = 20, TORCHES = 22, FREE = 21;
    /** Slot-locked hotbar slot, and a free one. */
    private static final int HOTBAR_LOCKED = 3, HOTBAR_FREE = 4;
    /** A Column Cycler cycle slot, above HOTBAR_FREE. */
    private static final int CYCLE = 9 + HOTBAR_FREE;

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(PICK, new ItemStack(Items.DIAMOND_PICKAXE));
                p.getInventory().setItem(TORCHES, new ItemStack(Items.TORCH, 10));
                p.getInventory().setItem(FREE, new ItemStack(Items.DIRT, 5));
                p.getInventory().setItem(HOTBAR_LOCKED, new ItemStack(Items.IRON_SWORD));
                p.getInventory().setItem(HOTBAR_FREE, new ItemStack(Items.STONE, 4));
                p.getInventory().setItem(CYCLE, new ItemStack(Items.COBBLESTONE, 3));
            });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                var exact = Reach.group(Reach.EXACT_ITEM);
                LockedItems.apply(mc.player.getInventory().getItem(PICK), exact);
                LockedItems.apply(mc.player.getInventory().getItem(TORCHES), exact);
                LockedSlots.setLockedSlot(menuSlot(mc.player, HOTBAR_LOCKED), Reach.defaultOf(LockKind.SLOT));
                IPConfig.setColumnCyclerEnabled(true);
                ColumnCycler.setCycle(CYCLE, true);
            });
            ctx.waitTicks(3);

            ctx.runOnClient(mc -> {
                // An item lock keeps the item with the player, and nothing more.
                allowed(mc, PICK, InventoryPlusOperations.COLUMN_CYCLE, true, "an item lock lets Column Cycler through");
                allowed(mc, PICK, InventoryPlusOperations.HOTBAR_CYCLE, true, "an item lock lets Hotbar Cycler through");
                allowed(mc, PICK, BehaviorKeys.COLLECT, true, "an item lock lets Collect through");
                allowed(mc, PICK, InventoryPlusOperations.SORT, false, "an item lock stops Sort");
                allowed(mc, PICK, BehaviorKeys.DROP, false, "an item lock stops Q");
                allowed(mc, PICK, BehaviorKeys.SHIFT_CLICK_OUT, false, "an item lock stops shift-click out");
                allowed(mc, TORCHES, InventoryPlusOperations.MOVE_MATCHING_IN, true,
                        "Move Matching may top up an item-locked stack");

                // A Slot lock lets the player's own swaps through.
                allowed(mc, HOTBAR_LOCKED, BehaviorKeys.HOTBAR_SWAP, true, "a Slot lock lets number keys through");
                allowed(mc, HOTBAR_LOCKED, BehaviorKeys.OFFHAND_SWAP, true, "a Slot lock lets F through");
                allowed(mc, HOTBAR_LOCKED, BehaviorKeys.DROP, false, "a Slot lock stops Q");

                // A cycle slot is an ordinary slot.
                expect(ColumnCycler.isCycleSlot(CYCLE), "the cycle slot is set up");
                allowed(mc, CYCLE, InventoryPlusOperations.SORT, true, "Sort goes through a cycle slot");
                allowed(mc, CYCLE, BehaviorKeys.SHIFT_CLICK_OUT, true, "shift-click out works on a cycle slot");
                allowed(mc, CYCLE, BehaviorKeys.DROP, true, "Q works on a cycle slot");
                expect(!Reach.featureDenies(Reach.HOTBAR_CYCLER, "inventoryplus:sort"),
                        "Hotbar Cycler's rows deny Sort nothing by default");

                // Move Matching and the hotbar: unticked by default, the box works.
                allowed(mc, HOTBAR_FREE, InventoryPlusOperations.MOVE_MATCHING_OUT, false,
                        "Move Matching leaves the hotbar alone by default");
                allowed(mc, FREE, InventoryPlusOperations.MOVE_MATCHING_OUT, true, "Move Matching takes from the main inventory");
                String hotbar = new SlotGroupId.Category(
                        com.trevlar.slotkit.api.group.SlotGroupCategory.PLAYER_HOTBAR).asString();
                Reach.setDenied("inventoryplus:move_matching_out", List.of(hotbar), false);
                allowed(mc, HOTBAR_FREE, InventoryPlusOperations.MOVE_MATCHING_OUT, true,
                        "ticking Move Matching's Hotbar box lets it take from the hotbar");
                Reach.revert("inventoryplus:move_matching_out");

                // A companion's protected slots: everything but what it allows.
                SlotGroupId fake = new SlotGroupId.Created("inventoryplus_checks:protected", "protected");
                InventoryPlusApi.protectInReach(List.of(fake), List.of(BehaviorKeys.CLICK_PUT));
                expect(Reach.denies("menukit:world_pickup", fake.asString()), "a protected group refuses pickups by default");
                expect(!Reach.denies("menukit:click_put", fake.asString()), "a protected group allows what it names");
            });

            // An item-locked pickaxe in a chest comes back out.
            onServer(world, p -> {
                BlockPos at = p.blockPosition().offset(2, 0, 0);
                p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                ((Container) p.level().getBlockEntity(at)).setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
                p.openMenu((MenuProvider) p.level().getBlockEntity(at));
            });
            ctx.waitTicks(8);
            ctx.runOnClient(mc -> {
                AbstractContainerMenu menu = mc.player.containerMenu;
                Slot chestSlot = menu.getSlot(0);
                expect(chestSlot.getItem().is(Items.DIAMOND_PICKAXE) && LockedItems.isExactLocked(chestSlot.getItem()),
                        "the chest holds an exact-locked pickaxe");
                expect(SlotOperations.allows(menu, chestSlot, mc.player, BehaviorKeys.SHIFT_CLICK_OUT),
                        "an item lock does not hold an item stored in a chest");
                Slot carried = null;
                for (Slot s : menu.slots) {
                    if (s.container == mc.player.getInventory() && s.getContainerSlot() == PICK) carried = s;
                }
                expect(carried != null && !SlotOperations.allows(menu, carried, mc.player, BehaviorKeys.SHIFT_CLICK_OUT),
                        "an item lock still holds the carried pickaxe in a chest's screen");
            });
            ctx.setScreen(() -> null);

            // A record saved before these defaults loads without its choices; a current one keeps them.
            ctx.runOnClient(mc -> {
                JsonObject saved = Reach.write();
                Reach.read(record(1));
                expect(!Reach.hasEntry("inventoryplus:sort"), "an old record's choices are cleared");
                Reach.read(record(2));
                expect(Reach.hasEntry("inventoryplus:sort"), "a current record keeps its choices");
                Reach.read(saved);
                ColumnCycler.setCycle(CYCLE, false);
            });
        }
    }

    /** A reach record of {@code version} with one choice: Sort may use everything. */
    private static JsonObject record(int version) {
        JsonObject json = new JsonObject();
        json.addProperty("version", version);
        JsonObject denied = new JsonObject();
        denied.add("inventoryplus:sort", new JsonArray());
        json.add("denied", denied);
        return json;
    }

    private static void allowed(Minecraft mc, int containerSlot, BehaviorKey<TriBool> op, boolean want, String what) {
        AbstractContainerMenu menu = mc.player.inventoryMenu;
        boolean got = SlotOperations.allows(menu, menuSlot(mc.player, containerSlot), mc.player, op);
        expect(got == want, what + " (slot " + containerSlot + ", " + op.id() + " allowed " + got + ")");
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Reach defaults: " + what);
    }
}
