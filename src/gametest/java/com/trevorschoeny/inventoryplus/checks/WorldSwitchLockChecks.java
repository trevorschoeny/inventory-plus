package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;

import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * A chest's slot locks after switching worlds.
 *
 * <p>The open chest's key used to be cached by container id, and ids start
 * again in each world: the first chest opened in the next world has the same
 * id as the last world's, so it was judged by the last world's chest. Its own
 * locks then lived under the wrong key and were gone the next time it opened,
 * and any lock stored under that old key showed through.
 *
 * <p>World A: lock the chest's slot 3. World B, a chest somewhere else: its
 * key is its own, slot 3 is free (nothing of A's shows through), L locks slot
 * 5, and slot 5 is still locked after closing and reopening the chest.
 */
public class WorldSwitchLockChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;

        BlockPos first;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            first = placeChest(world, null);
            open(first);
            expect(key(3).endsWith(":" + coords(first)), "world A: the chest's key is world A's chest (" + key(3) + ")");
            hover(3);
            press();
            expect(locked(3), "world A: L locks the chest's slot 3");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        }

        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            BlockPos second = placeChest(world, first);
            open(second);
            expect(key(3).endsWith(":" + coords(second)),
                    "world B: the first chest opened is judged as world B's own chest, not world A's (" + key(3) + ")");
            expect(!locked(3), "world B: world A's lock on slot 3 doesn't show through");
            hover(5);
            press();
            expect(locked(5), "world B: L locks the chest's slot 5");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);

            open(second);
            expect(locked(5), "world B: slot 5 is still locked after the chest is opened again");
            expect(!locked(3), "world B: slot 3 is still free after the chest is opened again");
            hover(5);
            press();
            expect(!locked(5), "world B: L again unlocks slot 5");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        }
    }

    /** A chest beside the player, never at {@code not} (the last world's chest), so the two keys differ. */
    private BlockPos placeChest(TestSingleplayerContext world, @Nullable BlockPos not) {
        ctx.waitTicks(20);
        world.getServer().runCommand("gamemode survival @a");
        BlockPos pos = world.getServer().computeOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            clear(p);
            BlockPos at = p.blockPosition().offset(not == null ? 2 : 0, 0, not == null ? 0 : 2);
            if (at.equals(not)) at = at.offset(0, 0, 1);
            p.level().setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
            return at;
        });
        ctx.waitTicks(5);
        return pos;
    }

    /** Opens the chest as a player does, by using it, so Inventory Plus knows which chest it is. */
    private void open(BlockPos pos) {
        ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        ctx.waitTicks(8);
        expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof ContainerScreen), "the chest at " + pos + " is open");
    }

    private static String coords(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }

    /** The chest's slot {@code i} on the open screen. */
    private static Slot chestSlot(net.minecraft.world.inventory.AbstractContainerMenu menu, int i) {
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory) && s.getContainerSlot() == i) return s;
        }
        throw new AssertionError("World switch: no chest slot " + i + " on the open screen");
    }

    /** The key Inventory Plus judges the open chest's slot {@code i} by. */
    private String key(int i) {
        return ctx.computeOnClient(mc -> LockedSlots.probeContainerKey(chestSlot(mc.player.containerMenu, i)));
    }

    private boolean locked(int i) {
        return ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, i)));
    }

    /** The cursor over the chest's slot {@code i}. */
    private void hover(int i) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
            Slot s = chestSlot(screen.getMenu(), i);
            double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{(ScreenLayout.leftPos(screen) + s.x + 8) * scale,
                    (ScreenLayout.topPos(screen) + s.y + 8) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        expect(ctx.computeOnClient(mc -> ScreenLayout.hoveredSlot((AbstractContainerScreen<?>) mc.gui.screen())
                == chestSlot(mc.player.containerMenu, i)), "the mouse is over the chest's slot " + i);
    }

    private void press() {
        ctx.getInput().pressKey(IPKeybinds.LOCK_SLOT);
        ctx.waitTicks(4);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("World switch: " + what);
    }
}
