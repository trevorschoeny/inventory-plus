package com.trevorschoeny.inventoryplus.lockedslots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves a stable identifier for the player's current world / server,
 * so per-world persistence scopes to where they're playing.
 *
 * <ul>
 *   <li><b>Multiplayer</b> — {@code "server:<ip>"} (the configured
 *       server address).</li>
 *   <li><b>Singleplayer</b> — {@code "singleplayer:<level name>"} (the
 *       world's save-folder name).</li>
 *   <li><b>No world</b> — null; callers fall back to default behavior
 *       (no read / no-op writes).</li>
 * </ul>
 *
 * <p>Used by {@link LockedSlots} to keep lock state per-world per Trev
 * 2026-05-16. A previous incarnation of this helper lived in the
 * Move Matching package; it was deleted with the Move Matching
 * simplification (no per-container persistence anymore). Re-created
 * here for Locked Slots' use.
 */
public final class WorldIdentity {

    private WorldIdentity() {}

    public static @Nullable String current(Minecraft mc) {
        if (mc == null) return null;
        IntegratedServer sp = mc.getSingleplayerServer();
        if (sp != null) {
            String levelName = sp.getWorldData().getLevelName();
            return "singleplayer:" + levelName;
        }
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            return "server:" + server.ip;
        }
        return null;
    }
}
