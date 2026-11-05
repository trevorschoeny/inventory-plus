package com.trevorschoeny.inventoryplus.hotbarsets;

import com.trevorschoeny.inventoryplus.mixin.HudAccessor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The list on the HUD: layers of the hotbar itself, its own art stacked straight up from the
 * hotbar, no panel. Layers sit 20 px apart, as slots do across, each one's outline over the
 * one below; the hotbar and each group keep a small gap. In creative each group has a label.
 * Drawn last of every HUD element, so it's in front of the hearts, the XP bar and the player
 * list for as long as the key is held.
 */
public final class HotbarSetsHud {

    private HotbarSetsHud() {}

    private static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
    /** The hotbar's selected-slot sprite as a plain texture, so it can be drawn in parts. */
    private static final Identifier SELECTION = Identifier.withDefaultNamespace("textures/gui/sprites/hud/hotbar_selection.png");

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("inventoryplus", "hotbar_sets"), HotbarSetsHud::draw);
    }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!HotbarSetsInput.isOpen() || player == null || mc.gui.screen() != null) return;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int hx = sw / 2 - 91, hy = sh - 22;
        boolean creative = player.isCreative();
        List<HotbarSets.Entry> entries = HotbarSets.entries(player);
        int y = hy - 3; // a little apart from the hotbar
        int[] layerY = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            HotbarSets.Entry e = entries.get(i);
            boolean groupStart = i == 0 || e.saved() != entries.get(i - 1).saved();
            if (groupStart && i > 0) y -= 4;
            // A group's first layer stands clear; the rest overlap the outline below, 20 apart.
            y -= groupStart ? 22 : 20;
            layerY[i] = y;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, hx, y, 182, 22);
            List<ItemStack> items = e.items(player);
            for (int c = 0; c < HotbarSets.COLUMNS; c++) {
                ((HudAccessor) mc.gui.hud).inventoryPlus$extractSlot(g, hx + 3 + c * 20, y + 3, delta, player, items.get(c), i * 9 + c + 1);
            }
            boolean groupEnd = i == entries.size() - 1 || entries.get(i + 1).saved() != e.saved();
            if (creative && groupEnd) {
                y -= 11;
                g.text(mc.font, e.saved() ? "Load saved set" : "Swap with inventory", hx + 1, y + 1, 0xFFFFFFFF, true);
            }
        }
        int selected = HotbarSetsInput.selected();
        wideSelection(g, hx - 1, (selected == 0 ? hy : layerY[selected - 1]) - 1, 184, 24);
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
