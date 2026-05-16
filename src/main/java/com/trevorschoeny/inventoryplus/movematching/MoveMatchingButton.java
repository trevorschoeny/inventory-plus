package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.MenuRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.inject.ScreenPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Registers the move-matching button at {@link MenuRegion#RIGHT_ALIGN_TOP}
 * across every simplecontainer screen in 18b's smoke scope.
 *
 * <h3>Click semantics</h3>
 *
 * MK's {@link Button} fires {@code onClick} for any mouse button; the
 * handler dispatches on {@code button} (the int param):
 * <ul>
 *   <li>{@code 0} (left mouse) → execute move-matching with the container's
 *       current cycle setting.</li>
 *   <li>{@code 1} (right mouse) → cycle to the next stop and persist. No
 *       execute on the cycle action — spec §"Cycling does not auto-trigger"
 *       is honored.</li>
 * </ul>
 *
 * <p>Spec §"Cycle" also describes a keybind-on-button cycle path. That
 * shape is deferred for the smoke pass — right-click on the button covers
 * the cycle UX cleanly, the keybind-on-button refinement is a polish pass
 * once MK's per-element hover query is exercised.
 *
 * <h3>Screen coverage</h3>
 *
 * Registered on the four AbstractContainerScreen subclasses that host
 * simplecontainer menus: chests (and barrels / trapped chests / ender
 * chests, which all use {@link ContainerScreen} in 1.21.11 — vanilla
 * unified the ChestMenu-backed screens), shulker boxes, hoppers,
 * dispensers (which also covers droppers — both use
 * {@link DispenserScreen}). The bare {@code InventoryScreen} is not
 * registered for the smoke pass — see {@link MoveMatchingExecutor}
 * javadoc.
 */
public final class MoveMatchingButton {

    private MoveMatchingButton() {}

    /** Button label — short identifier for the smoke pass. */
    private static final Component LABEL = Component.literal("M");

    private static final int BUTTON_WIDTH = 16;
    private static final int BUTTON_HEIGHT = 16;

    /** Stacking priority — move-matching button sits above the sort button. */
    public static final int PRIORITY = 100;

    public static void register() {
        List<PanelElement> elements = List.of(
                new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, LABEL,
                        MoveMatchingButton::onClick));

        Panel panel = new Panel(
                "inventory-plus-movematching-button",
                elements,
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);

        new ScreenPanelAdapter(
                panel,
                MenuRegion.RIGHT_ALIGN_TOP.priority(PRIORITY),
                /*padding=*/ 0)
                .on(ContainerScreen.class,
                        ShulkerBoxScreen.class,
                        HopperScreen.class,
                        DispenserScreen.class);
    }

    /**
     * MK {@link Button} click handler — we don't get the mouse-button
     * index from {@link Button} directly (it uses
     * {@code java.util.function.Consumer<Button>}), so left-click is
     * implicit. Right-click cycling is wired separately through a future
     * "secondary click" surface in MK; for the smoke we treat any click
     * as "execute" and provide cycle access via the keybind path
     * (or, in the next polish pass, via a right-click event hook).
     *
     * <p>Spec compliance check: spec §"Cycle" requires cycle access from
     * the button. Until MK exposes a per-element right-click hook, we
     * route the cycle through the {@link MoveMatchingKeybind} (M while
     * not over a slot) — see that class for the cycle binding choice.
     */
    private static void onClick(Button btn) {
        Minecraft mc = Minecraft.getInstance();
        ContainerKey key = ContainerKeyResolver.resolve(mc.screen);
        MoveMatchingCycle cycle = MoveMatchingPrefs.get(key);
        MoveMatchingExecutor.execute(mc, cycle);
    }

    /**
     * Cycle handler — invoked from the keybind path until MK exposes a
     * right-click hook on Button. Reads the current cycle for the open
     * container, advances to the next stop, persists.
     */
    public static void cycle(Minecraft mc) {
        ContainerKey key = ContainerKeyResolver.resolve(mc.screen);
        if (key == null) return;
        MoveMatchingCycle current = MoveMatchingPrefs.get(key);
        MoveMatchingCycle next = current.next();
        MoveMatchingPrefs.set(key, next);
        // Surface the new cycle to the player so they can see the change
        // without a tooltip refresh. Chat-message ack is the simplest
        // honest feedback in the smoke pass.
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("Move-matching: ").append(next.tooltip()),
                    /*overlay=*/ true);
        }
    }
}
