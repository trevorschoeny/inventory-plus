package com.trevorschoeny.inventoryplus.checks;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.editmode.EditMode;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.inventoryplus.sort.SortButton;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.slotkit.api.reserve.ReservationEditor;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import testgallery.SceneWorld;

/**
 * Edit mode in the slot-rules scene Trev walks, as he walks it: the real cursor, mouse and
 * keys on station 2's chest. The pencil turns it on, L over the cobblestone locks it, R
 * opens the reserve popup.
 */
public class EditModeSceneChecks implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "inventory-plus", "slot-rules")) {
            JsonObject st = scene.stations().get(1).getAsJsonObject();
            scene.standAt(st, 30);
            JsonArray o = st.getAsJsonArray("origin");
            BlockPos chest = new BlockPos(o.get(0).getAsInt() + 2, o.get(1).getAsInt(), o.get(2).getAsInt() + 2);
            // Walked up to it, within reach, as Trev does.
            scene.world().getServer().runCommand("tp @p " + (chest.getX() + 0.5) + " " + chest.getY() + " " + (chest.getZ() - 1.5) + " 0 30");
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false)));
            ctx.waitTicks(10);
            expect(ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>), "the chest opens");

            double[] pencil = ctx.computeOnClient(mc -> {
                Reference r = Toolbar.externalToolbars().stream()
                        .filter(t -> t.id().equals("inventoryplus.toolbar.external.chest_storage"))
                        .findFirst().orElseThrow().placedBounds();
                expect(r != null, "the chest's toolbar shows");
                double scale = mc.getWindow().getGuiScale();
                return new double[] {(r.leftPos() + r.imageWidth() - SortButton.SIZE - Toolbar.BUTTON_GAP - SortButton.SIZE / 2.0) * scale,
                        (r.topPos() + r.imageHeight() / 2.0) * scale};
            });
            ctx.getInput().setCursorPos(pencil[0], pencil[1]);
            ctx.waitTicks(2);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(2);
            expect(ctx.computeOnClient(mc -> EditMode.isOn()), "the pencil turns edit mode on");

            double[] cobble = ctx.computeOnClient(mc -> {
                AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) mc.gui.screen();
                Slot s = chestSlot(mc.player.containerMenu, 0);
                double scale = mc.getWindow().getGuiScale();
                return new double[] {(a.inventoryPlus$getLeftPos() + s.x + 8) * scale, (a.inventoryPlus$getTopPos() + s.y + 8) * scale};
            });
            ctx.getInput().setCursorPos(cobble[0], cobble[1]);
            ctx.waitTicks(2);
            ctx.getInput().pressKey(IPKeybinds.EDIT_LOCK);
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(chestSlot(mc.player.containerMenu, 0))
                            && mc.player.containerMenu.getCarried().isEmpty()),
                    "L on the cobblestone locks it and picks nothing up");
            // R opens SlotKit's reserve popup for it.
            ctx.getInput().pressKey(IPKeybinds.EDIT_RESERVE);
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> ReservationEditor.isOpen() && mc.player.containerMenu.getCarried().isEmpty()),
                    "R opens the reserve popup and picks nothing up");
            ctx.runOnClient(mc -> ReservationEditor.close());
            ctx.runOnClient(mc -> mc.player.closeContainer());
        }
    }

    private static Slot chestSlot(AbstractContainerMenu menu, int i) {
        for (Slot s : menu.slots) if (!(s.container instanceof Inventory) && s.getContainerSlot() == i) return s;
        throw new AssertionError("Edit mode scene: no container slot " + i);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Edit mode scene: " + what);
    }
}
