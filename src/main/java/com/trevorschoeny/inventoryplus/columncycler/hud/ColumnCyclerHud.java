package com.trevorschoeny.inventoryplus.columncycler.hud;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.hud.MKHudPanel;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Column Cycler "Mini-hotbar" HUD overlay.
 *
 * <p>Renders a hotbar-style strip just to the right of the player's
 * vanilla hotbar during gameplay, showing the active column's cycle
 * members. The highlighted slot represents what's currently in hand;
 * cycling animates items sliding through the strip.
 *
 * <h3>Display order</h3>
 *
 * The cycle is conceptually circular: [..., prev, current, next,
 * next-next, ...]. The HUD shows a window of up to 4 positions:
 *
 * <ul>
 *   <li>N=2 (1 inv + hotbar): [current, other]. Highlight at slot 0.
 *       Special case — with only 2 items in the cycle, there's no
 *       distinct "prev" and "next," so we don't fake it.</li>
 *   <li>N=3 (2 inv + hotbar): [prev, current, next]. Highlight at
 *       slot 1.</li>
 *   <li>N=4 (3 inv + hotbar): [prev, current, next, next-next].
 *       Highlight at slot 1.</li>
 * </ul>
 *
 * <p>Mapping to the cycle list ([s0, s1, ..., s_(N-2), hotbar]):
 * <ul>
 *   <li>prev = cycle[0] (= one BACKWARD from current — what'd come to
 *       hand if you cycle backward; the topmost inv slot wraps to
 *       hotbar on backward rotation)</li>
 *   <li>current = cycle[N-1] (= hotbar's item)</li>
 *   <li>next = cycle[N-2] (= one FORWARD from current — the slot just
 *       above hotbar comes to hand on forward rotation)</li>
 *   <li>next-next = cycle[N-3] (= two FORWARD from current)</li>
 * </ul>
 *
 * <h3>Slide animation</h3>
 *
 * Composed via {@link ColumnCyclerRotator}'s rotation-listener hook.
 * When a rotation fires, the HUD captures direction + start timestamp
 * and animates over {@link #ANIMATION_DURATION_MILLIS} milliseconds.
 *
 * <p>Items render at their NEW positions plus a horizontal slide
 * offset that decays from full slot-width at p=0 to zero at p=1. The
 * slide direction depends on rotation direction:
 * <ul>
 *   <li>FORWARD: items slide right-to-left (per Trev's design call —
 *       "right to left is forward"). Offset is positive at p=0 →
 *       items start one slot to the right of their NEW positions.</li>
 *   <li>BACKWARD: items slide left-to-right. Offset is negative at
 *       p=0 → items start one slot to the left of their NEW
 *       positions.</li>
 * </ul>
 *
 * <p>The wrap-around item (which moves across the entire cycle) gets
 * a duplicate render on the "opposite edge" so it visually slides in
 * from one side while the original slides out the other. Scissor
 * clipping hides items beyond the strip's visible bounds.
 *
 * <h3>Positioning</h3>
 *
 * The strip's left edge sits {@link #STRIP_GAP_FROM_HOTBAR_PX} pixels
 * to the right of the vanilla hotbar's right edge, bottom-aligned
 * with the hotbar. Vanilla hotbar is centered at screen-bottom with
 * total width 182px; its right edge is at {@code screenW/2 + 91}.
 *
 * <p>MenuKit's {@code HudRegion} system doesn't have an anchor for
 * "right-of-vanilla-hotbar" (regions are screen-edge-relative, not
 * hotbar-relative) — so we use {@code onRender} with manual position
 * computation. The {@code HudRegion.BOTTOM_CENTER} on the builder is
 * a no-op for our actual rendering; it just gives MenuKit something
 * to register the panel against. The render callback computes its own
 * coordinates.
 */
public final class ColumnCyclerHud {

    private ColumnCyclerHud() {}

    // ─── Layout constants (pixels in gui-scaled coordinates) ─────────

    /** One cycle slot's width — matches the vanilla hotbar's 20px slot stride. */
    private static final int SLOT_PX = 20;

    /** 1px outer border on the strip's left and right edges — matches vanilla hotbar. */
    private static final int OUTER_BORDER_PX = 1;

    /** Strip height = vanilla hotbar height. Slots are 20×22 in the vanilla sprite (the 22 includes top + bottom borders). */
    private static final int STRIP_HEIGHT_PX = 22;

    /** Visual gap between vanilla hotbar's right edge and our strip's left edge. */
    private static final int STRIP_GAP_FROM_HOTBAR_PX = 4;

    /** Half-width of the vanilla hotbar (182/2). Used to compute its right edge from screen center. */
    private static final int HOTBAR_HALF_WIDTH_PX = 91;

    /** Item is centered in the slot at offset 3 from slot top-left (matches vanilla & MKHudSlot). */
    private static final int ITEM_OFFSET_PX = 3;

    // ─── Vanilla sprites ─────────────────────────────────────────────

    /** The 182×22 hotbar background sprite. Same one vanilla {@code Gui} uses for the player hotbar. */
    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");

    /** Hotbar background sprite native dimensions. */
    private static final int HOTBAR_SPRITE_W = 182;
    private static final int HOTBAR_SPRITE_H = 22;

    /** The 24×23 chunky frame drawn over the selected hotbar slot. */
    private static final Identifier SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");

    /** Selection sprite native dimensions. */
    private static final int SELECTION_SPRITE_W = 24;
    private static final int SELECTION_SPRITE_H = 23;

    // ─── Animation state ─────────────────────────────────────────────

    /**
     * Total time for the slide animation. 220ms — gives the spring
     * curve room to overshoot and settle visibly without feeling slow.
     */
    private static final long ANIMATION_DURATION_MILLIS = 220L;

    /** -1 when no animation in flight; else the column index that's animating. */
    private static int animatingColumn = -1;

    /** System millis when the current animation started. */
    private static long animationStartMillis = 0L;

    /** Direction of the in-flight animation. Null when not animating. */
    private static ColumnCyclerRotator.Direction animationDirection = null;

    // ─── Registration ────────────────────────────────────────────────

    /**
     * One-shot init: subscribes to rotation events and registers the
     * MKHudPanel. Call once from client init.
     */
    public static void register() {
        // Drive the slide animation from rotation events. Each rotation
        // (whether keybind-driven, scroll-driven, or future Auto Tool
        // Switch-driven via the HotbarCyclable bring-to-hotbar path)
        // captures column + direction + timestamp here.
        ColumnCyclerRotator.addRotationListener(ColumnCyclerHud::onRotation);

        // Register with MenuKit. The HudRegion + autoSize + zero
        // elements give a 0x0 panel that doesn't render anything via
        // MenuKit's normal element pipeline; our onRender callback does
        // all the work with manual positioning.
        MKHudPanel.builder("inventoryplus-column-cycler-mini-hotbar")
                .region(HudRegion.BOTTOM_CENTER)
                .autoSize()
                .showWhen(ColumnCyclerHud::shouldShow)
                .onRender(ColumnCyclerHud::render)
                .build();
    }

    /** Listener fired by ColumnCyclerRotator after every actual rotation. */
    private static void onRotation(int column, ColumnCyclerRotator.Direction direction) {
        animatingColumn = column;
        animationStartMillis = System.currentTimeMillis();
        animationDirection = direction;
    }

    // ─── Visibility gate ─────────────────────────────────────────────

    /**
     * Returns true if the HUD should render this frame. Three gates:
     * <ul>
     *   <li>HUD mode is MINI_HOTBAR (player hasn't disabled it).</li>
     *   <li>Column Cycler feature itself is enabled.</li>
     *   <li>The currently-selected hotbar slot is a cycle slot — i.e.,
     *       the player is "on" a cycle. Otherwise the HUD has nothing
     *       meaningful to show.</li>
     * </ul>
     *
     * <p>MenuKit calls this once per frame; cheap O(1) checks only.
     */
    private static boolean shouldShow() {
        if (IPConfig.columnCyclerHudMode() != HudMode.MINI_HOTBAR) return false;
        if (!IPConfig.columnCyclerEnabled()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        return ColumnCycler.isCycleSlot(selectedSlot);
    }

    // ─── Rendering ───────────────────────────────────────────────────

    private static void render(GuiGraphics graphics, int unusedX, int unusedY, int unusedW, int unusedH, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        int activeColumn = inv.getSelectedSlot();
        // Guard: selected slot may not be in [0,8] in edge cases.
        if (activeColumn < 0 || activeColumn > 8) return;

        // Snapshot the cycle list for the active column. Build the
        // display order from it.
        List<Integer> cycleSlots = buildCycleSlots(activeColumn);
        int n = cycleSlots.size();
        if (n < 2) return; // no real cycle (single member or none)
        List<ItemStack> displayItems = computeDisplayItems(inv, cycleSlots);

        // Compute strip position: right of vanilla hotbar, bottom-aligned.
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int stripWidth = n * SLOT_PX + 2 * OUTER_BORDER_PX;
        int stripX = screenW / 2 + HOTBAR_HALF_WIDTH_PX + STRIP_GAP_FROM_HOTBAR_PX;
        int stripY = screenH - STRIP_HEIGHT_PX;

        // Compute animation progress and slide offset.
        // progress 1.0 = animation complete (items at NEW positions, no slide).
        // progress can briefly exceed 1.0 during the spring curve's
        // overshoot phase — items briefly pass their NEW positions and
        // settle back. The slide-offset math below handles this naturally
        // (negative offset = items slightly past target in the slide direction).
        float progress = 1.0f;
        ColumnCyclerRotator.Direction direction = null;
        if (animatingColumn == activeColumn && animationDirection != null) {
            long elapsed = System.currentTimeMillis() - animationStartMillis;
            if (elapsed < ANIMATION_DURATION_MILLIS) {
                float t = (float) elapsed / ANIMATION_DURATION_MILLIS;
                // Spring curve — ease-out-back. Overshoots ~10% past
                // target around t=0.58, settles to exactly 1.0 at t=1.0.
                // Gives the "bounce" feel Trev asked for without going
                // into full spring-physics simulation.
                progress = easeOutBack(t);
                direction = animationDirection;
            } else {
                // Animation finished — clear state.
                animatingColumn = -1;
                animationDirection = null;
            }
        }
        // Slide offset in pixels. At p=0: items start ±1 slot from NEW
        // positions. At p=1: items at NEW positions (offset 0). The
        // direction sign matches Trev's "right-to-left is forward" call:
        // FORWARD → items shift left over time → offset starts positive
        // (one slot right) and decays to 0.
        float remainingFraction = 1.0f - progress;
        int slideOffsetPx = 0;
        if (direction != null) {
            if (direction == ColumnCyclerRotator.Direction.FORWARD) {
                slideOffsetPx = Math.round(remainingFraction * SLOT_PX);
            } else { // BACKWARD
                slideOffsetPx = -Math.round(remainingFraction * SLOT_PX);
            }
        }

        // Background — simple vanilla-hotbar-ish gray with a 1px dark
        // border. (Not pixel-perfect vanilla; intentional v1 simplicity.)
        renderStripBackground(graphics, stripX, stripY, stripWidth);

        // Render items, clipping to the slot area so any item sliding
        // off-edge during animation is cleanly cut at the strip's
        // visual border. Scissor uses gui-scaled coordinates, matching
        // what we're drawing with. Note: the slot area excludes the 1px
        // outer borders left and right (so items don't bleed over them);
        // vertically we use the full strip height since item rendering
        // already insets via ITEM_OFFSET_PX.
        int slotAreaLeft = stripX + OUTER_BORDER_PX;
        int slotAreaTop = stripY;
        int slotAreaRight = stripX + stripWidth - OUTER_BORDER_PX;
        int slotAreaBottom = stripY + STRIP_HEIGHT_PX;
        graphics.enableScissor(slotAreaLeft, slotAreaTop, slotAreaRight, slotAreaBottom);
        try {
            // Render each item at its NEW HUD position plus the slide
            // offset. During animation the offset positions items at
            // their OLD positions (at p=0) and lerps them to their NEW
            // positions (at p=1).
            for (int k = 0; k < n; k++) {
                int itemX = slotAreaLeft + k * SLOT_PX + slideOffsetPx;
                renderItem(graphics, mc, displayItems.get(k), itemX, slotAreaTop);
            }
            // Wrap-item duplicate: during animation, render the
            // "leaving" item on the opposite edge so it appears to
            // slide in from one side as it slides out the other. The
            // duplicate uses the SAME slideOffsetPx; the difference is
            // its base virtual position.
            //
            // FORWARD: the item at NEW pos N-1 is the wrap (it was at
            // OLD pos 0 — leftmost — and is now at rightmost). At p=0
            // we need its duplicate visible at HUD pos 0; at p=1 the
            // duplicate slides off the left edge.
            //
            // BACKWARD: mirror — the item at NEW pos 0 wraps; its
            // duplicate sits at virtual pos N (off-screen right at p=1,
            // at HUD pos N-1 at p=0).
            if (direction != null && progress < 1.0f) {
                int wrapItemIndex;
                int wrapBaseVirtualPx;
                if (direction == ColumnCyclerRotator.Direction.FORWARD) {
                    wrapItemIndex = n - 1;
                    wrapBaseVirtualPx = -SLOT_PX; // virtual pos -1
                } else {
                    wrapItemIndex = 0;
                    wrapBaseVirtualPx = n * SLOT_PX; // virtual pos N
                }
                int wrapItemX = slotAreaLeft + wrapBaseVirtualPx + slideOffsetPx;
                renderItem(graphics, mc, displayItems.get(wrapItemIndex), wrapItemX, slotAreaTop);
            }
        } finally {
            graphics.disableScissor();
        }

        // Highlight — fixed position, never slides. Position depends on
        // N per Trev's spec: pos 1 for N>=3, pos 0 for N=2.
        int highlightSlotIndex = (n == 2) ? 0 : 1;
        int highlightX = slotAreaLeft + highlightSlotIndex * SLOT_PX;
        int highlightY = slotAreaTop;
        renderHighlight(graphics, highlightX, highlightY);
    }

    /**
     * Build the cycle list for {@code column} — same logic as
     * {@link ColumnCyclerRotator#planBringSlotToHotbar}'s internal
     * builder. Returns inv cycle members in visual top→bottom order,
     * with the hotbar slot appended last. Empty when the column has no
     * cycle members.
     */
    private static List<Integer> buildCycleSlots(int column) {
        if (column < 0 || column > 8) return Collections.emptyList();
        List<Integer> list = new ArrayList<>(4);
        int top = 9 + column, mid = 18 + column, bot = 27 + column;
        if (ColumnCycler.isCycleSlot(top)) list.add(top);
        if (ColumnCycler.isCycleSlot(mid)) list.add(mid);
        if (ColumnCycler.isCycleSlot(bot)) list.add(bot);
        if (list.isEmpty()) return Collections.emptyList();
        list.add(column); // hotbar slot last
        return list;
    }

    /**
     * Compute the items to show in HUD display order. See class javadoc
     * for the mapping rules.
     *
     * <p>Spec contract: position 1 is highlighted for N>=3 (it holds
     * the current hand item); position 0 is highlighted for N=2 (special
     * case — only 2 distinct items in cycle, no "prev" vs "next").
     */
    private static List<ItemStack> computeDisplayItems(Inventory inv, List<Integer> cycleSlots) {
        int n = cycleSlots.size();
        List<ItemStack> items = new ArrayList<>(n);
        if (n == 2) {
            // [current, other]. Highlight at slot 0.
            items.add(inv.getItem(cycleSlots.get(1))); // current = hotbar
            items.add(inv.getItem(cycleSlots.get(0))); // other = inv slot
        } else {
            // [prev, current, next, next-next, ...]. Highlight at slot 1.
            // prev = cycleSlots[0] (topmost inv = one BACKWARD from current
            //   = comes to hand on backward rotation via wrap)
            // current = cycleSlots[N-1] (hotbar)
            // next = cycleSlots[N-2] (slot just above hotbar = comes to
            //   hand on forward rotation)
            // next-next = cycleSlots[N-3]
            items.add(inv.getItem(cycleSlots.get(0)));      // prev
            items.add(inv.getItem(cycleSlots.get(n - 1)));  // current
            for (int k = n - 2; k >= 1; k--) {
                items.add(inv.getItem(cycleSlots.get(k)));
            }
        }
        return items;
    }

    /**
     * Render the strip background using the vanilla hotbar sprite.
     *
     * <p>Vanilla {@code hud/hotbar} is 182×22 — laid out as 1px left
     * outer border + 9 slots of 20px each + 1px right outer border.
     * For an N-slot strip we render three regions sourced from that
     * sprite: the 1px left border, an N×20-wide chunk of slots from
     * source x=1, and the 1px right border from source x=181.
     *
     * <p>This produces a pixel-perfect vanilla-hotbar look regardless
     * of resource pack — the same sprite the player's main hotbar uses.
     */
    private static void renderStripBackground(GuiGraphics graphics, int x, int y, int width) {
        int slotsCount = (width - 2 * OUTER_BORDER_PX) / SLOT_PX;
        int slotsLeftX = x + OUTER_BORDER_PX;
        // 1) Left outer border (1×22, from sprite x=0).
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, 0, 0,
                x, y, OUTER_BORDER_PX, STRIP_HEIGHT_PX);
        // 2) N slots in a single wide blit (slotsCount × 20 wide, from
        //    sprite x=1). This reads slotsCount * 20 source pixels —
        //    capped at 8 slots' worth (160 px) by IP design, well within
        //    the sprite's 180px slot region.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, OUTER_BORDER_PX, 0,
                slotsLeftX, y, slotsCount * SLOT_PX, STRIP_HEIGHT_PX);
        // 3) Right outer border (1×22, from sprite x=181).
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE,
                HOTBAR_SPRITE_W, HOTBAR_SPRITE_H, HOTBAR_SPRITE_W - OUTER_BORDER_PX, 0,
                slotsLeftX + slotsCount * SLOT_PX, y, OUTER_BORDER_PX, STRIP_HEIGHT_PX);
    }

    /**
     * Render a single item in a slot. Vanilla offset is 3px from slot
     * top-left (the slot is 20×22 with its borders; item is 16×16,
     * leaving a 2px gap inside the visible interior).
     */
    private static void renderItem(GuiGraphics graphics, Minecraft mc, ItemStack stack, int slotX, int slotY) {
        if (stack.isEmpty()) return;
        int itemX = slotX + ITEM_OFFSET_PX;
        int itemY = slotY + ITEM_OFFSET_PX;
        graphics.renderItem(stack, itemX, itemY);
        graphics.renderItemDecorations(mc.font, stack, itemX, itemY);
    }

    /**
     * Render the selection highlight using vanilla's
     * {@code hud/hotbar_selection} sprite — the same chunky 24×23 frame
     * vanilla draws around the player's selected hotbar slot.
     *
     * <p>The sprite has a 1px overlap on the left, right, and top
     * (so it visually bleeds out past the slot's 20px width and the
     * strip's 22px height). slotX/slotY parameters point to the slot's
     * top-left; the sprite is drawn at (slotX - 1, slotY - 1).
     */
    private static void renderHighlight(GuiGraphics graphics, int slotX, int slotY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SELECTION_SPRITE,
                SELECTION_SPRITE_W, SELECTION_SPRITE_H, 0, 0,
                slotX - 1, slotY - 1, SELECTION_SPRITE_W, SELECTION_SPRITE_H);
    }

    /**
     * Spring-style easing — overshoots the target by ~10% around t=0.58
     * and settles to exactly 1.0 at t=1.0. Classic "ease-out-back" curve
     * from CSS animation libraries; gives the "bounce" Trev asked for
     * without going into full spring-physics simulation.
     *
     * <p>Math: {@code 1 + c3 * (t-1)^3 + c1 * (t-1)^2} with c1 = 1.70158,
     * c3 = c1 + 1. Pure polynomial, cheap to evaluate per frame.
     */
    private static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        final float c1 = 1.70158f;
        final float c3 = c1 + 1f;
        float tm1 = t - 1f;
        return 1f + c3 * tm1 * tm1 * tm1 + c1 * tm1 * tm1;
    }
}
