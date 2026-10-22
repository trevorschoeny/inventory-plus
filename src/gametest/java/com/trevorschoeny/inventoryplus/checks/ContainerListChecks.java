package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;
import com.trevlar.slotkit.api.slot.CreatedSlot;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Sort, Move Matching and locks act on one container at a time with several
 * open in a chest's container list: the chest, and {@link ListFixture}'s two
 * boxes below it, each its own container.
 *
 * <ul>
 *   <li>The sort key over a box sorts that box, and the chest and the other box
 *       stay as they were; over the chest, the chest alone.</li>
 *   <li>L locks the slot under the mouse in that container only (the same index
 *       in the other two stays free), and a locked slot keeps its stack through
 *       its own container's sort.</li>
 *   <li>Move Matching out puts each item only in a container that already holds
 *       it: sand that overflows box A's full stack goes to box A's free slot, not
 *       the chest's first one.</li>
 * </ul>
 */
public class ContainerListChecks implements FabricClientGameTest {

    /** Which container a slot is in: the chest, or one of the boxes. */
    private enum In { CHEST, A, B }

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            // GUI scale 1, so the chest, both boxes and the inventory fit on the
            // test window without the list scrolling a box out of view.
            int scale = ctx.computeOnClient(mc -> mc.options.guiScale().get());
            ctx.runOnClient(mc -> {
                ListFixtureClient.on = true;
                mc.options.guiScale().set(1);
                mc.resizeGui();
            });
            BlockPos pos = world.getServer().computeOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                clear(p);
                BlockPos at = p.blockPosition().offset(2, 0, 0);
                p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                ListFixture.BOX_A.setActiveCount(p, ListFixture.SIZE);
                ListFixture.BOX_B.setActiveCount(p, ListFixture.SIZE);
                return at;
            });
            ctx.waitTicks(5);
            // Opened as a player opens it, by using the block, so Inventory Plus
            // knows which chest it is and its slots can be locked.
            ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
            ctx.waitTicks(8);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "the chest is open");
            expect(ctx.computeOnClient(mc -> com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker
                    .getBlockPos(mc.player.containerMenu.containerId) != null), "Inventory Plus knows which chest is open");
            ctx.runOnClient(mc -> {
                AbstractContainerMenu menu = mc.player.containerMenu;
                for (In in : In.values()) {
                    Slot s = slot(menu, in, 0);
                    expect(s != null && s.isActive(), in + ": its slots are on the open screen");
                }
            });

            sortChecks(world);
            lockChecks(world);
            moveMatchingChecks(world);

            ctx.setScreen(() -> null);
            ctx.runOnClient(mc -> {
                ListFixtureClient.on = false;
                mc.options.guiScale().set(scale);
                mc.resizeGui();
            });
            onServer(world, p -> {
                ListFixture.BOX_A.setActiveCount(p, 0);
                ListFixture.BOX_B.setActiveCount(p, 0);
            });
        }
    }

    /** Dirt in two stacks in each container: the key over one merges that one only. */
    private void sortChecks(TestSingleplayerContext world) {
        fill(world, In.CHEST, 0, Items.DIRT, 5); fill(world, In.CHEST, 5, Items.DIRT, 7);
        fill(world, In.A, 0, Items.DIRT, 4);     fill(world, In.A, 6, Items.DIRT, 6);
        fill(world, In.B, 0, Items.DIRT, 3);     fill(world, In.B, 4, Items.DIRT, 9);
        ctx.waitTicks(10);

        hover(In.A, 6);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        onServer(world, p -> {
            expect(stacks(p, In.A, Items.DIRT).equals("1x10"), "Sort over box A merges box A's dirt ("
                    + stacks(p, In.A, Items.DIRT) + ")");
            expect(stacks(p, In.B, Items.DIRT).equals("2x12"), "Sort over box A leaves box B alone ("
                    + stacks(p, In.B, Items.DIRT) + ")");
            expect(stacks(p, In.CHEST, Items.DIRT).equals("2x12"), "Sort over box A leaves the chest alone ("
                    + stacks(p, In.CHEST, Items.DIRT) + ")");
        });

        hover(In.CHEST, 5);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        onServer(world, p -> {
            expect(stacks(p, In.CHEST, Items.DIRT).equals("1x12"), "Sort over the chest merges the chest's dirt ("
                    + stacks(p, In.CHEST, Items.DIRT) + ")");
            expect(stacks(p, In.B, Items.DIRT).equals("2x12"), "Sort over the chest leaves box B alone ("
                    + stacks(p, In.B, Items.DIRT) + ")");
        });
        emptyAll(world);
    }

    /** L on one container's slot locks that container's slot alone; a locked stack stays through a sort. */
    private void lockChecks(TestSingleplayerContext world) {
        hover(In.A, 3);
        press(IPKeybinds.LOCK_SLOT);
        expect(locked(In.A, 3), "L over box A's slot 3 locks it");
        expect(!locked(In.B, 3), "L over box A's slot 3 leaves box B's slot 3 free");
        expect(!locked(In.CHEST, 3), "L over box A's slot 3 leaves the chest's slot 3 free");
        press(IPKeybinds.LOCK_SLOT);
        expect(!locked(In.A, 3), "L again unlocks box A's slot 3");

        hover(In.CHEST, 3);
        press(IPKeybinds.LOCK_SLOT);
        expect(locked(In.CHEST, 3), "L over the chest's slot 3 locks it");
        expect(!locked(In.A, 3) && !locked(In.B, 3), "L over the chest's slot 3 leaves both boxes' slot 3 free");
        press(IPKeybinds.LOCK_SLOT);
        expect(!locked(In.CHEST, 3), "L again unlocks the chest's slot 3");

        // Box B: stone locked in slot 4, dirt either side; its sort leaves the stone where it is.
        fill(world, In.B, 1, Items.DIRT, 3); fill(world, In.B, 4, Items.STONE, 9); fill(world, In.B, 8, Items.DIRT, 2);
        ctx.waitTicks(10);
        hover(In.B, 4);
        press(IPKeybinds.LOCK_SLOT);
        expect(locked(In.B, 4), "L over box B's slot 4 locks it");
        hover(In.B, 8);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        onServer(world, p -> {
            ItemStack kept = serverSlot(p, In.B, 4).getItem();
            expect(kept.is(Items.STONE) && kept.getCount() == 9, "Box B's sort leaves its locked stone in slot 4 ("
                    + kept + ")");
            expect(stacks(p, In.B, Items.DIRT).equals("1x5"), "Box B's sort still merges its free dirt ("
                    + stacks(p, In.B, Items.DIRT) + ")");
        });
        hover(In.B, 4);
        press(IPKeybinds.LOCK_SLOT);
        emptyAll(world);
    }

    /**
     * Move Matching out with the inventory holding stone and sand. The chest
     * holds stone; box A holds a full stack of sand and has room. Each goes to
     * the container that holds it: the stone to the chest, the sand to box A,
     * and none of the sand to the chest.
     */
    private void moveMatchingChecks(TestSingleplayerContext world) {
        fill(world, In.CHEST, 2, Items.STONE, 3);
        fill(world, In.A, 2, Items.SAND, 64);
        onServer(world, p -> {
            p.getInventory().setItem(20, new ItemStack(Items.STONE, 5));
            p.getInventory().setItem(21, new ItemStack(Items.SAND, 10));
        });
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> IPConfig.setMoveMatchingEnabled(true));
        ctx.getInput().setCursorPos(1, 1);
        ctx.getInput().pressKey(IPKeybinds.MOVE_MATCHING_OUT);
        ctx.waitTicks(15);
        onServer(world, p -> {
            expect(total(p, In.CHEST, Items.STONE) == 8, "Move Matching out puts the stone in the chest ("
                    + total(p, In.CHEST, Items.STONE) + ")");
            expect(total(p, In.CHEST, Items.SAND) == 0, "Move Matching out puts no sand in the chest ("
                    + total(p, In.CHEST, Items.SAND) + ")");
            expect(total(p, In.A, Items.SAND) == 74, "Move Matching out puts the sand in box A ("
                    + total(p, In.A, Items.SAND) + ")");
        });
        emptyAll(world);
    }

    // ── Slots ───────────────────────────────────────────────────────────

    /** The slot for {@code in}'s local index {@code i} on {@code menu}, or null. */
    private static @Nullable Slot slot(AbstractContainerMenu menu, In in, int i) {
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            CreatedSlot created = CreatedSlot.of(s);
            In which = created == null ? In.CHEST
                    : created.panelId().equals(ListFixture.BOX_A.id().panelId()) ? In.A
                    : created.panelId().equals(ListFixture.BOX_B.id().panelId()) ? In.B : null;
            int local = created == null ? s.getContainerSlot() : created.getLocalIndex();
            if (which == in && local == i) return s;
        }
        return null;
    }

    private static Slot serverSlot(ServerPlayer p, In in, int i) {
        Slot s = slot(p.containerMenu, in, i);
        if (s == null) throw new AssertionError("Container list: no " + in + " slot " + i + " on the server");
        return s;
    }

    private void fill(TestSingleplayerContext world, In in, int i, Item item, int count) {
        onServer(world, p -> {
            serverSlot(p, in, i).set(new ItemStack(item, count));
            p.containerMenu.broadcastChanges();
        });
    }

    private void emptyAll(TestSingleplayerContext world) {
        onServer(world, p -> {
            for (In in : In.values()) for (int i = 0; i < size(in); i++) serverSlot(p, in, i).set(ItemStack.EMPTY);
            clear(p);
            p.containerMenu.broadcastChanges();
        });
        ctx.waitTicks(10);
    }

    /** "stacks x total" of {@code item} in one container, on the server. */
    private static String stacks(ServerPlayer p, In in, Item item) {
        int stacks = 0, total = 0;
        for (int i = 0; i < size(in); i++) {
            ItemStack s = serverSlot(p, in, i).getItem();
            if (s.is(item)) { stacks++; total += s.getCount(); }
        }
        return stacks + "x" + total;
    }

    private static int total(ServerPlayer p, In in, Item item) {
        int total = 0;
        for (int i = 0; i < size(in); i++) {
            ItemStack s = serverSlot(p, in, i).getItem();
            if (s.is(item)) total += s.getCount();
        }
        return total;
    }

    private static int size(In in) {
        return in == In.CHEST ? 27 : ListFixture.SIZE;
    }

    private boolean locked(In in, int i) {
        return ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(slot(mc.player.containerMenu, in, i)));
    }

    // ── Input ───────────────────────────────────────────────────────────

    /** The cursor over {@code in}'s slot {@code i} on the open screen. */
    private void hover(In in, int i) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot s = slot(screen.getMenu(), in, i);
            expect(s != null && s.isActive(), in + " slot " + i + " is on the screen");
            double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{(ScreenLayout.leftPos(screen) + s.x + 8) * scale,
                    (ScreenLayout.topPos(screen) + s.y + 8) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        String hovered = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot h = ScreenLayout.hoveredSlot(screen);
            return h == slot(screen.getMenu(), in, i) ? null
                    : "menu slot " + (h == null ? "none" : h.index + " (" + h.x + "," + h.y + ")")
                    + ", wanted " + slot(screen.getMenu(), in, i).index + " at (" + slot(screen.getMenu(), in, i).x
                    + "," + slot(screen.getMenu(), in, i).y + "), screen " + mc.getWindow().getGuiScaledWidth()
                    + "x" + mc.getWindow().getGuiScaledHeight() + ", top " + ScreenLayout.topPos(screen);
        });
        expect(hovered == null, "the mouse is over " + in + " slot " + i + ": " + hovered);
    }

    private void press(KeyMapping key) {
        ctx.getInput().pressKey(key);
        ctx.waitTicks(4);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Container list: " + what);
    }
}
