package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.cyclable.CyclerDirection;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarSlide;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws Hotbar Cycler's slide in the vanilla hotbar: for the 220 ms after a
 * row rotation, each hotbar cell shows its old item sliding out and its new
 * item sliding in, on the mini-hotbar's spring curve. Slot frames and the
 * selection highlight never move; only items do.
 *
 * <h3>The seam (26.2)</h3>
 *
 * The HUD is built by render-state extraction, not draw calls. Hotbar items
 * come out of {@code Hud.extractSlot(graphics, x, y, delta, player, stack,
 * seed)}, one stack per cell, called from {@code extractItemHotbar} in a
 * loop over slots 0-8 and then twice more for the offhand. So the slide is
 * not "suppress the draw and paint over it"; it is: cancel vanilla's one
 * emission for a cell mid-slide and emit two of our own through
 * {@link HudAccessor}, at interpolated positions.
 *
 * <p>The {@code seed} parameter is an incrementing counter for the item pop
 * effect, not a slot index. Which cell a call is for is recovered from
 * {@code x}, using the layout vanilla computes in {@code extractItemHotbar}:
 *
 * <pre>
 *   x = guiWidth / 2 - 90 + slot * 20 + 2     (the item's left edge)
 *   y = guiHeight - 16 - 3                     (the item's top edge)
 * </pre>
 *
 * The offhand calls land at {@code guiWidth/2 - 91 - 26} and
 * {@code guiWidth/2 + 91 + 10}, neither of which is on that 20 px grid, so
 * the inversion excludes them by itself; the {@code y} check is a second
 * guard for anything else that might route through the same method.
 *
 * <h3>Re-entrancy</h3>
 *
 * The invoker calls the very method this injects into, so without a guard
 * each re-emission would be cancelled and re-emitted again, forever. A flag
 * marks the two nested calls as ours so the inject passes them through. HUD
 * extraction runs on the render thread only, so a plain static is enough.
 *
 * <h3>Motion</h3>
 *
 * FORWARD ({@code ]}) shifts rows down toward the hotbar: the new item drops
 * in from one cell above, the old one continues down and out below. BACKWARD
 * is the mirror. Both are clipped to the cell's 20 px band so nothing draws
 * over the hotbar frame; the same clip the column arm uses.
 */
@Mixin(Hud.class)
public abstract class HudHotbarSlideMixin {

    /** Vanilla's hotbar cell pitch; one cell is also the slide distance. */
    private static final int CELL_PX = 20;

    /** Set while we are inside our own re-emissions; see the class doc. */
    private static boolean inventoryPlus$reemitting = false;

    @Inject(
            method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void inventoryPlus$slideHotbarItem(GuiGraphicsExtractor graphics, int x, int y,
                                                DeltaTracker delta, Player player,
                                                ItemStack stack, int seed, CallbackInfo ci) {
        if (inventoryPlus$reemitting) return;
        if (!HotbarSlide.isActive()) return;

        int slot = inventoryPlus$hotbarSlotFor(graphics, x, y);
        if (slot < 0) return;

        // From here on this cell is ours: vanilla's single emission is
        // replaced by the two below.
        ci.cancel();

        float p = HotbarSlide.progress();
        int travelled = Math.round(CELL_PX * p);          // how far the slide has come
        int remaining = Math.round(CELL_PX * (1f - p));   // how far the incoming still has to go
        boolean down = HotbarSlide.direction() == CyclerDirection.FORWARD;

        // Incoming (the cell's current item) settles at y; outgoing (what was
        // there) leaves through the far side. Signs flip with direction.
        int yIn  = down ? y - remaining : y + remaining;
        int yOut = down ? y + travelled : y - travelled;

        // Clip to this cell's 20 px band: the vanilla bar is 22 px with a 1 px
        // border, and the item sits 2 px in from the cell's left edge.
        int cellLeft = x - 2;
        int bandTop = graphics.guiHeight() - 21;
        graphics.enableScissor(cellLeft, bandTop, cellLeft + CELL_PX, bandTop + CELL_PX);
        inventoryPlus$reemitting = true;
        try {
            HudAccessor self = (HudAccessor) (Object) this;
            // Outgoing first so the incoming, the real current item, ends on top.
            self.inventoryPlus$extractSlot(graphics, x, yOut, delta, player, HotbarSlide.outgoing(slot), seed);
            self.inventoryPlus$extractSlot(graphics, x, yIn, delta, player, stack, seed);
        } finally {
            inventoryPlus$reemitting = false;
            graphics.disableScissor();
        }
    }

    /**
     * The hotbar slot (0-8) a call at {@code (x, y)} is for, or -1 if the
     * position is not a hotbar cell. Inverts vanilla's layout exactly; a
     * position off the 20 px grid (the offhand) yields -1.
     */
    private static int inventoryPlus$hotbarSlotFor(GuiGraphicsExtractor graphics, int x, int y) {
        if (y != graphics.guiHeight() - 19) return -1;
        int dx = x - (graphics.guiWidth() / 2 - 88);
        if (dx < 0 || dx % CELL_PX != 0) return -1;
        int slot = dx / CELL_PX;
        return slot <= 8 ? slot : -1;
    }
}
