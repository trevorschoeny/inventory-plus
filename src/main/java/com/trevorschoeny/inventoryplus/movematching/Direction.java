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
 * OUT) and its own mode, held separately in {@link MoveMatchingModes}.
 *
 * <p>Both names are relative to the player inventory, which is always the
 * clicked group. {@code IN} is therefore the direction a player would
 * describe as taking things <em>out of</em> the chest.
 */
public enum Direction {

    IN("IN"),
    OUT("OUT");

    private final String label;

    Direction(String label) {
        this.label = label;
    }

    /** Display label — "IN" or "OUT" — used in tooltip text. */
    public String label() {
        return label;
    }
}
