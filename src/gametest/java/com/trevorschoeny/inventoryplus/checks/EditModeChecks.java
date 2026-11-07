package com.trevorschoeny.inventoryplus.checks;

import static com.trevorschoeny.inventoryplus.checks.CarriedItems.clear;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.count;
import static com.trevorschoeny.inventoryplus.checks.CarriedItems.onServer;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.editmode.EditMode;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.inventoryplus.carried.BoxSettingsPopup;
import com.trevorschoeny.inventoryplus.rules.BoxSettings;
import com.trevorschoeny.inventoryplus.rules.Reservations;
import com.trevorschoeny.inventoryplus.editmode.ReservePopup;
import com.trevorschoeny.inventoryplus.sort.SortButton;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;
import com.trevlar.menukit.api.panel.Reference;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;
import com.trevlar.menukit.api.element.ItemMatch;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.component.ItemContainerContents;
import com.trevlar.slotkit.api.item.ItemContents;
import com.trevlar.slotkit.api.item.ItemPath;
import com.trevlar.slotkit.api.item.ItemRef;

import java.util.List;

/**
 * Edit mode on a chest and on a shulker box. Each guarded click is one vanilla would
 * act on (a left-click on a full stack picks it up, Q on it throws one), so the mode is
 * the only thing that can stop it; the last click, out of the mode, shows vanilla's.
 * <ul>
 *   <li>Entering puts the cursor's item back in the inventory.</li>
 *   <li>L locks a slot (hold it and sweep for several); R opens SlotKit's reserve popup for it,
 *       prefilled from what the slot holds. Neither moves the stack, and a click does nothing.</li>
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
            openedBox(world);
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
        pressEditButton();
        expect(ctx.computeOnClient(mc -> EditMode.isOn() && mc.player.containerMenu.getCarried().isEmpty()),
                "the pencil turns edit mode on and the cursor is empty");
        ctx.waitTicks(5);
        onServer(world, p -> expect(count(p, Items.DIRT) == 5 && p.containerMenu.getCarried().isEmpty(),
                "the dirt is back in the inventory"));

        // A left-click on a full stack does nothing: no lock, nothing picked up.
        click(0, InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> !LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 0))
                        && mc.player.containerMenu.getCarried().isEmpty()),
                "a left-click in edit mode neither locks nor picks up");

        // L locks, and the stone stays; again unlocks.
        key(0, IPKeybinds.EDIT_LOCK);
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 0))
                        && mc.player.containerMenu.getCarried().isEmpty()),
                "L locks slot 0 and picks nothing up");
        onServer(world, p -> {
            Container chest = (Container) p.level().getBlockEntity(at[0]);
            expect(SlotLocks.CHANNEL.get(new Slot(chest, 0, 0, 0)) && chest.getItem(0).getCount() == 64,
                    "the chest holds the lock and keeps its stone");
        });
        key(0, IPKeybinds.EDIT_LOCK);
        ctx.waitTicks(5);
        expect(!ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 0))),
                "a second L unlocks it");

        // Hold L on slot 3 and sweep across 4 and 5: all three lock; then sweep them off.
        for (boolean lock : new boolean[] {true, false}) {
            hover(3);
            ctx.getInput().holdKey(IPKeybinds.EDIT_LOCK);
            ctx.waitTicks(3);
            hover(4);
            ctx.waitTicks(3);
            hover(5);
            ctx.waitTicks(3);
            ctx.getInput().releaseKey(IPKeybinds.EDIT_LOCK);
            ctx.waitTicks(3);
            expect(ctx.computeOnClient(mc -> {
                        AbstractContainerMenu menu = mc.player.containerMenu;
                        return LockedSlots.isLockedSlot(chestSlot(menu, 3)) == lock
                                && LockedSlots.isLockedSlot(chestSlot(menu, 4)) == lock
                                && LockedSlots.isLockedSlot(chestSlot(menu, 5)) == lock;
                    }), lock ? "holding L and sweeping locks every slot passed" : "sweeping again unlocks them");
        }

        // R opens the reserve popup, prefilled with what slot 1 holds; it picks nothing
        // up. Done (the picker's own button, called directly here) reserves it.
        key(1, IPKeybinds.EDIT_RESERVE);
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> ReservePopup.isOpen()
                        && ReservePopup.draft().matches(new ItemStack(Items.STONE))
                        && mc.player.containerMenu.getCarried().isEmpty()),
                "R opens the reserve popup, prefilled with the slot's stone, and picks nothing up");
        ctx.runOnClient(mc -> ReservePopup.done());
        ctx.waitTicks(5);
        onServer(world, p -> {
            Container chest = (Container) p.level().getBlockEntity(at[0]);
            ItemMatch r = Reservations.get(SlotRef.of(chest, 1, null));
            expect(!Reservations.isNone(r) && r.matches(new ItemStack(Items.STONE)) && chest.getItem(1).getCount() == 64,
                    "Done reserves slot 1 for stone and leaves the stone (" + r + ")");
        });

        // A second R reopens it on the existing reservation; Remove clears it.
        key(1, IPKeybinds.EDIT_RESERVE);
        ctx.waitTicks(5);
        expect(ctx.computeOnClient(mc -> ReservePopup.isOpen()), "a second R reopens the popup");
        ctx.runOnClient(mc -> ReservePopup.remove());
        ctx.waitTicks(5);
        onServer(world, p -> expect(Reservations.get(SlotRef.of((Container) p.level().getBlockEntity(at[0]), 1, null)).equals(Reservations.NONE),
                "Remove clears the reservation"));

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

    /** A placed box's settings: its popup opens from its toolbar outside edit mode, and what it sets holds on the box. */
    private void box(TestSingleplayerContext world) {
        BlockPos[] at = {null};
        onServer(world, p -> {
            clear(p);
            at[0] = p.blockPosition().offset(-2, 0, 0);
            p.level().setBlockAndUpdate(at[0], Blocks.SHULKER_BOX.defaultBlockState());
            ((Container) p.level().getBlockEntity(at[0])).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        });
        open(at[0]);
        // The box settings button, leftmost on the box's toolbar, with edit mode off.
        ctx.runOnClient(mc -> EditMode.set(false));
        pressBoxSettingsButton();
        expect(ctx.computeOnClient(mc -> BoxSettingsPopup.isOpen()), "the box settings button opens the popup outside edit mode");
        // Its controls, clicked with the real mouse: Before your inventory, then Only matching.
        BoxSettings beforeMatching = new BoxSettings(BoxSettings.When.BEFORE, true);
        clickPopup(BoxSettingsPopup.Control.BEFORE);
        expect(ctx.computeOnClient(mc -> BoxSettings.of(mc.player.containerMenu, chestSlot(mc.player.containerMenu, 4)))
                        .equals(new BoxSettings(BoxSettings.When.BEFORE, false)),
                "clicking Before your inventory sets it, and the popup reads it back");
        clickPopup(BoxSettingsPopup.Control.MATCHING_ONLY);
        expect(ctx.computeOnClient(mc -> BoxSettings.of(mc.player.containerMenu, chestSlot(mc.player.containerMenu, 4)))
                        .equals(beforeMatching),
                "clicking Only items already in this box sets it");
        onServer(world, p -> expect(BoxSettings.of(p.containerMenu, chestSlot(p.containerMenu, 4)).equals(beforeMatching),
                "the server's box holds what the popup shows"));
        ctx.getInput().pressKey(InputConstants.getKey("key.keyboard.escape"));
        ctx.waitTicks(2);
        expect(ctx.computeOnClient(mc -> !BoxSettingsPopup.isOpen() && mc.gui.screen() != null),
                "Escape closes the popup and leaves the box open");
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
        onServer(world, p -> {
            p.gameMode.destroyBlock(at[0]);
            ItemEntity dropped = p.level().getEntitiesOfClass(ItemEntity.class, new AABB(at[0]).inflate(2)).stream()
                    .filter(e -> BoxSettings.isBox(e.getItem())).findFirst().orElse(null);
            expect(dropped != null, "breaking the box drops it");
            expect(BoxSettings.of(dropped.getItem()).equals(beforeMatching),
                    "the broken box carries its BoxSettings (" + BoxSettings.of(dropped.getItem()) + ")");
            // Carried: the box goes into the inventory, and real pickups follow its settings.
            p.getInventory().setItem(9, dropped.getItem().copy());
            dropped.discard();
            drop(p, new ItemStack(Items.COBBLESTONE, 5));
            drop(p, new ItemStack(Items.DIRT, 3));
        });
        ctx.waitTicks(20);
        onServer(world, p -> {
            ItemStack box = p.getInventory().getItem(9);
            int boxCobble = 0, boxDirt = 0;
            for (ItemStack s : box.getOrDefault(DataComponents.CONTAINER,
                    ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList()) {
                if (s.is(Items.COBBLESTONE)) boxCobble += s.getCount();
                if (s.is(Items.DIRT)) boxDirt += s.getCount();
            }
            expect(boxCobble == 15 && count(p, Items.COBBLESTONE) == 0,
                    "a picked-up cobblestone goes into the box set Before + Only matching (box holds " + boxCobble + ")");
            expect(boxDirt == 0 && count(p, Items.DIRT) == 3,
                    "a picked-up dirt, which the box doesn't hold, stays out of it (box holds " + boxDirt + ")");
        });
    }

    /**
     * A box opened from the inventory, as Open Anywhere opens one: the screen's container
     * names its box item ({@link ItemContents}), so its settings are the item's. The box
     * was set before (Reach's migration sets carried boxes so), and the popup has to show
     * and change those, not a blank slot's.
     */
    private void openedBox(TestSingleplayerContext world) {
        BoxSettings beforeMatching = new BoxSettings(BoxSettings.When.BEFORE, true);
        onServer(world, p -> {
            clear(p);
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            items.set(0, new ItemStack(Items.COBBLESTONE, 10));
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            BoxSettings.set(box, beforeMatching);
            p.getInventory().setItem(9, box);
            p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ShulkerBoxMenu(id, inv, new OpenedBox(box, 9)),
                    Component.literal("Opened box")));
        });
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> EditMode.set(false));
        pressBoxSettingsButton();
        expect(ctx.computeOnClient(mc -> BoxSettingsPopup.isOpen()), "the opened box's settings button opens the popup");
        expect(ctx.computeOnClient(mc -> BoxSettings.of(mc.player.containerMenu, chestSlot(mc.player.containerMenu, 0)))
                        .equals(beforeMatching),
                "the popup shows the opened box's own settings, Before + Only matching ("
                        + ctx.computeOnClient(mc -> BoxSettings.of(mc.player.containerMenu, chestSlot(mc.player.containerMenu, 0))) + ")");
        clickPopup(BoxSettingsPopup.Control.AFTER);
        onServer(world, p -> expect(BoxSettings.of(p.getInventory().getItem(9))
                        .equals(new BoxSettings(BoxSettings.When.AFTER, true)),
                "clicking After changes only when, on the box item (" + BoxSettings.of(p.getInventory().getItem(9)) + ")"));
        expect(ctx.computeOnClient(mc -> BoxSettings.of(mc.player.containerMenu, chestSlot(mc.player.containerMenu, 0)))
                        .equals(new BoxSettings(BoxSettings.When.AFTER, true)),
                "the popup reads the change back");
        ctx.getInput().pressKey(InputConstants.getKey("key.keyboard.escape"));
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
    }

    /** An opened box's container, as an opener mod declares it: the box's slots, naming the box at inventory slot {@code slot}. */
    private static final class OpenedBox extends SimpleContainer implements ItemContents {
        private final int slot;

        OpenedBox(ItemStack box, int slot) {
            super(27);
            this.slot = slot;
            box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(getItems());
        }

        @Override
        public ItemPath itemPath(Player player) {
            return new ItemPath(ItemRef.Source.INVENTORY, slot, List.of());
        }
    }

    /** The box settings button, leftmost on the box's toolbar, pressed with the real mouse. */
    private void pressBoxSettingsButton() {
        double[] button = ctx.computeOnClient(mc -> {
            Reference r = Toolbar.externalToolbars().stream()
                    .filter(t -> t.id().equals("inventoryplus.toolbar.external.shulker_storage"))
                    .findFirst().orElseThrow().placedBounds();
            expect(r != null, "the box's toolbar shows");
            double scale = mc.getWindow().getGuiScale();
            return new double[] {(r.leftPos() + 4.5) * scale, (r.topPos() + r.imageHeight() / 2.0) * scale};
        });
        ctx.getInput().setCursorPos(button[0], button[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    /** Drops {@code stack} at the player's feet with no pickup delay, as a mined block's drop lands. */
    private static void drop(net.minecraft.server.level.ServerPlayer p, ItemStack stack) {
        ItemEntity e = new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), stack);
        e.setNoPickUpDelay();
        p.level().addFreshEntity(e);
    }

    /** One of the box settings popup's controls, clicked with the real mouse. */
    private void clickPopup(BoxSettingsPopup.Control control) {
        double[] at = ctx.computeOnClient(mc -> {
            double[] c = BoxSettingsPopup.centerOf(control);
            expect(c != null, "the box settings popup is placed");
            double scale = mc.getWindow().getGuiScale();
            return new double[] {c[0] * scale, c[1] * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
    }

    // Helpers

    /** Opens the container as a player does, by using it. */
    private void open(BlockPos pos) {
        ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        ctx.waitTicks(8);
        expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>), "the container at " + pos + " is open");
    }

    /**
     * Chest slot {@code i} clicked with mouse {@code button} as the player clicks it: the real
     * cursor moved over it, then a real press and release, so the click goes the whole way
     * the game sends it (MouseHandler, every mod's hooks, the screen) with that slot hovered.
     * {@code button} is an {@link InputConstants} constant: buttons are numbered from 1 here,
     * and a check that sent bare 0 and 1 once passed on code that read them as left and right.
     */
    private void click(int i, int button) {
        hover(i);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(2);
    }

    /** Edit mode's {@code key} pressed with the real cursor over chest slot {@code i}. */
    private void key(int i, KeyMapping key) {
        hover(i);
        ctx.getInput().pressKey(key);
        ctx.waitTicks(2);
    }

    /** The chest toolbar's pencil, left of Sort, pressed with the real mouse. */
    private void pressEditButton() {
        double[] at = ctx.computeOnClient(mc -> {
            Reference r = Toolbar.externalToolbars().stream()
                    .filter(t -> t.id().equals("inventoryplus.toolbar.external.chest_storage"))
                    .findFirst().orElseThrow().placedBounds();
            expect(r != null, "the chest's toolbar shows");
            double scale = mc.getWindow().getGuiScale();
            return new double[] {(r.leftPos() + r.imageWidth() - SortButton.SIZE - Toolbar.BUTTON_GAP - SortButton.SIZE / 2.0) * scale,
                    (r.topPos() + r.imageHeight() / 2.0) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
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
