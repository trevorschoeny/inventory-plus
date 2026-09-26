package com.trevorschoeny.inventoryplus.api;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;

import net.minecraft.client.Minecraft;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * One value per world or server, persisted by its owner: the only way
 * Inventory Plus and its companions keep client-side state that belongs to
 * a world. Slot locks, cycler membership, pinned button modes and pocket
 * counts all live in one of these.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Before it, every feature hand-rolled its own {@code Map<worldId, ...>},
 * and each copy decided for itself what "nothing stored for this world yet"
 * meant: some returned {@code null}, some an immutable empty sentinel, one
 * created an entry just by being read. In 1.5.0 a caller mutated what one of
 * those accessors returned, and in a world with no container locks that was
 * {@code Map.of()}, so the first block anyone broke there threw
 * {@code UnsupportedOperationException} and crashed the game. The bug was one
 * line; the cause was that there were eight places to write that line.
 *
 * <h2>The contract</h2>
 *
 * <ul>
 *   <li><b>Values are immutable.</b> Every value this store hands out, or
 *       holds, has been passed through the owner's {@code freeze} function.
 *       A caller that tries to mutate one fails on its first run, in any
 *       world, rather than only in the one case nobody tested.</li>
 *   <li><b>Reads never create anything.</b> {@link #get()} with nothing
 *       stored returns the empty value and leaves the store untouched.</li>
 *   <li><b>Writes go through {@link #set}, {@link #modify} or
 *       {@link #remove}</b>, which notify the owner (normally its
 *       {@code save}) only when the value actually changed.</li>
 *   <li><b>Empty and absent are the same state.</b> Writing the empty value
 *       removes the world's entry, so emptied worlds never accumulate in the
 *       owner's file.</li>
 *   <li><b>No world, no-op.</b> Outside a world, reads return the empty
 *       value and writes do nothing, the way MenuKit's {@code NoServerTier}
 *       null object behaves: deliberately, not by accident.</li>
 * </ul>
 *
 * <h2>Precedent</h2>
 *
 * <p>This follows Fabric's Data Attachment API, which Inventory Plus cannot
 * use directly: attachments persist on server-owned targets, and Inventory
 * Plus works on servers that run nothing. The verbs mirror
 * {@code AttachmentTarget}: {@code get}, {@code has}, {@code set},
 * {@code modify}, {@code remove}, each write returning the previous value.
 * Fabric's own documentation encourages immutable attachment values and
 * warns that mutating one in place bypasses change notification, which is
 * the failure above. Vanilla's {@code SavedDataStorage} splits reads that do
 * not create from writes that do in the same way.
 *
 * <p>Two deliberate departures from Fabric, both following from "empty and
 * absent are the same state": an empty value is not stored, and
 * {@link #modify}'s modifier receives the empty value, never {@code null},
 * so no call site needs a null check.
 *
 * <p>One deliberate departure from vanilla: vanilla's storage owns its file,
 * and this store does not. Each owner keeps its existing file format through
 * {@link #load} and {@link #forEachWorld}, so saved locks, pins and pockets
 * load exactly as they did before this class existed. A new feature with no
 * legacy file is free to use the same hooks.
 *
 * <h2>When not to use it</h2>
 *
 * <p>For state that is one keyed collection per world. Not for caches that
 * decode against the level's registries ({@code LockedItems} holds decoded
 * item stacks for one world at a time, a different shape), not for
 * per-session state that must not persist, and not for state shared across
 * worlds (use a plain config value).
 *
 * <h2>Threading</h2>
 *
 * <p>Writes come from the client thread. Reads may come from any thread; in
 * single player the integrated server reads lock state while applying a
 * move. The map is concurrent and every value is immutable, so a reader on
 * another thread always sees one complete value, never a half-written one.
 *
 * @param <V> the per-world value; must be an immutable type after
 *            {@code freeze}, with value equality
 */
public final class WorldStore<V> {

    /** worldId -> frozen, non-empty value. Absent key means empty. */
    private final Map<String, V> byWorld = new ConcurrentHashMap<>();

    /** The value that means "nothing stored"; equal to any stored value would be a bug. */
    private final V empty;

    /** Makes a caller's value into the immutable form the store holds. */
    private final UnaryOperator<V> freeze;

    /** Told after a write changes the current world's value. */
    private final Runnable onChange;

    /** Which world is current. Null outside one. */
    private final Supplier<@Nullable String> currentWorld;

    private WorldStore(V empty, UnaryOperator<V> freeze, Runnable onChange,
                       Supplier<@Nullable String> currentWorld) {
        this.empty = Objects.requireNonNull(empty, "empty");
        this.freeze = Objects.requireNonNull(freeze, "freeze");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        this.currentWorld = Objects.requireNonNull(currentWorld, "currentWorld");
    }

    // ── Construction ────────────────────────────────────────────────────

    /**
     * A store whose "current world" is the world or server the client is in,
     * as named by Inventory Plus's world identity.
     *
     * @param empty    the value that means nothing is stored (e.g. {@code Set.of()})
     * @param freeze   turns any value of this type into its immutable form
     * @param onChange run after a write changes the current world's value;
     *                 normally the owner's {@code save}
     */
    public static <V> WorldStore<V> of(V empty, UnaryOperator<V> freeze, Runnable onChange) {
        return new WorldStore<>(empty, freeze, onChange,
                () -> WorldIdentity.current(Minecraft.getInstance()));
    }

    /**
     * As {@link #of(Object, UnaryOperator, Runnable)} with the current world
     * supplied explicitly. The store's only input from the game is this one
     * function, so it is named rather than hidden.
     */
    public static <V> WorldStore<V> of(V empty, UnaryOperator<V> freeze, Runnable onChange,
                                       Supplier<@Nullable String> currentWorld) {
        return new WorldStore<>(empty, freeze, onChange, currentWorld);
    }

    /**
     * A set per world, iterated in ascending order. Sorted so that iteration
     * and the owner's saved file are deterministic.
     */
    public static <T extends Comparable<? super T>> WorldStore<Set<T>> sortedSet(Runnable onChange) {
        return of(Collections.emptySortedSet(), WorldStore::freezeSortedSet, onChange);
    }

    /** A map per world, iterated in ascending key order. */
    public static <K extends Comparable<? super K>, T> WorldStore<Map<K, T>> sortedMap(Runnable onChange) {
        return of(Collections.emptySortedMap(), WorldStore::freezeSortedMap, onChange);
    }

    // ── Reads (never create) ────────────────────────────────────────────

    /** The current world's value, or the empty value. Never null, never mutable. */
    public V get() {
        String world = currentWorld.get();
        if (world == null) return empty;
        return byWorld.getOrDefault(world, empty);
    }

    /** True when the current world has a non-empty value. */
    public boolean has() {
        String world = currentWorld.get();
        return world != null && byWorld.containsKey(world);
    }

    // ── Writes (notify on change) ───────────────────────────────────────

    /**
     * Replaces the current world's value. The empty value, or {@code null},
     * removes it. Returns the previous value (empty if there was none).
     */
    public V set(@Nullable V value) {
        String world = currentWorld.get();
        if (world == null) {
            InventoryPlusClient.LOGGER.debug("[world-store] write with no world; not stored");
            return empty;
        }
        V next = value == null ? empty : Objects.requireNonNull(freeze.apply(value), "freeze returned null");
        V previous = empty.equals(next) ? byWorld.remove(world) : byWorld.put(world, next);
        if (previous == null) previous = empty;
        if (!previous.equals(next)) onChange.run();
        return previous;
    }

    /**
     * Applies {@code modifier} to the current world's value (the empty value
     * if there is none) and stores the result. The modifier builds a new
     * value; it must not mutate the one it is given, which is immutable
     * anyway. Returns the previous value.
     */
    public V modify(UnaryOperator<V> modifier) {
        return set(modifier.apply(get()));
    }

    /** Removes the current world's value. Returns the previous value. */
    public V remove() {
        return set(empty);
    }

    // ── Persistence hooks (the owner keeps its own file format) ─────────

    /** Visits every world that has a value, for the owner's {@code save}. */
    public void forEachWorld(BiConsumer<String, V> action) {
        byWorld.forEach(action);
    }

    /**
     * Stores {@code value} for {@code worldId} while the owner reads its file.
     * Does not notify: loading is not a change. Empty values are skipped.
     */
    public void load(String worldId, V value) {
        V frozen = freeze.apply(value);
        if (!empty.equals(frozen)) byWorld.put(worldId, frozen);
    }

    // ── Building new values (values are immutable, so writes make new ones) ─

    /** {@code set} with {@code element} present or absent. The input is not modified. */
    public static <T> Set<T> withElement(Set<T> set, T element, boolean present) {
        Set<T> next = new HashSet<>(set);
        if (present) next.add(element); else next.remove(element);
        return next;
    }

    /** {@code map} with {@code key} mapped to {@code value}. The input is not modified. */
    public static <K, T> Map<K, T> withEntry(Map<K, T> map, K key, T value) {
        Map<K, T> next = new HashMap<>(map);
        next.put(key, value);
        return next;
    }

    /** {@code map} without {@code key}. The input is not modified. */
    public static <K, T> Map<K, T> withoutKey(Map<K, T> map, K key) {
        Map<K, T> next = new HashMap<>(map);
        next.remove(key);
        return next;
    }

    private static <T extends Comparable<? super T>> Set<T> freezeSortedSet(Set<T> set) {
        SortedSet<T> copy = new TreeSet<>(set);
        return Collections.unmodifiableSortedSet(copy);
    }

    private static <K extends Comparable<? super K>, T> Map<K, T> freezeSortedMap(Map<K, T> map) {
        SortedMap<K, T> copy = new TreeMap<>(map);
        return Collections.unmodifiableSortedMap(copy);
    }
}
