package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundle;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundleItems;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

import com.trevorschoeny.inventoryplus.carried.IntakeClient;
import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Intake ({@code nested/CarriedPut}): pickups go into a carried shulker box by the box's
 * own Intake, before or after the inventory, and within a tier to the most specific
 * home. Off by default, never a bundle or the ender chest, and the client's request
 * reaches the server.
 */
public class IntakeChecks implements FabricClientGameTest {

    private static final Intake BEFORE = new Intake(Intake.When.BEFORE, false);
    private static final Intake BEFORE_MATCHING = new Intake(Intake.When.BEFORE, true);
    private static final Intake AFTER = new Intake(Intake.When.AFTER, false);

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            // The client sends its locks twice a second; the server then knows it runs Inventory Plus.
            ctx.waitTicks(25);

            // Off by default: a box holding cobblestone, a bundle holding a dandelion and the
            // ender chest holding dirt all leave a pickup to the inventory.
            stock(world);
            onServer(world, p -> {
                expect(SlotLocks.known(p), "the client's locks reached the server");
                p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
                p.getInventory().add(new ItemStack(Items.DANDELION, 2));
                p.getInventory().add(new ItemStack(Items.DIRT, 3));
                expect(count(p, Items.COBBLESTONE) == 5 && count(p, Items.DANDELION) == 2 && count(p, Items.DIRT) == 3
                                && contents(p.getInventory().getItem(20)).get(4).getCount() == 10
                                && bundleItems(p.getInventory().getItem(21)).get(0).getCount() == 1
                                && p.getEnderChestInventory().getItem(0).getCount() == 5,
                        "with no Intake, pickups land in the inventory, never a box, bundle or ender chest");
            });

            // The client's request: Before inventory on the box in slot 20.
            stock(world);
            ctx.waitTicks(5);   // the box reaches the client
            ctx.runOnClient(mc -> expect(IntakeClient.set(mc.player, 20, BEFORE), "the client can ask for an Intake"));
            ctx.waitTicks(5);
            onServer(world, p -> {
                expect(Intake.of(p.getInventory().getItem(20)).equals(BEFORE), "the server set the box's Intake");
                ItemStack give = new ItemStack(Items.COBBLESTONE, 30);
                p.getInventory().add(give);
                expect(give.isEmpty() && contents(p.getInventory().getItem(20)).get(4).getCount() == 40
                                && count(p, Items.COBBLESTONE) == 0,
                        "Before inventory: a pickup tops up the box's cobblestone, not the inventory");
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> expect(IntakeClient.of(mc.player, 20).equals(BEFORE),
                    "the client reads the box's Intake off the item"));

            // After inventory: only what the inventory has no room for.
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(20, box(AFTER));
                p.getInventory().add(new ItemStack(Items.DIRT, 10));
                expect(count(p, Items.DIRT) == 10 && contents(p.getInventory().getItem(20)).get(0).isEmpty(),
                        "After inventory: with room in the inventory, the box is left alone");
                fillExcept(p, 20);
                ItemStack more = new ItemStack(Items.DIRT, 10);
                p.getInventory().add(more);
                expect(more.isEmpty() && contents(p.getInventory().getItem(20)).get(0).getCount() == 10,
                        "After inventory: what the full inventory can't take goes into the box ("
                                + contents(p.getInventory().getItem(20)).get(0) + ")");
            });

            // The tie rule, three Before boxes in inventory order: a Misc box (slot 0), a
            // Matching only box full of cobblestone in its slot 0 (slot 1), and a Misc box
            // with its slot 7 reserved for cobblestone (slot 2). Position alone would pick
            // the Misc box every time.
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(0, box(BEFORE));
                ItemStack matching = shulker(0, new ItemStack(Items.COBBLESTONE, 64));
                Intake.set(matching, BEFORE_MATCHING);
                p.getInventory().setItem(1, matching);
                ItemStack reserved = box(BEFORE);
                Reservations.set(reserved, 7, Reservation.of(new ItemStack(Items.COBBLESTONE)));
                p.getInventory().setItem(2, reserved);

                p.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));
                expect(contents(p.getInventory().getItem(2)).get(7).getCount() == 5
                                && contents(p.getInventory().getItem(0)).stream().allMatch(ItemStack::isEmpty),
                        "a slot reserved for it beats an empty slot, wherever the box sits");

                p.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));
                expect(contents(p.getInventory().getItem(2)).get(7).getCount() == 64,
                        "a stack of it that isn't full fills first");
                expect(contents(p.getInventory().getItem(1)).get(1).getCount() == 5
                                && contents(p.getInventory().getItem(0)).stream().allMatch(ItemStack::isEmpty),
                        "then an empty slot in the Matching only box that holds it, before the Misc box ("
                                + contents(p.getInventory().getItem(1)).get(1) + ")");

                p.getInventory().add(new ItemStack(Items.DIRT, 3));
                expect(contents(p.getInventory().getItem(0)).get(0).is(Items.DIRT)
                                && contents(p.getInventory().getItem(1)).stream().noneMatch(s -> s.is(Items.DIRT)),
                        "a kind the Matching only box doesn't hold goes to the Misc box");
            });

            // A box's own locked slot is passed over, as automation passes it.
            onServer(world, p -> {
                clear(p);
                ItemStack misc = box(BEFORE);
                SlotLocks.CHANNEL.set(misc, 0, true);
                p.getInventory().setItem(5, misc);
                p.getInventory().add(new ItemStack(Items.DIRT, 4));
                expect(contents(p.getInventory().getItem(5)).get(0).isEmpty()
                                && contents(p.getInventory().getItem(5)).get(1).getCount() == 4,
                        "a pickup skips the box's locked slot 0 (" + contents(p.getInventory().getItem(5)).get(0) + ")");
            });

            // Never a box inside another: a shulker box picked up stays out of the Misc box.
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(5, box(BEFORE));
                p.getInventory().add(new ItemStack(Items.SHULKER_BOX));
                expect(contents(p.getInventory().getItem(5)).get(0).isEmpty() && count(p, Items.SHULKER_BOX) == 2,
                        "a picked-up shulker box never goes into another");
            });
        }
    }

    /** An empty shulker box with {@code intake}. */
    private static ItemStack box(Intake intake) {
        ItemStack box = shulker();
        Intake.set(box, intake);
        return box;
    }

    /** Fills the hotbar and main inventory with full stacks of stone, except {@code keep}. */
    private static void fillExcept(ServerPlayer p, int keep) {
        for (int i = 0; i < 36; i++) if (i != keep) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
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

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Intake: " + what);
    }
}
