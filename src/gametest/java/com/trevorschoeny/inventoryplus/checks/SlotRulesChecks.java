package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.menuSlot;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.trevorschoeny.inventoryplus.api.InventoryPlusOperations;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;
import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.Automation;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.BehaviorKeys;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Slot rules: what a lock refuses and lets through, the player's own locks held on
 * the client and by the server, a chest's lock held against a hopper, and the
 * migrations from lock groups, Reach and the old restock switches.
 */
public class SlotRulesChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        rule();
        migrations();
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            ownLocks(world);
            chestLocks(world);
        }
    }

    /** The one rule: a lock lets the player's deliberate moves through and refuses the rest. */
    private void rule() {
        List<BehaviorKey<?>> refused = List.of(BehaviorKeys.SHIFT_CLICK_IN, BehaviorKeys.COLLECT,
                BehaviorKeys.INVENTORY_INSERT, BehaviorKeys.DROP, BehaviorKeys.DROP_STACK,
                BehaviorKeys.AUTOMATION_PUT, BehaviorKeys.AUTOMATION_TAKE,
                InventoryPlusOperations.SORT, InventoryPlusOperations.MOVE_MATCHING_IN,
                InventoryPlusOperations.MOVE_MATCHING_OUT, InventoryPlusOperations.RESTOCK_TAKE,
                InventoryPlusOperations.PICK_BLOCK, InventoryPlusOperations.HOTBAR_CYCLE);
        List<BehaviorKey<?>> allowed = List.of(BehaviorKeys.CLICK_TAKE, BehaviorKeys.CLICK_PUT,
                BehaviorKeys.DRAG_FILL, BehaviorKeys.HOTBAR_SWAP, BehaviorKeys.OFFHAND_SWAP,
                BehaviorKeys.SHIFT_CLICK_OUT, InventoryPlusOperations.COLUMN_CYCLE,
                InventoryPlusOperations.AUTO_TOOL_SWITCH,
                InventoryPlusOperations.RESTOCK_PUT);
        for (BehaviorKey<?> op : refused) expect(SlotLocks.refuses(op), "a lock refuses " + op.id());
        for (BehaviorKey<?> op : allowed) expect(!SlotLocks.refuses(op), "a lock lets " + op.id() + " through");
    }

    /** Lock groups, Reach's pickup choice and the per-kind restock switches, read as 3.4.0 reads them. */
    private void migrations() {
        expect(LockedSlots.readLocks(json("{\"9\":\"g2\",\"36\":\"slot_lock\"}"), Integer::parseInt).equals(Set.of(9, 36)),
                "a version 2 lock of any group is a lock");
        expect(LockedSlots.readLocks(JsonParser.parseString("[\"4\"]"), Integer::parseInt).equals(Set.of(4)),
                "a version 3 lock reads back");

        String pickupEmpty = "{\"version\":3,\"denied\":{\"menukit:world_pickup\":[]},\"matching\":{}}";
        String pickupMatching = "{\"version\":3,\"denied\":{\"menukit:world_pickup\":[\"inventoryplus:place/shulker_boxes\"]},"
                + "\"matching\":{\"menukit:world_pickup\":[]}}";
        expect(new Intake(Intake.When.BEFORE, false).equals(IPConfig.pickupIntakeOf(json(pickupEmpty))),
                "Shulker Boxes ticked under Empty slots becomes Before inventory");
        expect(new Intake(Intake.When.BEFORE, true).equals(IPConfig.pickupIntakeOf(json(pickupMatching))),
                "ticked under Matching only becomes Before inventory, Matching only");
        expect(IPConfig.pickupIntakeOf(json("{\"version\":3,\"denied\":{},\"matching\":{}}")) == null,
                "an untouched pickup leaves boxes Off");
        expect(IPConfig.pickupIntakeOf(json("{\"version\":2,\"denied\":{\"menukit:world_pickup\":[]}}")) == null,
                "version 2 had no carried places");

        expect(IPConfig.restockFromShulkersOf(json("{\"restockToolsFromShulkers\":true}"), false),
                "any kind restocking from shulkers turns the one setting on");
        expect(!IPConfig.restockFromShulkersOf(json("{\"restockItemsFromBundles\":true}"), false),
                "bundles alone don't");
        expect(!IPConfig.restockFromShulkersOf(json("{\"restockFromShulkers\":false,\"restockItemsFromShulkers\":true}"), false),
                "once saved, the one setting is what counts");
    }

    /** The player's own locks: a pickup skips a locked slot, on the client's word and on the server's own. */
    private void ownLocks(TestSingleplayerContext world) {
        ctx.runOnClient(mc -> {
            LockedSlots.setLocked(menuSlot(mc.player, 15), true);
            // On the client thread only the client's own veto can answer: the server's
            // copy judges server-side players.
            expect(!IPSlotOperations.allowsPlayerSlot(mc.player, 15, BehaviorKeys.INVENTORY_INSERT)
                            && IPSlotOperations.allowsPlayerSlot(mc.player, 15, BehaviorKeys.CLICK_PUT),
                    "the client refuses a pickup into locked slot 15 and allows a click");
        });
        ctx.waitTicks(25);   // the masks reach the server
        onServer(world, p -> {
            fillExcept(p, 15, 16);
            p.getInventory().add(new ItemStack(Items.DIRT, 5));
            expect(p.getInventory().getItem(15).isEmpty() && p.getInventory().getItem(16).is(Items.DIRT),
                    "a pickup skips locked slot 15 for 16");
            // The control: unlocked, 15 is where the same pickup goes.
            p.getInventory().setItem(16, new ItemStack(Items.STONE, 64));
        });
        ctx.runOnClient(mc -> LockedSlots.setLocked(menuSlot(mc.player, 15), false));
        ctx.waitTicks(25);
        onServer(world, p -> {
            p.getInventory().add(new ItemStack(Items.DIRT, 5));
            expect(p.getInventory().getItem(15).is(Items.DIRT), "unlocked, slot 15 takes the pickup");
        });

        // The server's own copy: slot 15 unlocked on the client, so only the masks speak.
        onServer(world, p -> {
            SlotLocks.setMasks(p.getUUID(), new SlotLocks.Masks(1L << 15, 0));
            fillExcept(p, 15, 16);
            p.getInventory().add(new ItemStack(Items.DIRT, 5));
            expect(p.getInventory().getItem(15).isEmpty() && p.getInventory().getItem(16).is(Items.DIRT),
                    "the server holds the lock its client sent");
            SlotLocks.setMasks(p.getUUID(), new SlotLocks.Masks(0, 0));
        });

        // Deliberate moves out of a locked slot: Q is refused, a shift-click out goes.
        ctx.runOnClient(mc -> LockedSlots.setLocked(menuSlot(mc.player, 15), true));
        ctx.waitTicks(25);
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(15, new ItemStack(Items.DIRT, 5));
            int menuIndex = PlayerMenuSlots.menuIndexOf(p.inventoryMenu, p, 15);
            p.inventoryMenu.clicked(menuIndex, 0, ContainerInput.THROW, p);
            expect(p.getInventory().getItem(15).getCount() == 5, "Q is refused on a locked slot");
            p.inventoryMenu.clicked(menuIndex, 0, ContainerInput.QUICK_MOVE, p);
            expect(p.getInventory().getItem(15).isEmpty() && count(p, Items.DIRT) == 5,
                    "a shift-click out of a locked slot moves it");
        });
        ctx.runOnClient(mc -> LockedSlots.setLocked(menuSlot(mc.player, 15), false));
    }

    /** A chest's and a box's locks: SHARED, held against a hopper, seen and set by the client. */
    private void chestLocks(TestSingleplayerContext world) {
        BlockPos[] at = {null, null};
        onServer(world, p -> {
            clear(p);
            BlockPos chestPos = p.blockPosition().offset(2, 0, 0);
            p.level().setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
            Container chest = (Container) p.level().getBlockEntity(chestPos);
            for (int i = 0; i < 3; i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
            SlotLocks.CHANNEL.set(new Slot(chest, 3, 0, 0), true);
            expect(Automation.room(chest, 3, new ItemStack(Items.DIRT)) == 0 && !Automation.mayTake(chest, 3),
                    "automation may neither put into nor take from the chest's locked slot");
            expect(Automation.room(chest, 4, new ItemStack(Items.DIRT)) == 1, "the slot beside it is open");
            // A hopper on top: it fills the first slot with room, which is 3 unless the lock holds.
            BlockPos hopperPos = chestPos.above();
            p.level().setBlockAndUpdate(hopperPos, Blocks.HOPPER.defaultBlockState());
            ((Container) p.level().getBlockEntity(hopperPos)).setItem(0, new ItemStack(Items.DIRT));
            at[0] = chestPos;

            ItemStack box = CarriedItems.shulker();
            SlotLocks.CHANNEL.set(box, 2, true);
            expect(Automation.room(box, 2, new ItemStack(Items.DIRT)) == 0 && Automation.room(box, 3, new ItemStack(Items.DIRT)) == 1,
                    "a box carries its own lock: automation skips its slot 2");
        });
        ctx.waitTicks(30);
        onServer(world, p -> {
            Container chest = (Container) p.level().getBlockEntity(at[0]);
            expect(chest.getItem(3).isEmpty() && chest.getItem(4).is(Items.DIRT),
                    "the hopper passes over the locked slot 3 (" + chest.getItem(3) + ", " + chest.getItem(4) + ")");
            p.level().removeBlock(at[0].above(), false);
            Reservations.set(chest, 1, Reservation.of(new ItemStack(Items.DIAMOND)).withCap(16));
        });

        // The client sees the chest's lock, and L's write reaches the chest.
        open(at[0]);
        expect(ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 3))),
                "the client sees the chest's lock on slot 3");
        ctx.runOnClient(mc -> LockedSlots.setLocked(chestSlot(mc.player.containerMenu, 6), true));
        ctx.waitTicks(5);
        onServer(world, p -> expect(SlotLocks.CHANNEL.get(new Slot((Container) p.level().getBlockEntity(at[0]), 6, 0, 0)),
                "the client's lock on slot 6 is on the chest"));

        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
    }

    /** Opens the chest as a player does, by using it, so Inventory Plus knows which chest it is. */
    private void open(BlockPos pos) {
        ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        ctx.waitTicks(8);
        expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "the chest at " + pos + " is open");
    }

    private static Slot chestSlot(AbstractContainerMenu menu, int i) {
        for (Slot s : menu.slots) if (!(s.container instanceof Inventory) && s.getContainerSlot() == i) return s;
        throw new AssertionError("Slot rules: no chest slot " + i + " on the open screen");
    }

    /** Fills the hotbar and main inventory with full stacks of stone, except {@code a} and {@code b}. */
    private static void fillExcept(ServerPlayer p, int a, int b) {
        clear(p);
        for (int i = 0; i < 36; i++) if (i != a && i != b) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Slot rules: " + what);
    }
}
