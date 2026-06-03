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
 * shared animation clock, and this one HUD draws whichever cycler is active on
 * the current hotbar slot. The render math is unchanged from the verified
 * Column Cycler HUD — only the data source moved behind the registry.
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
        return CycleHudRegistry.activeView(selectedSlot) != null;
    }

    // ─── Rendering ───────────────────────────────────────────────────

    private static void render(GuiGraphics graphics, int ux, int uy, int uw, int uh, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int activeSlot = mc.player.getInventory().getSelectedSlot();
        if (activeSlot < 0 || activeSlot > 8) return;

        CycleView view = CycleHudRegistry.activeView(activeSlot);
        if (view == null) return;
        List<ItemStack> displayItems = view.orderedItems();
        int n = displayItems.size();
        if (n < 2) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int stripWidth = n * SLOT_PX + 2 * OUTER_BORDER_PX;
        int stripX = screenW / 2 + HOTBAR_HALF_WIDTH_PX + STRIP_GAP_FROM_HOTBAR_PX;
        int stripY = screenH - STRIP_HEIGHT_PX;

        // Animation progress + slide offset — keyed on the selected hotbar slot.
        float progress = 1.0f;
        CyclerDirection direction = null;
        if (CycleHudRegistry.animatingSlot() == activeSlot
                && CycleHudRegistry.animationDirection() != null) {
            long elapsed = System.currentTimeMillis() - CycleHudRegistry.animationStartMillis();
            if (elapsed < ANIMATION_DURATION_MILLIS) {
                progress = easeOutBack((float) elapsed / ANIMATION_DURATION_MILLIS);
                direction = CycleHudRegistry.animationDirection();
            } else {
                CycleHudRegistry.clearAnimation();
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
