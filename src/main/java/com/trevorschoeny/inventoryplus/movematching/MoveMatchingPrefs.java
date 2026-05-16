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
 * Per-world, per-container persistence of the move-matching cycle setting.
 *
 * <h3>File layout (v2)</h3>
 *
 * Saves to {@code <config-dir>/inventoryplus/movematch-prefs.json}. Format:
 *
 * <pre>{@code
 * {
 *   "version": 2,
 *   "perWorld": {
 *     "singleplayer:New World": {
 *       "inventory": "ALL_MATCHING",
 *       "block:100,64,-30": "DISABLED"
 *     },
 *     "server:my.minecraft.server": {
 *       "ender_chest": "STACKABLE_ONLY"
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p>The world dimension is keyed by {@link WorldIdentity#current} — different
 * singleplayer worlds and different multiplayer servers each get their own
 * sub-map. Two chests at the same coordinates in different worlds no longer
 * collide.
 *
 * <h3>v1 → v2 migration</h3>
 *
 * If the loader sees {@code version: 1} (the flat {@code perContainer}
 * shape from the previous schema), it migrates every entry into a
 * {@code "legacy_v1_unscoped"} world bucket. Those entries stay reachable
 * until the player overwrites them in their actual worlds; new writes go
 * to the current world's bucket. The next save rewrites the file in v2
 * format.
 *
 * <h3>Stale-key pruning</h3>
 *
 * {@link #pruneStale} walks the current world's {@code block:x,y,z} keys
 * and drops any whose chunk is loaded AND whose block is no longer a
 * traditional container block (chest / barrel / shulker / hopper /
 * dispenser / dropper). Called at 1 Hz from a client tick handler so
 * broken chests don't carry their old cycle settings to whatever block is
 * placed at the same coords next. Chunks that aren't loaded are skipped
 * — the entry survives until the player visits.
 *
 * <h3>Concurrency</h3>
 *
 * All access is client-thread only (button clicks, keybind callbacks,
 * tick handler). No synchronization at this scope.
 */
public final class MoveMatchingPrefs {

    private MoveMatchingPrefs() {}

    private static final int CURRENT_VERSION = 2;

    /** Pretty-print for human inspection of the prefs file. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** {@code <config>/inventoryplus/movematch-prefs.json}. */
    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("movematch-prefs.json");
    }

    /** Outer dim: world id ({@link WorldIdentity}) → container key → cycle. */
    private static final Map<String, Map<String, MoveMatchingCycle>> PER_WORLD = new HashMap<>();

    /** Bucket name used for entries migrated from the pre-per-world v1 schema. */
    private static final String LEGACY_BUCKET = "legacy_v1_unscoped";

    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;

            if (version == 1) {
                // v1 schema: flat "perContainer" map; migrate into legacy bucket
                // so the user doesn't lose their existing settings, even though
                // those settings were unscoped (collided across worlds).
                migrateV1(root);
                InventoryPlusClient.LOGGER.info(
                        "[move-matching] migrated v1 prefs file → v2 (legacy bucket: '{}', {} entries)",
                        LEGACY_BUCKET,
                        PER_WORLD.getOrDefault(LEGACY_BUCKET, Map.of()).size());
                save();   // rewrite as v2 immediately
                return;
            }

            // v2 — perWorld map
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

    private static void migrateV1(JsonObject v1Root) {
        JsonObject perContainer = v1Root.has("perContainer")
                ? v1Root.getAsJsonObject("perContainer")
                : new JsonObject();
        Map<String, MoveMatchingCycle> legacyBucket = new HashMap<>();
        for (var entry : perContainer.entrySet()) {
            MoveMatchingCycle cycle = parseCycleSafely(entry.getValue().getAsString());
            if (cycle != null) legacyBucket.put(entry.getKey(), cycle);
        }
        if (!legacyBucket.isEmpty()) {
            PER_WORLD.put(LEGACY_BUCKET, legacyBucket);
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

    /**
     * Returns the cycle for the given container key in the player's
     * current world. Returns {@link MoveMatchingCycle#defaultCycle} when
     * unknown — and uses an empty-string world bucket when the player
     * isn't currently in a world (callers should generally not hit this
     * branch, but it's safe).
     */
    public static MoveMatchingCycle get(ContainerKey key) {
        if (key == null) return MoveMatchingCycle.defaultCycle();
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) return MoveMatchingCycle.defaultCycle();
        Map<String, MoveMatchingCycle> bucket = PER_WORLD.get(worldId);
        if (bucket == null) return MoveMatchingCycle.defaultCycle();
        return bucket.getOrDefault(key.toKeyString(), MoveMatchingCycle.defaultCycle());
    }

    /**
     * Sets the cycle for the given container key in the player's current
     * world + writes the file. Null key or no-world is a no-op (settings
     * for unkeyed containers don't survive a restart anyway).
     */
    public static void set(ContainerKey key, MoveMatchingCycle cycle) {
        if (key == null) return;
        String worldId = WorldIdentity.current(Minecraft.getInstance());
        if (worldId == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] cycle change with no world id — not persisting");
            return;
        }
        PER_WORLD.computeIfAbsent(worldId, k -> new HashMap<>())
                .put(key.toKeyString(), cycle);
        save();
    }

    /**
     * Tick-driven cleanup — walks {@code block:x,y,z} keys in the player's
     * current world, drops any whose chunk is loaded but whose block is
     * no longer a traditional container.
     *
     * <p>Chunks that aren't loaded are skipped (we don't know the state).
     * Block positions whose chunk loads later get pruned on the next
     * pass through.
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
        for (Map.Entry<String, MoveMatchingCycle> entry : bucket.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("block:")) continue;
            BlockPos pos = parseBlockPos(key);
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
     * Parses {@code block:x,y,z} back into a {@link BlockPos}. Returns null
     * on malformed input (defensive — shouldn't normally happen but a
     * hand-edited prefs file could produce one).
     */
    private static @Nullable BlockPos parseBlockPos(String key) {
        if (!key.startsWith("block:")) return null;
        try {
            String[] parts = key.substring("block:".length()).split(",");
            if (parts.length != 3) return null;
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * True if the block at the given position is in the move-matching
     * traditional-container set (chest, trapped chest, barrel, shulker,
     * hopper, dispenser, dropper). Ender chests aren't covered — they
     * use a shared key not tied to a position. Anything else (a chest
     * that got broken into air, replaced with a furnace, etc.) returns
     * false → prune.
     */
    private static boolean isTraditionalContainerBlock(Level level, BlockPos pos) {
        var block = level.getBlockState(pos).getBlock();
        return block instanceof ChestBlock          // covers TrappedChestBlock via subclass
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock; // covers DropperBlock via subclass
    }

    private static void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            JsonObject perWorld = new JsonObject();
            for (Map.Entry<String, Map<String, MoveMatchingCycle>> worldEntry : PER_WORLD.entrySet()) {
                JsonObject bucketJson = new JsonObject();
                for (Map.Entry<String, MoveMatchingCycle> entry : worldEntry.getValue().entrySet()) {
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
