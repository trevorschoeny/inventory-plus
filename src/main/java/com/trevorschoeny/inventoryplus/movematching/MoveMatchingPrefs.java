package com.trevorschoeny.inventoryplus.movematching;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-container persistence of the move-matching cycle setting.
 *
 * <h3>File layout</h3>
 *
 * Saves to {@code <config-dir>/inventoryplus/movematch-prefs.json}. Format:
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "perContainer": {
 *     "inventory": "ALL_MATCHING",
 *     "ender_chest": "STACKABLE_ONLY",
 *     "block:100,64,-30": "DISABLED"
 *   }
 * }
 * }</pre>
 *
 * <h3>Lifecycle</h3>
 *
 * <ul>
 *   <li>{@link #load()} — called once at mod init. Reads the file if
 *       present; on parse error logs + starts with an empty map (the
 *       broken file is left in place so a maintainer can inspect it).</li>
 *   <li>{@link #get(ContainerKey)} — returns the saved cycle for the key,
 *       or {@link MoveMatchingCycle#defaultCycle()} if unknown.</li>
 *   <li>{@link #set(ContainerKey, MoveMatchingCycle)} — updates the
 *       in-memory map AND writes the file on the same call. Save-on-
 *       change is fine here because cycle changes are rare user inputs
 *       (a few per session at most).</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 *
 * The cycle map is accessed from the client thread only (button clicks,
 * keybind callbacks, init). No synchronization needed at this scope. If
 * a future feature reads prefs from a non-client thread, revisit.
 */
public final class MoveMatchingPrefs {

    private MoveMatchingPrefs() {}

    private static final int CURRENT_VERSION = 1;

    /** Pretty-print for human inspection of the prefs file. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code <config>/inventoryplus/movematch-prefs.json}. */
    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("movematch-prefs.json");
    }

    /**
     * Cycle setting per stringified {@link ContainerKey}. We store strings
     * rather than {@link ContainerKey} objects in the map because JSON
     * round-trip is string-keyed and the comparison is identity-cheaper.
     */
    private static final Map<String, MoveMatchingCycle> CYCLE_BY_KEY = new HashMap<>();

    private static boolean loaded = false;

    /**
     * Loads prefs from disk. Safe to call multiple times — only the first
     * call reads the file. Subsequent calls are no-op (the in-memory map
     * is the source of truth after first load).
     */
    public static void load() {
        if (loaded) return;
        loaded = true;

        Path path = filePath();
        if (!Files.exists(path)) {
            // First run — empty map. Don't pre-create the file; it's
            // written on first {@link #set} call.
            return;
        }

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject perContainer = root.has("perContainer")
                    ? root.getAsJsonObject("perContainer")
                    : new JsonObject();
            for (var entry : perContainer.entrySet()) {
                String keyStr = entry.getKey();
                String cycleStr = entry.getValue().getAsString();
                try {
                    MoveMatchingCycle cycle = MoveMatchingCycle.valueOf(cycleStr);
                    CYCLE_BY_KEY.put(keyStr, cycle);
                } catch (IllegalArgumentException ignored) {
                    // Unknown cycle enum value — silently drop. Could be
                    // a value from a future version of the mod; defaulting
                    // is the safe behavior.
                    InventoryPlusClient.LOGGER.warn(
                            "[move-matching] dropping unknown cycle value '{}' for key '{}'",
                            cycleStr, keyStr);
                }
            }
            InventoryPlusClient.LOGGER.info(
                    "[move-matching] loaded {} container-cycle entries from {}",
                    CYCLE_BY_KEY.size(), path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            // Don't blow up the mod on a broken prefs file — log + start
            // empty. The broken file stays on disk for inspection; the
            // next {@link #set} call overwrites it.
            InventoryPlusClient.LOGGER.error(
                    "[move-matching] failed to parse {} — starting with empty prefs",
                    path, e);
        }
    }

    /**
     * Returns the cycle setting for the given key, or the mod-config
     * default ({@link MoveMatchingCycle#defaultCycle()}) when the key is
     * unknown.
     *
     * <p>{@code null} key is treated as "no key resolved" and falls back
     * to the default — used by execution paths where
     * {@link ContainerKeyResolver} couldn't pin down the container
     * (minecart variants etc., per resolver javadoc).
     */
    public static MoveMatchingCycle get(ContainerKey key) {
        if (key == null) return MoveMatchingCycle.defaultCycle();
        return CYCLE_BY_KEY.getOrDefault(
                key.toKeyString(),
                MoveMatchingCycle.defaultCycle());
    }

    /**
     * Sets the cycle setting for the given key + writes the file. Null
     * key is a no-op (we don't persist for unkeyed containers — those
     * always read the default).
     */
    public static void set(ContainerKey key, MoveMatchingCycle cycle) {
        if (key == null) return;
        CYCLE_BY_KEY.put(key.toKeyString(), cycle);
        save();
    }

    /**
     * Writes the prefs file. Creates the parent directory if missing.
     */
    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perContainer = new JsonObject();
            for (var entry : CYCLE_BY_KEY.entrySet()) {
                perContainer.addProperty(entry.getKey(), entry.getValue().name());
            }
            root.add("perContainer", perContainer);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[move-matching] failed to write {} — cycle change won't survive a restart",
                    path, e);
        }
    }
}
