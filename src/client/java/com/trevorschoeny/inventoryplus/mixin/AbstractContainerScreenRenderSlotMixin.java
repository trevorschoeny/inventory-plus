package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerEditMode;
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
 * Renders Locked-Slots + Column-Cycler overlays on top of vanilla slot rendering:
 *
 * <ol>
 *   <li><b>Edit-mode tint</b>, gray, on the slots {@link ColumnCyclerEditMode}
 *       edits. Lock edit mode and its pink tint are gone (Trev, 2026-09-11):
 *       locks are applied with {@code L}.</li>
 *   <li><b>Lock icon</b> on locked player slots — 5×6 PNG at top-right
 *       with 1 px inset from the slot's right edge.</li>
 *   <li><b>Cycle icon</b> on cycle-member slots — 6×6 PNG. When ALSO
 *       locked, the cycle icon sits 1 px LEFT of the lock icon (so the
 *       two corner indicators don't touch). When NOT locked, the cycle
 *       icon takes the lock's top-right position with 1 px inset.</li>
 *   <li><b>Locked-item mark</b> on any stack the player has protected by
 *       type — 5×5 circle at the top-LEFT, hollow for a lock by item id
 *       and solid for an exact lock.</li>
 * </ol>
 *
 * <h3>Which corner the item mark takes</h3>
 *
 * <p>{@code plans/locked-items.md} asks for top-right, "deliberately not
 * top-left, where the Locked Slots icon lives". That premise is inverted:
 * the slot lock icon has always drawn at top-RIGHT, as item 2 below shows,
 * and the cycle icon shares that corner with it. Top-left is the corner
 * that is actually free, so the item mark goes there.
 *
 * <p>This keeps what the spec was actually asking for, that a locked item
 * in a locked slot shows both marks without collision, and it changes
 * nothing already shipped. Raised with the Designer rather than silently
 * swapped.
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

    /** Hollow circle: locked by item id. Solid: locked exactly. Both 5×5. */
    private static final Identifier INVENTORYPLUS$ITEM_LOCK_ID =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_item_id.png");
    private static final Identifier INVENTORYPLUS$ITEM_LOCK_EXACT =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_item_exact.png");
    private static final int INVENTORYPLUS$ITEM_MARK_SIZE = 5;

    /** The Column Cycler's edit-mode overlay: 50%-translucent light gray. */
    private static final int INVENTORYPLUS$CYCLER_EDIT_TINT = 0x80808080;

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
        // 1. Column Cycler edit-mode gray overlay, on the inv+hotbar slots it edits.
        int tint = 0;
        if (ColumnCyclerEditMode.isOn() && LockedSlots.isInvOrHotbarSlot(slot)) {
            tint = INVENTORYPLUS$CYCLER_EDIT_TINT;
        }
        if (tint != 0) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, tint);
        }

        // The Lock groups switch pauses every lock: nothing blocked, nothing drawn.
        boolean locksOn = IPConfig.lockGroupsEnabled();
        boolean locked = locksOn && LockedSlots.isLockedSlot(slot);
        boolean cycle = ColumnCycler.isCycleSlot(slot);
        // A cycle slot's mark is the cycle mark; it carries no lock of its
        // own (its cycler's reach group, reach.md). A real Slot lock put on
        // one with L draws its padlock beside the cycle mark (2026-09-30).

        // 2. Lock icon — top-right of slot, 1 px inset from the right edge.
        // In the colour of the slot's lock group (lock-groups.md, "What the
        // player sees"); a shared lock is Slot lock.
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

        // 3. Cycle icon — sits left of the lock icon when the slot also
        // carries a real lock; otherwise takes the top-right slot with 1 px
        // inset from the right edge.
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

        // 4. Locked-item mark — top-left, the corner the other two leave free.
        // Every matching stack carries it, not just the first one found, since
        // every one of them is protected (locked-items.md).
        // In its group's colour; an exact lock draws over an item one, as the
        // more specific rule (lock-groups.md).
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
