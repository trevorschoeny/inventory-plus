package com.trevorschoeny.inventoryplus.movematching;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves a stable identifier for the player's current world / server, so
 * per-container persistence is scoped to where they're playing rather than
 * shared across every game they touch.
 *
 * <h3>Identifier shape</h3>
 *
 * <ul>
 *   <li><b>Multiplayer</b> — {@code "server:<ip>"} where {@code <ip>} is the
 *       server entry's IP/hostname as the player connected. Different
 *       servers get different namespaces; reconnecting to the same server
 *       address reuses the same namespace.</li>
 *   <li><b>Singleplayer</b> — {@code "singleplayer:<level name>"} where
 *       {@code <level name>} is the world's save-folder name. Different
 *       singleplayer worlds get different namespaces.</li>
 *   <li><b>No world</b> (between worlds / disconnected / main menu) — null.
 *       Callers fall back to default behavior (no per-container read /
 *       no-op writes).</li>
 * </ul>
 *
 * <h3>Why ServerData.ip and not the full ServerAddress</h3>
 *
 * ServerData carries both the configured address and a resolved IP. The
 * configured address is what the player chose; we key by that so a server
 * moving hosts doesn't reset everyone's prefs. If two distinct servers
 * happen to share an address, they collide — acceptable trade-off for
 * the smoke pass (collision recovery would need server-handshake
 * fingerprinting, which is non-trivial client-side).
 *
 * <h3>Edge cases acknowledged</h3>
 *
 * <ul>
 *   <li>LAN join — the host's ServerData entry is synthesized by vanilla
 *       and should populate {@code ip} (typically {@code lan-host:port}).
 *       Works.</li>
 *   <li>Realms — ServerData populated with the realm's address.
 *       Works.</li>
 *   <li>Direct connect not added to server list — ServerData is still
 *       set during the session, just not persisted. Works for the
 *       active session; the prefs entry survives the session.</li>
 * </ul>
 */
public final class WorldIdentity {

    private WorldIdentity() {}

    /**
     * Returns the current world's persistence namespace, or null when
     * the player isn't in a world (main menu, between transitions).
     */
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
