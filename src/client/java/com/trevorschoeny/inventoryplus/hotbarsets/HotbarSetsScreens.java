package com.trevorschoeny.inventoryplus.hotbarsets;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Floating;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.group.VanillaSlotResolver;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
 *       sets behind it. While the key is held the list is a MenuKit panel over the dimmed screen,
 *       its bottom on the hotbar, with the hotbar lit through the dim as its bottom entry.</li>
 * </ul>
 */
public final class HotbarSetsScreens {

    private HotbarSetsScreens() {}

    private static final int LABEL = 0xFF404040;
    private static final int PADDING = 4;

    public static void register() {
        registerList();
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen inv) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
                    if (HotbarSetsInput.isOpen()) drawInventorySelection(g, inv);
                });
            } else if (screen instanceof CreativeModeInventoryScreen cs) {
                ScreenEvents.afterBackground(screen).register((s, g, mx, my, d) -> {
                    if (IPConfig.hotbarSetsEnabled()) drawCards(g, cs);
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

    /**
     * The list as a MenuKit panel: its bottom 4 px over the hotbar, the screen dimmed behind it
     * and the hotbar lit, as the sets window does. It shows the entries and takes no input (the
     * key, the wheel and the cancel are {@link HotbarSetsInput}'s). The selection box on the
     * hotbar itself floats over the lit row, outside the panel.
     */
    private static void registerList() {
        Panel panel = Panel.builder("inventoryplus:hotbar_sets_list")
                .elements(List.of(new ListContent(),
                        Floating.at(HotbarSetsScreens::hotbarSelectionOrigin, new HotbarSelection())))
                .style(PanelStyle.RAISED)
                .visibleWhen(() -> HotbarSetsInput.isOpen() && creativeHotbar() != null)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixelBottomLeft(HotbarSetsScreens::listOrigin)))
                .build()
                .opaque(false)
                .dimsBehind(true)
                .litWhileDimmed(HotbarSetsScreens::litHotbar);
        new ScreenPanelAdapter(panel, PADDING).onPlayerInventory();
    }

    /** The creative screen's hotbar, or null when creative's screen isn't open. */
    static @Nullable SlotScreenRect creativeHotbar() {
        return Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen cs ? slot(cs, 0) : null;
    }

    private static @Nullable ScreenOrigin listOrigin() {
        SlotScreenRect h = creativeHotbar();
        return h == null ? null : new ScreenOrigin(h.frameX() - PADDING, h.frameY() - 4);
    }

    private static @Nullable ScreenOrigin hotbarSelectionOrigin() {
        SlotScreenRect h = creativeHotbar();
        return h == null ? null : new ScreenOrigin(h.x() - 4, h.y() - 4);
    }

    /** The hotbar row, lit through the dim: the list's bottom entry. */
    static List<ScreenRectangle> litHotbar() {
        SlotScreenRect h = creativeHotbar();
        return h == null ? List.of() : List.of(new ScreenRectangle(h.frameX(), h.frameY(), 9 * 18, 18));
    }

    /** The widened selection box on the hotbar itself, while it's the selected entry. */
    private static final class HotbarSelection extends AbstractPanelElement {
        HotbarSelection() {
            this.width = 168;
            this.height = 24;
            this.authoredWidth = width;
        }

        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }

        @Override
        public void render(RenderContext ctx) {
            if (HotbarSetsInput.selected() != 0) return;
            HotbarSetsHud.wideSelection(ctx.graphics(), ctx.originX() + getChildX(), ctx.originY() + getChildY(), width, height);
        }
    }

    /**
     * The entries above the hotbar, top down: each group's label, then its rows, a gap between
     * groups. Sized from the entries every frame, so the panel grows upward as sets are saved.
     */
    private static final class ListContent extends AbstractPanelElement {
        private static final int ROW = 20, GROUP_GAP = 6, LABEL_ROW = 11;

        ListContent() {
            this.width = 9 * 18;
            this.authoredWidth = width;
        }

        private static List<HotbarSets.Entry> entries() {
            Player player = Minecraft.getInstance().player;
            return player == null ? List.of() : HotbarSets.entries(player);
        }

        @Override public int getWidth() { return width; }

        @Override
        public int getHeight() {
            List<HotbarSets.Entry> entries = entries();
            int h = 0;
            for (int i = 0; i < entries.size(); i++) {
                boolean groupStart = i == 0 || entries.get(i).saved() != entries.get(i - 1).saved();
                if (groupStart) h += LABEL_ROW + (i > 0 ? GROUP_GAP : 0);
                h += ROW;
            }
            return Math.max(0, h - 2);
        }

        @Override
        public void render(RenderContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;
            GuiGraphicsExtractor g = ctx.graphics();
            Font font = mc.font;
            int x = ctx.originX() + getChildX();
            // Bottom up from the content's bottom, as the HUD stacks them.
            int y = ctx.originY() + getChildY() + getHeight() + 2;
            List<HotbarSets.Entry> entries = entries();
            int selected = HotbarSetsInput.selected();
            for (int i = 0; i < entries.size(); i++) {
                HotbarSets.Entry e = entries.get(i);
                if (i > 0 && e.saved() != entries.get(i - 1).saved()) y -= GROUP_GAP;
                y -= ROW;
                row(g, font, x, y, e.items(player));
                if (selected == i + 1) HotbarSetsHud.wideSelection(g, x - 3, y - 3, 168, 24);
                boolean groupEnd = i == entries.size() - 1 || entries.get(i + 1).saved() != e.saved();
                if (groupEnd) {
                    y -= LABEL_ROW;
                    g.text(font, e.saved() ? "Load saved set" : "Swap with inventory", x, y + 1, LABEL, false);
                }
            }
        }
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
}
