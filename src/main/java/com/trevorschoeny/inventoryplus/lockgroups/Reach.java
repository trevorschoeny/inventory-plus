package com.trevorschoeny.inventoryplus.lockgroups;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import com.trevorschoeny.inventoryplus.lockeditems.LockKind;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The reach record (`plans/reach.md`): one record, kept by the player, that
 * answers "may this operation touch this group?" for every operation, and
 * the lock groups whose keys it holds.
 *
 * <h2>What is stored</h2>
 *
 * <p>Only what the player changed (Designer and Imp, 2026-09-27):
 * <ul>
 *   <li><b>denied</b>: operation id to the exact set of group keys that
 *       operation may not use. An operation with no entry uses the default;
 *       an operation with an entry uses exactly that set, nothing added.</li>
 *   <li><b>groups</b>: the lock groups, defaults first.</li>
 *   <li><b>seen</b>: operation ids the player has been shown, so a new one is
 *       marked new in its reach.</li>
 *   <li><b>active</b>: the group the lock button has selected, which {@code L}
 *       applies.</li>
 * </ul>
 *
 * <h2>The default, computed by kind</h2>
 *
 * <p>An operation with no entry denies no slot group and no place, and denies
 * a lock group exactly when that group's kind's default does
 * ({@link #SLOT_LOCK_DENIES}, {@link #EXACT_ITEM_DENIES}; Item groups copy
 * Exact item). So an untouched operation treats a new custom group like its
 * kind's default without anything being written, and a changed default table
 * reaches everyone who never touched that operation.
 *
 * <h2>Keys</h2>
 *
 * <p>A slot group's key is {@code SlotGroupId.asString()}; a lock group's is
 * {@code inventoryplus:lock/<id>}; a place's is {@code inventoryplus:place/<name>}.
 * Keys the running game does not know (a mod removed, say) are kept and never
 * consulted, so reinstalling it finds its choices where it left them.
 *
 * <h2>Reading it</h2>
 *
 * <p>The record is an immutable snapshot swapped whole on every change, and is
 * flattened then into two lookup maps, so a read from any thread (the
 * integrated server asks too) is a volatile read and two hash lookups.
 */
public final class Reach {

    private Reach() {}

    // ── Ids and keys ────────────────────────────────────────────────────

    public static final String SLOT_LOCK = "slot_lock";
    public static final String EXACT_ITEM = "exact_item";

    private static final String LOCK_PREFIX = "inventoryplus:lock/";
    private static final String PLACE_PREFIX = "inventoryplus:place/";

    public static String lockKey(String groupId) {
        return LOCK_PREFIX + groupId;
    }

    public static String placeKey(String name) {
        return PLACE_PREFIX + name;
    }

    /** The three places Inventory Plus declares (`plans/reach.md`, "Places"). */
    public static final String SHULKER_BOXES = placeKey("shulker_boxes");
    public static final String BUNDLES = placeKey("bundles");
    public static final String ENDER_CHEST = placeKey("ender_chest");

    // ── The two defaults' tables (`plans/lock-groups.md`) ───────────────

    /** What Slot lock stops by default (Trev, 2026-09-11). */
    public static final Set<String> SLOT_LOCK_DENIES = Set.of(
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "menukit:shift_click_in", "menukit:shift_click_out", "menukit:collect",
            "menukit:world_pickup", "menukit:drop", "menukit:drop_stack", "menukit:drag_fill",
            "menukit:hotbar_swap", "menukit:offhand_swap", "inventoryplus:restock_take");

    /** What Exact item stops by default (Trev, 2026-09-11). Item groups copy it. */
    public static final Set<String> EXACT_ITEM_DENIES = Set.of(
            "menukit:drop", "menukit:drop_stack", "menukit:shift_click_out", "menukit:collect",
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "inventoryplus:column_cycle", "inventoryplus:hotbar_cycle");

    /** The default group of {@code kind}: what a new group copies and an orphaned lock falls back to. */
    public static String defaultOf(LockKind kind) {
        return kind == LockKind.SLOT ? SLOT_LOCK : EXACT_ITEM;
    }

    private static Set<String> defaultDenies(LockKind kind) {
        return kind == LockKind.SLOT ? SLOT_LOCK_DENIES : EXACT_ITEM_DENIES;
    }

    private static List<LockGroup> defaultGroups() {
        return List.of(
                new LockGroup(SLOT_LOCK, LockKind.SLOT, "Slot lock", "gray", "key.inventoryplus.lock_slot"),
                new LockGroup(EXACT_ITEM, LockKind.EXACT, "Exact item", "gray", null));
    }

    // ── The record ──────────────────────────────────────────────────────

    /**
     * One immutable snapshot of the record, with its flattened lookups.
     *
     * @param lockDenies lock group id to the operations it stops, defaults and
     *                   entries combined
     * @param groupDenies operation id to the slot-group and place keys it may
     *                    not use (only entries can deny these)
     */
    private record State(Map<String, Set<String>> denied, List<LockGroup> groups, Set<String> seen,
                         String active, Map<String, LockGroup> byId,
                         Map<String, Set<String>> lockDenies, Map<String, Set<String>> groupDenies) {

        static State of(Map<String, Set<String>> denied, List<LockGroup> groups, Set<String> seen, String active) {
            Map<String, Set<String>> frozenDenied = new TreeMap<>();
            denied.forEach((op, keys) -> frozenDenied.put(op, Collections.unmodifiableSortedSet(new TreeSet<>(keys))));

            Map<String, LockGroup> byId = new LinkedHashMap<>();
            for (LockGroup g : groups) byId.putIfAbsent(g.id(), g);
            // The defaults always exist, whatever a hand-edited file says.
            for (LockGroup d : defaultGroups()) {
                LockGroup present = byId.get(d.id());
                if (present == null || present.kind() != d.kind()) byId.put(d.id(), d);
            }
            List<LockGroup> ordered = new ArrayList<>();
            for (LockGroup d : defaultGroups()) ordered.add(byId.get(d.id()));
            for (LockGroup g : byId.values()) if (!g.isDefault()) ordered.add(g);

            // Flatten: a lock group stops an operation if the operation's entry
            // names it, or, for an operation with no entry, if its kind's
            // default stops it.
            Map<String, Set<String>> lockDenies = new HashMap<>();
            Map<String, Set<String>> groupDenies = new HashMap<>();
            for (LockGroup g : ordered) {
                Set<String> ops = new HashSet<>();
                for (String op : defaultDenies(g.kind())) if (!frozenDenied.containsKey(op)) ops.add(op);
                frozenDenied.forEach((op, keys) -> { if (keys.contains(g.reachKey())) ops.add(op); });
                lockDenies.put(g.id(), Set.copyOf(ops));
            }
            frozenDenied.forEach((op, keys) -> {
                Set<String> others = new HashSet<>();
                for (String k : keys) if (!k.startsWith(LOCK_PREFIX)) others.add(k);
                if (!others.isEmpty()) groupDenies.put(op, Set.copyOf(others));
            });

            String activeId = byId.containsKey(active) ? active : SLOT_LOCK;
            return new State(Collections.unmodifiableMap(frozenDenied), List.copyOf(ordered),
                    Collections.unmodifiableSortedSet(new TreeSet<>(seen)), activeId,
                    Collections.unmodifiableMap(byId), Map.copyOf(lockDenies), Map.copyOf(groupDenies));
        }
    }

    private static volatile State state = State.of(Map.of(), defaultGroups(), Set.of(), SLOT_LOCK);

    /** Told after a change, to save the file. Set once by the config at init. */
    private static Runnable onChange = () -> {};

    public static void onChange(Runnable save) {
        onChange = Objects.requireNonNull(save, "save");
    }

    private static void replace(State next) {
        state = next;
        onChange.run();
    }

    // ── Reads ───────────────────────────────────────────────────────────

    /** Every group, the two defaults first. */
    public static List<LockGroup> groups() {
        return state.groups();
    }

    public static @Nullable LockGroup group(String id) {
        return state.byId().get(id);
    }

    /**
     * The group a stored lock belongs to: the one named, or the default of
     * its kind when that group is gone or has a different kind (a swapped
     * config, say). `plans/lock-groups.md`, "Storage".
     */
    public static LockGroup resolve(@Nullable String id, LockKind kind) {
        LockGroup g = id == null ? null : state.byId().get(id);
        if (g != null && g.kind() == kind) return g;
        return state.byId().get(defaultOf(kind));
    }

    /** True when lock group {@code groupId} stops {@code operation}. */
    public static boolean lockDenies(String groupId, String operation) {
        Set<String> ops = state.lockDenies().get(groupId);
        return ops != null && ops.contains(operation);
    }

    /** True when {@code operation} may not use slot group or place {@code key}. */
    public static boolean denies(String operation, String key) {
        Set<String> keys = state.groupDenies().get(operation);
        return keys != null && keys.contains(key);
    }

    /** True when the player has changed {@code operation}'s reach. */
    public static boolean hasEntry(String operation) {
        return state.denied().containsKey(operation);
    }

    /** The group the lock button has selected, which {@code L} applies. */
    public static LockGroup active() {
        return state.byId().get(state.active());
    }

    public static boolean seen(String operation) {
        return state.seen().contains(operation);
    }

    // ── Writes ──────────────────────────────────────────────────────────

    /** Selects the next group ({@code step} 1) or the previous one ({@code -1}), wrapping. */
    public static LockGroup cycleActive(int step) {
        State s = state;
        List<LockGroup> gs = s.groups();
        int i = 0;
        for (int j = 0; j < gs.size(); j++) if (gs.get(j).id().equals(s.active())) i = j;
        LockGroup next = gs.get(Math.floorMod(i + step, gs.size()));
        replace(State.of(s.denied(), s.groups(), s.seen(), next.id()));
        return next;
    }

    /**
     * Makes a custom group and returns it. It starts with its kind's
     * default's choices: operations with no entry treat it like that default
     * by construction, and an operation with an entry gets its key exactly
     * when the default's key is in that entry.
     */
    public static LockGroup createGroup(LockKind kind, String name, String colour) {
        State s = state;
        int max = 0;
        for (LockGroup g : s.groups()) {
            if (g.id().matches("g\\d+")) max = Math.max(max, Integer.parseInt(g.id().substring(1)));
        }
        LockGroup made = new LockGroup("g" + (max + 1), kind, name, colour, null);
        String defaultKey = lockKey(defaultOf(kind));
        Map<String, Set<String>> denied = new TreeMap<>();
        s.denied().forEach((op, keys) -> {
            Set<String> copy = new HashSet<>(keys);
            if (copy.contains(defaultKey)) copy.add(made.reachKey());
            denied.put(op, copy);
        });
        List<LockGroup> groups = new ArrayList<>(s.groups());
        groups.add(made);
        replace(State.of(denied, groups, s.seen(), s.active()));
        return made;
    }

    /**
     * Sets whether lock group {@code groupId} stops {@code operation}, writing
     * the operation's entry from its current effective set if it had none.
     * Used by the upgrade from 1.5.x; the menu's own writes come in stage 3.
     */
    public static void setLockDenies(String groupId, String operation, boolean deny) {
        State s = state;
        if (lockDenies(groupId, operation) == deny) return;
        Set<String> entry = new HashSet<>(effectiveDenied(operation));
        if (deny) entry.add(lockKey(groupId)); else entry.remove(lockKey(groupId));
        Map<String, Set<String>> denied = new TreeMap<>(s.denied());
        denied.put(operation, entry);
        replace(State.of(denied, s.groups(), s.seen(), s.active()));
    }

    /**
     * The set of keys {@code operation} may not use as it stands: its entry,
     * or for an operation without one, the lock groups the kind defaults stop.
     */
    public static Set<String> effectiveDenied(String operation) {
        State s = state;
        Set<String> entry = s.denied().get(operation);
        if (entry != null) return entry;
        Set<String> out = new TreeSet<>();
        for (LockGroup g : s.groups()) {
            if (defaultDenies(g.kind()).contains(operation)) out.add(g.reachKey());
        }
        return Collections.unmodifiableSet(out);
    }

    // ── Persistence (the "reach" object in config.json) ─────────────────

    private static final int VERSION = 1;

    /** Replaces the record with {@code json}'s; {@code null} means a fresh install. Does not save. */
    public static void read(@Nullable JsonObject json) {
        if (json == null) {
            state = State.of(Map.of(), defaultGroups(), Set.of(), SLOT_LOCK);
            return;
        }
        Map<String, Set<String>> denied = new TreeMap<>();
        if (json.has("denied") && json.get("denied").isJsonObject()) {
            for (var e : json.getAsJsonObject("denied").entrySet()) {
                Set<String> keys = new HashSet<>();
                if (e.getValue().isJsonArray()) for (JsonElement k : e.getValue().getAsJsonArray()) keys.add(k.getAsString());
                denied.put(e.getKey(), keys);
            }
        }
        List<LockGroup> groups = new ArrayList<>();
        if (json.has("groups") && json.get("groups").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("groups")) {
                if (!el.isJsonObject()) continue;
                JsonObject g = el.getAsJsonObject();
                LockKind kind = LockKind.fromId(string(g, "kind"));
                String id = string(g, "id");
                if (kind == null || id == null) continue;   // unreadable: dropped, its locks fall back by kind
                String name = string(g, "name");
                String colour = string(g, "colour");
                groups.add(new LockGroup(id, kind, name == null ? id : name,
                        colour == null ? "gray" : colour, string(g, "key")));
            }
        }
        Set<String> seen = new HashSet<>();
        if (json.has("seen") && json.get("seen").isJsonArray()) {
            for (JsonElement k : json.getAsJsonArray("seen")) seen.add(k.getAsString());
        }
        String active = string(json, "active");
        state = State.of(denied, groups, seen, active == null ? SLOT_LOCK : active);
    }

    /** The record as the "reach" object of {@code config.json}. */
    public static JsonObject write() {
        State s = state;
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        JsonObject denied = new JsonObject();
        s.denied().forEach((op, keys) -> {
            JsonArray arr = new JsonArray();
            for (String k : keys) arr.add(k);
            denied.add(op, arr);
        });
        json.add("denied", denied);
        JsonArray groups = new JsonArray();
        for (LockGroup g : s.groups()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", g.id());
            o.addProperty("kind", g.kind().id());
            o.addProperty("name", g.name());
            o.addProperty("colour", g.colour());
            if (g.key() == null) o.add("key", JsonNull.INSTANCE); else o.addProperty("key", g.key());
            groups.add(o);
        }
        json.add("groups", groups);
        JsonArray seen = new JsonArray();
        for (String op : s.seen()) seen.add(op);
        json.add("seen", seen);
        json.addProperty("active", s.active());
        return json;
    }

    private static @Nullable String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
