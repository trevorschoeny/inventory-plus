package com.trevorschoeny.inventoryplus.lockgroups;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import com.trevlar.menukit.api.panel.SlotDecorations;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The padlock, cycle icon and locked-item corner mark, one MenuKit over-decoration (7.5.0) so
 * their shared position logic stays in one place: the cycle icon steps left of the padlock
 * only when the slot also carries a real lock.
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
 */
public final class SlotMarks {

    private SlotMarks() {}

    private static final Identifier LOCK_ICON =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_slot.png");
    private static final Identifier CYCLE_ICON =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/cycle_slot_symbol.png");

    private static final int LOCK_ICON_W = 5;
    private static final int LOCK_ICON_H = 6;
    private static final int CYCLE_ICON_W = 6;
    private static final int CYCLE_ICON_H = 6;

    /** Hollow circle: locked by item id. Solid: locked exactly. Both 5x5. */
    private static final Identifier ITEM_LOCK_ID =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_item_id.png");
    private static final Identifier ITEM_LOCK_EXACT =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_item_exact.png");
    private static final int ITEM_MARK_SIZE = 5;

    /**
     * ARGB tint for the corner indicator sprites: alpha 0xAB (67%) and white,
     * so no color change. Applied through the color overload of
     * {@link GuiGraphicsExtractor#blit} so the icons read as hints rather than
     * dominating the slot content.
     */
    private static final int INDICATOR_TINT = 0xABFFFFFF;

    /** Public so the gametest can call it directly, the same way MenuKit's render pass does. */
    public static final SlotDecorations.Decoration DECORATION = new SlotDecorations.Decoration() {
        @Override
        public void draw(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot,
                         ItemStack stack, int x, int y) {
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
                int iconX = x + 16 - LOCK_ICON_W - 1;
                int iconY = y + 1;
                LockGroup group = lockGroup(slot);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        LOCK_ICON,
                        iconX, iconY,
                        /*u=*/ 0f, /*v=*/ 0f,
                        LOCK_ICON_W, LOCK_ICON_H,
                        LOCK_ICON_W, LOCK_ICON_H,
                        LockColours.argb(group));
            }

            // Cycle icon. Sits left of the lock icon when the slot also carries a
            // real lock.
            if (cycle) {
                boolean lockIconAlsoShown = locked;
                int cycleX = lockIconAlsoShown
                        ? (x + 16 - LOCK_ICON_W - 1) - 1 - CYCLE_ICON_W
                        : x + 16 - CYCLE_ICON_W - 1;
                int cycleY = y + 1;
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        CYCLE_ICON,
                        cycleX, cycleY,
                        /*u=*/ 0f, /*v=*/ 0f,
                        CYCLE_ICON_W, CYCLE_ICON_H,
                        CYCLE_ICON_W, CYCLE_ICON_H,
                        INDICATOR_TINT);
            }

            // Locked-item mark, top left. Every matching stack carries it, not just
            // the first one found, since every one of them is protected. It is in
            // its group's color, and an exact lock wins over an item one as the
            // more specific rule.
            String exact = locksOn && !stack.isEmpty() ? LockedItems.exactGroup(stack) : null;
            String byId = locksOn && !stack.isEmpty() ? LockedItems.itemGroup(stack) : null;
            if (exact != null || byId != null) {
                Identifier mark = exact != null ? ITEM_LOCK_EXACT : ITEM_LOCK_ID;
                LockGroup group = exact != null
                        ? Reach.resolve(exact, LockKind.EXACT)
                        : Reach.resolve(byId, LockKind.ITEM);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        mark,
                        x + 1, y + 1,
                        /*u=*/ 0f, /*v=*/ 0f,
                        ITEM_MARK_SIZE, ITEM_MARK_SIZE,
                        ITEM_MARK_SIZE, ITEM_MARK_SIZE,
                        LockColours.argb(group));
            }
        }

        /** The padlock's own pixels name its group, since an empty locked slot has no item tooltip. */
        @Override
        public @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack,
                                                  int mouseX, int mouseY) {
            if (!IPConfig.lockGroupsEnabled() || !LockedSlots.isLockedSlot(slot)) return null;
            int iconX = 16 - LOCK_ICON_W - 1;
            int iconY = 1;
            if (mouseX < iconX || mouseX >= iconX + LOCK_ICON_W || mouseY < iconY || mouseY >= iconY + LOCK_ICON_H) {
                return null;
            }
            return List.of(Component.literal("Locked: " + lockGroup(slot).name()));
        }
    };

    private static LockGroup lockGroup(Slot slot) {
        String stored = LockedSlots.storedGroup(slot);
        return Reach.resolve(stored == null ? Reach.SLOT_LOCK : stored, LockKind.SLOT);
    }

    public static void register() {
        SlotDecorations.over("inventoryplus:slot_marks", DECORATION);
    }
}
