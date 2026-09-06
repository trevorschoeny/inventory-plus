package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.buttonmode.ModeState;

import org.jetbrains.annotations.Nullable;

/**
 * Sort's mode: one global order, with containers pinned to their own on
 * request. The mechanism is {@link ModeState}; this only names the file
 * and the default.
 *
 * <p>The file is still {@code config/inventoryplus/sort-state.json}. Its
 * version-1 form (a per-container map from the earlier per-container
 * design) is discarded on load, per the 2026-09-05 decision, and the
 * global default becomes Category for upgrading players too.
 */
public final class SortState {

    private SortState() {}

    public static final ModeState<SortType> MODE =
            ModeState.of("sort-state", SortType.class, SortType.DEFAULT);

    public static void load() {
        MODE.load();
    }

    /** The order in force for this container: its pin, else the global. */
    public static SortType getType(@Nullable ContainerIdentity identity) {
        return MODE.effective(identity);
    }
}
