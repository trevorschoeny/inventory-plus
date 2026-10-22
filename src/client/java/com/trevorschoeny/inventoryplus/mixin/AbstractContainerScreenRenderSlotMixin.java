package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockgroups.LockColours;
import com.trevorschoeny.inventoryplus.lockgroups.LockGroup;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the lock and Column Cycler marks on top of vanilla slot rendering:
 *
 * <ol>
 *   <li><b>Lock icon</b> on locked slots: a 5x6 PNG at the top right, inset
 *       1 px from the slot's right edge.</li>
 *   <li><b>Cycle icon</b> on cycle slots: a 6x6 PNG. When the slot is also
 *       locked, it sits 1 px left of the lock icon so the two don't touch.
 *       Otherwise it takes the lock's top-right position.</li>
 *   <li><b>Locked-item mark</b> on any stack the player has protected by
 *       type: a 5x5 circle at the top left, hollow for a lock by item id
 *       and solid for an exact lock.</li>
 * </ol>
 *
 * <p>The item mark takes the top-left corner because the lock icon has
 * always drawn at the top right and the cycle icon shares that corner. A
 * locked item in a locked slot so shows both marks without collision.
 *
 * <p>Render order is lock icon, then cycle icon. The cycle icon's x position
 * depends on whether the lock icon is drawn this frame, and is computed
 * inline rather than carried as state.
 *
 * <p>Vanilla's {@code renderContents} pushes a matrix translated by
 * {@code (leftPos, topPos)} before calling {@code renderSlots}, so inside
 * {@code renderSlot}, drawing at {@code (slot.x, slot.y)} already lands in
 * screen space.
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

    /** Hollow circle: locked by item id. Solid: locked exactly. Both 5x5. */
    private static final Identifier INVENTORYPLUS$ITEM_LOCK_ID =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_item_id.png");
    private static final Identifier INVENTORYPLUS$ITEM_LOCK_EXACT =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_item_exact.png");
    private static final int INVENTORYPLUS$ITEM_MARK_SIZE = 5;

    /**
     * ARGB tint for the corner indicator sprites: alpha 0xAB (67%) and white,
     * so no color change. Applied through the color overload of
     * {@link GuiGraphicsExtractor#blit} so the icons read as hints rather than
     * dominating the slot content.
     */
    private static final int INVENTORYPLUS$INDICATOR_TINT = 0xABFFFFFF;

    @Inject(method = "extractSlot", at = @At("TAIL"))   // 26.2 extract/draw rename
    private void inventoryplus$renderOverlays(GuiGraphicsExtractor graphics, Slot slot,
                                              int mouseX, int mouseY, CallbackInfo ci) {
        // The Lock groups switch pauses every lock: nothing blocked, nothing drawn.
        boolean locksOn = IPConfig.lockGroupsEnabled();
        boolean locked = locksOn && LockedSlots.isLockedSlot(slot);
        boolean cycle = ColumnCycler.isCycleSlot(slot);
        // A cycle slot's mark is the cycle mark. It carries no lock of its
        // own (its cycler has a reach group instead). A real Slot lock put on
        // one with L draws its padlock beside the cycle mark.

        // Lock icon, in the color of the slot's lock group. A shared lock
        // is Slot lock.
        if (locked) {
            int iconX = slot.x + 16 - INVENTORYPLUS$LOCK_ICON_W - 1;
            int iconY = slot.y + 1;
            String stored = LockedSlots.storedGroup(slot);
            LockGroup group = Reach.resolve(stored == null ? Reach.SLOT_LOCK : stored, LockKind.SLOT);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    INVENTORYPLUS$LOCK_ICON,
                    iconX, iconY,
                    /*u=*/ 0f, /*v=*/ 0f,
                    INVENTORYPLUS$LOCK_ICON_W, INVENTORYPLUS$LOCK_ICON_H,
                    INVENTORYPLUS$LOCK_ICON_W, INVENTORYPLUS$LOCK_ICON_H,
                    LockColours.argb(group));
            // Hovering exactly over the padlock names its group: the padlock's
            // pixels and no more, so it never competes with the item tooltip.
            inventoryplus$padlockTooltip(graphics, iconX, iconY, group);
        }

        // Cycle icon. Sits left of the lock icon when the slot also carries a
        // real lock.
        if (cycle) {
            boolean lockIconAlsoShown = locked;
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

        // Locked-item mark, top left. Every matching stack carries it, not just
        // the first one found, since every one of them is protected. It is in
        // its group's color, and an exact lock wins over an item one as the
        // more specific rule.
        ItemStack stack = slot.getItem();
        String exact = locksOn && !stack.isEmpty() ? LockedItems.exactGroup(stack) : null;
        String byId = locksOn && !stack.isEmpty() ? LockedItems.itemGroup(stack) : null;
        if (exact != null || byId != null) {
            Identifier mark = exact != null
                    ? INVENTORYPLUS$ITEM_LOCK_EXACT
                    : INVENTORYPLUS$ITEM_LOCK_ID;
            LockGroup group = exact != null ? Reach.resolve(exact, LockKind.EXACT) : Reach.resolve(byId, LockKind.ITEM);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    mark,
                    slot.x + 1, slot.y + 1,
                    /*u=*/ 0f, /*v=*/ 0f,
                    INVENTORYPLUS$ITEM_MARK_SIZE, INVENTORYPLUS$ITEM_MARK_SIZE,
                    INVENTORYPLUS$ITEM_MARK_SIZE, INVENTORYPLUS$ITEM_MARK_SIZE,
                    LockColours.argb(group));
        }
    }

    /**
     * Sets a tooltip naming the padlock's group when the cursor is on the
     * padlock's own pixels. The slot is drawn in screen-frame coordinates, so
     * the padlock's screen position adds the frame's top-left.
     */
    @Unique
    private void inventoryplus$padlockTooltip(GuiGraphicsExtractor graphics, int iconX, int iconY, LockGroup group) {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerScreenAccessor frame = (AbstractContainerScreenAccessor) this;
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        int x = frame.inventoryPlus$getLeftPos() + iconX;
        int y = frame.inventoryPlus$getTopPos() + iconY;
        if (mx < x || mx >= x + INVENTORYPLUS$LOCK_ICON_W || my < y || my >= y + INVENTORYPLUS$LOCK_ICON_H) return;
        graphics.setTooltipForNextFrame(mc.font, Component.literal("Locked: " + group.name()), (int) mx, (int) my);
    }
}
