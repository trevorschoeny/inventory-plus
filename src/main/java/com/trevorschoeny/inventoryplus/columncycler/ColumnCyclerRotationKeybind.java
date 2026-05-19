package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Forward / backward cycle keybind handler — fires in two contexts:
 *
 * <ol>
 *   <li><b>HUD (no screen open)</b> — game-world tick handler polls
 *       {@link KeyMapping#consumeClick} for the cycle direction
 *       keybinds. Column = active hotbar slot (the slot the player is
 *       currently holding).</li>
 *   <li><b>In container screen</b> — {@code ScreenKeyboardEvents}
 *       {@code afterKeyPress} handler. Column = hovered slot's column,
 *       provided the hovered slot is itself a cycle slot (i.e. the
 *       column is active). Hovering a non-cycle-slot is a no-op.</li>
 * </ol>
 *
 * <p>Both paths gate on {@link IPConfig#columnCyclerEnabled} and
 * delegate to {@link ColumnCyclerRotator#rotate}.
 *
 * <h3>Drain-on-screen-open</h3>
 *
 * The tick handler drains the keybind's click queue even when a screen
 * is open (without firing) so presses that occurred while the screen
 * was open don't fire later when the player closes back to HUD. The
 * in-screen path is handled by the {@code afterKeyPress} listener,
 * which doesn't consume via {@code consumeClick} — vanilla's input
 * dispatcher feeds both the screen handler and the click queue.
 */
public final class ColumnCyclerRotationKeybind {

    private ColumnCyclerRotationKeybind() {}

    /** Registers both the tick (HUD) and screen-key (in-inv) listeners. */
    public static void register() {
        registerInScreen();
    }

    /** Drains queued clicks and fires rotation in HUD context. */
    public static void tick(Minecraft mc) {
        // Drain forward queue.
        boolean firedForward = false;
        while (IPKeybinds.CYCLE_FORWARD.consumeClick()) {
            if (canFireHud(mc) && !firedForward) {
                fireHud(mc, ColumnCyclerRotator.Direction.FORWARD);
                firedForward = true;
            }
        }
        // Drain backward queue.
        boolean firedBackward = false;
        while (IPKeybinds.CYCLE_BACKWARD.consumeClick()) {
            if (canFireHud(mc) && !firedBackward) {
                fireHud(mc, ColumnCyclerRotator.Direction.BACKWARD);
                firedBackward = true;
            }
        }
    }

    private static boolean canFireHud(Minecraft mc) {
        return mc != null
                && mc.player != null
                && mc.screen == null
                && IPConfig.columnCyclerEnabled();
    }

    private static void fireHud(Minecraft mc, ColumnCyclerRotator.Direction direction) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        int activeHotbar = player.getInventory().getSelectedSlot(); // 0-8
        // Rotation no-ops if the column isn't active (no cycle slots above).
        ColumnCyclerRotator.rotate(activeHotbar, direction);
    }

    private static void registerInScreen() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (!IPConfig.columnCyclerEnabled()) return;
                        boolean isForward = IPKeybinds.CYCLE_FORWARD.matches(event);
                        boolean isBackward = !isForward && IPKeybinds.CYCLE_BACKWARD.matches(event);
                        if (!isForward && !isBackward) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Minecraft mc = Minecraft.getInstance();
                        double mouseX = mc.mouseHandler.xpos()
                                * (double) mc.getWindow().getGuiScaledWidth()
                                / (double) mc.getWindow().getScreenWidth();
                        double mouseY = mc.mouseHandler.ypos()
                                * (double) mc.getWindow().getGuiScaledHeight()
                                / (double) mc.getWindow().getScreenHeight();
                        Slot hovered = slotUnderMouse(currentAcs, mouseX, mouseY);
                        if (hovered == null) return;

                        // Must be hovering a CYCLE slot in the local player's
                        // inv-or-hotbar (covers both direct inv membership and
                        // derived hotbar membership). The column is derived
                        // from the hovered slot's container index.
                        if (!isPlayerInvSlot(hovered, mc)) return;
                        int containerIdx = hovered.getContainerSlot();
                        if (!ColumnCycler.isCycleSlot(containerIdx)) return;
                        int column = containerIdx % 9;
                        ColumnCyclerRotator.rotate(column,
                                isForward
                                        ? ColumnCyclerRotator.Direction.FORWARD
                                        : ColumnCyclerRotator.Direction.BACKWARD);
                    });
        });
    }

    private static boolean isPlayerInvSlot(Slot slot, Minecraft mc) {
        if (!(slot.container instanceof Inventory inv)) return false;
        int ci = slot.getContainerSlot();
        if (ci < 0 || ci > 35) return false;
        if (mc == null || mc.player == null) return false;
        return inv.player.getUUID().equals(mc.player.getUUID());
    }

    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs,
                                                 double mouseX, double mouseY) {
        int leftPos = ScreenLayout.leftPos(acs);
        int topPos = ScreenLayout.topPos(acs);
        for (Slot slot : acs.getMenu().slots) {
            int sx = leftPos + slot.x;
            int sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16
                    && mouseY >= sy && mouseY < sy + 16) {
                return slot;
            }
        }
        return null;
    }
}
