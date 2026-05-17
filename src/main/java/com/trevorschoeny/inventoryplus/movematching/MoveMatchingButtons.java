package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registers Move Matching IN + OUT widgets on screen open, when a
 * supported container is open (Trev / Lead 2026-05-16 simplification).
 *
 * <h3>Activation rule</h3>
 *
 * Buttons are added <b>only on the player inventory's slot group</b>, and
 * <b>only when at least one external simplecontainer is visible on the
 * same screen</b>. Concretely:
 *
 * <ul>
 *   <li>Standalone vanilla {@code InventoryScreen} → no external
 *       container → no widgets.</li>
 *   <li>{@code ContainerScreen} / shulker / hopper / dispenser → external
 *       container present → IN + OUT widgets above the inventory 3×9.
 *       No widgets on the external container itself.</li>
 *   <li>Future Shulker Peek inside {@code InventoryScreen} → external
 *       container (the peek) present → widgets appear automatically.</li>
 * </ul>
 *
 * <p>The pre-simplification model registered widgets on every targetable
 * group (one IN+OUT pair per group, including the chest's own slot
 * group). The new model is inventory-centric — buttons only on the
 * inv side, paired with whichever single open container is present.
 *
 * <h3>Future config note</h3>
 *
 * {@code TODO} — Lead/Trev 2026-05-16: Move Matching and Sort buttons
 * should be toggleable on/off in config. Hotbar inclusion will also be
 * a shared toggle. When the config feature lands, gate the
 * {@code Screens.getButtons(screen).add(widget)} calls below behind the
 * toggle. Both items filed in
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
            if (!hasExternalTargetable(groups)) {
                InventoryPlusClient.LOGGER.debug(
                        "[move-matching] no external container on {} — no widgets",
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
    static @Nullable SlotGroup findPlayerMainInv(List<SlotGroup> groups) {
        for (SlotGroup g : groups) {
            if (g.role() == SlotRole.PLAYER_MAIN_INV) return g;
        }
        return null;
    }

    /**
     * True if at least one targetable EXTERNAL group is present — that's
     * the signal "a real simplecontainer is open alongside the
     * inventory". Used to gate widget visibility per the simplified
     * spec.
     */
    static boolean hasExternalTargetable(List<SlotGroup> groups) {
        for (SlotGroup g : groups) {
            if (g.role() == SlotRole.EXTERNAL && g.targetable()) return true;
        }
        return false;
    }
}
