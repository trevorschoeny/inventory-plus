package com.trevorschoeny.inventoryplus.sort;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-container sort-type state with JSON persistence — same pattern as
 * {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlots}.
 *
 * <h3>File layout</h3>
 *
 * <pre>{@code
 * config/inventoryplus/sort-state.json
 *
 * {
 *   "version": 1,
 *   "perWorld": {
 *     "singleplayer:My World": {
 *       "playerinv": "QUANTITY_DESC",
 *       "enderchest": "QUANTITY_DESC",
 *       "block:overworld:10,64,20": "QUANTITY_ASC"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>{@link #getType} returns {@link SortType#DEFAULT} when the
 * identity has no stored value. {@link #setType} writes through to the
 * JSON; storing the default value is treated as "no entry" and removes
 * the key, so the file only carries divergences from default.
 *
 * <h3>MVP scope</h3>
 *
 * Storage is wired end-to-end now even though MVP has no UI to change
 * the type — sort always reads {@code QUANTITY_DESC} via {@link
 * #getType}. When the type-cycle becomes a power-user feature, the
 * cycle handler calls {@link #setType} and the rest of the chain just
 * works.
 */
public final class SortState {

    private SortState() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code Map<worldId, Map<containerIdentityKey, SortType>>}. */
    private static final Map<String, Map<String, SortType>> PER_WORLD = new HashMap<>();
    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("sort-state.json");
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld")
                    ? root.getAsJsonObject("perWorld")
                    : new JsonObject();
            int total = 0;
            for (var worldEntry : perWorld.entrySet()) {
                String worldId = worldEntry.getKey();
                JsonObject worldMap = worldEntry.getValue().getAsJsonObject();
                Map<String, SortType> map = new HashMap<>();
                for (var entry : worldMap.entrySet()) {
                    String identityKey = entry.getKey();
                    String typeName = entry.getValue().getAsString();
                    SortType type = parseTypeOrDefault(typeName, identityKey);
                    if (type != SortType.DEFAULT) {
                        map.put(identityKey, type);
                    }
                }
                if (!map.isEmpty()) {
                    PER_WORLD.put(worldId, map);
                    total += map.size();
                }
            }
            InventoryPlusClient.LOGGER.info(
                    "[sort] loaded {} entries across {} world(s) from {}",
                    total, PER_WORLD.size(), path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[sort] failed to read {} — starting empty", path, e);
        }
    }

    private static SortType parseTypeOrDefault(String typeName, String identityKey) {
        try {
            return SortType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            InventoryPlusClient.LOGGER.warn(
                    "[sort] unknown sort type {} for {} — falling back to default",
                    typeName, identityKey);
            return SortType.DEFAULT;
        }
    }

    /**
     * Returns the stored sort type for the given identity, or
     * {@link SortType#DEFAULT} if none is stored. Never returns null.
     */
    public static SortType getType(ContainerIdentity identity) {
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return SortType.DEFAULT;
        Map<String, SortType> map = PER_WORLD.get(worldId);
        if (map == null) return SortType.DEFAULT;
        return map.getOrDefault(identity.key(), SortType.DEFAULT);
    }

    /**
     * Sets the sort type for the given identity. Storing
     * {@link SortType#DEFAULT} removes the entry (file only carries
     * divergences from default). Persists immediately.
     *
     * <p>No-op for non-persistent identities (e.g., session-only
     * identities for menus opened via non-click paths). Sort still
     * runs for those; just doesn't persist.
     */
    public static void setType(ContainerIdentity identity, SortType type) {
        if (!identity.isPersistent()) return;
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] setType with no world id — not persisting");
            return;
        }
        Map<String, SortType> map = PER_WORLD.computeIfAbsent(worldId, k -> new HashMap<>());
        boolean changed;
        if (type == SortType.DEFAULT) {
            changed = map.remove(identity.key()) != null;
        } else {
            SortType prev = map.put(identity.key(), type);
            changed = prev != type;
        }
        if (changed) save();
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            for (var entry : PER_WORLD.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                JsonObject worldMap = new JsonObject();
                for (var inner : entry.getValue().entrySet()) {
                    worldMap.addProperty(inner.getKey(), inner.getValue().name());
                }
                perWorld.add(entry.getKey(), worldMap);
            }
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[sort] failed to write {} — change won't survive a restart",
                    path, e);
        }
    }
}
