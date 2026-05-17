package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Locked-Slots-related overlays on top of vanilla slot rendering:
 *
 * <ol>
 *   <li><b>Gray edit-mode overlay</b> on inv + hotbar slots when
 *       {@link LockEditMode} is on. Visually reinforces "no item
 *       interactions in this slot during edit mode" (Trev 2026-05-16).</li>
 *   <li><b>Lock icon</b> on locked player slots (any of hotbar / main /
 *       armor / offhand). 5×6 PNG at the top-right with 1 px inset.</li>
 * </ol>
 *
 * <p>Render order: overlay first, then icon — so the lock icon stays
 * visible on top of the gray overlay during edit mode.
 *
 * <p>Vanilla's {@code renderContents} pushes a matrix translated by
 * {@code (leftPos, topPos)} before calling {@code renderSlots};
 * inside {@code renderSlot}, drawing at {@code (slot.x, slot.y)}
 * already lands at screen-space.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenRenderSlotMixin {

    private static final Identifier INVENTORYPLUS$LOCK_ICON =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_slot.png");

    private static final int INVENTORYPLUS$ICON_W = 5;
    private static final int INVENTORYPLUS$ICON_H = 6;

    /** 50%-translucent light gray for the edit-mode overlay. */
    private static final int INVENTORYPLUS$EDIT_OVERLAY_COLOR = 0x80808080;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void inventoryplus$renderOverlays(GuiGraphics graphics, Slot slot,
                                              int mouseX, int mouseY, CallbackInfo ci) {
        // 1. Edit-mode gray overlay on inv + hotbar slots only.
        if (LockEditMode.isOn() && LockedSlots.isInvOrHotbarSlot(slot)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                    INVENTORYPLUS$EDIT_OVERLAY_COLOR);
        }

        // 2. Lock icon on top-right of locked player slots.
        if (LockedSlots.isLockedSlot(slot)) {
            int iconX = slot.x + 16 - INVENTORYPLUS$ICON_W - 1;
            int iconY = slot.y + 1;
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    INVENTORYPLUS$LOCK_ICON,
                    iconX, iconY,
                    /*u=*/ 0f, /*v=*/ 0f,
                    INVENTORYPLUS$ICON_W, INVENTORYPLUS$ICON_H,
                    INVENTORYPLUS$ICON_W, INVENTORYPLUS$ICON_H);
        }
    }
}
