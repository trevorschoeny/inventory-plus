package com.trevorschoeny.inventoryplus.cyclable;

import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.hud.MKHudPanel;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The shared cycle HUD — drawn just right of the vanilla hotbar, showing the
 * cycle(s) active on the selected hotbar slot, with a spring slide animation
 * when a cycle rotates. Cycler-agnostic: any cycler registers a
 * {@link CycleHudSource} and fires the shared per-source animation clock.
 *
 * <h3>Two layouts</h3>
 *
 * <ul>
 *   <li><b>Solo</b> — one cycler on the slot: a horizontal mini-hotbar strip
 *       (vanilla {@code hud/hotbar} sprite), right of the real hotbar at the
 *       same height.</li>
 *   <li><b>Cross</b> — two cyclers on the same slot (Column + Pocket): the
 *       horizontal arm (Pocket) is that mini-hotbar at hotbar height; the
 *       vertical arm (Column) floats straight up from the held item. The held
 *       cell (bottom of the column = the highlighted cell of the row) is shared,
 *       so exactly ONE arm draws the held item — whichever is <em>animating</em>
 *       (it draws last, on top): a column cycle slides it vertically, a pocket
 *       cycle slides it horizontally. The selected slot animates in both
 *       directions, with no duplicate of the held item.</li>
 * </ul>
 *
 * <p>All cells use vanilla textures: {@code hud/hotbar} for slot backgrounds and
 * {@code hud/hotbar_selection} for the held-item highlight. Slot frames stay
 * fixed; only items slide.
 */
public final class CycleHud {

    private CycleHud() {}

    // ─── Layout constants (gui-scaled pixels) ────────────────────────

    private static final int SLOT_PX = 20;
    private static final int OUTER_BORDER_PX = 1;
    private static final int STRIP_HEIGHT_PX = 22;
    private static final int STRIP_GAP_FROM_HOTBAR_PX = 4;
    private static final int HOTBAR_HALF_WIDTH_PX = 91;
    private static final int ITEM_OFFSET_PX = 3;
    /** The floating vertical stack clears the shared hotbar cell by this many px. */
    private static final int COLUMN_RISE_PX = 1;
    /** The floating vertical stack sits this many px right of the shared held cell. */
    private static final int COLUMN_NUDGE_X_PX = 1;

    // ─── Vanilla sprites ─────────────────────────────────────────────

    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int SELECTION_SPRITE_W = 24;
    private static final int SELECTION_SPRITE_H = 23;

    // ─── Animation ───────────────────────────────────────────────────

    private static final long ANIMATION_DURATION_MILLIS = 220L;

    // ─── Registration ────────────────────────────────────────────────

    public static void register() {
        MKHudPanel.builder("inventoryplus-cycle-mini-hotbar")
                .region(HudRegion.BOTTOM_CENTER)
                .autoSize()
                .showWhen(CycleHud::shouldShow)
                .onRender(CycleHud::render)
                .build();
    }

    // ─── Visibility ──────────────────────────────────────────────────

    private static boolean shouldShow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (selectedSlot < 0 || selectedSlot > 8) return false;
        return !CycleHudRegistry.activeViews(selectedSlot).isEmpty();
    }

    // ─── Dispatch ────────────────────────────────────────────────────

    private static void render(GuiGraphics graphics, int ux, int uy, int uw, int uh, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int activeSlot = mc.player.getInventory().getSelectedSlot();
        if (activeSlot < 0 || activeSlot > 8) return;

        List<CycleHudRegistry.ActiveCycle> cycles = CycleHudRegistry.activeViews(activeSlot);
        if (cycles.isEmpty()) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int stripX = screenW / 2 + HOTBAR_HALF_WIDTH_PX + STRIP_GAP_FROM_HOTBAR_PX;
        int stripY = screenH - STRIP_HEIGHT_PX; // hotbar height

        if (cycles.size() == 1) {
            renderHorizontalStrip(graphics, mc, cycles.get(0), activeSlot, stripX, stripY, true);
            return;
        }

        // Cross: horizontal arm (Pocket) + vertical arm (Column) sharing the held cell.
        CycleHudRegistry.ActiveCycle horizontal = null, vertical = null;
        for (CycleHudRegistry.ActiveCycle ac : cycles) {
            if (ac.source().verticalInCross()) {
                if (vertical == null) vertical = ac;
            } else if (horizontal == null) {
                horizontal = ac;
            }
        }

        int heldHi = (horizontal != null)
                ? Math.max(0, Math.min(horizontal.view().size() - 1, horizontal.view().highlightIndex()))
                : 0;
        int heldCellX = stripX + OUTER_BORDER_PX + heldHi * SLOT_PX;

        // The arm that's animating draws LAST (on top) AND owns the shared held
        // cell's item, so only ONE copy of the held item is ever drawn: a column
        // cycle slides it vertically, a pocket cycle slides it horizontally. The
        // non-owning arm skips the held item (otherwise it draws a static second
        // copy the moving one slides over — a brief duplicate near the end).
        boolean pocketAnim = horizontal != null && animDirection(horizontal.source(), activeSlot) != null;
        boolean columnAnim = vertical != null && animDirection(vertical.source(), activeSlot) != null;
        boolean columnOwnsHeld = columnAnim && !pocketAnim;

        if (!columnOwnsHeld && vertical != null) {
            renderColumnStrip(graphics, mc, vertical, activeSlot, heldCellX, stripY, false);
        }
        if (horizontal != null) {
            renderHorizontalStrip(graphics, mc, horizontal, activeSlot, stripX, stripY, !columnOwnsHeld);
        }
        if (columnOwnsHeld && vertical != null) {
            renderColumnStrip(graphics, mc, vertical, activeSlot, heldCellX, stripY, true);
        }
        renderHighlight(graphics, heldCellX, stripY);
    }

    // ─── Horizontal strip (solo, and the cross's Pocket arm) ─────────

    private static void renderHorizontalStrip(GuiGraphics graphics, Minecraft mc,
                                              CycleHudRegistry.ActiveCycle cycle, int activeSlot,
                                              int stripX, int stripY, boolean includeHeld) {
        CycleView view = cycle.view();
        List<ItemStack> displayItems = view.orderedItems();
        int n = displayItems.size();
        if (n < 2) return;

        int stripWidth = n * SLOT_PX + 2 * OUTER_BORDER_PX;
        int slide = slideOffset(cycle.source(), activeSlot);
        CyclerDirection direction = animDirection(cycle.source(), activeSlot);
        int hi = Math.max(0, Math.min(n - 1, view.highlightIndex()));

        renderStripBackground(graphics, stripX, stripY, stripWidth);

        int slotAreaLeft = stripX + OUTER_BORDER_PX;
        graphics.enableScissor(slotAreaLeft, stripY, stripX + stripWidth - OUTER_BORDER_PX, stripY + STRIP_HEIGHT_PX);
        try {
            for (int k = 0; k < n; k++) {
                // The column arm owns the held cell's item when it's the one
                // animating — skip it here so the two don't both draw it.
                if (k == hi && !includeHeld) continue;
                renderItem(graphics, mc, displayItems.get(k), slotAreaLeft + k * SLOT_PX + slide, stripY);
            }
            if (direction != null) {
                int wrapIndex = (direction == CyclerDirection.FORWARD) ? n - 1 : 0;
                int wrapBase = (direction == CyclerDirection.FORWARD) ? -SLOT_PX : n * SLOT_PX;
                renderItem(graphics, mc, displayItems.get(wrapIndex), slotAreaLeft + wrapBase + slide, stripY);
            }
        } finally {
            graphics.disableScissor();
        }

        renderHighlight(graphics, slotAreaLeft + hi * SLOT_PX, stripY);
    }

    // ─── Vertical strip (the cross's Column arm) ─────────────────────

    /**
     * Draw the column rising from the held cell at {@code heldTop}. Static slot
     * frames for the cells ABOVE the held (the held cell's frame is the
     * horizontal arm's hotbar bar); the floating stack is lifted
     * {@link #COLUMN_RISE_PX} so it clears the bar.
     *
     * <p>Animation is a per-cell interpolation: each item slides FROM the cell it
     * occupied one rotation step ago TO its current cell, and the wrapped item
     * ghosts out the opposite end. Because every position is a real cell, the
     * lift "just works" — the held↔first-floating gap is wider than the gaps
     * between floating cells, and the slide spans whichever gap it crosses with
     * no discontinuity. The held cell always rests exactly at {@code heldTop}, so
     * handing it back to the horizontal arm when the cycle ends never snaps.
     *
     * <p>Direction: the column's FORWARD ({@code ]}) means "items shift toward
     * the hand" — <b>down</b>; BACKWARD ({@code [}) shifts them <b>up</b>, away.
     *
     * @param includeHeld draw the held (bottom) item + its exit ghost. False when
     *                    the horizontal arm owns the held cell (it's animating, or
     *                    nothing is) — avoids a duplicate of the held item.
     */
    private static void renderColumnStrip(GuiGraphics graphics, Minecraft mc,
                                          CycleHudRegistry.ActiveCycle cycle, int activeSlot,
                                          int cellX, int heldTop, boolean includeHeld) {
        List<ItemStack> visual = cycle.view().visualOrder();
        int n = visual.size();
        if (n < 2) return;

        CyclerDirection direction = animDirection(cycle.source(), activeSlot);
        float p = animProgress(cycle.source(), activeSlot); // eased 0→1 (→1 at rest)
        int topY = columnCellY(heldTop, 0, n);
        // The floating stack sits a hair right of the shared held cell; the held
        // item itself stays aligned to the bar (so handing it back never snaps).
        int floatX = cellX + COLUMN_NUDGE_X_PX;

        // Static frames for the cells above the held cell (lifted to clear the
        // hotbar bar). The held cell's frame is the horizontal arm's bar.
        for (int j = 0; j <= n - 2; j++) {
            drawHotbarCellBackground(graphics, floatX, columnCellY(heldTop, j, n));
        }

        // Items animate cell→cell; clip to the column (+ one cell below the hand,
        // so a FORWARD exit ghost can slide out the bottom).
        graphics.enableScissor(cellX, topY, floatX + SLOT_PX, heldTop + SLOT_PX);
        try {
            // visual[0]=topmost … visual[n-1]=held (bottom). Floating cells draw
            // at floatX; the held item draws at cellX (aligned to the bar).
            for (int i = 0; i < n; i++) {
                if (i == n - 1 && !includeHeld) continue; // horizontal arm owns the held item
                int toY = columnCellY(heldTop, i, n);
                int fromY = columnItemFromY(heldTop, i, n, direction);
                int x = (i == n - 1) ? cellX : floatX;
                renderItem(graphics, mc, visual.get(i), x, Math.round(fromY + (toY - fromY) * p));
            }
            // The wrapped item's exit ghost leaves through the shared held end, so
            // it only applies when this arm owns that cell. FORWARD: the old hand
            // item (now wrapped to top, visual[0]) slides out the bottom (held x).
            // BACKWARD: the item that wrapped into the hand (visual[n-1]) slides
            // out the top (floating x).
            if (direction != null && includeHeld) {
                boolean fwd = direction == CyclerDirection.FORWARD;
                int wrapIndex = fwd ? 0 : n - 1;
                int fromY = fwd ? heldTop : columnCellY(heldTop, 0, n);
                int toY = fwd ? heldTop + SLOT_PX : columnCellY(heldTop, 0, n) - SLOT_PX;
                int x = fwd ? cellX : floatX;
                renderItem(graphics, mc, visual.get(wrapIndex), x, Math.round(fromY + (toY - fromY) * p));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * Y of the column's grid cell {@code j} (0 = topmost … n-1 = held). The held
     * cell sits on the hotbar bar ({@code heldTop}); every cell above it is
     * lifted {@link #COLUMN_RISE_PX} so the floating stack clears the bar.
     */
    private static int columnCellY(int heldTop, int j, int n) {
        return heldTop - (n - 1 - j) * SLOT_PX - (j < n - 1 ? COLUMN_RISE_PX : 0);
    }

    /**
     * Where item {@code i} animates FROM this rotation step. FORWARD (down) pulls
     * each item out of the cell above (the topmost wraps in from off the top);
     * BACKWARD (up) pulls each out of the cell below (the held wraps in from below
     * the hand). With no animation, from == its rest cell.
     */
    private static int columnItemFromY(int heldTop, int i, int n, CyclerDirection direction) {
        if (direction == null) return columnCellY(heldTop, i, n);
        if (direction == CyclerDirection.FORWARD) {
            return (i == 0) ? columnCellY(heldTop, 0, n) - SLOT_PX : columnCellY(heldTop, i - 1, n);
        }
        return (i == n - 1) ? heldTop + SLOT_PX : columnCellY(heldTop, i + 1, n);
    }

    // ─── Animation helpers (per-source) ──────────────────────────────

    private static int slideOffset(CycleHudSource source, int activeSlot) {
        CyclerDirection dir = animDirection(source, activeSlot);
        if (dir == null) return 0;
        CycleHudRegistry.Animation anim = CycleHudRegistry.animationFor(source);
        float remaining = 1.0f - easeOutBack((float) (System.currentTimeMillis() - anim.startMillis()) / ANIMATION_DURATION_MILLIS);
        int px = Math.round(remaining * SLOT_PX);
        return (dir == CyclerDirection.FORWARD) ? px : -px;
    }

    private static CyclerDirection animDirection(CycleHudSource source, int activeSlot) {
        CycleHudRegistry.Animation anim = CycleHudRegistry.animationFor(source);
        if (anim == null || anim.hotbarSlot() != activeSlot || anim.direction() == null) return null;
        if (System.currentTimeMillis() - anim.startMillis() >= ANIMATION_DURATION_MILLIS) {
            CycleHudRegistry.clearAnimation(source);
            return null;
        }
        return anim.direction();
    }

    /**
     * Eased animation progress 0→1 for a source's active cycle on {@code
     * activeSlot} (returns 1 — fully settled — when there's no animation). The
     * column's cell→cell interpolation uses this directly; easeOutBack lets it
     * overshoot ~10% past 1 for the spring before settling.
     */
    private static float animProgress(CycleHudSource source, int activeSlot) {
        CycleHudRegistry.Animation anim = CycleHudRegistry.animationFor(source);
        if (animDirection(source, activeSlot) == null || anim == null) return 1f;
        return easeOutBack((float) (System.currentTimeMillis() - anim.startMillis()) / ANIMATION_DURATION_MILLIS);
    }

    // ─── Vanilla-texture drawing ─────────────────────────────────────

    /** One vanilla hotbar slot background (20×20 slice of the hud/hotbar sprite) — vertical cells. */
    private static void drawHotbarCellBackground(GuiGraphics graphics, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, OUTER_BORDER_PX, 0,
                x, y, SLOT_PX, SLOT_PX);
    }

    /** A horizontal hotbar bar of {@code width} px: left cap + tiled slots + right cap. */
    private static void renderStripBackground(GuiGraphics graphics, int x, int y, int width) {
        int slotsCount = (width - 2 * OUTER_BORDER_PX) / SLOT_PX;
        int slotsLeftX = x + OUTER_BORDER_PX;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, 0, 0,
                x, y, OUTER_BORDER_PX, STRIP_HEIGHT_PX);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, OUTER_BORDER_PX, 0,
                slotsLeftX, y, slotsCount * SLOT_PX, STRIP_HEIGHT_PX);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, HOTBAR_SPRITE_W - OUTER_BORDER_PX, 0,
                slotsLeftX + slotsCount * SLOT_PX, y, OUTER_BORDER_PX, STRIP_HEIGHT_PX);
    }

    private static void renderItem(GuiGraphics graphics, Minecraft mc, ItemStack stack, int slotX, int slotY) {
        if (stack.isEmpty()) return;
        int itemX = slotX + ITEM_OFFSET_PX;
        int itemY = slotY + ITEM_OFFSET_PX;
        graphics.renderItem(stack, itemX, itemY);
        graphics.renderItemDecorations(mc.font, stack, itemX, itemY);
    }

    private static void renderHighlight(GuiGraphics graphics, int slotX, int slotY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_SPRITE,
                SELECTION_SPRITE_W, SELECTION_SPRITE_H, 0, 0,
                slotX - 1, slotY - 1, SELECTION_SPRITE_W, SELECTION_SPRITE_H);
    }

    /** Ease-out-back spring curve: overshoots ~10% then settles to 1.0. */
    private static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        float tm1 = t - 1f;
        return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1;
    }
}
