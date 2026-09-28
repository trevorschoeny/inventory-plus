package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.trevorschoeny.inventoryplus.api.CycleHudSource;
import com.trevorschoeny.inventoryplus.api.CycleView;
import com.trevorschoeny.inventoryplus.api.CyclerDirection;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.cyclable.CycleHudRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Bridges Hotbar Cycler's rotation events into the two things that animate:
 * the hotbar slide itself ({@link HotbarSlide}, drawn by the Hud mixin) and
 * the Column / Pocket previews ({@link CycleHudRegistry}'s per-source clock).
 *
 * <p>Mirrors {@code ColumnCyclerHudSource.register()}'s bridge, so
 * {@link HotbarCycler} stays ignorant of the HUD. It is deliberately NOT a
 * {@link CycleHudSource}: Hotbar Cycler has no preview strip of its own by
 * spec. The vanilla hotbar is the preview.
 *
 * <h3>Before and after</h3>
 *
 * Both animations need the world as it was, and the rotation's clicks change
 * the predicted inventory the instant they're sent. So the snapshot is taken
 * on {@code beforeRotation} and consumed on {@code onRotation}:
 *
 * <ul>
 *   <li>The hotbar's nine stacks, copied, become the outgoing ghosts.</li>
 *   <li>Every registered source's view for the selected slot is captured so
 *       the registry can fire a slide only where the contents actually
 *       changed. A column whose members sit in untouched rows moves only
 *       its held cell; a pocket's own slots never move at all. Doing the
 *       diff in the registry keeps this cycler-agnostic, so Pocket Cycler in
 *       Inventory Max participates without IP referencing it.</li>
 * </ul>
 */
public final class HotbarCyclerHud {

    private HotbarCyclerHud() {}

    private static ItemStack[] hotbarBefore;
    private static Map<CycleHudSource, CycleView> viewsBefore;
    private static int selectedBefore = -1;

    public static void register() {
        HotbarCycler.addRotationListener(new HotbarCycler.RotationListener() {
            @Override
            public void beforeRotation() {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;
                Inventory inv = player.getInventory();
                ItemStack[] snap = new ItemStack[HotbarCycler.COLUMNS];
                for (int i = 0; i < snap.length; i++) snap[i] = inv.getItem(i).copy();
                hotbarBefore = snap;
                selectedBefore = inv.getSelectedSlot();
                viewsBefore = CycleHudRegistry.snapshotViews(selectedBefore);
            }

            @Override
            public void onRotation(ColumnCyclerRotator.Direction direction) {
                CyclerDirection dir = direction == ColumnCyclerRotator.Direction.FORWARD
                        ? CyclerDirection.FORWARD
                        : CyclerDirection.BACKWARD;
                if (hotbarBefore != null) HotbarSlide.start(hotbarBefore, dir);
                if (viewsBefore != null && selectedBefore >= 0) {
                    CycleHudRegistry.fireChangedViews(selectedBefore, dir, viewsBefore);
                }
                hotbarBefore = null;
                viewsBefore = null;
                selectedBefore = -1;
            }
        });
    }
}
