package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
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
 * The padlock and cycle icon, one MenuKit over-decoration (7.5.0) so their shared
 * position logic stays in one place: the cycle icon steps left of the padlock only
 * when the slot also carries a lock.
 *
 * <ol>
 *   <li><b>Lock icon</b> on locked slots: a 5x6 PNG at the top right, inset
 *       1 px from the slot's right edge.</li>
 *   <li><b>Cycle icon</b> on cycle slots: a 6x6 PNG. When the slot is also
 *       locked, it sits 1 px left of the lock icon so the two don't touch.
 *       Otherwise it takes the lock's top-right position.</li>
 * </ol>
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

    /**
     * ARGB tint for the corner indicator sprites: alpha 0xAB (67%) and white,
     * so no color change. Applied through the color overload of
     * {@link GuiGraphicsExtractor#blit} so the icons read as hints rather than
     * dominating the slot content.
     */
    private static final int INDICATOR_TINT = 0xABFFFFFF;

    /** Whether {@code slot} shows as locked: wherever its lock lives, it always holds. */
    static boolean shownLocked(Slot slot) {
        return LockedSlots.isLockedSlot(slot);
    }

    /** Public so the gametest can call it directly, the same way MenuKit's render pass does. */
    public static final SlotDecorations.Decoration DECORATION = new SlotDecorations.Decoration() {
        @Override
        public void draw(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot,
                         ItemStack stack, int x, int y) {
            boolean locked = shownLocked(slot);
            // A cycle slot's mark is the cycle mark; it carries no lock of its own. A
            // lock put on one with L draws its padlock beside the cycle mark.
            boolean cycle = ColumnCycler.isCycleSlot(slot);

            if (locked) {
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        LOCK_ICON,
                        x + 16 - LOCK_ICON_W - 1, y + 1,
                        /*u=*/ 0f, /*v=*/ 0f,
                        LOCK_ICON_W, LOCK_ICON_H,
                        LOCK_ICON_W, LOCK_ICON_H,
                        INDICATOR_TINT);
            }

            if (cycle) {
                int cycleX = locked
                        ? (x + 16 - LOCK_ICON_W - 1) - 1 - CYCLE_ICON_W
                        : x + 16 - CYCLE_ICON_W - 1;
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        CYCLE_ICON,
                        cycleX, y + 1,
                        /*u=*/ 0f, /*v=*/ 0f,
                        CYCLE_ICON_W, CYCLE_ICON_H,
                        CYCLE_ICON_W, CYCLE_ICON_H,
                        INDICATOR_TINT);
            }
        }

        /** The padlock's own pixels say what it is, since an empty locked slot has no item tooltip. */
        @Override
        public @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack,
                                                  int mouseX, int mouseY) {
            if (!shownLocked(slot)) return null;
            int iconX = 16 - LOCK_ICON_W - 1;
            int iconY = 1;
            if (mouseX < iconX || mouseX >= iconX + LOCK_ICON_W || mouseY < iconY || mouseY >= iconY + LOCK_ICON_H) {
                return null;
            }
            return List.of(Component.literal("Locked"));
        }
    };

    public static void register() {
        SlotDecorations.over("inventoryplus:slot_marks", DECORATION);
    }
}
