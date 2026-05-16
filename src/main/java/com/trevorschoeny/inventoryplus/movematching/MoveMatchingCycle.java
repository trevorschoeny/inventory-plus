package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.network.chat.Component;

/**
 * The three cycle stops for move-matching per {@code move-matching.md} spec
 * §"Cycle":
 *
 * <ol>
 *   <li><b>{@link #ALL_MATCHING}</b> (default) — move every matching item
 *       type, including non-stackables (tools, armor, totems).</li>
 *   <li><b>{@link #STACKABLE_ONLY}</b> — move only stackable matches
 *       (max stack size &gt; 1). Non-stackables stay put.</li>
 *   <li><b>{@link #DISABLED}</b> — move-matching off for this container.
 *       Button click + keybind are no-op while in this state. Re-cycling
 *       (via right-click on the button) advances past disabled.</li>
 * </ol>
 *
 * <p>Forward cycling: {@link #ALL_MATCHING} → {@link #STACKABLE_ONLY} →
 * {@link #DISABLED} → {@link #ALL_MATCHING}.
 */
public enum MoveMatchingCycle {

    ALL_MATCHING(Component.literal("Move all matching")),
    STACKABLE_ONLY(Component.literal("Move stackable matching")),
    DISABLED(Component.literal("Move-matching disabled"));

    private final Component tooltip;

    MoveMatchingCycle(Component tooltip) {
        this.tooltip = tooltip;
    }

    /** Tooltip text shown over the button while this stop is active. */
    public Component tooltip() {
        return tooltip;
    }

    /** Mod-config default for newly-encountered containers. */
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
