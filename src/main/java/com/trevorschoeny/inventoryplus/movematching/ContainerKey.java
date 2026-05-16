package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.core.BlockPos;

/**
 * Identity key for per-container persistence — the value the move-matching
 * (and later sorting) cycle setting is keyed against.
 *
 * <p>Three flavors cover the smoke-pass simplecontainer scope:
 * <ul>
 *   <li>{@link Block} — block-backed containers (chests, shulkers, barrels,
 *       hoppers, dispensers, droppers). Position-keyed so each block-in-the-
 *       world remembers its own setting.</li>
 *   <li>{@link EnderChest} — ender chests resolve to one shared key because
 *       the player has a single ender chest inventory regardless of which
 *       block they opened it through.</li>
 *   <li>{@link PlayerInventory} — the bare inventory screen (the player's
 *       main 3×9 grid). One shared key.</li>
 * </ul>
 *
 * <p>Dimension-aware keying (different dims having distinct block-position
 * spaces) is a deliberate omission for the smoke pass — same coords across
 * Overworld / Nether / End collide. Acceptable simplification because cycle
 * settings are user preferences, not world state, and the collision is
 * limited to "two chests at the same coords in different dimensions share
 * a setting." Filed as polish.
 */
public sealed interface ContainerKey {

    /** Stable string form for JSON serialization. */
    String toKeyString();

    /** Block-backed simplecontainer keyed by position. */
    record Block(BlockPos pos) implements ContainerKey {
        @Override
        public String toKeyString() {
            return "block:" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
    }

    /** Single shared key for the player's ender chest inventory. */
    record EnderChest() implements ContainerKey {
        @Override
        public String toKeyString() {
            return "ender_chest";
        }
    }

    /** Single shared key for the player's main inventory (3×9). */
    record PlayerInventory() implements ContainerKey {
        @Override
        public String toKeyString() {
            return "inventory";
        }
    }

    /** Singleton convenience instance for {@link EnderChest}. */
    EnderChest ENDER_CHEST = new EnderChest();

    /** Singleton convenience instance for {@link PlayerInventory}. */
    PlayerInventory PLAYER_INVENTORY = new PlayerInventory();
}
