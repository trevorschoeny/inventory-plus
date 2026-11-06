package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundle;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.bundleItems;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

import com.trevorschoeny.inventoryplus.carried.BoxSettingsClient;
import com.trevorschoeny.inventoryplus.carried.CarriedFetch;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.nested.CarriedTake;
import com.trevorschoeny.inventoryplus.rules.BoxSettings;
import com.trevorschoeny.inventoryplus.rules.Reservations;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;

import com.trevlar.menukit.api.element.ItemMatch;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

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
 * reaches the server. The player's "Fill shulker boxes with pickups" turns it off, and a
 * box's own "Restock from this box" keeps restock out of it.
 */
public class IntakeChecks implements FabricClientGameTest {

    private static final BoxSettings BEFORE = new BoxSettings(BoxSettings.When.BEFORE, false);
    private static final BoxSettings BEFORE_MATCHING = new BoxSettings(BoxSettings.When.BEFORE, true);
    private static final BoxSettings AFTER = new BoxSettings(BoxSettings.When.AFTER, false);

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
                        "with no BoxSettings, pickups land in the inventory, never a box, bundle or ender chest");
            });

            // The client's request: Before inventory on the box in slot 20.
            stock(world);
            ctx.waitTicks(5);   // the box reaches the client
            ctx.runOnClient(mc -> expect(BoxSettingsClient.set(mc.player, 20, BEFORE), "the client can ask for an Intake"));
            ctx.waitTicks(5);
            onServer(world, p -> {
                expect(BoxSettings.of(p.getInventory().getItem(20)).equals(BEFORE), "the server set the box's Intake");
                ItemStack give = new ItemStack(Items.COBBLESTONE, 30);
                p.getInventory().add(give);
                expect(give.isEmpty() && contents(p.getInventory().getItem(20)).get(4).getCount() == 40
                                && count(p, Items.COBBLESTONE) == 0,
                        "Before inventory: a pickup tops up the box's cobblestone, not the inventory");
            });
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> expect(BoxSettingsClient.of(mc.player, 20).equals(BEFORE),
                    "the client reads the box's BoxSettings off the item"));

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
                BoxSettings.set(matching, BEFORE_MATCHING);
                p.getInventory().setItem(1, matching);
                ItemStack reserved = box(BEFORE);
                Reservations.set(reserved, 7, new ItemMatch(new ItemStack(Items.COBBLESTONE), ItemMatch.NONE));
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

            // "Fill shulker boxes with pickups" off: the same Before box, the same pickup,
            // and only the client's global setting differs.
            ctx.runOnClient(mc -> IPConfig.setFillShulkersWithPickups(false));
            ctx.waitTicks(3);   // the setting reaches the server
            onServer(world, p -> {
                clear(p);
                p.getInventory().setItem(20, box(BEFORE));
                p.getInventory().add(new ItemStack(Items.DIRT, 4));
                expect(count(p, Items.DIRT) == 4 && contents(p.getInventory().getItem(20)).get(0).isEmpty(),
                        "with filling off, a Before box is left alone");
            });
            ctx.runOnClient(mc -> IPConfig.setFillShulkersWithPickups(true));
            ctx.waitTicks(3);
            onServer(world, p -> {
                p.getInventory().add(new ItemStack(Items.DIRT, 4));
                expect(contents(p.getInventory().getItem(20)).get(0).getCount() == 4,
                        "with filling back on, the same box takes the pickup");
            });

            // Restock from this box: the same box of torches, an empty hotbar slot to fill;
            // only the box's restock setting differs.
            for (boolean restock : new boolean[] {false, true}) {
                onServer(world, p -> {
                    clear(p);
                    ItemStack box = shulker(0, new ItemStack(Items.TORCH, 16));
                    BoxSettings.set(box, BoxSettings.DEFAULT.withRestock(restock));
                    p.getInventory().setItem(20, box);
                });
                ctx.waitTicks(5);   // the box reaches the client
                boolean asked = ctx.computeOnClient(mc -> CarriedFetch.fetch(mc.gameMode, mc.player,
                        new CarriedFetch.Reach(true, false, false), CarriedTake.Purpose.RESTOCK,
                        s -> s.is(Items.TORCH), null, new ItemStack(Items.TORCH), 0));
                ctx.waitTicks(5);
                onServer(world, p -> {
                    boolean filled = p.getInventory().getItem(0).is(Items.TORCH);
                    expect(asked == restock && filled == restock,
                            "restock " + (restock ? "takes from" : "skips") + " a box with Restock from this box "
                                    + (restock ? "on" : "off") + " (asked " + asked + ", filled " + filled + ")");
                });
            }

            // A box set before the restock setting existed reads as restocked from.
            expect(BoxSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"when\":\"before\",\"matching_only\":true}"))
                            .getOrThrow().equals(new BoxSettings(BoxSettings.When.BEFORE, true, true)),
                    "an old box's settings keep their Intake and restock on");
        }
    }

    /** An empty shulker box with {@code intake}. */
    private static ItemStack box(BoxSettings intake) {
        ItemStack box = shulker();
        BoxSettings.set(box, intake);
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
