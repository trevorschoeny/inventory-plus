package com.trevorschoeny.inventoryplus.movematching;

/**
 * Direction of a Move Matching operation.
 *
 * <ul>
 *   <li><b>{@link #IN}</b> — items flow INTO the clicked slot group from
 *       elsewhere. The match-set is built from the clicked group's
 *       existing items (per spec §"What — Move Matching IN": "matches
 *       on item types already in the target").</li>
 *   <li><b>{@link #OUT}</b> — items flow OUT of the clicked slot group
 *       to elsewhere. The match-set is built from all OTHER visible
 *       slot groups (excluding hotbar) — the items that have a "home"
 *       elsewhere get pushed out.</li>
 * </ul>
 *
 * <p>Each direction has its own keybind ({@code I} for IN, {@code O} for
 * OUT) and its own per-container cycle setting (IN cycle is independent
 * of OUT cycle, per spec's resolution of the open question).
 *
 * <h3>Storage prefix</h3>
 *
 * {@link #storageKey()} returns the prefix used in
 * {@link MoveMatchingPrefs}'s JSON keys ({@code "in:"} / {@code "out:"})
 * so the same prefs file can track both directions per container
 * without collision.
 */
public enum Direction {

    IN("IN", "in"),
    OUT("OUT", "out");

    private final String label;
    private final String storageKey;

    Direction(String label, String storageKey) {
        this.label = label;
        this.storageKey = storageKey;
    }

    /** Display label — "IN" or "OUT" — used in tooltip text. */
    public String label() {
        return label;
    }

    /** Storage-key prefix for {@link MoveMatchingPrefs} — "in" or "out". */
    public String storageKey() {
        return storageKey;
    }
}
