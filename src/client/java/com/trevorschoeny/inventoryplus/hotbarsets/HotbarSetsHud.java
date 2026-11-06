package com.trevorschoeny.inventoryplus.hotbarsets;

import com.trevorschoeny.inventoryplus.mixin.HudAccessor;
import com.trevlar.menukit.api.MK;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The list on the HUD: layers of the hotbar itself, its own art stacked straight up from the
 * hotbar, no panel look. Layers sit 20 px apart, as slots do across, each one's outline over
 * the one below; the hotbar and each group keep a small gap. In creative each group has a
 * label. A MenuKit HUD panel holds it: placed by its bottom edge just over the hotbar, so it
 * grows upward and isn't stacked with the hotbar's other panels, and drawn after vanilla's
 * HUD, over the hearts, the XP bar and the player list, for as long as the key is held. The
 * rows are vanilla's own art and slot drawing, so they're drawn here (MenuKit's call: no
 * primitive while only Inventory Plus draws hotbar layers).
 */
public final class HotbarSetsHud {

    private HotbarSetsHud() {}

    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    /** The hotbar's selected-slot sprite as a plain texture, so it can be drawn in parts. */
    private static final Identifier SELECTION = Identifier.withDefaultNamespace("textures/gui/sprites/hud/hotbar_selection.png");
    /** Between the hotbar and the first layer. */
    private static final int GAP = 3;

    public static void register() {
        Panel panel = Panel.builder("inventoryplus:hotbar_sets_hud")
                .elements(List.of(new Layers()))
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixelBottomLeft(HotbarSetsHud::origin)))
                .fixed()
                .build()
                .visibleWhen(() -> HotbarSetsInput.isOpen() && Minecraft.getInstance().gui.screen() == null);
        MK.registerHud(panel, 0);
    }

    /** The bottom-left just over the hotbar, or null with no player. */
    private static @Nullable ScreenOrigin origin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        return new ScreenOrigin(sw / 2 - 91, sh - 22 - GAP);
    }

    /** The layers, bottom up from the panel's bottom, with the selection box. */
    private static final class Layers extends AbstractPanelElement {
        private static final int LAYER = 22, STEP = 20, GROUP_GAP = 4, LABEL_ROW = 11;

        Layers() {
            this.width = 182;
            this.authoredWidth = width;
        }

        private static List<HotbarSets.Entry> entries() {
            Player player = Minecraft.getInstance().player;
            return player == null ? List.of() : HotbarSets.entries(player);
        }

        @Override public int getWidth() { return width; }

        @Override
        public int getHeight() {
            Player player = Minecraft.getInstance().player;
            List<HotbarSets.Entry> entries = entries();
            int h = 0;
            for (int i = 0; i < entries.size(); i++) {
                boolean groupStart = i == 0 || entries.get(i).saved() != entries.get(i - 1).saved();
                if (groupStart && i > 0) h += GROUP_GAP;
                h += groupStart ? LAYER : STEP;
                boolean groupEnd = i == entries.size() - 1 || entries.get(i + 1).saved() != entries.get(i).saved();
                if (groupEnd && player != null && player.isCreative()) h += LABEL_ROW;
            }
            return h;
        }

        @Override
        public void render(RenderContext ctx) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player == null) return;
            GuiGraphicsExtractor g = ctx.graphics();
            HudAccessor hud = (HudAccessor) mc.gui.hud;
            int x = ctx.originX() + getChildX();
            int bottom = ctx.originY() + getChildY() + getHeight();
            boolean creative = player.isCreative();
            List<HotbarSets.Entry> entries = entries();
            int y = bottom;
            int[] layerY = new int[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                HotbarSets.Entry e = entries.get(i);
                boolean groupStart = i == 0 || e.saved() != entries.get(i - 1).saved();
                if (groupStart && i > 0) y -= GROUP_GAP;
                // A group's first layer stands clear; the rest overlap the outline below, 20 apart.
                y -= groupStart ? LAYER : STEP;
                layerY[i] = y;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, x, y, 182, 22);
                List<ItemStack> items = e.items(player);
                for (int c = 0; c < HotbarSets.COLUMNS; c++) {
                    hud.inventoryPlus$extractSlot(g, x + 3 + c * 20, y + 3, mc.getDeltaTracker(), player, items.get(c), i * 9 + c + 1);
                }
                boolean groupEnd = i == entries.size() - 1 || entries.get(i + 1).saved() != e.saved();
                if (creative && groupEnd) {
                    y -= LABEL_ROW;
                    g.text(mc.font, e.saved() ? "Load saved set" : "Swap with inventory", x + 1, y + 1, 0xFFFFFFFF, true);
                }
            }
            // The hotbar itself sits just under the panel, the gap below its bottom.
            int selected = HotbarSetsInput.selected();
            int sy = selected == 0 ? bottom + GAP : layerY[selected - 1];
            wideSelection(g, x - 1, sy - 1, 184, 24);
        }
    }

    /**
     * The hotbar's selected-slot box (24 x 23) at {@code w} by {@code h}: its ends at their own
     * width, the middle stretched, so the border stays crisp at any width. Drawn from the
     * sprite's own texture, so a resource pack's box is the one widened.
     */
    public static void wideSelection(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int end = 4, tw = 24, th = 23;
        g.blit(RenderPipelines.GUI_TEXTURED, SELECTION, x, y, 0, 0, end, h, end, th, tw, th, 0xFFFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, SELECTION, x + end, y, end, 0, w - 2 * end, h, tw - 2 * end, th, tw, th, 0xFFFFFFFF);
        g.blit(RenderPipelines.GUI_TEXTURED, SELECTION, x + w - end, y, tw - end, 0, end, h, end, th, tw, th, 0xFFFFFFFF);
    }
}
