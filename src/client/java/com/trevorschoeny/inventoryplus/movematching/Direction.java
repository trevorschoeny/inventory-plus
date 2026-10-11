package com.trevorschoeny.inventoryplus.movematching;

/**
 * Direction of a Move Matching operation.
 *
 * <ul>
 *   <li><b>{@link #IN}</b>: items flow into the clicked slot group from
 *       elsewhere. The match set is built from the item types already in
 *       the clicked group.</li>
 *   <li><b>{@link #OUT}</b>: items flow out of the clicked slot group to
 *       elsewhere. The match set is built from all other visible slot
 *       groups (excluding the hotbar), so items that have a home elsewhere
 *       get pushed out.</li>
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

    /** Display label, "IN" or "OUT", used in tooltip text. */
    public String label() {
        return label;
    }
}
