package com.trevorschoeny.inventoryplus;


import com.trevorschoeny.inventoryplus.InventoryPlusConfig;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MKHudAnchor;
import com.trevorschoeny.menukit.MKHudPanel;
import com.trevorschoeny.menukit.MKPanel;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HUD overlay showing pocket item previews above the hotbar.
 *
 * <p>The selected hotbar slot shows its pocket contents as scaled-down item
 * sprites arranged in an A-shape triangle above the hotbar:
 * <pre>
 *       [1]        ← pocket slot 1, centered on top
 *     [0]   [2]    ← pocket slots 0 and 2, bottom row spread apart
 *    ┌──────────┐
 *    │  HOTBAR  │
 *    └──────────┘
 * </pre>
 *
 * <p>On cycling, items animate between positions with smooth interpolation.
 * The hotbar item is covered and re-rendered at an animated scale so it
 * participates in the rotation animation alongside the pocket items.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class PocketHud {

    private static final int POCKET_SIZE = PocketCycler.POCKET_SIZE;

    // Item rendering scale for pocket items
    private static final float ITEM_SCALE = 0.6f;
    private static final float SCALED_SIZE = 16 * ITEM_SCALE;

    // Vanilla hotbar layout constants
    private static final int HOTBAR_WIDTH = 182;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_SLOT_WIDTH = 20;
    private static final int HOTBAR_LEFT_PAD = 1;
    private static final Identifier HOTBAR_SPRITE =
            Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION_SPRITE =
            Identifier.withDefaultNamespace("hud/hotbar_selection");

    // A-shape layout spacing
    private static final int BOTTOM_ROW_SPACING = 11;
    private static final int GAP = 1;

    // Animation
    private static final long ANIMATION_DURATION_MS = 300;
    private static float animProgress = 1.0f;
    private static long animStartTime = 0;
    private static boolean animForward;
    private static int animHotbarSlot = -1;
    private static ItemStack[] animOldItems = null; // 0=hotbar, 1=pocket0, 2=pocket1, 3=pocket2
    private static List<Integer> animEnabledPositions = null;

    public static void register() {
        int hudWidth = HOTBAR_WIDTH;
        int hudHeight = (int)(SCALED_SIZE * 2) + GAP + 20;

        MKHudPanel.builder("pocket_hud")
                .anchor(MKHudAnchor.BOTTOM_CENTER, 0, 0)
                .autoSize()
                .style(MKPanel.Style.NONE)
                .showWhen(PocketHud::hasAnyPocketItems)
                .custom(0, 0, hudWidth, hudHeight, PocketHud::render)
                .build();

        InventoryPlus.LOGGER.info("[PocketHud] Registered");
    }

    /**
     * Snapshots the current state and starts the animation.
     * Called on the CLIENT thread BEFORE the server executes the swap.
     */
    public static void triggerAnimation(boolean forward) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int selected = mc.player.getInventory().getSelectedSlot();
        MKContainer pocket = MenuKit.getContainerForPlayer(
                "pocket_" + selected, mc.player.getUUID(), false);
        if (pocket == null) return;

        animOldItems = new ItemStack[1 + POCKET_SIZE];
        animOldItems[0] = mc.player.getInventory().getItem(selected).copy();
        for (int i = 0; i < POCKET_SIZE; i++) {
            animOldItems[i + 1] = pocket.getItem(i).copy();
        }

        Set<Integer> disabled = PocketsPanel.getDisabledSlots(selected);
        int maxSlots = InventoryPlusConfig.get().pocketSlotCount;
        animEnabledPositions = new ArrayList<>();
        animEnabledPositions.add(0);
        for (int i = 0; i < POCKET_SIZE; i++) {
            if (i < maxSlots && !disabled.contains(i)) {
                animEnabledPositions.add(i + 1);
            }
        }

        animForward = forward;
        animHotbarSlot = selected;
        animProgress = 0.0f;
        animStartTime = System.currentTimeMillis();
    }

    private static boolean hasAnyPocketItems() {
        InventoryPlusConfig cfg = InventoryPlusConfig.get();

        // Master toggle or HUD toggle off — hide entirely
        if (!cfg.enablePockets || !cfg.showPocketHud) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        int selected = mc.player.getInventory().getSelectedSlot();
        MKContainer pocket = MenuKit.getContainerForPlayer(
                "pocket_" + selected, mc.player.getUUID(), false);
        if (pocket == null) return false;

        // Show HUD whenever there's at least one enabled pocket slot
        // within the configured slot count — even if empty.
        Set<Integer> disabled = PocketsPanel.getDisabledSlots(selected);
        int maxSlots = cfg.pocketSlotCount;
        for (int i = 0; i < POCKET_SIZE; i++) {
            if (i < maxSlots && !disabled.contains(i)) return true;
        }
        return false;
    }

    // ── Screen positions ────────────────────────────────────────────────────
    // Position 0 = hotbar center, 1 = pocket 0 (bottom-left),
    // 2 = pocket 1 (top-center), 3 = pocket 2 (bottom-right)

    private static int posX(int pos, int slotCenterX, int scaledItem) {
        int bottomRowWidth = scaledItem * 2 + BOTTOM_ROW_SPACING;
        int bottomRowLeftX = slotCenterX - bottomRowWidth / 2;
        return switch (pos) {
            case 0 -> slotCenterX - 8;                                        // hotbar: center a 16px item
            case 1 -> bottomRowLeftX;                                         // bottom-left
            case 2 -> slotCenterX - scaledItem / 2;                          // top-center
            case 3 -> bottomRowLeftX + scaledItem + BOTTOM_ROW_SPACING;      // bottom-right
            default -> slotCenterX;
        };
    }

    private static int posY(int pos, int bottomRowY, int topRowY, int hotbarItemY) {
        return switch (pos) {
            case 0 -> hotbarItemY;      // hotbar item Y
            case 1 -> bottomRowY;       // bottom-left
            case 2 -> topRowY;          // top-center
            case 3 -> bottomRowY;       // bottom-right
            default -> bottomRowY;
        };
    }

    /** Scale at each position: 1.0 for hotbar, ITEM_SCALE for pocket positions. */
    private static float posScale(int pos) {
        return pos == 0 ? 1.0f : ITEM_SCALE;
    }

    private static int sourcePosition(int destPos, List<Integer> enabled, boolean forward) {
        int idx = enabled.indexOf(destPos);
        if (idx < 0) return destPos;

        if (forward) {
            int srcIdx = (idx + 1) % enabled.size();
            return enabled.get(srcIdx);
        } else {
            int srcIdx = (idx - 1 + enabled.size()) % enabled.size();
            return enabled.get(srcIdx);
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    private static void render(GuiGraphics graphics, int x, int y,
                               int width, int height, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenH = mc.getWindow().getGuiScaledHeight();
        int hotbarTop = screenH - HOTBAR_HEIGHT;
        int hotbarLeft = (mc.getWindow().getGuiScaledWidth() - HOTBAR_WIDTH) / 2;

        int scaledItem = (int) SCALED_SIZE;
        int bottomRowY = hotbarTop - scaledItem + 9;
        int topRowY = bottomRowY - scaledItem - GAP + 3;

        int selected = mc.player.getInventory().getSelectedSlot();
        int slotCenterX = hotbarLeft + HOTBAR_LEFT_PAD + selected * HOTBAR_SLOT_WIDTH + HOTBAR_SLOT_WIDTH / 2;

        // Hotbar item position (where vanilla renders the 16px item)
        int hotbarItemX = slotCenterX - 8;
        int hotbarItemY = hotbarTop + 3; // 3px padding inside the hotbar sprite

        MKContainer pocket = MenuKit.getContainerForPlayer(
                "pocket_" + selected, mc.player.getUUID(), false);
        if (pocket == null) return;

        Set<Integer> disabled = PocketsPanel.getDisabledSlots(selected);
        int maxSlots = InventoryPlusConfig.get().pocketSlotCount;

        // Advance animation
        boolean animating = animProgress < 1.0f && animOldItems != null
                && animHotbarSlot == selected && animEnabledPositions != null;
        if (animating) {
            long elapsed = System.currentTimeMillis() - animStartTime;
            animProgress = Math.min(1.0f, (float) elapsed / ANIMATION_DURATION_MS);
            if (animProgress >= 1.0f) {
                animating = false;
            }
        }

        // Ease-out cubic
        float ease = animating
                ? 1.0f - (1.0f - animProgress) * (1.0f - animProgress) * (1.0f - animProgress)
                : 1.0f;

        if (animating) {
            // ── Cover vanilla's hotbar item ─────────────────────────────
            // Use the selection highlight sprite — it's the exact shape of
            // the selected slot including the bright border. First fill black
            // to fully obscure the item, then draw the selection sprite on top.
            // The selection sprite is 24×23 and vanilla positions it 1px left
            // and 1px above the slot's item origin.
            int selX = hotbarItemX - 4;
            int selY = hotbarItemY - 4;
            graphics.fill(selX, selY, selX + 24, selY + 24, 0xFF000000);
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    HOTBAR_SELECTION_SPRITE,
                    selX, selY,
                    24, 23
            );

            // ── Animate all items in the rotation ───────────────────────
            // Render pocket-bound items first, hotbar-bound item last (on top).
            // Items crossing the hotbar boundary get a fade:
            //   - Going TO hotbar (pos == 0): fade out (1 → 0) as vanilla takes over
            //   - Coming FROM hotbar (srcPos == 0): fade in (0 → 1) from invisible
            float hotbarDrawX = 0, hotbarDrawY = 0, hotbarScale = 1.0f;
            ItemStack hotbarAnimItem = ItemStack.EMPTY;

            for (int pos : animEnabledPositions) {
                int srcPos = sourcePosition(pos, animEnabledPositions, animForward);

                ItemStack item = (srcPos >= 0 && srcPos < animOldItems.length)
                        ? animOldItems[srcPos] : ItemStack.EMPTY;
                if (item.isEmpty()) continue;

                // Interpolate position
                float srcXf = posX(srcPos, slotCenterX, scaledItem);
                float srcYf = posY(srcPos, bottomRowY, topRowY, hotbarItemY);
                float dstXf = posX(pos, slotCenterX, scaledItem);
                float dstYf = posY(pos, bottomRowY, topRowY, hotbarItemY);

                float drawX = srcXf + (dstXf - srcXf) * ease;
                float drawY = srcYf + (dstYf - srcYf) * ease;

                // Interpolate scale
                float srcScale = posScale(srcPos);
                float dstScale = posScale(pos);
                float scale = srcScale + (dstScale - srcScale) * ease;

                if (pos == 0) {
                    // Defer hotbar-bound item to render last (z-order on top)
                    hotbarDrawX = drawX;
                    hotbarDrawY = drawY;
                    hotbarScale = scale;
                    hotbarAnimItem = item;
                } else {
                    renderAnimatedItem(graphics, mc, item, drawX, drawY, scale);
                }
            }

            // Render hotbar-bound item on top of everything
            if (!hotbarAnimItem.isEmpty()) {
                renderAnimatedItem(graphics, mc, hotbarAnimItem, hotbarDrawX, hotbarDrawY, hotbarScale);
            }
        } else {
            // ── Static render ───────────────────────────────────────────
            // Render pocket items first, then re-render the hotbar item on
            // top so it's never obscured by overlapping pocket corners.
            if (0 < maxSlots && !disabled.contains(0)) {
                ItemStack stack0 = pocket.getItem(0);
                if (!stack0.isEmpty()) {
                    renderScaledItem(graphics, stack0,
                            posX(1, slotCenterX, scaledItem), bottomRowY);
                }
            }

            if (2 < maxSlots && !disabled.contains(2)) {
                ItemStack stack2 = pocket.getItem(2);
                if (!stack2.isEmpty()) {
                    renderScaledItem(graphics, stack2,
                            posX(3, slotCenterX, scaledItem), bottomRowY);
                }
            }

            if (1 < maxSlots && !disabled.contains(1)) {
                ItemStack stack1 = pocket.getItem(1);
                if (!stack1.isEmpty()) {
                    renderScaledItem(graphics, stack1,
                            posX(2, slotCenterX, scaledItem), topRowY);
                }
            }

            // No hotbar re-render at rest — vanilla's item sits naturally
            // and pocket icons overlay at small scale above it.
        }
    }

    /**
     * Renders an item at a specific position and scale, with decorations.
     * Used during animation for smooth scale transitions.
     */
    private static void renderAnimatedItem(GuiGraphics graphics, Minecraft mc,
                                            ItemStack stack, float x, float y, float scale) {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(mc.font, stack, 0, 0);
        pose.popMatrix();
    }

    /**
     * Renders an item at pocket scale (static, no animation), with decorations.
     */
    private static void renderScaledItem(GuiGraphics graphics, ItemStack stack,
                                          int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(ITEM_SCALE, ITEM_SCALE);
        graphics.renderItem(stack, 0, 0);
        graphics.renderItemDecorations(mc.font, stack, 0, 0);
        pose.popMatrix();
    }
}
