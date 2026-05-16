package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;

/**
 * Registers per-slot-group move-matching widgets on screen open, gated
 * by the <b>2+ traditional containers</b> rule (Trev 2026-05-16).
 *
 * <h3>Activation rule</h3>
 *
 * After {@link SlotGroupDetector#detect}, we count how many groups are
 * {@link SlotGroup#targetable}. Widgets are only added when that count
 * is {@code ≥ 2}:
 *
 * <ul>
 *   <li>Standalone vanilla {@code InventoryScreen} → 1 targetable
 *       (main inv 3×9) → no widgets.</li>
 *   <li>{@code ContainerScreen} / shulker / hopper / dispenser → 2
 *       targetable (external container + main inv) → 2 widgets.</li>
 *   <li>Future Shulker Peek inside {@code InventoryScreen} → 2
 *       targetable (shulker peek container + main inv) → 2 widgets
 *       automatically. The targetability check
 *       ({@link SlotGroup#targetable}) is purely data-driven from
 *       container type, so no code change needed when Shulker Peek
 *       lands.</li>
 * </ul>
 *
 * <h3>Why vanilla {@link Screens#getButtons} rather than Fabric afterRender</h3>
 *
 * See {@link MoveMatchingWidget} class javadoc — the tooltip queue must
 * be primed during the screen's renderables iteration, not after the
 * deferred-elements flush. Adding the widget via vanilla's button list
 * puts it in the renderables iteration where tooltips work correctly.
 *
 * <h3>Future config note</h3>
 *
 * {@code TODO} — Trev 2026-05-16: Move-matching and Sort buttons should
 * be toggleable on/off in config. When the config feature lands, gate
 * the {@code Screens.getButtons(screen).add(widget)} call below behind
 * the toggle. Filed for the post-18b config-UI phase.
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
            for (SlotGroup group : groups) {
                if (!group.targetable()) continue;
                buttons.add(new MoveMatchingWidget(group, acs));
            }
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] registered widgets for {} targetable group(s) on {}",
                    targetableCount, screen.getClass().getSimpleName());
        });
    }
}
