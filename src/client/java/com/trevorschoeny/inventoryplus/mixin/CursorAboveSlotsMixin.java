package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The cursor rests above the slots when the survival inventory opens. Opening a
 * screen from gameplay releases the grabbed mouse and puts the cursor at the window's
 * centre, and the exact centre of the survival inventory is a slot in its top row: if
 * that slot holds an item with a long tooltip, a big tooltip is the first thing you
 * see. This moves the cursor up {@link #OFFSET_GUI_PX} GUI pixels, onto the plain
 * panel above the row, so nothing is hovered on open. Ported from Dock and Hide
 * Tooltips (main 5a71bb3), under a Tooltips tab setting instead of a hardcoded on.
 *
 * <p>Only from gameplay: {@code releaseMouse} returns early unless the mouse was
 * grabbed, so moving between screens never reaches the call this changes. Gui
 * assigns the new screen before it releases the mouse, so the open screen is the one
 * being opened. Survival inventory only ({@link InventoryScreen}; the creative screen
 * is another class): in a chest or the creative tab the centre is still a slot a few
 * pixels up, so the move wouldn't help there.
 */
@Mixin(MouseHandler.class)
public abstract class CursorAboveSlotsMixin {

    /**
     * How far above centre the cursor starts, in GUI pixels, so it scales with GUI
     * scale. Calibration: the survival inventory is 176 x 166 GUI px, so its centre is
     * (88, 83). The top row's slots start at y 84 and a slot counts as hovered from 1 px
     * outside it, so the centre hovers the middle slot of that row. 3 up puts the cursor
     * at y 80, on the "Inventory" label (y 72 to 80), 3 px clear of the row. 6 proved too
     * high in game. Raise it if a window size still lands on a slot (rounding of an odd
     * height moves the centre by up to 1 px); lower it if it ever reaches the crafting
     * grid's frame.
     */
    private static final int OFFSET_GUI_PX = 3;

    @Shadow @Final private Minecraft minecraft;
    @Shadow private double ypos;

    @ModifyArg(method = "releaseMouse",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/InputConstants;releaseMouse(Lcom/mojang/blaze3d/platform/Window;DD)V"),
            index = 2)
    private double inventoryplus$cursorAboveSlots(double y) {
        if (!IPConfig.tooltipsEnabled() || !IPConfig.tooltipCursorAboveSlots()) return y;
        if (!(minecraft.gui.screen() instanceof InventoryScreen)) return y;
        // GUI pixels to window (screen) pixels: the window's height over its scaled height.
        Window window = minecraft.getWindow();
        double moved = y - OFFSET_GUI_PX * (double) window.getScreenHeight() / window.getGuiScaledHeight();
        // Keep MouseHandler's own position in step with where the cursor is put.
        ypos = moved;
        return moved;
    }
}
