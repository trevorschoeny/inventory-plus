package com.trevorschoeny.inventoryplus.blueprints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotRef;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Blueprints: a saved layout of a container's reservations and locks, never its Intake.
 * Saved from a container's slots and loaded onto another's, by slot index. One library
 * for every world, kept on this computer.
 *
 * <p>Loading makes the container match: each slot the blueprint covers gets the
 * blueprint's reservation (or none) and lock (or none), and whatever no longer belongs
 * in a newly reserved slot is moved out by SlotKit's {@code Reservations.reserve}. A
 * container larger than the blueprint keeps its extra slots as they are; a smaller one
 * takes the slots it has. A placed container's reservations and locks need Inventory
 * Plus and SlotKit on the server; the player's own inventory works anywhere.
 *
 * <p>File: {@code config/inventoryplus/blueprints.json}:
 * <pre>{@code
 * { "version": 1, "blueprints": [
 *   { "name": "Smelting", "size": 27,
 *     "slots": [ { "slot": 0, "reservation": { "item": {...}, "cap": 16 }, "locked": true } ] } ] }
 * }</pre>
 * A reservation is SlotKit's own saved form ({@link Reservation#CODEC}), so it reads back
 * whichever mod wrote it. Item stacks need the game's registries to read, so the library
 * is held as it was saved and each blueprint is read when it is asked for, in a world.
 */
public final class Blueprints {

    private Blueprints() {}

    private static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One saved slot: its reservation ({@link Reservation#NONE} for none) and whether it is locked. */
    public record SlotRule(Reservation reservation, boolean locked) {
        public static final SlotRule EMPTY = new SlotRule(Reservation.NONE, false);

        public boolean isEmpty() {
            return reservation.isNone() && !locked;
        }
    }

    /**
     * A blueprint: {@code size} slots, of which {@code slots} holds every one with a
     * reservation or a lock, by slot index. Any other index is {@link SlotRule#EMPTY}.
     */
    public record Blueprint(String name, int size, Map<Integer, SlotRule> slots) {
        public Blueprint {
            slots = Map.copyOf(slots);
        }

        public SlotRule at(int index) {
            return slots.getOrDefault(index, SlotRule.EMPTY);
        }
    }

    /** The library as saved: name to its JSON, in save order. Read from the file once. */
    private static final Map<String, JsonObject> LIBRARY = new LinkedHashMap<>();
    private static boolean loaded;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("inventoryplus").resolve("blueprints.json");
    }

    // The container's slots

    /**
     * The open menu's own container's slots, by container index: the container of the
     * menu's first slot that isn't the player's (a chest's, a box's; a double chest is
     * one container). Slot groups a mod adds to the menu are containers of their own and
     * stay out; a screen listing several containers passes the one it means itself.
     */
    public static List<Slot> containerSlots(AbstractContainerMenu menu) {
        List<Slot> out = new ArrayList<>();
        Object own = null;
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) continue;
            if (own == null) own = s.container;
            if (s.container == own) out.add(s);
        }
        out.sort(Comparator.comparingInt(Slot::getContainerSlot));
        return out;
    }

    // Save and load

    /** Reads {@code slots}' reservations and locks as a blueprint named {@code name}. Nothing is stored. */
    public static Blueprint capture(String name, Player player, List<Slot> slots) {
        Map<Integer, SlotRule> rules = new LinkedHashMap<>();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            SlotRule rule = new SlotRule(Reservations.get(SlotRef.of(player.containerMenu, slot, player)),
                    LockedSlots.isLockedSlot(slot));
            if (!rule.isEmpty()) rules.put(i, rule);
        }
        return new Blueprint(name, slots.size(), rules);
    }

    /** Saves {@code blueprint} in the library, over one of the same name. */
    public static void save(Blueprint blueprint, HolderLookup.Provider registries) {
        load();
        LIBRARY.put(blueprint.name(), encode(blueprint, registries));
        write();
    }

    /**
     * Makes {@code slots} match {@code blueprint}, slot by slot, as the player's own
     * action. Returns how many slots it changed.
     */
    public static int apply(Blueprint blueprint, Player player, List<Slot> slots) {
        int changed = 0;
        for (int i = 0; i < Math.min(blueprint.size(), slots.size()); i++) {
            Slot slot = slots.get(i);
            SlotRule want = blueprint.at(i);
            boolean did = false;
            if (!Reservations.get(SlotRef.of(player.containerMenu, slot, player)).equals(want.reservation())) {
                Reservations.reserve(player, slot, want.reservation());
                did = true;
            }
            if (LockedSlots.isLockedSlot(slot) != want.locked() && (LockedSlots.isLockableHere(slot) || !want.locked())) {
                LockedSlots.setLocked(slot, want.locked());
                did = true;
            }
            if (did) changed++;
        }
        return changed;
    }

    /** The blueprint named {@code name}, read with {@code registries}, or empty. */
    public static Optional<Blueprint> get(String name, HolderLookup.Provider registries) {
        load();
        JsonObject json = LIBRARY.get(name);
        return json == null ? Optional.empty() : Optional.of(decode(json, registries));
    }

    /** Every blueprint's name, in save order. */
    public static List<String> names() {
        load();
        return List.copyOf(LIBRARY.keySet());
    }

    /**
     * Renames blueprint {@code from} to {@code to}, keeping its place in the library and
     * replacing one already called {@code to}. Returns whether anything changed: not for
     * a name not in the library, a blank one, or the same one.
     */
    public static boolean rename(String from, String to) {
        load();
        String name = to.strip();
        if (!LIBRARY.containsKey(from) || name.isEmpty() || name.equals(from)) return false;
        Map<String, JsonObject> renamed = new LinkedHashMap<>();
        LIBRARY.forEach((key, json) -> {
            if (key.equals(name)) return;
            if (!key.equals(from)) {
                renamed.put(key, json);
                return;
            }
            JsonObject copy = json.deepCopy();
            copy.addProperty("name", name);
            renamed.put(name, copy);
        });
        LIBRARY.clear();
        LIBRARY.putAll(renamed);
        write();
        return true;
    }

    public static void delete(String name) {
        load();
        if (LIBRARY.remove(name) != null) write();
    }

    // The saved form

    public static JsonObject encode(Blueprint blueprint, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        JsonObject json = new JsonObject();
        json.addProperty("name", blueprint.name());
        json.addProperty("size", blueprint.size());
        JsonArray slots = new JsonArray();
        blueprint.slots().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            JsonObject slot = new JsonObject();
            slot.addProperty("slot", e.getKey());
            if (!e.getValue().reservation().isNone()) {
                slot.add("reservation", Reservation.CODEC.encodeStart(ops, e.getValue().reservation()).getOrThrow());
            }
            if (e.getValue().locked()) slot.addProperty("locked", true);
            slots.add(slot);
        });
        json.add("slots", slots);
        return json;
    }

    /** A blueprint from its saved form. A slot that doesn't read (an item from a removed mod) is skipped. */
    public static Blueprint decode(JsonObject json, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Map<Integer, SlotRule> rules = new LinkedHashMap<>();
        for (JsonElement el : json.getAsJsonArray("slots")) {
            JsonObject slot = el.getAsJsonObject();
            Reservation r = !slot.has("reservation") ? Reservation.NONE
                    : Reservation.CODEC.parse(ops, slot.get("reservation")).result().orElse(null);
            if (r == null) {
                InventoryPlusClient.LOGGER.warn("[blueprints] {}: slot {} has a reservation that doesn't read here, skipped",
                        json.get("name").getAsString(), slot.get("slot").getAsInt());
                continue;
            }
            SlotRule rule = new SlotRule(r, slot.has("locked") && slot.get("locked").getAsBoolean());
            if (!rule.isEmpty()) rules.put(slot.get("slot").getAsInt(), rule);
        }
        return new Blueprint(json.get("name").getAsString(), json.get("size").getAsInt(), rules);
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("blueprints")) {
                JsonObject b = el.getAsJsonObject();
                LIBRARY.put(b.get("name").getAsString(), b);
            }
        } catch (IOException | RuntimeException e) {
            InventoryPlusClient.LOGGER.error("[blueprints] failed to read {}, starting with none", path, e);
        }
    }

    private static void write() {
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            JsonArray all = new JsonArray();
            LIBRARY.values().forEach(all::add);
            root.add("blueprints", all);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error("[blueprints] failed to write {}, change won't survive a restart", path, e);
        }
    }

    /** For the self-checks: forget the library in memory, so the next read comes from the file. */
    public static void reloadForChecks() {
        LIBRARY.clear();
        loaded = false;
    }
}
