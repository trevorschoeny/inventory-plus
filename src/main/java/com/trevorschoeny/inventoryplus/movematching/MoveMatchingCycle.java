package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The three cycle stops for move-matching per {@code move-matching.md}
 * spec §"Cycle":
 *
 * <ol>
 *   <li><b>{@link #ALL_MATCHING}</b> (default) — move every matching item
 *       type, including non-stackables.</li>
 *   <li><b>{@link #STACKABLE_ONLY}</b> — move only stackable matches
 *       (max stack size &gt; 1).</li>
 *   <li><b>{@link #DISABLED}</b> — move-matching off for the slot group.
 *       Click + keybind are no-op while in this state. Pressing the
 *       cycle keybind (M) on the button still advances past disabled
 *       back to {@link #ALL_MATCHING}.</li>
 * </ol>
 *
 * <h3>Tooltip text</h3>
 *
 * Each stop carries a {@code List<Component>} of tooltip lines.
 * Vanilla's
 * {@link net.minecraft.client.gui.GuiGraphics#setComponentTooltipForNextFrame}
 * takes a list and renders one Component per line — we use that
 * (rather than the single-Component variant) because Component-with-
 * {@code \n} doesn't auto-split in 1.21.11 (the LF character renders
 * as a literal glyph). One Component per line is the correct API
 * shape.
 *
 * <p>All three stops include the "Press M to cycle" hint (Trev 2026-05-16
 * — including DISABLED so the player knows how to get out of the off
 * state).
 */
public enum MoveMatchingCycle {

    ALL_MATCHING(withCycleHint("Move matching items IN")),
    STACKABLE_ONLY(withCycleHint("Move stackable matching items IN")),
    DISABLED(withCycleHint("Move matching items IN disabled"));

    private final List<Component> tooltipLines;

    MoveMatchingCycle(List<Component> tooltipLines) {
        this.tooltipLines = tooltipLines;
    }

    /** Tooltip lines for this stop — one Component per visible line. */
    public List<Component> tooltipLines() {
        return tooltipLines;
    }

    /** Mod-config default for newly-encountered slot groups. */
    public static MoveMatchingCycle defaultCycle() {
        return ALL_MATCHING;
    }

    /** Next stop in the forward cycle. */
    public MoveMatchingCycle next() {
        return switch (this) {
            case ALL_MATCHING   -> STACKABLE_ONLY;
            case STACKABLE_ONLY -> DISABLED;
            case DISABLED       -> ALL_MATCHING;
        };
    }

    /**
     * Helper — every cycle stop's tooltip has the same "Press M to
     * cycle" second line, so the per-stop literal is just the first
     * line. Java permits static-method calls in enum-constant init
     * because methods are bound to the class structure before constants
     * initialize.
     */
    private static List<Component> withCycleHint(String firstLine) {
        return List.of(
                Component.literal(firstLine),
                Component.literal("Press M to cycle"));
    }
}
