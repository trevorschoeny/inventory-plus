package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;
import com.trevorschoeny.inventoryplus.sort.SortType;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.slotkit.api.slot.CreatedSlot;

import com.mojang.blaze3d.platform.InputConstants;

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

import java.util.ArrayList;
import java.util.List;

/**
 * Sort, Move Matching and locks act on one container at a time with several
 * open in a chest's container list: the chest, and {@link ListFixture}'s two
 * shulker boxes and bundle below it, each its own container.
 *
 * <ul>
 *   <li>Each box has its own Sort button, and box B's sorts box B, not the
 *       chest or box A.</li>
 *   <li>Every container's Sort button lines up with the inventory toolbar's
 *       right edge, the bundle's too while it shows one slot.</li>
 *   <li>The bundle's Sort button and the sort key over it put its stacks in
 *       order, though a stack taken out of a bundle moves the rest up.</li>
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
    private enum In { CHEST, A, B, BUNDLE }

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
                ListFixture.BUNDLE.setActiveCount(p, ListFixture.SIZE);
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
            buttonChecks(world);
            headerChecks(world);
            bundleChecks(world);
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
                ListFixture.BUNDLE.setActiveCount(p, 0);
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

    /**
     * Each box's own Sort button sorts that box. Box B's is the second shulker
     * toolbar; before each toolbar had its own group, every one sorted the
     * first container on the menu, the chest.
     */
    private void buttonChecks(TestSingleplayerContext world) {
        fill(world, In.CHEST, 0, Items.DIRT, 5); fill(world, In.CHEST, 5, Items.DIRT, 7);
        fill(world, In.A, 0, Items.DIRT, 4);     fill(world, In.A, 6, Items.DIRT, 6);
        fill(world, In.B, 0, Items.DIRT, 3);     fill(world, In.B, 4, Items.DIRT, 9);
        ctx.waitTicks(10);

        clickSort("shulker_storage.1");
        onServer(world, p -> {
            expect(stacks(p, In.B, Items.DIRT).equals("1x12"), "Box B's Sort button merges box B's dirt ("
                    + stacks(p, In.B, Items.DIRT) + ")");
            expect(stacks(p, In.A, Items.DIRT).equals("2x10"), "Box B's Sort button leaves box A alone ("
                    + stacks(p, In.A, Items.DIRT) + ")");
            expect(stacks(p, In.CHEST, Items.DIRT).equals("2x12"), "Box B's Sort button leaves the chest alone ("
                    + stacks(p, In.CHEST, Items.DIRT) + ")");
        });
        clickSort("shulker_storage");
        onServer(world, p -> expect(stacks(p, In.A, Items.DIRT).equals("1x10"),
                "Box A's Sort button merges box A's dirt (" + stacks(p, In.A, Items.DIRT) + ")"));
        emptyAll(world);
    }

    /**
     * A container's Sort sits at the right end of its title row, where the
     * inventory toolbar's sits, however narrow its grid. With one slot showing
     * (an empty bundle has only its add slot) the bundle's box is one slot wide,
     * and a toolbar placed on that box alone lands on the bundle's title.
     */
    private void headerChecks(TestSingleplayerContext world) {
        onServer(world, p -> ListFixture.BUNDLE.setActiveCount(p, 1));
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            Reference inventory = Toolbar.inventoryToolbar().placedBounds();
            expect(inventory != null, "the inventory toolbar shows");
            int right = inventory.leftPos() + inventory.imageWidth();
            for (String which : List.of("chest_storage", "shulker_storage", "shulker_storage.1", "bundle_storage")) {
                Reference r = Toolbar.externalToolbars().stream()
                        .filter(t -> t.id().equals("inventoryplus.toolbar.external." + which))
                        .findFirst().orElseThrow().placedBounds();
                expect(r != null, "the " + which + " Sort toolbar shows");
                expect(r.leftPos() + r.imageWidth() == right, "the " + which + " Sort toolbar's right edge ("
                        + (r.leftPos() + r.imageWidth()) + ") lines up with the inventory toolbar's (" + right + ")");
            }
        });
        onServer(world, p -> ListFixture.BUNDLE.setActiveCount(p, ListFixture.SIZE));
        ctx.waitTicks(10);
    }

    /**
     * Four stacks in the bundle, out of order; its Sort button, then the sort
     * key over it, put them in Sort's order with every stack kept. Each lift
     * moves the stacks after it up, so a sort that swaps slots in place would
     * scramble them.
     */
    private void bundleChecks(TestSingleplayerContext world) {
        Runnable shuffle = () -> {
            fill(world, In.BUNDLE, 0, Items.OAK_LOG, 3);
            fill(world, In.BUNDLE, 1, Items.DIAMOND, 2);
            fill(world, In.BUNDLE, 2, Items.BREAD, 5);
            fill(world, In.BUNDLE, 3, Items.COBBLESTONE, 7);
            ctx.waitTicks(10);
            onServer(world, p -> expect(!sorted(bundle(p)), "the bundle starts out of order (" + bundle(p) + ")"));
        };
        shuffle.run();
        clickSort("bundle_storage");
        onServer(world, p -> expectSortedBundle(p, "the bundle's Sort button"));

        emptyAll(world);
        shuffle.run();
        hover(In.BUNDLE, 1);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        ctx.waitTicks(15);
        onServer(world, p -> expectSortedBundle(p, "the sort key over the bundle"));
        emptyAll(world);
    }

    private static void expectSortedBundle(ServerPlayer p, String by) {
        List<ItemStack> stacks = bundle(p);
        expect(stacks.size() == 4 && sorted(stacks), by + " puts its four stacks in order (" + stacks + ")");
        expect(total(p, In.BUNDLE, Items.OAK_LOG) == 3 && total(p, In.BUNDLE, Items.DIAMOND) == 2
                && total(p, In.BUNDLE, Items.BREAD) == 5 && total(p, In.BUNDLE, Items.COBBLESTONE) == 7,
                by + " keeps every item (" + stacks + ")");
    }

    /** The bundle's stacks on the server, in slot order. */
    private static List<ItemStack> bundle(ServerPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < ListFixture.SIZE; i++) {
            ItemStack s = serverSlot(p, In.BUNDLE, i).getItem();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** In the order the bundle's Sort uses, the default (nothing pinned on it). */
    private static boolean sorted(List<ItemStack> stacks) {
        for (int i = 1; i < stacks.size(); i++) {
            if (SortType.DEFAULT.comparator().compare(stacks.get(i - 1), stacks.get(i)) > 0) return false;
        }
        return true;
    }

    /** Clicks the Sort button of the container toolbar whose id ends in {@code which}, and waits for the sort. */
    private void clickSort(String which) {
        double[] at = ctx.computeOnClient(mc -> {
            Panel toolbar = Toolbar.externalToolbars().stream()
                    .filter(t -> t.id().equals("inventoryplus.toolbar.external." + which)).findFirst().orElseThrow();
            Reference r = toolbar.placedBounds();
            expect(r != null, "the " + which + " Sort toolbar shows");
            double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{(r.leftPos() + r.imageWidth() / 2.0) * scale, (r.topPos() + r.imageHeight() / 2.0) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(15);
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
                    : created.panelId().equals(ListFixture.BOX_B.id().panelId()) ? In.B
                    : created.panelId().equals(ListFixture.BUNDLE.id().panelId()) ? In.BUNDLE : null;
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
            // Back to front: the bundle moves its stacks up as each one goes.
            for (In in : In.values()) for (int i = size(in) - 1; i >= 0; i--) serverSlot(p, in, i).set(ItemStack.EMPTY);
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
