package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registers Move Matching IN + OUT widgets on screen open.
 *
 * <h3>Activation (Trev 2026-05-16 post-simplification)</h3>
 *
 * Widgets are added <b>only on the player main inventory's slot group</b>,
 * and <b>only on screens that pair the inventory with a container</b>
 * ({@code ContainerScreen} / shulker / hopper / dispenser — see
 * {@link SlotGroupDetector#isMoveMatchingScreen}). The standalone
 * survival / creative inventory is excluded at the screen-class level —
 * no dynamic "is a real container open?" gate.
 *
 * <p>If a future feature surfaces a container inside the standalone
 * inventory (Shulker Peek, etc.), it will add its own widgets at its
 * own positioning rather than reusing this registration path.
 *
 * <h3>Future config note</h3>
 *
 * {@code TODO} — Lead/Trev 2026-05-16: Move Matching buttons should be
 * toggleable on/off in config; hotbar inclusion will also be a shared
 * toggle. When the config feature lands, gate the
 * {@code Screens.getButtons(screen).add(widget)} calls below behind
 * the toggle. Filed in
 * {@code @ Inventory Plus/2 | Working Files/DEFERRED.md}.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    /** Public init — call once from the client entrypoint. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            List<SlotGroup> groups = SlotGroupDetector.detect(screen);
            SlotGroup playerMainInv = findPlayerMainInv(groups);
            if (playerMainInv == null) {
                InventoryPlusClient.LOGGER.debug(
                        "[move-matching] no player main inv group on {} — no widgets",
                        screen.getClass().getSimpleName());
                return;
            }

            var buttons = Screens.getButtons(screen);
            buttons.add(new MoveMatchingWidget(playerMainInv, acs, Direction.IN));
            buttons.add(new MoveMatchingWidget(playerMainInv, acs, Direction.OUT));
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] registered IN+OUT widgets on player main inv for {}",
                    screen.getClass().getSimpleName());
        });
    }

    /** Returns the first {@link SlotRole#PLAYER_MAIN_INV} group, or null. */
    public static @Nullable SlotGroup findPlayerMainInv(List<SlotGroup> groups) {
        for (SlotGroup g : groups) {
            if (g.role() == SlotRole.PLAYER_MAIN_INV) return g;
        }
        return null;
    }
}
