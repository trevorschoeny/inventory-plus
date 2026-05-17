package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

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
 * Draws the locked-slot indicator icon on top of any locked player
 * slot in any {@link AbstractContainerScreen} that exposes the slot.
 *
 * <h3>Position</h3>
 *
 * Per Trev 2026-05-16: top-right of the slot, 1 px from the top and
 * 1 px from the right. Icon is 5×6; slot is 16×16:
 * <ul>
 *   <li>{@code iconX = slot.x + 16 - 5 - 1 = slot.x + 10}</li>
 *   <li>{@code iconY = slot.y + 1}</li>
 * </ul>
 *
 * <h3>Z-order</h3>
 *
 * Injected at TAIL of {@code renderSlot} — fires AFTER vanilla
 * renders the item, count, and any vanilla overlay. The icon
 * therefore renders <i>on top</i> of the item (not hidden by it),
 * per Trev's spec.
 *
 * <p>Vanilla's {@code renderContents} pushes a matrix translated by
 * {@code (leftPos, topPos)} before calling {@code renderSlots}; inside
 * {@code renderSlot}, drawing at {@code (slot.x, slot.y)} already lands
 * at the slot's screen-space position. No extra offset needed.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenRenderSlotMixin {

    private static final Identifier INVENTORYPLUS$LOCK_ICON =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_slot.png");

    /** PNG dimensions — 5 wide × 6 tall. Keep in sync with the asset file. */
    private static final int INVENTORYPLUS$ICON_W = 5;
    private static final int INVENTORYPLUS$ICON_H = 6;

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void inventoryplus$renderLockIcon(GuiGraphics graphics, Slot slot,
                                              int mouseX, int mouseY, CallbackInfo ci) {
        if (!LockedSlots.isLockedSlot(slot)) return;

        // Slot is 16×16. Icon (5×6) sits at top-right with 1 px inset.
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
