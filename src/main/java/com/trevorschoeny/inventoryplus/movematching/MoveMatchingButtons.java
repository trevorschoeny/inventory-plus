package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * Registers Move Matching IN + OUT widgets on screen open, gated by the
 * <b>2+ traditional containers</b> rule (Trev 2026-05-16).
 *
 * <h3>Activation rule</h3>
 *
 * After {@link SlotGroupDetector#detect}, we count how many groups are
 * {@link SlotGroup#targetable}. Widgets are only added when that count
 * is {@code ≥ 2}. Per Trev's "anywhere there's an IN button there
 * should be an OUT button" direction, both directions register together
 * — for each targetable group we add two widgets (IN + OUT).
 *
 * <ul>
 *   <li>Standalone vanilla {@code InventoryScreen} → 1 targetable
 *       (main inv 3×9) → no widgets.</li>
 *   <li>{@code ContainerScreen} / shulker / hopper / dispenser → 2
 *       targetable (external container + main inv) → 4 widgets (IN+OUT
 *       above each).</li>
 *   <li>Future Shulker Peek inside {@code InventoryScreen} → 2 targetable
 *       (shulker peek container + main inv) → 4 widgets automatically.</li>
 * </ul>
 *
 * <h3>Future config note</h3>
 *
 * {@code TODO} — Trev 2026-05-16: Move Matching and Sort buttons should
 * be toggleable on/off in config. When the config feature lands, gate
 * the {@code Screens.getButtons(screen).add(widget)} calls below behind
 * the toggle. Filed in DEFERRED.md.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    /** Public init — call once from the client entrypoint. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            List<SlotGroup> groups = SlotGroupDetector.detect(screen);

            int targetableCount = 0;
            for (SlotGroup g : groups) if (g.targetable()) targetableCount++;
            if (targetableCount < 2) {
                InventoryPlusClient.LOGGER.debug(
                        "[move-matching] only {} targetable group(s) on {} — no widgets",
                        targetableCount, screen.getClass().getSimpleName());
                return;
            }

            var buttons = Screens.getButtons(screen);
            int widgetsAdded = 0;
            for (SlotGroup group : groups) {
                if (!group.targetable()) continue;
                // Two widgets per targetable group — IN (rightmost) + OUT
                // (one button width + 1px to its left). Layout math lives
                // in MoveMatchingWidget.renderWidget.
                buttons.add(new MoveMatchingWidget(group, acs, Direction.IN));
                buttons.add(new MoveMatchingWidget(group, acs, Direction.OUT));
                widgetsAdded += 2;
            }
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] registered {} widget(s) for {} targetable group(s) on {}",
                    widgetsAdded, targetableCount, screen.getClass().getSimpleName());
        });
    }
}
