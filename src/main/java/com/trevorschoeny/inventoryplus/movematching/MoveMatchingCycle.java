package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The three cycle stops for Move Matching per {@code move-matching.md}
 * spec §"Cycle":
 *
 * <ol>
 *   <li><b>{@link #ALL_MATCHING}</b> (default) — move every matching item
 *       type, including non-stackables.</li>
 *   <li><b>{@link #STACKABLE_ONLY}</b> — move only stackable matches.</li>
 *   <li><b>{@link #DISABLED}</b> — Move Matching off for the slot
 *       group + direction. Left-click trigger is a no-op; right-clicking
 *       the widget still cycles back to {@link #ALL_MATCHING}.</li>
 * </ol>
 *
 * <h3>Direction-aware tooltips</h3>
 *
 * Tooltip text is generated per-cycle, per-{@link Direction} via
 * {@link #tooltipLines(Direction)} — "Move matching IN" vs "Move matching
 * OUT", etc. The DISABLED tooltip is the same first line regardless of
 * direction; the second line ("Right-click to cycle") is always present
 * in gray + italic.
 *
 * <p>The cycle enum itself is direction-agnostic. The same three stops
 * apply to both IN and OUT directions, persisted independently per
 * container per direction.
 */
public enum MoveMatchingCycle {

    ALL_MATCHING,
    STACKABLE_ONLY,
    DISABLED;

    /** Second-line cycle-hint text — matches the right-click cycle action in {@link MoveMatchingWidget#onClick}. */
    private static final String CYCLE_HINT = "Right-click to cycle";

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
     * Builds the tooltip lines for this cycle stop in the given
     * direction. Returned as a {@code List<Component>} so vanilla's
     * {@code setComponentTooltipForNextFrame} renders one Component
     * per line.
     */
    public List<Component> tooltipLines(Direction direction) {
        String firstLine = switch (this) {
            case ALL_MATCHING   -> "Move matching " + direction.label();
            case STACKABLE_ONLY -> "Move stackable matching " + direction.label();
            case DISABLED       -> "DISABLED";
        };
        return List.of(
                Component.literal(firstLine),
                Component.literal(CYCLE_HINT)
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
