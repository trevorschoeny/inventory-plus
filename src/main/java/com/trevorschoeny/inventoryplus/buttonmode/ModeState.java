package com.trevorschoeny.inventoryplus.buttonmode;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A button's mode: one global value every container uses, plus any
 * containers pinned to a value of their own. The shared mechanism behind
 * {@code features/button-modes.md}; Sort and Move Matching each own one
 * instance and differ only in the enum they cycle.
 *
 * <h3>Global by default, pinned as the exception</h3>
 *
 * {@link #effective} answers "what mode applies here": the container's
 * pinned value if it has one, otherwise the global. {@link #cycle} moves
 * whichever of those the container is currently following, so
 * right-clicking on a pinned container changes only that container.
 * {@link #togglePin} copies the current global into a pin, or discards
 * the pin; re-pinning starts from the global again, per the spec.
 *
 * <p>A container with no identity (an open path the tracker did not see)
 * cannot be pinned and simply follows the global. Pins on session-only
 * identities live until the game closes and are not written out.
 *
 * <h3>Global-only modes</h3>
 *
 * <p>A mode built with {@link #globalOnly} has no per-container scope at
 * all: middle-click does nothing and the tooltip does not offer it. The
 * lock button is the case (`features/button-modes.md`): its stops choose
 * what a press of {@code L} locks, which is a granularity choice rather
 * than a scope one, so pinning it to a container would mean nothing.
 *
 * <h3>Persistence</h3>
 *
 * {@code config/inventoryplus/<feature>.json}, version 2:
 * <pre>{@code
 * { "version": 2, "global": "CATEGORY",
 *   "pins": { "singleplayer:World": { "block:overworld:1,64,2": "QUANTITY_DESC" } } }
 * }</pre>
 * A version-1 file (Sort's old per-container map) is discarded on load,
 * by decision: converting it would have pinned every chest the player
 * had ever sorted.
 */
public final class ModeState<E extends Enum<E> & ModeStop> {

    private static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String feature;
    private final Class<E> type;
    private final E[] stops;
    private final E fallback;
    /** False for a mode with one value everywhere; see {@link #globalOnly}. */
    private final boolean pinnable;
    private E global;
    /** worldId -> identity key -> pinned value. */
    /**
     * Pinned stops per world: container identity key -> stop. A
     * {@link WorldStore}, saved whenever a pin changes. Pins on session-only
     * identities are held here too and filtered out when saving, exactly as
     * before; saving on their change just rewrites the same file contents.
     */
    private final WorldStore<Map<String, E>> pins = WorldStore.sortedMap(this::save);
    private boolean loaded;

    private ModeState(String feature, Class<E> type, E fallback, boolean pinnable) {
        this.feature = feature;
        this.type = type;
        this.stops = type.getEnumConstants();
        this.fallback = fallback;
        this.pinnable = pinnable;
        this.global = fallback;
    }

    /** @param feature file stem under {@code config/inventoryplus/}, also the log tag */
    public static <E extends Enum<E> & ModeStop> ModeState<E> of(String feature, Class<E> type, E fallback) {
        return new ModeState<>(feature, type, fallback, true);
    }

    /**
     * A mode with one value everywhere and no pins, for a button whose
     * stops are not a per-container choice.
     *
     * @param feature file stem under {@code config/inventoryplus/}, also the log tag
     */
    public static <E extends Enum<E> & ModeStop> ModeState<E> globalOnly(
            String feature, Class<E> type, E fallback) {
        return new ModeState<>(feature, type, fallback, false);
    }

    /** Whether this mode can be pinned to a container at all. */
    public boolean pinnable() {
        return pinnable;
    }

    /** True when there is more than one stop, i.e. the gestures do something. */
    public boolean hasCycle() {
        return stops.length > 1;
    }

    public E global() {
        return global;
    }

    public void setGlobal(E value) {
        if (value == null || value == global) return;
        global = value;
        save();
    }

    private static @Nullable String worldId() {
        return WorldIdentity.current(Minecraft.getInstance());
    }

    public boolean isPinned(@Nullable ContainerIdentity id) {
        if (!pinnable || id == null) return false;
        return pins.get().containsKey(id.key());
    }

    /** The mode in force for this container: its pin if it has one, else the global. */
    public E effective(@Nullable ContainerIdentity id) {
        if (pinnable && id != null) {
            E pinned = pins.get().get(id.key());
            if (pinned != null) return pinned;
        }
        return global;
    }

    /**
     * Advance by {@code step} stops (+1 forward, -1 back), wrapping. Moves
     * the container's pin if it has one, otherwise the global.
     */
    public void cycle(@Nullable ContainerIdentity id, int step) {
        if (!hasCycle()) return;
        E current = effective(id);
        int n = stops.length;
        E next = stops[((current.ordinal() + step) % n + n) % n];
        if (isPinned(id)) {
            pins.modify(m -> WorldStore.withEntry(m, id.key(), next));
        } else {
            setGlobal(next);
        }
        InventoryPlusClient.LOGGER.debug("[{}] {} -> {}{}", feature, current, next,
                isPinned(id) ? " (pinned " + id.key() + ")" : " (global)");
    }

    /**
     * Pin this container to its own value (starting from the current
     * global), or release it if already pinned. No-op without an identity.
     */
    public void togglePin(@Nullable ContainerIdentity id) {
        if (!pinnable || id == null) return;
        String key = id.key();
        Map<String, E> previous = pins.modify(m -> m.containsKey(key)
                ? WorldStore.withoutKey(m, key)
                : WorldStore.withEntry(m, key, global));
        if (previous.containsKey(key)) {
            InventoryPlusClient.LOGGER.debug("[{}] unpinned {}", feature, key);
        } else {
            InventoryPlusClient.LOGGER.debug("[{}] pinned {} at {}", feature, key, global);
        }
    }

    // ── Persistence ─────────────────────────────────────────────────────

    private Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve(feature + ".json");
    }

    public void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int version = root.has("version") ? root.get("version").getAsInt() : 1;
            if (version < CURRENT_VERSION) {
                // Decided 2026-09-05: the old per-container entries are dropped,
                // not converted. Every container follows the new global.
                InventoryPlusClient.LOGGER.info(
                        "[{}] {} is version {}; discarding its per-container entries (global mode replaces them)",
                        feature, path.getFileName(), version);
                save();
                return;
            }
            if (root.has("global")) global = parse(root.get("global").getAsString(), fallback);
            int count = 0;
            if (root.has("pins")) {
                for (var world : root.getAsJsonObject("pins").entrySet()) {
                    Map<String, E> m = new HashMap<>();
                    for (var pin : world.getValue().getAsJsonObject().entrySet()) {
                        m.put(pin.getKey(), parse(pin.getValue().getAsString(), global));
                    }
                    if (!m.isEmpty()) { pins.load(world.getKey(), m); count += m.size(); }
                }
            }
            InventoryPlusClient.LOGGER.info("[{}] loaded: global {}, {} pin(s)", feature, global, count);
        } catch (IOException | JsonSyntaxException | IllegalStateException | NumberFormatException e) {
            InventoryPlusClient.LOGGER.error("[{}] failed to read {}; using defaults", feature, path, e);
        }
    }

    private E parse(String name, E orElse) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            InventoryPlusClient.LOGGER.warn("[{}] unknown stop {}; using {}", feature, name, orElse);
            return orElse;
        }
    }

    private void save() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", CURRENT_VERSION);
            root.addProperty("global", global.name());
            JsonObject allPins = new JsonObject();
            pins.forEachWorld((worldId, worldPins) -> {
                JsonObject w = new JsonObject();
                worldPins.forEach((key, stop) -> {
                    // Session-only identities are remembered while the game runs, not written.
                    if (!key.startsWith("session:")) w.addProperty(key, stop.name());
                });
                if (w.size() > 0) allPins.add(worldId, w);
            });
            root.add("pins", allPins);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error("[{}] failed to write {}", feature, path, e);
        }
    }
}
