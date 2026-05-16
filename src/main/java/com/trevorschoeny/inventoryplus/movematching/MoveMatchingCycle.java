package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.network.chat.Component;

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
 *       Click + keybind are no-op while in this state. Shift-click on
 *       the button advances past disabled back to {@link #ALL_MATCHING}.</li>
 * </ol>
 *
 * <h3>Tooltip text (Trev 2026-05-16 #2)</h3>
 *
 * Each stop carries its full hover-tooltip Component. The string uses
 * a {@code \n} for the line break — vanilla's
 * {@code GuiGraphics.setTooltipForNextFrame} splits Components on
 * embedded newlines automatically.
 *
 * <p>The DISABLED tooltip deliberately omits the "Shift-click to cycle"
 * second line per Trev's direction — the single-line variant signals
 * "this is the off state" by its different shape.
 */
public enum MoveMatchingCycle {

    ALL_MATCHING(Component.literal("Move matching items IN\nShift-click to cycle")),
    STACKABLE_ONLY(Component.literal("Move stackable matching items IN\nShift-click to cycle")),
    DISABLED(Component.literal("Move matching items IN disabled"));

    private final Component tooltip;

    MoveMatchingCycle(Component tooltip) {
        this.tooltip = tooltip;
    }

    /** Full hover-tooltip text for this stop, including any newline. */
    public Component tooltip() {
        return tooltip;
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
}
