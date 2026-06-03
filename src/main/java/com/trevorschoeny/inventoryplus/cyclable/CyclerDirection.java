package com.trevorschoeny.inventoryplus.cyclable;

/**
 * Direction of a cycle rotation, used by the shared HUD's slide animation.
 *
 * <p>Cycler-neutral so any cycler can fire animation events into
 * {@link CycleHudRegistry} without the HUD depending on a specific cycler's
 * own direction enum. Column Cycler's internal {@code ColumnCyclerRotator.Direction}
 * maps to this when it bridges rotation events to the registry.
 *
 * <p><b>FORWARD</b> = items shift toward the hotbar / in-hand position (the
 * HUD slides items right-to-left, per Trev's 2026-05-25 call). <b>BACKWARD</b>
 * = the mirror.
 */
public enum CyclerDirection {
    FORWARD,
    BACKWARD
}
