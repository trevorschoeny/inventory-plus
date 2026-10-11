package com.trevorschoeny.inventoryplus.columncycler.hud;

import com.trevorschoeny.inventoryplus.api.CycleHudSource;
import com.trevorschoeny.inventoryplus.api.CycleView;
import com.trevorschoeny.inventoryplus.api.CyclerDirection;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.CycleHudRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Column Cycler's contribution to the shared cycle HUD ({@link CycleHudSource}).
 *
 * <p>Holds the column-specific parts: the gating (feature enabled, HUD mode
 * MINI_HOTBAR, and the selected slot is a column cycle slot) and the
 * cycle-contents math (which inventory slots form the column's cycle). The
 * generic render lives in {@link com.trevorschoeny.inventoryplus.cyclable.CycleHud}.
 *
 * <p>Also bridges {@link ColumnCyclerRotator}'s rotation events to the shared
 * animation clock so the HUD slides on a column rotation, without the rotator
 * knowing about the HUD.
 */
public final class ColumnCyclerHudSource implements CycleHudSource {

    /** Registered once with {@link CycleHudRegistry} at client init. */
    public static final ColumnCyclerHudSource INSTANCE = new ColumnCyclerHudSource();

    private ColumnCyclerHudSource() {}

    /**
     * Registers this source with the shared HUD registry and bridges column
     * rotation events into the shared animation clock. Call once at client init.
     */
    public static void register() {
        CycleHudRegistry.register(INSTANCE);
        // Maps the column-internal Direction to the cycler-neutral
        // CyclerDirection. The column index is the hotbar slot (0 to 8).
        ColumnCyclerRotator.addRotationListener((column, dir) ->
                CycleHudRegistry.fireCycleAnimation(
                        INSTANCE,
                        column,
                        dir == ColumnCyclerRotator.Direction.FORWARD
                                ? CyclerDirection.FORWARD
                                : CyclerDirection.BACKWARD));
    }

    /**
     * Column Cycler stacks above the bottom-pinned Pocket Cycler when both are
     * active on the same hotbar slot. Uses the interface default rank (100);
     * declared explicitly so the stacking intent is visible here.
     */
    @Override
    public int hudStackOrder() {
        return 100;
    }

    /** A column is vertical, alone or as the cross's vertical arm. */
    @Override
    public boolean vertical() {
        return true;
    }

    @Override
    public CycleView cycleViewForHotbar(int hotbarSlot) {
        // All three must hold or this source contributes nothing.
        if (IPConfig.columnCyclerHudMode() != HudMode.MINI_HOTBAR) return null;
        if (!IPConfig.columnCyclerEnabled()) return null;
        if (!ColumnCycler.isCycleSlot(hotbarSlot)) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        Inventory inv = mc.player.getInventory();

        List<Integer> cycleSlots = buildCycleSlots(hotbarSlot);
        int n = cycleSlots.size();
        if (n < 2) return null;

        // Items in visual top-to-bottom order (hotbar last). The shared helper
        // applies the prev/current/next display convention and highlight.
        List<ItemStack> visual = new ArrayList<>(n);
        for (int slot : cycleSlots) {
            visual.add(inv.getItem(slot));
        }
        return CycleView.fromVisualOrder(visual);
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
