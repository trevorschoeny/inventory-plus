package com.trevorschoeny.inventoryplus.columncycler.hud;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.hud.HudPanel;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Column Cycler's strip right of the hotbar: a one-cell bar holding the held
 * stack, with the rest of the column rising above it the way the column sits in
 * the inventory, and each turn sliding the stacks one cell. Drawn here since
 * MenuKit 8.0.0 removed its cycle strip; the look and timing are the strip's,
 * unchanged. MenuKit gives the room: a HUD panel in the hotbar's right-side
 * region, 4 px off the bar, so another mod's panel there stacks beside it.
 */
public final class ColumnCyclerHud {

    private ColumnCyclerHud() {}

    // Vanilla's hotbar measurements, in GUI px.
    /** One hotbar cell, and the distance a slide travels. */
    private static final int SLOT_PX = 20;
    /** The hotbar sprite's outline, each side of the cells. */
    private static final int BORDER_PX = 1;
    /** A framed cell: the cell plus its outline each side (also the sprite's height). */
    private static final int CELL_FRAME_PX = SLOT_PX + 2 * BORDER_PX;
    /** The floating cells clear the held cell's bar by this much. */
    private static final int RISE_PX = 1;
    /** Vanilla draws hotbar items 2 px in from the cell's left and 3 px down from the bar's top. */
    private static final int ITEM_X_PX = 2, ITEM_Y_PX = 3;

    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int SELECTION_W = 24, SELECTION_H = 23;

    /** One turn's slide, on MenuKit's damped spring (about 9% over, then settles). */
    private static final long SLIDE_MILLIS = 300L;

    /** The turn in flight: which column, when, and which way. */
    private record Slide(int column, long startMillis, boolean forward) {}

    private static @Nullable Slide slide;

    /**
     * Declares the HUD panel and slides the strip after each turn, so the
     * rotator never knows about the HUD. Call once at client init, before
     * MenuKit's declarations freeze.
     */
    public static void register() {
        HudPanel.builder("inventoryplus:column-cycler", ModeScope.BOTH)
                .region(OutsideRegion.RIGHT_ALIGN_BOTTOM)
                .gap(4) // where MenuKit's strip sat
                .fixed() // its place beside the hotbar is part of what it shows
                .visibleWhen(() -> column() != null)
                .element(new Strip())
                .build();
        // The column index is the hotbar slot. FORWARD brings the stack above
        // the hotbar into the hand, so every stack moves one cell down.
        ColumnCyclerRotator.addRotationListener((column, dir) ->
                slide = new Slide(column, Util.getMillis(), dir == ColumnCyclerRotator.Direction.FORWARD));
    }

    /**
     * The selected column's stacks top to bottom, held last, or null when the
     * strip shows nothing: the mode is off, the slot isn't cycled, nothing above
     * it is, or the player is a spectator (whose hotbar is the spectator menu).
     */
    private static @Nullable List<ItemStack> column() {
        if (IPConfig.columnCyclerHudMode() != HudMode.MINI_HOTBAR) return null;
        if (!IPConfig.columnCyclerEnabled()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator()) return null;
        Inventory inv = mc.player.getInventory();
        int held = inv.getSelectedSlot();
        if (held < 0 || held > 8 || !ColumnCycler.isCycleSlot(held)) return null;

        // The column's cycled rows top to bottom, as ColumnCyclerRotator lists them.
        List<ItemStack> stacks = new ArrayList<>(4);
        for (int row = 1; row <= 3; row++) {
            int slot = row * 9 + held;
            if (ColumnCycler.isCycleSlot(slot)) stacks.add(inv.getItem(slot));
        }
        if (stacks.isEmpty()) return null;
        stacks.add(inv.getItem(held)); // the hotbar's stack last
        return stacks;
    }

    /** The panel's one element, sized to the column every frame. */
    private static final class Strip implements PanelElement {

        @Override public int getChildX() { return 0; }
        @Override public int getChildY() { return 0; }
        @Override public int getWidth() { return CELL_FRAME_PX; }

        @Override
        public int getHeight() {
            List<ItemStack> stacks = column();
            return stacks == null ? 0 : height(stacks.size());
        }

        @Override
        public void render(RenderContext ctx) {
            List<ItemStack> stacks = column();
            if (stacks == null) return;
            GuiGraphicsExtractor g = ctx.graphics();
            int n = stacks.size();
            int cellX = ctx.originX() + BORDER_PX;
            // The held cell sits on the hotbar's row, at the panel's bottom.
            int heldTop = ctx.originY() + height(n) - CELL_FRAME_PX;

            Slide s = running();
            Boolean forward = s == null ? null : s.forward();
            float p = s == null ? 1f
                    : Easing.SPRING.apply((float) (Util.getMillis() - s.startMillis()) / SLIDE_MILLIS);

            // The held cell's frame, then the floating cells' top to bottom, so
            // each one's outline covers the bottom edge of the one above and the
            // lowest lands on the held bar's top row: one framed column.
            drawFrame(g, cellX - BORDER_PX, heldTop);
            for (int j = 0; j < n - 1; j++) drawFrame(g, cellX - BORDER_PX, cellY(heldTop, j, n));

            // Each stack slides from the cell it held a turn ago to its cell now.
            // Clipped to the column plus a cell below the hand, so a forward
            // turn's old held stack can slide out of the bottom.
            g.enableScissor(cellX, cellY(heldTop, 0, n), cellX + SLOT_PX, heldTop + SLOT_PX);
            try {
                for (int i = 0; i < n; i++) {
                    int to = cellY(heldTop, i, n), from = fromY(heldTop, i, n, forward);
                    drawItem(g, stacks.get(i), cellX, Math.round(from + (to - from) * p));
                }
                // The stack that wrapped around leaves through the far end: forward,
                // the old held stack (now on top) exits the bottom; backward, the one
                // that wrapped into the hand exits the top.
                if (forward != null) {
                    int i = forward ? 0 : n - 1;
                    int from = forward ? heldTop : cellY(heldTop, 0, n);
                    int to = forward ? heldTop + SLOT_PX : cellY(heldTop, 0, n) - SLOT_PX;
                    drawItem(g, stacks.get(i), cellX, Math.round(from + (to - from) * p));
                }
            } finally {
                g.disableScissor();
            }

            // The selection frame around the held cell, placed as vanilla places
            // it: two px left of the cell, one above the bar.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_SPRITE, SELECTION_W, SELECTION_H, 0, 0,
                    cellX - BORDER_PX - 1, heldTop - 1, SELECTION_W, SELECTION_H);
        }
    }

    /** The held cell's frame plus each floating cell above it, lifted {@link #RISE_PX}. */
    private static int height(int n) {
        return CELL_FRAME_PX + (n - 1) * SLOT_PX + RISE_PX;
    }

    /** The turn running on the selected column, or null once it has settled. */
    private static @Nullable Slide running() {
        Slide s = slide;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || s.column() != mc.player.getInventory().getSelectedSlot()) return null;
        return Util.getMillis() - s.startMillis() >= SLIDE_MILLIS ? null : s;
    }

    /** Top of cell {@code j} (0 the top, {@code n - 1} the held cell at {@code heldTop}). */
    private static int cellY(int heldTop, int j, int n) {
        return heldTop - (n - 1 - j) * SLOT_PX - (j < n - 1 ? RISE_PX : 0);
    }

    /**
     * Where stack {@code i} slides from. Forward (down), out of the cell above,
     * the top one wrapping in from above the column; backward (up), out of the
     * cell below, the held one wrapping in from below the hand. At rest, its own cell.
     */
    private static int fromY(int heldTop, int i, int n, @Nullable Boolean forward) {
        if (forward == null) return cellY(heldTop, i, n);
        if (forward) return i == 0 ? cellY(heldTop, 0, n) - SLOT_PX : cellY(heldTop, i - 1, n);
        return i == n - 1 ? heldTop + SLOT_PX : cellY(heldTop, i + 1, n);
    }

    /** One cell's frame from the hotbar sprite: left cap, one cell, right cap, all 22 px tall. */
    private static void drawFrame(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_SPRITE_W, CELL_FRAME_PX, 0, 0,
                x, y, BORDER_PX, CELL_FRAME_PX);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_SPRITE_W, CELL_FRAME_PX, BORDER_PX, 0,
                x + BORDER_PX, y, SLOT_PX, CELL_FRAME_PX);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, HOTBAR_SPRITE_W, CELL_FRAME_PX,
                HOTBAR_SPRITE_W - BORDER_PX, 0, x + BORDER_PX + SLOT_PX, y, BORDER_PX, CELL_FRAME_PX);
    }

    /** A stack with its count and bar, where vanilla's hotbar puts one in a cell. */
    private static void drawItem(GuiGraphicsExtractor g, ItemStack stack, int cellX, int cellY) {
        if (stack.isEmpty()) return;
        g.item(stack, cellX + ITEM_X_PX, cellY + ITEM_Y_PX);
        g.itemDecorations(Minecraft.getInstance().font, stack, cellX + ITEM_X_PX, cellY + ITEM_Y_PX);
    }
}
