package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.inventoryplus.editmode.EditMode;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;
import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Edit mode on a chest and on a shulker box. Each guarded click is one vanilla would
 * act on (a left-click on a full stack picks it up, Q on it throws one), so the mode is
 * the only thing that can stop it; the last click, out of the mode, shows vanilla's.
 * <ul>
 *   <li>Entering puts the cursor's item back in the inventory.</li>
 *   <li>Left-click locks a slot and right-click reserves it, and neither moves the stack.</li>
 *   <li>Q on a slot does nothing in the mode.</li>
 *   <li>Escape leaves the mode and keeps the screen open.</li>
 *   <li>A placed box's Intake, set from its screen, holds on the box and comes off with
 *       it when it is broken.</li>
 * </ul>
 */
public class EditModeChecks implements FabricClientGameTest {

    private ClientGameTestContext ctx;

    @Override
    public void runTest(ClientGameTestContext context) {
        ctx = context;
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(20);
            world.getServer().runCommand("gamemode survival @a");
            chest(world);
            box(world);
        }
    }

    private void chest(TestSingleplayerContext world) {
        BlockPos[] at = {null};
        onServer(world, p -> {
            clear(p);
            p.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
            at[0] = p.blockPosition().offset(2, 0, 0);
            p.level().setBlockAndUpdate(at[0], Blocks.CHEST.defaultBlockState());
            Container chest = (Container) p.level().getBlockEntity(at[0]);
            for (int i = 0; i < 3; i++) chest.setItem(i, new ItemStack(Items.STONE, 64));
        });
        open(at[0]);

        // Dirt on the cursor, then into edit mode: it goes back to the inventory.
        ctx.runOnClient(mc -> {
            AbstractContainerMenu menu = mc.player.containerMenu;
            mc.gameMode.handleContainerInput(menu.containerId, inventorySlot(menu, 0).index, 0, ContainerInput.PICKUP, mc.player);
        });
        expect(ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().is(Items.DIRT)), "the dirt is on the cursor");
        ctx.runOnClient(mc -> EditMode.set(true));
        expect(ctx.computeOnClient(mc -> EditMode.isOn() && mc.player.containerMenu.getCarried().isEmpty()),
                "edit mode is on and the cursor is empty");
        ctx.waitTicks(5);
        onServer(world, p -> expect(count(p, Items.DIRT) == 5 && p.containerMenu.getCarried().isEmpty(),
                "the dirt is back in the inventory"));

        // Left-click locks, and the stone stays; again unlocks.
        ctx.runOnClient(mc -> click(mc, chestSlot(mc.player.containerMenu, 0), 0));
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 0))
                        && mc.player.containerMenu.getCarried().isEmpty()),
                "a left-click locks slot 0 and picks nothing up");
        onServer(world, p -> {
            Container chest = (Container) p.level().getBlockEntity(at[0]);
            expect(SlotLocks.CHANNEL.get(new Slot(chest, 0, 0, 0)) && chest.getItem(0).getCount() == 64,
                    "the chest holds the lock and keeps its stone");
        });
        ctx.runOnClient(mc -> click(mc, chestSlot(mc.player.containerMenu, 0), 0));
        ctx.waitTicks(5);
        expect(!ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 0))),
                "a second left-click unlocks it");

        // Right-click reserves slot 1 for what it holds, and a second one clears it.
        ctx.runOnClient(mc -> click(mc, chestSlot(mc.player.containerMenu, 1), 1));
        ctx.waitTicks(5);
        onServer(world, p -> {
            Container chest = (Container) p.level().getBlockEntity(at[0]);
            Reservation r = Reservations.get(SlotRef.of(chest, 1, null));
            expect(!r.isNone() && r.matches(new ItemStack(Items.STONE)) && chest.getItem(1).getCount() == 64,
                    "a right-click reserves slot 1 for stone and leaves the stone (" + r + ")");
        });
        expect(ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().isEmpty()), "and picks nothing up");
        ctx.runOnClient(mc -> click(mc, chestSlot(mc.player.containerMenu, 1), 1));
        ctx.waitTicks(5);
        onServer(world, p -> expect(Reservations.get(SlotRef.of((Container) p.level().getBlockEntity(at[0]), 1, null)).isNone(),
                "a second right-click clears it"));

        // Q over slot 2 throws nothing.
        hover(2);
        ctx.getInput().pressKey(o -> o.keyDrop);
        ctx.waitTicks(5);
        onServer(world, p -> expect(((Container) p.level().getBlockEntity(at[0])).getItem(2).getCount() == 64,
                "Q in edit mode throws nothing"));

        // Escape leaves the mode, and the chest stays open.
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(2);
        expect(ctx.computeOnClient(mc -> !EditMode.isOn() && mc.gui.screen() instanceof AbstractContainerScreen<?>),
                "Escape leaves edit mode and the chest stays open");

        // Out of the mode, the same Q throws one: the mode was what held it.
        hover(2);
        ctx.getInput().pressKey(o -> o.keyDrop);
        ctx.waitTicks(5);
        onServer(world, p -> expect(((Container) p.level().getBlockEntity(at[0])).getItem(2).getCount() == 63,
                "out of edit mode, Q throws one"));
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
    }

    /** A placed box's Intake, set from its screen as edit mode's row sets it. */
    private void box(TestSingleplayerContext world) {
        BlockPos[] at = {null};
        onServer(world, p -> {
            clear(p);
            at[0] = p.blockPosition().offset(-2, 0, 0);
            p.level().setBlockAndUpdate(at[0], Blocks.SHULKER_BOX.defaultBlockState());
            ((Container) p.level().getBlockEntity(at[0])).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        });
        open(at[0]);
        ctx.runOnClient(mc -> {
            EditMode.set(true);
            AbstractContainerMenu menu = mc.player.containerMenu;
            ClientPlayNetworking.send(new Intake.SetOpen(menu.containerId, chestSlot(menu, 4).index,
                    new Intake(Intake.When.BEFORE, true)));
        });
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> Intake.of(mc.player.containerMenu, chestSlot(mc.player.containerMenu, 4)))
                        .equals(new Intake(Intake.When.BEFORE, true)),
                "the box's screen shows the Intake just set");
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
        onServer(world, p -> {
            p.gameMode.destroyBlock(at[0]);
            ItemEntity dropped = p.level().getEntitiesOfClass(ItemEntity.class, new AABB(at[0]).inflate(2)).stream()
                    .filter(e -> Intake.isBox(e.getItem())).findFirst().orElse(null);
            expect(dropped != null, "breaking the box drops it");
            expect(Intake.of(dropped.getItem()).equals(new Intake(Intake.When.BEFORE, true)),
                    "the broken box carries its Intake (" + Intake.of(dropped.getItem()) + ")");
        });
    }

    // Helpers

    /** Opens the container as a player does, by using it. */
    private void open(BlockPos pos) {
        ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        ctx.waitTicks(8);
        expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>), "the container at " + pos + " is open");
    }

    /** A press and release at the slot's centre, through the screen's own click. */
    private static void click(Minecraft mc, Slot slot, int button) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        MouseButtonEvent event = new MouseButtonEvent(a.inventoryPlus$getLeftPos() + slot.x + 8,
                a.inventoryPlus$getTopPos() + slot.y + 8, new MouseButtonInfo(button, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    /** Puts the real cursor over chest slot {@code i}, so the screen hovers it. */
    private void hover(int i) {
        double[] at = ctx.computeOnClient(mc -> {
            AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) mc.gui.screen();
            Slot slot = chestSlot(mc.player.containerMenu, i);
            double scale = mc.getWindow().getGuiScale();
            return new double[] {(a.inventoryPlus$getLeftPos() + slot.x + 8) * scale,
                    (a.inventoryPlus$getTopPos() + slot.y + 8) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
    }

    private static Slot chestSlot(AbstractContainerMenu menu, int i) {
        for (Slot s : menu.slots) if (!(s.container instanceof Inventory) && s.getContainerSlot() == i) return s;
        throw new AssertionError("Edit mode: no container slot " + i + " on the open screen");
    }

    private static Slot inventorySlot(AbstractContainerMenu menu, int i) {
        for (Slot s : menu.slots) if (s.container instanceof Inventory && s.getContainerSlot() == i) return s;
        throw new AssertionError("Edit mode: no inventory slot " + i + " on the open screen");
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Edit mode: " + what);
    }
}
