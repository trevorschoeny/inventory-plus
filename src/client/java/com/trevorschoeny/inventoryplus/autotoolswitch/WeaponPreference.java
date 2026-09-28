package com.trevorschoeny.inventoryplus.autotoolswitch;

/**
 * Player's preferred weapon type for Auto Tool Switch's combat path.
 *
 * <p>When the Weapons toggle fires a switch and the player has multiple
 * weapon kinds available, the preferred type wins regardless of
 * material — e.g., a preferred AXE in iron beats a non-preferred SWORD
 * in netherite. Within the preferred type, material/durability decide.
 *
 * <p>Cycled via the YACL config screen (Auto Tool Switch group →
 * Preferred Weapon). Persisted in {@code config.json} as the enum
 * name string.
 */
public enum WeaponPreference {
    SWORD,
    AXE,
    MACE,
    TRIDENT,
    SPEAR;

    /**
     * Parse a preference name from JSON config. Returns {@code fallback}
     * on null / empty / unknown name — tolerates config edits and
     * future enum additions.
     */
    public static WeaponPreference fromName(String name, WeaponPreference fallback) {
        if (name == null || name.isEmpty()) return fallback;
        for (WeaponPreference p : values()) {
            if (p.name().equals(name)) return p;
        }
        return fallback;
    }
}
