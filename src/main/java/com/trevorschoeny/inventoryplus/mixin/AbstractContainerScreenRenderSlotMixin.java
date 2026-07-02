package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerEditMode;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Locked-Slots + Column-Cycler overlays on top of vanilla slot rendering:
 *
 * <ol>
 *   <li><b>Gray edit-mode overlay</b> on inv + hotbar slots when ANY edit
 *       mode is on ({@link LockEditMode} or {@link ColumnCyclerEditMode}).
 *       Both edit modes are mutually exclusive and use the same visual.
 *       Reinforces "no item interactions in this slot during edit mode"
 *       (Trev 2026-05-16).</li>
 *   <li><b>Lock icon</b> on locked player slots — 5×6 PNG at top-right
 *       with 1 px inset from the slot's right edge.</li>
 *   <li><b>Cycle icon</b> on cycle-member slots — 6×6 PNG. When ALSO
 *       locked, the cycle icon sits 1 px LEFT of the lock icon (so the
 *       two corner indicators don't touch). When NOT locked, the cycle
 *       icon takes the lock's top-right position with 1 px inset.</li>
 * </ol>
 *
 * <p>Render order: overlay → lock icon → cycle icon. The cycle icon's
 * x-position depends on whether the lock icon is also being drawn this
 * frame; computed inline rather than carrying state.
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
    private static final Identifier INVENTORYPLUS$CYCLE_ICON =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/cycle_slot_symbol.png");

    private static final int INVENTORYPLUS$LOCK_ICON_W = 5;
    private static final int INVENTORYPLUS$LOCK_ICON_H = 6;
    private static final int INVENTORYPLUS$CYCLE_ICON_W = 6;
    private static final int INVENTORYPLUS$CYCLE_ICON_H = 6;

    /** 50%-translucent light gray for the edit-mode overlay. */
    private static final int INVENTORYPLUS$EDIT_OVERLAY_COLOR = 0x80808080;

    /**
     * ARGB tint for the corner indicator sprites — alpha 0xAB (67%),
     * RGB 0xFFFFFF (white = no color modulation). Applied via the
     * color-overload of {@link GuiGraphicsExtractor#blit} so the icons read
     * as subtle hints rather than dominating the slot content
     * (Trev 2026-05-19).
     */
    private static final int INVENTORYPLUS$INDICATOR_TINT = 0xABFFFFFF;

    @Inject(method = "extractSlot", at = @At("TAIL"))   // 26.2 extract/draw rename
    private void inventoryplus$renderOverlays(GuiGraphicsExtractor graphics, Slot slot,
                                              int mouseX, int mouseY, CallbackInfo ci) {
        // 1. Edit-mode gray overlay. The two edit modes diverge now that
        // lock-edit reaches beyond the player inventory: lock-edit grays every
        // lock-toggleable slot (inv+hotbar + ender + placed containers), while
        // cycler-edit stays scoped to the player inv+hotbar slots it operates on.
        boolean overlay = (LockEditMode.isOn() && LockedSlots.isEditModeToggleable(slot))
                || (ColumnCyclerEditMode.isOn() && LockedSlots.isInvOrHotbarSlot(slot));
        if (overlay) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16,
                    INVENTORYPLUS$EDIT_OVERLAY_COLOR);
        }

        boolean locked = LockedSlots.isLockedSlot(slot);
        boolean cycle = ColumnCycler.isCycleSlot(slot);
        // When the lock-cycle pairing is ON, a cycle slot's lock is
        // implied by the cycle — suppress the lock icon to avoid
        // double-indication. When the pairing is OFF, cycle and lock
        // are independent and both icons render side-by-side.
        boolean suppressLockIcon = cycle && IPConfig.cycleSlotsLocked();

        // 2. Lock icon — top-right of slot, 1 px inset from the right edge.
        if (locked && !suppressLockIcon) {
            int iconX = slot.x + 16 - INVENTORYPLUS$LOCK_ICON_W - 1;
            int iconY = slot.y + 1;
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    INVENTORYPLUS$LOCK_ICON,
                    iconX, iconY,
                    /*u=*/ 0f, /*v=*/ 0f,
                    INVENTORYPLUS$LOCK_ICON_W, INVENTORYPLUS$LOCK_ICON_H,
                    INVENTORYPLUS$LOCK_ICON_W, INVENTORYPLUS$LOCK_ICON_H,
                    INVENTORYPLUS$INDICATOR_TINT);
        }

        // 3. Cycle icon — sits left of the lock icon when BOTH are shown
        // (only happens when cycleSlotsLocked is OFF and both states are
        // independently true). Otherwise takes the top-right slot with
        // 1 px inset from the right edge.
        if (cycle) {
            boolean lockIconAlsoShown = locked && !suppressLockIcon;
            int cycleX = lockIconAlsoShown
                    ? (slot.x + 16 - INVENTORYPLUS$LOCK_ICON_W - 1) - 1 - INVENTORYPLUS$CYCLE_ICON_W
                    : slot.x + 16 - INVENTORYPLUS$CYCLE_ICON_W - 1;
            int cycleY = slot.y + 1;
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    INVENTORYPLUS$CYCLE_ICON,
                    cycleX, cycleY,
                    /*u=*/ 0f, /*v=*/ 0f,
                    INVENTORYPLUS$CYCLE_ICON_W, INVENTORYPLUS$CYCLE_ICON_H,
                    INVENTORYPLUS$CYCLE_ICON_W, INVENTORYPLUS$CYCLE_ICON_H,
                    INVENTORYPLUS$INDICATOR_TINT);
        }
    }
}
