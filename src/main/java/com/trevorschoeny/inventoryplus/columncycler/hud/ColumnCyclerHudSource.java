package com.trevorschoeny.inventoryplus.columncycler.hud;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.CycleHudRegistry;
import com.trevorschoeny.inventoryplus.cyclable.CycleView;
import com.trevorschoeny.inventoryplus.cyclable.CycleHudSource;
import com.trevorschoeny.inventoryplus.cyclable.CyclerDirection;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Column Cycler's contribution to the shared cycle HUD ({@link CycleHudSource}).
 *
 * <p>Holds the column-specific bits the old {@code ColumnCyclerHud} mixed into
 * its render: the gating (feature enabled + HUD mode MINI_HOTBAR + the selected
 * slot is a column cycle slot) and the cycle-contents math (which inventory
 * slots form the column's cycle, and the prev/current/next display order). The
 * generic render lives in {@link com.trevorschoeny.inventoryplus.cyclable.CycleHud}.
 *
 * <p>Also bridges {@link ColumnCyclerRotator}'s rotation events to the shared
 * animation clock so the HUD slides on a column rotation — leaving the verified
 * rotator code untouched.
 */
public final class ColumnCyclerHudSource implements CycleHudSource {

    /** Singleton; registered once with {@link CycleHudRegistry} at client init. */
    public static final ColumnCyclerHudSource INSTANCE = new ColumnCyclerHudSource();

    private ColumnCyclerHudSource() {}

    /**
     * Registers this source with the shared HUD registry and bridges column
     * rotation events into the shared animation clock. Call once at client init.
     */
    public static void register() {
        CycleHudRegistry.register(INSTANCE);
        // Bridge: a real column rotation → the shared animation clock. Maps the
        // column-internal Direction to the cycler-neutral CyclerDirection. The
        // column index IS the hotbar slot (0–8).
        ColumnCyclerRotator.addRotationListener((column, dir) ->
                CycleHudRegistry.fireCycleAnimation(
                        INSTANCE,
                        column,
                        dir == ColumnCyclerRotator.Direction.FORWARD
                                ? CyclerDirection.FORWARD
                                : CyclerDirection.BACKWARD));
    }

    /**
     * Column Cycler stacks ABOVE the bottom-pinned Pocket Cycler when both are
     * active on the same hotbar slot. Uses the interface default rank (100);
     * declared explicitly so the stacking intent is visible here.
     */
    @Override
    public int hudStackOrder() {
        return 100;
    }

    /** Column Cycler is the vertical arm of the cross — a column is vertical. */
    @Override
    public boolean verticalInCross() {
        return true;
    }

    @Override
    public CycleView cycleViewForHotbar(int hotbarSlot) {
        // Gating — all three must hold or this source contributes nothing.
        if (IPConfig.columnCyclerHudMode() != HudMode.MINI_HOTBAR) return null;
        if (!IPConfig.columnCyclerEnabled()) return null;
        if (!ColumnCycler.isCycleSlot(hotbarSlot)) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        Inventory inv = mc.player.getInventory();

        List<Integer> cycleSlots = buildCycleSlots(hotbarSlot);
        int n = cycleSlots.size();
        if (n < 2) return null;

        // Items in visual top→bottom order (hotbar last); the shared helper
        // applies the prev/current/next display convention + highlight.
        List<ItemStack> visual = new ArrayList<>(n);
        for (int slot : cycleSlots) {
            visual.add(inv.getItem(slot));
        }
        return CycleView.fromVisualOrder(visual);
    }

    /**
     * The column's cycle members in visual top→bottom order, hotbar last.
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
