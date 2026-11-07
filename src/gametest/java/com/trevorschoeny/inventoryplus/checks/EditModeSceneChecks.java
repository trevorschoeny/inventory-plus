package com.trevorschoeny.inventoryplus.checks;

import com.trevorschoeny.inventoryplus.editmode.ReservePopup;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.editmode.EditMode;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.inventoryplus.sort.SortButton;
import com.trevorschoeny.inventoryplus.toolbar.Toolbar;
import com.trevlar.menukit.api.panel.Reference;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import testgallery.SceneWorld;

/**
 * Edit mode in the slot-rules scene, played as a player would: the real cursor, mouse and
 * keys on the inventory screen at station 1. The pencil turns it on, L over the hotbar's
 * cobblestone locks it, R opens the reserve popup.
 */
public class EditModeSceneChecks implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (SceneWorld.Opened scene = SceneWorld.open(ctx, "inventory-plus", "slot-rules")) {
            JsonObject st = scene.stations().get(0).getAsJsonObject();
            scene.standAt(st, 30);
            scene.world().getServer().runCommand("item replace entity @p hotbar.0 with minecraft:cobblestone 32");
            ctx.waitTicks(10);
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitTicks(5);

            double[] pencil = ctx.computeOnClient(mc -> {
                Reference r = Toolbar.inventoryToolbar().placedBounds();
                expect(r != null, "the inventory's toolbar shows");
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
                Slot s = hotbarSlot(mc.player.containerMenu, 0);
                double scale = mc.getWindow().getGuiScale();
                return new double[] {(a.inventoryPlus$getLeftPos() + s.x + 8) * scale, (a.inventoryPlus$getTopPos() + s.y + 8) * scale};
            });
            ctx.getInput().setCursorPos(cobble[0], cobble[1]);
            ctx.waitTicks(2);
            ctx.getInput().pressKey(IPKeybinds.EDIT_LOCK);
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> LockedSlots.isLockedSlot(hotbarSlot(mc.player.containerMenu, 0))
                            && mc.player.containerMenu.getCarried().isEmpty()),
                    "L on the cobblestone locks it and picks nothing up");
            // R opens the reserve popup for it.
            ctx.getInput().pressKey(IPKeybinds.EDIT_RESERVE);
            ctx.waitTicks(5);
            expect(ctx.computeOnClient(mc -> ReservePopup.isOpen() && mc.player.containerMenu.getCarried().isEmpty()),
                    "R opens the reserve popup and picks nothing up");
            ctx.runOnClient(mc -> ReservePopup.close());
            // The lock is the player's, kept in config: take it off for the checks after this one.
            ctx.runOnClient(mc -> LockedSlots.setLocked(hotbarSlot(mc.player.containerMenu, 0), false));
            ctx.runOnClient(mc -> mc.player.closeContainer());
        }
    }

    private static Slot hotbarSlot(AbstractContainerMenu menu, int i) {
        for (Slot s : menu.slots) if (s.container instanceof Inventory && s.getContainerSlot() == i) return s;
        throw new AssertionError("Edit mode scene: no hotbar slot " + i);
    }

    private static void expect(boolean ok, String what) {
        if (!ok) throw new AssertionError("Edit mode scene: " + what);
    }
}
