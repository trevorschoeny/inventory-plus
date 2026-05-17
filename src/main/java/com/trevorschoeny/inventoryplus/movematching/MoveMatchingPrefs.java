package com.trevorschoeny.inventoryplus.movematching;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-world, per-container, per-direction persistence of the Move
 * Matching cycle setting.
 *
 * <h3>File layout (v3)</h3>
 *
 * Saves to {@code <config-dir>/inventoryplus/movematch-prefs.json}.
 * Format:
 *
 * <pre>{@code
 * {
 *   "version": 3,
 *   "perWorld": {
 *     "singleplayer:New World": {
 *       "in:inventory": "ALL_MATCHING",
 *       "out:inventory": "STACKABLE_ONLY",
 *       "in:block:100,64,-30": "DISABLED",
 *       "out:block:100,64,-30": "ALL_MATCHING"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>Each entry is keyed by {@code "<direction-prefix>:<container-key>"}
 * — see {@link Direction#storageKey()}. IN and OUT cycles are
 * independent per container.
 *
 * <h3>Migration</h3>
 *
 * <ul>
 *   <li><b>v1 → v3:</b> v1's flat {@code perContainer} entries get
 *       moved into a {@code "legacy_v1_unscoped"} world bucket AND
 *       prefixed with {@code "in:"} (since OUT didn't exist in v1).</li>
 *   <li><b>v2 → v3:</b> existing per-world entries get prefixed with
 *       {@code "in:"} (since OUT didn't exist in v2).</li>
 * </ul>
 *
 * <p>The next save after a migration rewrites the file in v3 format.
 *
 * <h3>Stale-key pruning</h3>
 *
 * {@link #pruneStale} walks the current world's
 * {@code "<dir>:block:x,y,z"} keys and drops any whose chunk is loaded
 * AND whose block is no longer a traditional container — covers both
 * IN and OUT entries since the block-validity check is direction-
 * agnostic.
 */
public final class MoveMatchingPrefs {

    private MoveMatchingPrefs() {}

    private static final int CURRENT_VERSION = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("movematch-prefs.json");
    }

    /** Outer dim: world id ({@link WorldIdentity}) → "<dir>:<container-key>" → cycle. */
    private static final Map<String, Map<String, MoveMatchingCycle>> PER_WORLD = new HashMap<>();

    private static final String LEGACY_BUCKET = "legacy_v1_unscoped";

    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;

        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;

            if (version == 1) {
                migrateV1ToV3(root);
                InventoryPlusClient.LOGGER.info(
                        "[move-matching] migrated v1 prefs file → v3 (legacy bucket: '{}', {} entries)",
                        LEGACY_BUCKET,
                        PER_WORLD.getOrDefault(LEGACY_BUCKET, Map.of()).size());
                save();
                return;
            }

            if (version == 2) {
                migrateV2ToV3(root);
                int total = PER_WORLD.values().stream().mapToInt(Map::size).sum();
                InventoryPlusClient.LOGGER.info(
                        "[move-matching] migrated v2 prefs → v3 (existing entries prefixed with 'in:', {} total)",
                        total);
                save();
                return;
            }

            // v3 — read as-is.
            JsonObject perWorld = root.has("perWorld")
                    ? root.getAsJsonObject("perWorld")
                    : new JsonObject();
            int totalEntries = 0;
            for (var worldEntry : perWorld.entrySet()) {
                String worldId = worldEntry.getKey();
                JsonElement worldValue = worldEntry.getValue();
                if (!worldValue.isJsonObject()) continue;
                Map<String, MoveMatchingCycle> bucket = new HashMap<>();
                for (var keyEntry : worldValue.getAsJsonObject().entrySet()) {
                    MoveMatchingCycle cycle = parseCycleSafely(keyEntry.getValue().getAsString());
                    if (cycle != null) bucket.put(keyEntry.getKey(), cycle);
                }
                if (!bucket.isEmpty()) {
                    PER_WORLD.put(worldId, bucket);
                    totalEntries += bucket.size();
                }
            }
            InventoryPlusClient.LOGGER.info(
                    "[move-matching] loaded {} entries across {} world(s) from {}",
                    totalEntries, PER_WORLD.size(), path);
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[move-matching] failed to parse {} — starting with empty prefs",
                    path, e);
        }
    }

    private static void migrateV1ToV3(JsonObject v1Root) {
        JsonObject perContainer = v1Root.has("perContainer")
                ? v1Root.getAsJsonObject("perContainer")
                : new JsonObject();
        Map<String, MoveMatchingCycle> legacyBucket = new HashMap<>();
        for (var entry : perContainer.entrySet()) {
            MoveMatchingCycle cycle = parseCycleSafely(entry.getValue().getAsString());
            if (cycle != null) {
                legacyBucket.put(Direction.IN.storageKey() + ":" + entry.getKey(), cycle);
            }
        }
        if (!legacyBucket.isEmpty()) {
            PER_WORLD.put(LEGACY_BUCKET, legacyBucket);
        }
    }

    private static void migrateV2ToV3(JsonObject v2Root) {
        JsonObject perWorld = v2Root.has("perWorld")
                ? v2Root.getAsJsonObject("perWorld")
                : new JsonObject();
        for (var worldEntry : perWorld.entrySet()) {
            String worldId = worldEntry.getKey();
            JsonElement worldValue = worldEntry.getValue();
            if (!worldValue.isJsonObject()) continue;
            Map<String, MoveMatchingCycle> bucket = new HashMap<>();
            for (var keyEntry : worldValue.getAsJsonObject().entrySet()) {
                MoveMatchingCycle cycle = parseCycleSafely(keyEntry.getValue().getAsString());
                if (cycle != null) {
                    bucket.put(Direction.IN.storageKey() + ":" + keyEntry.getKey(), cycle);
                }
            }
            if (!bucket.isEmpty()) PER_WORLD.put(worldId, bucket);
        }
    }

    private static @Nullable MoveMatchingCycle parseCycleSafely(String name) {
        try {
            return MoveMatchingCycle.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            InventoryPlusClient.LOGGER.warn(
                    "[move-matching] dropping unknown cycle value '{}'", name);
            return null;
        }
    }

    private static String buildKey(ContainerKey key, Direction direction) {
        return direction.storageKey() + ":" + key.toKeyString();
    }

    /**
     * Returns the cycle for the given container + direction in the
     * player's current world, or {@link MoveMatchingCycle#defaultCycle}
     * when unknown / no world.
     */
    public static MoveMatchingCycle get(ContainerKey key, Direction direction) {
        if (key == null) return MoveMatchingCycle.defaultCycle();
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return MoveMatchingCycle.defaultCycle();
        Map<String, MoveMatchingCycle> bucket = PER_WORLD.get(worldId);
        if (bucket == null) return MoveMatchingCycle.defaultCycle();
        return bucket.getOrDefault(buildKey(key, direction), MoveMatchingCycle.defaultCycle());
    }

    /**
     * Sets the cycle for the given container + direction in the
     * player's current world + writes the file.
     */
    public static void set(ContainerKey key, Direction direction, MoveMatchingCycle cycle) {
        if (key == null) return;
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] cycle change with no world id — not persisting");
            return;
        }
        PER_WORLD.computeIfAbsent(worldId, k -> new HashMap<>())
                .put(buildKey(key, direction), cycle);
        save();
    }

    /**
     * Tick-driven cleanup — walks {@code "<dir>:block:x,y,z"} keys in
     * the player's current world, drops any whose chunk is loaded but
     * whose block is no longer a traditional container. Direction-
     * agnostic — both IN and OUT entries for a broken chest get pruned
     * together.
     */
    public static void pruneStale(Minecraft mc) {
        if (mc == null) return;
        Level level = mc.level;
        if (level == null) return;
        String worldId = WorldIdentity.current(mc);
        if (worldId == null) return;
        Map<String, MoveMatchingCycle> bucket = PER_WORLD.get(worldId);
        if (bucket == null || bucket.isEmpty()) return;

        List<String> toRemove = new ArrayList<>();
        for (var entry : bucket.entrySet()) {
            String key = entry.getKey();
            // Keys look like "in:block:x,y,z" or "out:block:x,y,z". Skip
            // non-block keys (in:inventory, out:ender_chest, etc.).
            BlockPos pos = parseBlockPosFromDirectionalKey(key);
            if (pos == null) continue;
            if (!level.hasChunkAt(pos)) continue;
            if (!isTraditionalContainerBlock(level, pos)) {
                toRemove.add(key);
            }
        }
        if (toRemove.isEmpty()) return;
        for (String key : toRemove) bucket.remove(key);
        InventoryPlusClient.LOGGER.debug(
                "[move-matching] pruned {} stale block-keyed entries from world '{}'",
                toRemove.size(), worldId);
        save();
    }

    /**
     * Parses a key like {@code "in:block:1,2,3"} or {@code "out:block:1,2,3"}
     * into a {@link BlockPos}. Returns null for non-block keys (e.g.,
     * {@code "in:inventory"}) or malformed input.
     */
    private static @Nullable BlockPos parseBlockPosFromDirectionalKey(String key) {
        // Strip the "in:" or "out:" prefix.
        int colonIdx = key.indexOf(':');
        if (colonIdx < 0) return null;
        String rest = key.substring(colonIdx + 1);
        if (!rest.startsWith("block:")) return null;
        try {
            String[] parts = rest.substring("block:".length()).split(",");
            if (parts.length != 3) return null;
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isTraditionalContainerBlock(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock;
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            for (var worldEntry : PER_WORLD.entrySet()) {
                JsonObject bucketJson = new JsonObject();
                for (var entry : worldEntry.getValue().entrySet()) {
                    bucketJson.addProperty(entry.getKey(), entry.getValue().name());
                }
                perWorld.add(worldEntry.getKey(), bucketJson);
            }
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[move-matching] failed to write {} — change won't survive a restart",
                    path, e);
        }
    }
}
