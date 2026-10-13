package com.trevorschoeny.inventoryplus.columncycler.hud;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevlar.menukit.api.hud.HudCycleStrip;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Column Cycler's ring on MenuKit's cycle strip ({@link HudCycleStrip}, MenuKit
 * 7.1.0): a {@link HudCycleStrip.Axis#COLUMN} ring, so the strip draws the held
 * stack beside the hotbar with the rest of the column rising above it, the way
 * the column sits in the inventory. MenuKit does all the drawing; this says
 * what the column holds and when it turned.
 */
public final class ColumnCyclerHudSource implements HudCycleStrip.Source {

    /** Registered once with MenuKit at client init. */
    public static final ColumnCyclerHudSource INSTANCE = new ColumnCyclerHudSource();

    private ColumnCyclerHudSource() {}

    /**
     * Registers the source and slides the strip after each column rotation, so
     * the rotator never knows about the HUD. Call once at client init, before
     * MenuKit's declarations freeze at client start.
     */
    public static void register() {
        HudCycleStrip.register(INSTANCE);
        // The column index is the hotbar slot (0 to 8). FORWARD brings the
        // stack above the hotbar into the hand, which is MenuKit's FORWARD too.
        ColumnCyclerRotator.addRotationListener((column, dir) ->
                HudCycleStrip.slide(INSTANCE, column, dir == ColumnCyclerRotator.Direction.FORWARD
                        ? HudCycleStrip.Direction.FORWARD
                        : HudCycleStrip.Direction.BACKWARD));
    }

    @Override
    public HudCycleStrip.Axis axis() {
        return HudCycleStrip.Axis.COLUMN;
    }

    @Override
    public HudCycleStrip.@Nullable View viewFor(int hotbarSlot) {
        // All three must hold or this source shows nothing.
        if (IPConfig.columnCyclerHudMode() != HudMode.MINI_HOTBAR) return null;
        if (!IPConfig.columnCyclerEnabled()) return null;
        if (!ColumnCycler.isCycleSlot(hotbarSlot)) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Inventory inv = mc.player.getInventory();

        List<Integer> cycleSlots = buildCycleSlots(hotbarSlot);
        if (cycleSlots.size() < 2) return null;

        // Top to bottom, hotbar last. A forward turn moves every stack one
        // down and the hotbar's to the top, so the stack that reaches the hand
        // next is the one just above it: read bottom-up, held first.
        List<ItemStack> topToBottom = new ArrayList<>(cycleSlots.size());
        for (int slot : cycleSlots) topToBottom.add(inv.getItem(slot));
        return new HudCycleStrip.View(topToBottom.reversed(), 0);
    }

    /**
     * The column's cycle members in visual top-to-bottom order, hotbar last.
     * Mirrors {@code ColumnCyclerRotator}'s internal list shape.
     */
    private static List<Integer> buildCycleSlots(int column) {
        List<Integer> list = new ArrayList<>(4);
        int top = 9 + column, mid = 18 + column, bot = 27 + column;
        if (ColumnCycler.isCycleSlot(top)) list.add(top);
        if (ColumnCycler.isCycleSlot(mid)) list.add(mid);
        if (ColumnCycler.isCycleSlot(bot)) list.add(bot);
        if (list.isEmpty()) return list;
        list.add(column); // hotbar slot last
        return list;
    }
}
