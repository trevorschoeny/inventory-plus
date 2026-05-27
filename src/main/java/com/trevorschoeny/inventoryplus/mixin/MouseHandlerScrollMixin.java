package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link MouseHandler#onScroll} to redirect HUD scroll
 * input to Column Cycler rotation when:
 *
 * <ul>
 *   <li>Column Cycler is enabled (master flag).</li>
 *   <li>Scroll-to-Cycle is enabled (sub-flag).</li>
 *   <li>No screen is open ({@code mc.screen == null}) — we don't
 *       intercept scroll inside chests/inventories.</li>
 *   <li>The active hotbar slot's column has cycle members (the slot
 *       is itself a cycle slot, direct or derived).</li>
 * </ul>
 *
 * <p>When all conditions hold, we rotate the cycle and cancel vanilla's
 * hotbar-switch path. Otherwise the call passes through and vanilla
 * scrolls the hotbar slot as usual — so a player can still navigate
 * to non-cycle columns by scrolling past them.
 *
 * <p>Direction: positive {@code dy} (wheel scrolled UP, away from the
 * user) maps to BACKWARD (items shift visually upward), matching
 * vanilla's "scroll up = previous slot" convention. Negative {@code dy}
 * maps to FORWARD.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$scrollToCycle(long window, double dx, double dy, CallbackInfo ci) {
        if (!IPConfig.columnCyclerEnabled()) return;
        if (!IPConfig.columnCyclerScrollToCycle()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        // Only intercept when there's something to cycle. dy == 0
        // shouldn't normally happen, but guard against horizontal
        // scroll wheels passing dx with no dy.
        if (dy == 0) return;

        int activeHotbar = player.getInventory().getSelectedSlot(); // 0-8
        if (!ColumnCycler.isCycleSlot(activeHotbar)) return;

        ColumnCyclerRotator.Direction direction = dy > 0
                ? ColumnCyclerRotator.Direction.BACKWARD
                : ColumnCyclerRotator.Direction.FORWARD;
        ColumnCyclerRotator.rotate(activeHotbar, direction);
        ci.cancel();
    }
}
