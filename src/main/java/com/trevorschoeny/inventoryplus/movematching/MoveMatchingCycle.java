package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The three cycle stops for move-matching per {@code move-matching.md}
 * spec §"Cycle":
 *
 * <ol>
 *   <li><b>{@link #ALL_MATCHING}</b> (default) — move every matching item
 *       type, including non-stackables.</li>
 *   <li><b>{@link #STACKABLE_ONLY}</b> — move only stackable matches.</li>
 *   <li><b>{@link #DISABLED}</b> — move-matching off for the slot group.
 *       Click + keybind trigger are no-op; pressing the cycle keybind
 *       (M) on the widget still advances back to
 *       {@link #ALL_MATCHING}.</li>
 * </ol>
 *
 * <h3>Tooltip text (Trev 2026-05-16 #3)</h3>
 *
 * Each stop's tooltip is a {@code List<Component>} — vanilla's
 * {@link net.minecraft.client.gui.GuiGraphics#setComponentTooltipForNextFrame}
 * renders one Component per line. The second line ("Press M to cycle")
 * is styled gray + italic across all stops so the hint reads as
 * secondary information.
 *
 * <p>Tooltips per stop:
 * <ul>
 *   <li>{@code ALL_MATCHING}    → {@code "Move matching IN"}    + cycle hint</li>
 *   <li>{@code STACKABLE_ONLY}  → {@code "Move stackable matching IN"} + cycle hint</li>
 *   <li>{@code DISABLED}        → {@code "DISABLED"}            + cycle hint</li>
 * </ul>
 */
public enum MoveMatchingCycle {

    ALL_MATCHING(withCycleHint("Move matching IN")),
    STACKABLE_ONLY(withCycleHint("Move stackable matching IN")),
    DISABLED(withCycleHint("DISABLED"));

    private final List<Component> tooltipLines;

    MoveMatchingCycle(List<Component> tooltipLines) {
        this.tooltipLines = tooltipLines;
    }

    public List<Component> tooltipLines() {
        return tooltipLines;
    }

    public static MoveMatchingCycle defaultCycle() {
        return ALL_MATCHING;
    }

    public MoveMatchingCycle next() {
        return switch (this) {
            case ALL_MATCHING   -> STACKABLE_ONLY;
            case STACKABLE_ONLY -> DISABLED;
            case DISABLED       -> ALL_MATCHING;
        };
    }

    /**
     * Helper — every cycle stop's tooltip has the same gray+italic
     * "Press M to cycle" second line. Per-stop literal is just the
     * first (primary) line.
     */
    private static List<Component> withCycleHint(String firstLine) {
        return List.of(
                Component.literal(firstLine),
                Component.literal("Press M to cycle")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
