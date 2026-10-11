package com.trevorschoeny.inventoryplus.api;

/**
 * Direction of a cycle rotation, used by the shared HUD's slide animation.
 *
 * <p>Cycler-neutral so any cycler can fire animation events into
 * {@link CycleHudRegistry} without the HUD depending on a specific cycler's
 * own direction enum. Column Cycler's internal {@code ColumnCyclerRotator.Direction}
 * maps to this when it bridges rotation events to the registry.
 *
 * <p>{@code FORWARD} moves items toward the hotbar, the in-hand position, and
 * the HUD slides them right to left. {@code BACKWARD} is the mirror.
 */
public enum CyclerDirection {
    FORWARD,
    BACKWARD
}
