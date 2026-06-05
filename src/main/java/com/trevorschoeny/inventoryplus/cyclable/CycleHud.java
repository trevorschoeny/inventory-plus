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
 * The shared "Mini-hotbar" cycle HUD — a hotbar-style strip drawn just right
 * of the vanilla hotbar showing the cycle active on the selected hotbar slot,
 * with a spring slide animation when the cycle rotates.
 *
 * <p><b>Cycler-agnostic</b> (generalized 2026-06-02, Trev-approved option a).
 * This was formerly {@code ColumnCyclerHud}, hard-wired to Column Cycler.
 * It now reads from {@link CycleHudRegistry}: any cycler (Column Cycler in IP,
 * Pocket Cycler in IPP, …) registers a {@link CycleHudSource} and fires the
 * shared animation clock, and this one HUD draws whichever cyclers are active
 * on the current hotbar slot. The render math is unchanged from the verified
 * Column Cycler HUD — only the data source moved behind the registry.
 *
 * <h3>Stacking (2026-06-04)</h3>
 *
 * When more than one cycler is active on the selected slot (e.g. Column Cycler
 * AND Pocket Cycler both on the held slot), each gets its own strip, stacked
 * vertically. The registry hands them back bottom→top by
 * {@link CycleHudSource#hudStackOrder()}: Pocket Cycler pins to the bottom
 * (flush with the vanilla hotbar), Column Cycler sits above it. Each strip
 * reads its OWN source's animation, so cycling one slides only that strip.
 *
 * <h3>Display + animation</h3>
 *
 * The {@link CycleView} the registry hands back carries the ordered items and
 * the highlight index (each source applies the prev/current/next convention).
 * Items render at their NEW positions plus a slide offset that decays from one
 * slot-width to zero over {@link #ANIMATION_DURATION_MILLIS}, eased by a
 * spring (ease-out-back) curve. FORWARD slides right-to-left; the wrap-around
 * item gets a duplicate render on the opposite edge so it visually slides in
 * as the original slides out, with scissor clipping at the strip bounds.
 *
 * <h3>Positioning</h3>
 *
 * Drawn manually (not via MenuKit's screen-edge regions, which can't anchor
 * "right of the vanilla hotbar"). The {@code HudRegion.BOTTOM_CENTER} on the
 * builder is just a registration anchor; the onRender callback computes its
 * own coordinates from the vanilla hotbar geometry.
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
    /** Vertical gap between stacked strips when multiple cyclers are active. */
    private static final int STACK_GAP_PX = 2;

    // ─── Vanilla sprites ─────────────────────────────────────────────

    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final int SELECTION_SPRITE_W = 24;
    private static final int SELECTION_SPRITE_H = 23;

    // ─── Animation ───────────────────────────────────────────────────

    /** Spring slide duration — long enough for the overshoot to read. */
    private static final long ANIMATION_DURATION_MILLIS = 220L;

    // ─── Registration ────────────────────────────────────────────────

    /**
     * Register the shared HUD panel with MenuKit. Call once from client init,
     * AFTER all cyclers have registered their {@link CycleHudSource}s (order
     * doesn't strictly matter — sources can register after too; the HUD reads
     * the registry live each frame).
     */
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

    // ─── Rendering ───────────────────────────────────────────────────

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

        // Highlight alignment — ONLY when more than one strip is showing. A
        // 2-slot strip highlights its leftmost cell (index 0); a 3+-slot strip
        // highlights one cell in (index 1). Left-aligned, those highlights sit
        // a slot-width apart. So shift each strip right by
        // (maxHighlight − thisHighlight) slots: the strip whose held slot is
        // furthest in stays put, and shorter strips slide right until every
        // highlighted/held slot lands at the same x. With a single strip there's
        // nothing to align — offset 0, exactly the original behavior.
        boolean aligning = cycles.size() > 1;
        int maxHighlight = 0;
        if (aligning) {
            for (CycleHudRegistry.ActiveCycle ac : cycles) {
                maxHighlight = Math.max(maxHighlight, highlightIndexOf(ac.view()));
            }
        }

        // Stack bottom→top: index 0 (lowest hudStackOrder — Pocket) sits flush
        // with the vanilla hotbar; each next strip is one strip-height (+ gap)
        // higher up the screen.
        for (int i = 0; i < cycles.size(); i++) {
            CycleHudRegistry.ActiveCycle cycle = cycles.get(i);
            int stripY = screenH - STRIP_HEIGHT_PX - i * (STRIP_HEIGHT_PX + STACK_GAP_PX);
            int alignOffsetPx = aligning
                    ? (maxHighlight - highlightIndexOf(cycle.view())) * SLOT_PX
                    : 0;
            renderStrip(graphics, mc, cycle, activeSlot, stripX + alignOffsetPx, stripY);
        }
    }

    /**
     * The clamped highlight index a strip will draw at — matches the clamp in
     * {@link #renderStrip}. Used for cross-strip highlight alignment so both
     * computations agree on where each strip's held slot lands.
     */
    private static int highlightIndexOf(CycleView view) {
        int n = view.orderedItems().size();
        return Math.max(0, Math.min(n - 1, view.highlightIndex()));
    }

    /**
     * Render a single cycle strip at {@code (stripX, stripY)}. Reads the slide
     * animation from this cycle's OWN source, so stacked strips animate
     * independently. This body is the verified single-strip render, lifted out
     * of {@code render} unchanged except for the per-source animation lookup
     * and the parameterized strip origin.
     */
    private static void renderStrip(GuiGraphics graphics, Minecraft mc,
                                    CycleHudRegistry.ActiveCycle cycle, int activeSlot,
                                    int stripX, int stripY) {
        CycleView view = cycle.view();
        List<ItemStack> displayItems = view.orderedItems();
        int n = displayItems.size();
        if (n < 2) return;

        int stripWidth = n * SLOT_PX + 2 * OUTER_BORDER_PX;

        // Animation progress + slide offset — keyed on THIS source's clock.
        float progress = 1.0f;
        CyclerDirection direction = null;
        CycleHudRegistry.Animation anim = CycleHudRegistry.animationFor(cycle.source());
        if (anim != null && anim.hotbarSlot() == activeSlot && anim.direction() != null) {
            long elapsed = System.currentTimeMillis() - anim.startMillis();
            if (elapsed < ANIMATION_DURATION_MILLIS) {
                progress = easeOutBack((float) elapsed / ANIMATION_DURATION_MILLIS);
                direction = anim.direction();
            } else {
                CycleHudRegistry.clearAnimation(cycle.source());
            }
        }
        float remainingFraction = 1.0f - progress;
        int slideOffsetPx = 0;
        if (direction != null) {
            slideOffsetPx = (direction == CyclerDirection.FORWARD)
                    ? Math.round(remainingFraction * SLOT_PX)
                    : -Math.round(remainingFraction * SLOT_PX);
        }

        renderStripBackground(graphics, stripX, stripY, stripWidth);

        int slotAreaLeft = stripX + OUTER_BORDER_PX;
        int slotAreaTop = stripY;
        int slotAreaRight = stripX + stripWidth - OUTER_BORDER_PX;
        int slotAreaBottom = stripY + STRIP_HEIGHT_PX;
        graphics.enableScissor(slotAreaLeft, slotAreaTop, slotAreaRight, slotAreaBottom);
        try {
            for (int k = 0; k < n; k++) {
                int itemX = slotAreaLeft + k * SLOT_PX + slideOffsetPx;
                renderItem(graphics, mc, displayItems.get(k), itemX, slotAreaTop);
            }
            // Wrap-item duplicate on the opposite edge during animation.
            if (direction != null && progress < 1.0f) {
                int wrapItemIndex;
                int wrapBaseVirtualPx;
                if (direction == CyclerDirection.FORWARD) {
                    wrapItemIndex = n - 1;
                    wrapBaseVirtualPx = -SLOT_PX;
                } else {
                    wrapItemIndex = 0;
                    wrapBaseVirtualPx = n * SLOT_PX;
                }
                int wrapItemX = slotAreaLeft + wrapBaseVirtualPx + slideOffsetPx;
                renderItem(graphics, mc, displayItems.get(wrapItemIndex), wrapItemX, slotAreaTop);
            }
        } finally {
            graphics.disableScissor();
        }

        // Highlight — fixed position from the view's highlight index.
        int hi = Math.max(0, Math.min(n - 1, view.highlightIndex()));
        renderHighlight(graphics, slotAreaLeft + hi * SLOT_PX, slotAreaTop);
    }

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
