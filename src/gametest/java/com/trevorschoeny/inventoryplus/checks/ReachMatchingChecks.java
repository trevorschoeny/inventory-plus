package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.contents;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.shulker;

import com.trevorschoeny.inventoryplus.lockgroups.Reach;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Reach's two lists for a put: Empty slots lets any item in, Matching only an item the
 * slot's container already holds. Each list alone and neither, on shift-click into a
 * chest and on a world pickup; a placed shulker box filled by Matching alone; and a
 * carried shulker box filled by Matching pickups.
 *
 * <p>Every refusal is paired with the same click or pickup landing, where only the
 * box or what the container holds differs, so a click that never ran can't pass.
 */
public class ReachMatchingChecks implements FabricClientGameTest {

    private static final String SHIFT_IN = "menukit:shift_click_in", PICKUP = "menukit:world_pickup";
    private static final String CHEST = key(SlotGroupCategory.CHEST_STORAGE);
    private static final String SHULKER = key(SlotGroupCategory.SHULKER_STORAGE);
    private static final List<String> INVENTORY = List.of(
            key(SlotGroupCategory.PLAYER_INVENTORY), key(SlotGroupCategory.PLAYER_HOTBAR));
    /** The inventory slot a shift-click starts from. */
    private static final int FROM = 20;

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            chest(world);
            placedShulker(world);
            pickup(world);
            carriedShulker(world);
        } finally {
            ctx.runOnClient(mc -> Reach.revertAll());
        }
    }

    /** Shift-click into a chest, under each list alone and neither. */
    private void chest(TestSingleplayerContext world) {
        BlockPos at = open(world, 2, Blocks.CHEST);

        lists(SHIFT_IN, List.of(CHEST), true, true);
        expect(shiftIn(world, at, null, Items.DIRT) == 16, "default: dirt goes into an empty chest");
        lists(SHIFT_IN, List.of(CHEST), true, false);
        expect(shiftIn(world, at, null, Items.DIRT) == 16, "Empty slots alone: dirt goes into an empty chest");

        lists(SHIFT_IN, List.of(CHEST), false, true);
        expect(shiftIn(world, at, null, Items.DIRT) == 0, "Matching alone: dirt stays out of an empty chest");
        expect(shiftIn(world, at, Items.COBBLESTONE, Items.DIRT) == 0,
                "Matching alone: dirt stays out of a chest holding only cobblestone");
        expect(shiftIn(world, at, Items.COBBLESTONE, Items.COBBLESTONE) == 16,
                "Matching alone: cobblestone goes into a chest holding cobblestone");

        lists(SHIFT_IN, List.of(CHEST), false, false);
        expect(shiftIn(world, at, Items.COBBLESTONE, Items.COBBLESTONE) == 0,
                "neither: cobblestone stays out even of a chest holding it");
        ctx.runOnClient(mc -> Reach.revert(SHIFT_IN));
        onServer(world, ServerPlayer::closeContainer);
    }

    /** A placed shulker box with one cobblestone, Matching alone: it fills with cobblestone and refuses dirt. */
    private void placedShulker(TestSingleplayerContext world) {
        BlockPos at = open(world, -2, Blocks.SHULKER_BOX);
        lists(SHIFT_IN, List.of(SHULKER), false, true);
        int[] got = new int[3];
        onServer(world, p -> {
            clear(p);
            Container box = (Container) p.level().getBlockEntity(at);
            box.clearContent();
            box.setItem(0, new ItemStack(Items.COBBLESTONE, 1));
            for (int i = 0; i < 3; i++) p.getInventory().setItem(9 + i, new ItemStack(Items.COBBLESTONE, 64));
            p.getInventory().setItem(FROM, new ItemStack(Items.DIRT, 16));
            for (int i = 0; i < 3; i++) quickMove(p, 9 + i);
            quickMove(p, FROM);
            got[0] = held(box, Items.COBBLESTONE);
            got[1] = held(box, Items.DIRT);
            got[2] = count(p, Items.DIRT);
        });
        expect(got[0] == 1 + 3 * 64, "Matching alone: three stacks of cobblestone fill the box holding one ("
                + got[0] + ")");
        expect(got[1] == 0 && got[2] == 16, "Matching alone: the box refuses dirt (in box " + got[1] + ")");
        ctx.runOnClient(mc -> Reach.revert(SHIFT_IN));
        onServer(world, ServerPlayer::closeContainer);
    }

    /** A world pickup, with the inventory and hotbar rows under each list alone and neither. */
    private void pickup(TestSingleplayerContext world) {
        ctx.waitTicks(5);
        lists(PICKUP, INVENTORY, true, false);
        expect(pick(world, null, Items.DIRT) == 5, "Empty slots alone: dirt is picked up into an empty inventory");

        lists(PICKUP, INVENTORY, false, true);
        expect(pick(world, null, Items.DIRT) == 0, "Matching alone: dirt isn't picked up into an inventory without it");
        expect(pick(world, Items.COBBLESTONE, Items.DIRT) == 0,
                "Matching alone: dirt isn't picked up into an inventory holding only cobblestone");
        expect(pick(world, Items.DIRT, Items.DIRT) == 5 + 1, "Matching alone: dirt is picked up where dirt already is");

        lists(PICKUP, INVENTORY, false, false);
        expect(pick(world, Items.DIRT, Items.DIRT) == 1, "neither: nothing is picked up");
        ctx.runOnClient(mc -> Reach.revert(PICKUP));
    }

    /**
     * A carried shulker box holding one cobblestone, and an empty one, with Shulker
     * Boxes' Matching box ticked for pickups: picked-up cobblestone fills the first, and
     * dirt goes into neither. With Empty slots ticked too, dirt goes into a box.
     */
    private void carriedShulker(TestSingleplayerContext world) {
        ctx.runOnClient(mc -> Reach.setDenied(Reach.matching(PICKUP), List.of(Reach.SHULKER_BOXES), false));
        ctx.waitTicks(25);   // the client's choices reach the server twice a second
        int[] got = new int[5];
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(20, shulker(4, new ItemStack(Items.COBBLESTONE, 1)));
            p.getInventory().setItem(21, shulker());
            p.getInventory().add(new ItemStack(Items.COBBLESTONE, 100));
            p.getInventory().add(new ItemStack(Items.DIRT, 5));
            got[0] = held(p, 20, Items.COBBLESTONE);
            got[1] = held(p, 21, Items.COBBLESTONE);
            got[2] = count(p, Items.COBBLESTONE);
            got[3] = held(p, 20, Items.DIRT) + held(p, 21, Items.DIRT);
            got[4] = count(p, Items.DIRT);
        });
        expect(got[0] == 101 && got[1] == 0 && got[2] == 0, "Matching: picked-up cobblestone fills the box holding it ("
                + got[0] + " in it, " + got[1] + " in the empty box, " + got[2] + " loose)");
        expect(got[3] == 0 && got[4] == 5, "Matching: dirt goes into no box (" + got[3] + " in boxes)");

        ctx.runOnClient(mc -> Reach.setDenied(PICKUP, List.of(Reach.SHULKER_BOXES), false));
        ctx.waitTicks(25);
        onServer(world, p -> {
            p.getInventory().add(new ItemStack(Items.DIRT, 5));
            got[3] = held(p, 20, Items.DIRT) + held(p, 21, Items.DIRT);
            got[4] = count(p, Items.DIRT);
        });
        expect(got[3] == 5 && got[4] == 5, "Empty slots: picked-up dirt goes into a box (" + got[3] + " in boxes)");
        ctx.runOnClient(mc -> Reach.revert(PICKUP));
        ctx.waitTicks(25);
    }

    // Helpers

    /** Sets {@code op}'s boxes for {@code keys}: Empty slots and Matching. */
    private void lists(String op, List<String> keys, boolean empty, boolean matching) {
        ctx.runOnClient(mc -> {
            Reach.setDenied(op, keys, !empty);
            Reach.setDenied(Reach.matching(op), keys, !matching);
        });
    }

    /** Places {@code block} {@code dx} blocks east of the player and opens it. */
    private BlockPos open(TestSingleplayerContext world, int dx, net.minecraft.world.level.block.Block block) {
        BlockPos[] at = {null};
        onServer(world, p -> {
            at[0] = p.blockPosition().offset(dx, 0, 0);
            p.level().setBlockAndUpdate(at[0], block.defaultBlockState());
            p.openMenu((MenuProvider) p.level().getBlockEntity(at[0]));
        });
        ctx.waitTicks(8);
        return at[0];
    }

    /**
     * With the chest at {@code at} open and holding one {@code holds} (or nothing),
     * shift-clicks 16 {@code item} from the inventory into it; returns how many of
     * {@code item} it then holds beyond what it started with.
     */
    private int shiftIn(TestSingleplayerContext world, BlockPos at, Item holds, Item item) {
        int[] landed = {0};
        onServer(world, p -> {
            clear(p);
            Container chest = (Container) p.level().getBlockEntity(at);
            chest.clearContent();
            if (holds != null) chest.setItem(5, new ItemStack(holds, 1));
            int before = held(chest, item);
            p.getInventory().setItem(FROM, new ItemStack(item, 16));
            quickMove(p, FROM);
            landed[0] = held(chest, item) - before;
        });
        return landed[0];
    }

    /** With one {@code holds} in main slot 30 (or nothing), picks up 5 {@code item}; returns how many the inventory then holds. */
    private int pick(TestSingleplayerContext world, Item holds, Item item) {
        int[] got = {0};
        onServer(world, p -> {
            clear(p);
            if (holds != null) p.getInventory().setItem(30, new ItemStack(holds, 1));
            p.getInventory().add(new ItemStack(item, 5));
            got[0] = count(p, item);
        });
        return got[0];
    }

    /** A shift-click on the player's inventory slot {@code containerSlot} in the open menu, as the server runs one. */
    private static void quickMove(ServerPlayer p, int containerSlot) {
        var menu = p.containerMenu;
        for (Slot s : menu.slots) {
            if (s.container == p.getInventory() && s.getContainerSlot() == containerSlot) {
                menu.clicked(s.index, 0, ContainerInput.QUICK_MOVE, p);
                return;
            }
        }
        throw new AssertionError("Reach matching: inventory slot " + containerSlot + " is not on the open menu");
    }

    private static int held(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /** How many of {@code item} the carried shulker in inventory slot {@code slot} holds. */
    private static int held(ServerPlayer p, int slot, Item item) {
        return contents(p.getInventory().getItem(slot)).stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static String key(SlotGroupCategory category) {
        return new SlotGroupId.Category(category).asString();
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Reach matching: " + what);
    }
}
