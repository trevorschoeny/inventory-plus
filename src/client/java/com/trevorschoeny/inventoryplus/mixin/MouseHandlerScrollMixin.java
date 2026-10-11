package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.hotbarcycler.HotbarCycler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@link MouseHandler#onScroll} to redirect HUD scroll
 * input to whichever cycler owns the wheel. Column Cycler's conditions:
 *
 * <ul>
 *   <li>Column Cycler is enabled.</li>
 *   <li>Scroll to cycle is enabled.</li>
 *   <li>No screen is open ({@code mc.gui.screen() == null}), so scrolling
 *       inside chests and inventories is left alone.</li>
 *   <li>The active hotbar slot's column has cycle members (the slot
 *       is itself a cycle slot, direct or derived).</li>
 * </ul>
 *
 * <p>When all conditions hold, we rotate the cycle and cancel vanilla's
 * hotbar-switch path. Otherwise the call passes through and vanilla
 * scrolls the hotbar slot as usual, so a player can still navigate
 * to non-cycle columns by scrolling past them.
 *
 * <p>Positive {@code dy} (wheel scrolled up, away from the player) maps to
 * BACKWARD (items shift visually upward), matching vanilla's convention that
 * scrolling up selects the previous slot. Negative {@code dy}
 * maps to FORWARD.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void inventoryplus$scrollToCycle(long window, double dx, double dy, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;

        // A horizontal scroll wheel can pass dx with no dy.
        if (dy == 0) return;

        ColumnCyclerRotator.Direction direction = dy > 0
                ? ColumnCyclerRotator.Direction.BACKWARD
                : ColumnCyclerRotator.Direction.FORWARD;

        // Column Cycler first, as it always was. The two
        // branches can't both be live: IPConfig enforces a single scroll owner.
        if (IPConfig.columnCyclerEnabled() && IPConfig.columnCyclerScrollToCycle()) {
            int activeHotbar = player.getInventory().getSelectedSlot(); // 0-8
            if (ColumnCycler.isCycleSlot(activeHotbar)) {
                ColumnCyclerRotator.rotate(activeHotbar, direction);
                ci.cancel();
            }
            return;
        }

        // Hotbar Cycler needs no active-column test: rows are not scoped to
        // the held slot, so an active row cycle is the whole condition.
        if (IPConfig.hotbarCyclerEnabled() && IPConfig.hotbarCyclerScrollToCycle()
                && HotbarCycler.hasActiveCycle()) {
            HotbarCycler.rotate(direction);
            ci.cancel();
        }
    }
}
