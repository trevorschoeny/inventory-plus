package com.trevorschoeny.inventoryplus.hotbarsets;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.group.VanillaSlotResolver;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Hotbar Sets on the player's own inventory screens.
 *
 * <ul>
 *   <li><b>Survival's inventory</b> has no list: while the key is held the selection box moves
 *       over the real rows, drawn over the screen's background, so under the items and under
 *       Inventory Plus's buttons.</li>
 *   <li><b>Creative's screen</b> always shows two faint cards peeking out under the hotbar, the
 *       sets behind it. While the key is held the list is a panel over the dimmed screen, with
 *       the screen's own hotbar, drawn again over the dimming, as its bottom entry.</li>
 * </ul>
 */
public final class HotbarSetsScreens {

    private HotbarSetsScreens() {}

    /** MenuKit's modal dim ({@code LayerPlan.DIM_COLOR}), so the list and the sets window match. */
    static final int DIM = 0xC0000000;
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath("menukit", "panel_raised");
    private static final int LABEL = 0xFF404040;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen inv) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
                    if (HotbarSetsInput.isOpen()) drawInventorySelection(g, inv);
                });
            } else if (screen instanceof CreativeModeInventoryScreen cs) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
                    if (IPConfig.hotbarSetsEnabled()) drawCards(g, cs);
                });
                ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                    if (HotbarSetsInput.isOpen()) drawList(g, cs);
                });
            }
        });
    }

    /** The screen position of container slot {@code index}'s item box, or null where it isn't shown. */
    static @Nullable SlotScreenRect slot(AbstractContainerScreen<?> screen, int index) {
        return VanillaSlotResolver.resolve(screen, index).orElse(null);
    }

    // Survival: the selection over the real rows

    private static void drawInventorySelection(GuiGraphicsExtractor g, InventoryScreen screen) {
        int selected = HotbarSetsInput.selected();
        Player player = Minecraft.getInstance().player;
        HotbarSets.Entry entry = player == null ? null : HotbarSets.entryAt(player, selected);
        int first = entry == null ? 0 : HotbarSets.firstSlotOf(entry.index());
        SlotScreenRect rect = slot(screen, first);
        if (rect != null) selectRow(g, rect);
    }

    /** The widened selection box around a row of nine slots whose first item box is {@code rect}. */
    static void selectRow(GuiGraphicsExtractor g, SlotScreenRect rect) {
        HotbarSetsHud.wideSelection(g, rect.x() - 4, rect.y() - 4, 168, 24);
    }

    // Creative: the cards under the hotbar

    /**
     * Two cards peeking out under the hotbar, always two, veiled in the frame's grey so their
     * items barely show: the saved sets nearest the hotbar, or empty cards while there are
     * fewer. Then the hotbar's own slot frames again, on top of them.
     */
    private static void drawCards(GuiGraphicsExtractor g, CreativeModeInventoryScreen screen) {
        SlotScreenRect hotbar = slot(screen, 0);
        if (hotbar == null) return;
        int x = hotbar.frameX(), y = hotbar.frameY();
        List<List<ItemStack>> sets = HotbarSets.savedSets();
        for (int k = 2; k >= 1; k--) {
            int cy = y + 2 * k;
            List<ItemStack> items = sets.size() >= k ? sets.get(sets.size() - k) : List.of();
            for (int c = 0; c < HotbarSets.COLUMNS; c++) {
                SlotRendering.drawSlotBackground(g, x + c * 18, cy, 18, false);
                if (c < items.size()) g.item(items.get(c), x + c * 18 + 1, cy + 1);
            }
            g.fill(x, cy, x + 162, cy + 18, 0xC8C6C6C6);
        }
        for (int c = 0; c < HotbarSets.COLUMNS; c++) SlotRendering.drawSlotBackground(g, x + c * 18, y, 18, false);
    }

    // Creative: the list while the key is held

    private static void drawList(GuiGraphicsExtractor g, CreativeModeInventoryScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        SlotScreenRect hotbar = slot(screen, 0);
        if (player == null || hotbar == null) return;
        Font font = mc.font;
        g.fill(0, 0, screen.width, screen.height, DIM);

        int x = hotbar.frameX(), hy = hotbar.frameY();
        List<HotbarSets.Entry> entries = HotbarSets.entries(player);
        int y = hy - 6;
        int[] rowY = new int[entries.size()];
        int[][] labels = new int[2][];
        for (int i = 0; i < entries.size(); i++) {
            HotbarSets.Entry e = entries.get(i);
            if (i > 0 && e.saved() != entries.get(i - 1).saved()) y -= 6;
            y -= 20;
            rowY[i] = y;
            boolean groupEnd = i == entries.size() - 1 || entries.get(i + 1).saved() != e.saved();
            if (groupEnd) {
                y -= 11;
                labels[e.saved() ? 1 : 0] = new int[] {x, y + 1};
            }
        }
        if (!entries.isEmpty()) g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x - 5, y - 4, 162 + 10, hy - 4 - (y - 4));
        if (labels[0] != null) g.text(font, "Swap with inventory", labels[0][0], labels[0][1], LABEL, false);
        if (labels[1] != null) g.text(font, "Load saved set", labels[1][0], labels[1][1], LABEL, false);
        for (int i = 0; i < entries.size(); i++) row(g, font, x, rowY[i], entries.get(i).items(player));
        hotbarThroughDim(g, screen);
        int selected = HotbarSetsInput.selected();
        int sy = selected == 0 ? hy : rowY[selected - 1];
        HotbarSetsHud.wideSelection(g, x - 3, sy - 3, 168, 24);
    }

    /** Nine slot frames at {@code (x, y)} with {@code items} in them. */
    static void row(GuiGraphicsExtractor g, Font font, int x, int y, List<ItemStack> items) {
        for (int c = 0; c < HotbarSets.COLUMNS; c++) {
            SlotRendering.drawSlotBackground(g, x + c * 18, y, 18, false);
            if (c >= items.size()) continue;
            g.item(items.get(c), x + c * 18 + 1, y + 1);
            g.itemDecorations(font, items.get(c), x + c * 18 + 1, y + 1);
        }
    }

    /** The creative screen's hotbar, drawn again over the dimming. */
    static void hotbarThroughDim(GuiGraphicsExtractor g, CreativeModeInventoryScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        SlotScreenRect hotbar = slot(screen, 0);
        if (mc.player == null || hotbar == null) return;
        List<ItemStack> items = new java.util.ArrayList<>(HotbarSets.COLUMNS);
        for (int c = 0; c < HotbarSets.COLUMNS; c++) items.add(mc.player.getInventory().getItem(c));
        row(g, mc.font, hotbar.frameX(), hotbar.frameY(), items);
    }
}
