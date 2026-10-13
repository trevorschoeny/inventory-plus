package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;

import com.trevlar.menukit.api.hud.HudCycleStrip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Bridges Hotbar Cycler's rotation events into the two things that animate:
 * the hotbar slide itself ({@link HotbarSlide}, drawn by the Hud mixin) and
 * the rings on MenuKit's cycle strip beside it ({@link HudCycleStrip}).
 *
 * <p>Mirrors {@code ColumnCyclerHudSource.register()}'s bridge, so
 * {@link HotbarCycler} stays ignorant of the HUD. It is deliberately not a
 * strip source: Hotbar Cycler has no strip of its own. The vanilla hotbar is
 * the preview.
 *
 * <h3>Before and after</h3>
 *
 * Both animations need the world as it was, and the rotation's clicks change
 * the predicted inventory the instant they're sent. So the snapshot is taken
 * on {@code beforeRotation} and consumed on {@code onRotation}:
 *
 * <ul>
 *   <li>The hotbar's nine stacks, copied, become the outgoing ghosts.</li>
 *   <li>MenuKit's strip snapshots every source's ring on the selected slot
 *       ({@link HudCycleStrip#snapshot}) and slides only the held cell of the
 *       rings whose contents changed ({@link HudCycleStrip#slideChanged}). A
 *       column whose members sit in untouched rows moves only its held cell,
 *       and another mod's ring takes part without this mod naming it.</li>
 * </ul>
 */
public final class HotbarCyclerHud {

    private HotbarCyclerHud() {}

    private static ItemStack[] hotbarBefore;
    private static Map<HudCycleStrip.Source, HudCycleStrip.View> viewsBefore;
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
                viewsBefore = HudCycleStrip.snapshot(selectedBefore);
            }

            @Override
            public void onRotation(ColumnCyclerRotator.Direction direction) {
                HudCycleStrip.Direction dir = direction == ColumnCyclerRotator.Direction.FORWARD
                        ? HudCycleStrip.Direction.FORWARD
                        : HudCycleStrip.Direction.BACKWARD;
                if (hotbarBefore != null) HotbarSlide.start(hotbarBefore, dir);
                if (viewsBefore != null && selectedBefore >= 0) {
                    HudCycleStrip.slideChanged(selectedBefore, dir, viewsBefore);
                }
                hotbarBefore = null;
                viewsBefore = null;
                selectedBefore = -1;
            }
        });
    }
}
