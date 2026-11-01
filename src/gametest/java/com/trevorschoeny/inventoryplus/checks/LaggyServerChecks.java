package com.trevorschoeny.inventoryplus.checks;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Sort, Move Matching and Restock on a dedicated server, with 75 ms added to
 * the connection each way. The rest of the suite runs on the integrated
 * server, where the client and the server answer each other at once.
 *
 * <ul>
 *   <li>Sort and the sort key pressed five times in a row: the chest ends in
 *       order on the server, with no item gained or lost, the cursor empty,
 *       and the client showing what the server holds. The mouse is on the
 *       slot's rim, outside its 16 pixels but highlighted.</li>
 *   <li>A sort whose clicks land after another player changed the chest
 *       keeps every item and leaves nothing on the cursor.</li>
 *   <li>With an item on the cursor, Sort and Move Matching still act on the
 *       slots and leave the held item on the cursor untouched. Their clicks
 *       assume an empty cursor, so the held item is parked on an empty slot
 *       outside the ones in play for the run, then handed back.</li>
 *   <li>Move Matching in with the inventory nearly full: no item lost.</li>
 *   <li>Restock while placing blocks: the hand refills once from one stack.</li>
 * </ul>
 */
public class LaggyServerChecks implements FabricClientGameTest {

    private static final int LAG_MS = 75;

    private ClientGameTestContext ctx;
    private TestDedicatedServerContext server;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestDedicatedServerContext s = ctx.worldBuilder().createServer();
             TestDedicatedServerConnection connection = s.connect()) {
            server = s;
            ctx.waitTicks(20);
            server.runCommand("gamemode survival @a");
            ctx.runOnClient(mc -> addLag(mc));
            ctx.runOnClient(mc -> {
                IPConfig.setRestockEnabled(true);
                IPConfig.setAutoRestockItem(true);
                IPConfig.setMoveMatchingEnabled(true);
            });

            restockChecks();
            BlockPos chest = onServer(p -> {
                p.getInventory().clearContent();
                BlockPos at = p.blockPosition().offset(2, 0, 0);
                p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                return at;
            });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false)));
            ctx.waitTicks(20);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "the chest is open");

            sortChecks();
            raceChecks();
            cursorChecks();
            moveMatchingChecks();
            ctx.setScreen(() -> null);
            ctx.waitTicks(10);
        }
    }

    /**
     * Dirt and stone strewn about the chest, sorted by the key five times in a
     * row, with the mouse on the slot's rim: vanilla highlights a slot from a
     * pixel outside it, so the gap between two slots belongs to one of them.
     */
    private void sortChecks() {
        fillChest(p -> {
            chestSlot(p, 1).set(new ItemStack(Items.DIRT, 10));
            chestSlot(p, 4).set(new ItemStack(Items.STONE, 20));
            chestSlot(p, 6).set(new ItemStack(Items.DIRT, 40));
            chestSlot(p, 13).set(new ItemStack(Items.DIRT, 30));
            chestSlot(p, 20).set(new ItemStack(Items.STONE, 7));
            chestSlot(p, 26).set(new ItemStack(Items.COBBLESTONE, 1));
        });
        hoverChest(6, -0.5);
        expect(ctx.computeOnClient(mc -> ScreenLayout.hoveredSlot((AbstractContainerScreen<?>) mc.gui.screen())
                == mc.player.containerMenu.getSlot(6)), "vanilla highlights chest slot 6 from its rim");
        for (int i = 0; i < 5; i++) ctx.getInput().pressKey(IPKeybinds.SORT);
        settle();
        onServer(p -> {
            expect(total(p, Items.DIRT) == 80 && total(p, Items.STONE) == 27 && total(p, Items.COBBLESTONE) == 1,
                    "sorting keeps every item (" + chestOf(p) + ")");
            expect(packed(p), "sorting leaves no gap before the last stack (" + chestOf(p) + ")");
            expect(p.containerMenu.getCarried().isEmpty(), "sorting leaves the cursor empty");
            return null;
        });
        expectClientMatches("after sorting five times");
    }

    /**
     * Another player puts a diamond in the chest while the sort's clicks are on
     * their way: they land on a chest the client didn't plan for.
     */
    private void raceChecks() {
        fillChest(p -> {
            chestSlot(p, 1).set(new ItemStack(Items.DIRT, 10));
            chestSlot(p, 4).set(new ItemStack(Items.STONE, 20));
            chestSlot(p, 6).set(new ItemStack(Items.DIRT, 40));
            chestSlot(p, 9).set(new ItemStack(Items.STONE, 3));
        });
        hoverChest(6);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        onServer(p -> {
            chestSlot(p, 0).set(new ItemStack(Items.DIAMOND, 2));
            return null;
        });
        settle();
        onServer(p -> {
            expect(total(p, Items.DIRT) == 50 && total(p, Items.STONE) == 23 && total(p, Items.DIAMOND) == 2,
                    "a sort that races another player keeps every item (" + chestOf(p) + ", cursor "
                    + p.containerMenu.getCarried() + ")");
            expect(p.containerMenu.getCarried().isEmpty(), "and leaves nothing on the cursor ("
                    + p.containerMenu.getCarried() + ")");
            return null;
        });
        expectClientMatches("after a sort raced another player");
    }

    /**
     * A diamond on the cursor: Sort still consolidates the chest's two dirt
     * stacks and Move Matching out still moves the inventory's dirt into it,
     * with the diamond never leaving the cursor.
     */
    private void cursorChecks() {
        fillChest(p -> {
            chestSlot(p, 2).set(new ItemStack(Items.DIRT, 10));
            chestSlot(p, 9).set(new ItemStack(Items.DIRT, 10));
            p.getInventory().setItem(15, new ItemStack(Items.DIRT, 5));
            p.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
        });
        expect(ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().is(Items.DIAMOND)),
                "the client sees the diamond on the cursor");

        hoverChest(2);
        ctx.getInput().pressKey(IPKeybinds.SORT);
        settle();
        onServer(p -> {
            expect(total(p, Items.DIRT) == 20, "Sort with a diamond on the cursor still sorts the chest ("
                    + chestOf(p) + ")");
            expect(packed(p), "and leaves no gap before the last stack (" + chestOf(p) + ")");
            expect(p.containerMenu.getCarried().is(Items.DIAMOND), "and the diamond stays on the cursor ("
                    + p.containerMenu.getCarried() + ")");
            return null;
        });
        expectClientMatches("after sorting with a diamond on the cursor");

        ctx.getInput().pressKey(IPKeybinds.MOVE_MATCHING_OUT);
        settle();
        onServer(p -> {
            expect(total(p, Items.DIRT) == 25, "Move Matching out with a diamond on the cursor still moves the dirt ("
                    + chestOf(p) + ")");
            expect(p.getInventory().getItem(15).isEmpty(), "and empties the inventory slot it came from ("
                    + p.getInventory().getItem(15) + ")");
            expect(p.containerMenu.getCarried().is(Items.DIAMOND), "and the diamond stays on the cursor ("
                    + p.containerMenu.getCarried() + ")");
            p.containerMenu.setCarried(ItemStack.EMPTY);
            return null;
        });
        expectClientMatches("after the cursor checks");
    }

    /** Move Matching in, the inventory full but for room for 20 dirt. */
    private void moveMatchingChecks() {
        fillChest(p -> {
            for (int i = 0; i < 5; i++) chestSlot(p, i).set(new ItemStack(Items.DIRT, 64));
            for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            p.getInventory().setItem(10, new ItemStack(Items.DIRT, 44));
        });
        ctx.getInput().setCursorPos(1, 1);
        ctx.getInput().pressKey(IPKeybinds.MOVE_MATCHING_IN);
        ctx.getInput().pressKey(IPKeybinds.MOVE_MATCHING_IN);
        settle();
        onServer(p -> {
            int inv = 0;
            for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(Items.DIRT)) inv += p.getInventory().getItem(i).getCount();
            expect(inv + total(p, Items.DIRT) == 364, "Move Matching in keeps every dirt (inventory " + inv
                    + ", chest " + total(p, Items.DIRT) + ")");
            expect(inv == 64, "Move Matching in fills the inventory's dirt stack (" + inv + ")");
            expect(p.containerMenu.getCarried().isEmpty(), "Move Matching in leaves the cursor empty");
            return null;
        });
        expectClientMatches("after Move Matching in");
    }

    /** Three cobblestone in hand, two stacks behind: placing five refills once. */
    private void restockChecks() {
        List<BlockPos> spots = onServer(p -> {
            p.getInventory().clearContent();
            p.getInventory().setSelectedSlot(0);
            p.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
            p.getInventory().setItem(20, new ItemStack(Items.COBBLESTONE, 64));
            p.getInventory().setItem(25, new ItemStack(Items.COBBLESTONE, 64));
            List<BlockPos> at = new ArrayList<>();
            int[][] offsets = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}};
            for (int[] o : offsets) {
                BlockPos floor = p.blockPosition().offset(o[0], -1, o[1]);
                p.level().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                p.level().setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
                at.add(floor);
            }
            return at;
        });
        ctx.waitTicks(20);
        for (BlockPos floor : spots) {
            ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
            ctx.waitTicks(1);
        }
        settle();
        onServer(p -> {
            int n = 0;
            for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(Items.COBBLESTONE)) n += p.getInventory().getItem(i).getCount();
            int placed = 0;
            for (BlockPos floor : spots) if (p.level().getBlockState(floor.above()).is(Blocks.COBBLESTONE)) placed++;
            expect(placed == 5, "all five cobblestone were placed (" + placed + ")");
            expect(n == 126, "restock under lag keeps every cobblestone (" + n + ")");
            ItemStack hand = p.getInventory().getItem(0);
            expect(hand.is(Items.COBBLESTONE) && hand.getCount() == 62, "the hand refilled once (" + hand + ")");
            expect(p.getInventory().getItem(20).isEmpty() != p.getInventory().getItem(25).isEmpty(),
                    "from one of the two stacks (" + p.getInventory().getItem(20) + ", " + p.getInventory().getItem(25) + ")");
            expect(p.containerMenu.getCarried().isEmpty(), "restock leaves the cursor empty");
            return null;
        });
    }

    // ── Lag ─────────────────────────────────────────────────────────────

    /** Holds every packet LAG_MS on the client's side of the connection, each way, in order. */
    private static void addLag(Minecraft mc) {
        try {
            Field f = Connection.class.getDeclaredField("channel");
            f.setAccessible(true);
            Channel channel = (Channel) f.get(mc.getConnection().getConnection());
            channel.pipeline().addFirst("inventoryplus_checks_lag", new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext c, Object msg) {
                    c.executor().schedule(() -> c.fireChannelRead(msg), LAG_MS, TimeUnit.MILLISECONDS);
                }

                @Override
                public void channelReadComplete(ChannelHandlerContext c) {
                    c.executor().schedule(c::fireChannelReadComplete, LAG_MS, TimeUnit.MILLISECONDS);
                }

                @Override
                public void write(ChannelHandlerContext c, Object msg, ChannelPromise promise) {
                    c.executor().schedule(() -> c.write(msg, promise), LAG_MS, TimeUnit.MILLISECONDS);
                }

                @Override
                public void flush(ChannelHandlerContext c) {
                    c.executor().schedule(c::flush, LAG_MS, TimeUnit.MILLISECONDS);
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Laggy server: can't reach the client's channel", e);
        }
    }

    /** Long enough for every click and its answer to cross the lagged connection. */
    private void settle() {
        ctx.waitTicks(30);
    }

    // ── Server side ─────────────────────────────────────────────────────

    private <T> T onServer(Function<ServerPlayer, T> action) {
        return server.computeOnServer((MinecraftServer s) -> action.apply(s.getPlayerList().getPlayers().get(0)));
    }

    /** Empties the chest and the inventory, then sets them up with {@code setup}. */
    private void fillChest(Consumer<ServerPlayer> setup) {
        onServer(p -> {
            for (int i = 0; i < 27; i++) chestSlot(p, i).set(ItemStack.EMPTY);
            p.getInventory().clearContent();
            setup.accept(p);
            p.containerMenu.broadcastChanges();
            return null;
        });
        settle();
    }

    private static Slot chestSlot(ServerPlayer p, int i) {
        return p.containerMenu.getSlot(i);
    }

    private static int total(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < 27; i++) if (chestSlot(p, i).getItem().is(item)) n += chestSlot(p, i).getItem().getCount();
        return n;
    }

    /** Whether the chest's stacks fill its first slots with no empty one between. */
    private static boolean packed(ServerPlayer p) {
        boolean gap = false;
        for (int i = 0; i < 27; i++) {
            boolean empty = chestSlot(p, i).getItem().isEmpty();
            if (!empty && gap) return false;
            gap |= empty;
        }
        return true;
    }

    private static String chestOf(ServerPlayer p) {
        return describe(p.containerMenu);
    }

    private static String describe(AbstractContainerMenu menu) {
        StringBuilder b = new StringBuilder();
        for (Slot s : menu.slots) {
            ItemStack st = s.getItem();
            if (!st.isEmpty()) b.append(s.index).append('=').append(st.getCount()).append(' ')
                    .append(st.getItem()).append(", ");
        }
        return b.toString();
    }

    /** The client's open menu shows exactly what the server's holds. */
    private void expectClientMatches(String when) {
        String serverView = onServer(p -> describe(p.containerMenu));
        String clientView = ctx.computeOnClient(mc -> describe(mc.player.containerMenu));
        expect(serverView.equals(clientView), "the client shows what the server holds " + when
                + " (server: " + serverView + " client: " + clientView + ")");
    }

    // ── Input ───────────────────────────────────────────────────────────

    private void hoverChest(int i) {
        hoverChest(i, 8);
    }

    /** The mouse {@code inset} gui pixels right of and below chest slot {@code i}'s top-left corner. */
    private void hoverChest(int i, double inset) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot s = screen.getMenu().getSlot(i);
            double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{(ScreenLayout.leftPos(screen) + s.x + inset) * scale,
                    (ScreenLayout.topPos(screen) + s.y + inset) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Laggy server: " + what);
    }
}
