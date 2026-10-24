package com.trevorschoeny.inventoryplus.lockeditems;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.trevorschoeny.inventoryplus.lockgroups.LockGroup;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;

import net.fabricmc.loader.api.FabricLoader;

import com.trevlar.menukit.api.element.ItemMatch;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Locks that belong to the item rather than the slot: Item locks cover every
 * item of a type, Exact locks one item as it is. Each lock names its lock
 * group, and an item carries at most one lock of each kind.
 *
 * <h3>What "exact" ignores</h3>
 *
 * <p>An Exact lock's comparison ({@link #exactRule}) leaves out durability
 * damage and stack count (MenuKit 7.3.0's {@code ItemMatch.ignoring} /
 * {@code ignoringCount}): a lock that released the moment the player used
 * the item would be useless, since that is precisely the item they cared
 * most about protecting. Everything else about the stack, enchantments,
 * custom data, a renamed label, has to match exactly.
 *
 * <h3>Storage: a {@link WorldStore} of the encoded form</h3>
 *
 * <p>Per world or server, like every per-world lock. The store holds what the file holds: item ids as text
 * and exact stacks as their encoded JSON. That form needs no registries, so it
 * loads at client init, and it has value equality, so the store's
 * notify-on-change works. Matching needs real stacks, which need the level's
 * registries. {@link #view} decodes the current world's value on first use and
 * keeps it until the stored value changes. Ids for items that no longer exist
 * stay in the file and are never matched.
 *
 * <p>{@code config/inventoryplus/locked-items.json}, version 2:
 *
 * <pre>{@code
 * { "version": 2,
 *   "perWorld": {
 *     "singleplayer:World": {
 *       "byId":  { "minecraft:diamond_pickaxe": "g1" },
 *       "exact": [ { "group": "exact_item", "stack": { "id": "...", "components": {...} } } ] } } }
 * }</pre>
 *
 * <p>Version 1 held a bare {@code byId} array and a bare {@code exact} array.
 * Loading one is the 1.5.x migration: exact
 * locks become Exact item locks, and item locks move into a custom Item group
 * named "Locked items", made only if any exist, whose reach reproduces 1.5.0.
 */
public final class LockedItems {

    private LockedItems() {}

    private static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One exact lock: its group and its stack, encoded. */
    public record ExactLock(String group, JsonElement stack) {}

    /** One world's item locks, in the form the file holds. */
    public record ItemLocks(Map<String, String> byId, List<ExactLock> exact) {
        static final ItemLocks EMPTY = new ItemLocks(Collections.emptySortedMap(), List.of());

        static ItemLocks freeze(ItemLocks v) {
            List<ExactLock> exact = new ArrayList<>();
            for (ExactLock e : v.exact()) exact.add(new ExactLock(e.group(), e.stack().deepCopy()));
            return new ItemLocks(Collections.unmodifiableSortedMap(new TreeMap<>(v.byId())), List.copyOf(exact));
        }
    }

    private static final WorldStore<ItemLocks> STORE =
            WorldStore.of(ItemLocks.EMPTY, ItemLocks::freeze, LockedItems::save);

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("locked-items.json");
    }

    // Loading, and the 1.5.x migration

    /** Reads the file. Registry-free: the decode waits for a world ({@link #view}). */
    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld") ? root.getAsJsonObject("perWorld") : new JsonObject();
            boolean v1 = !root.has("version") || root.get("version").getAsInt() < 2;
            // v1 item locks go to one group made for them, only if there are any.
            String migratedItemGroup = null;
            if (v1 && anyV1ItemLocks(perWorld)) migratedItemGroup = makeLockedItemsGroup().id();

            for (var world : perWorld.entrySet()) {
                JsonObject section = world.getValue().getAsJsonObject();
                Map<String, String> byId = new HashMap<>();
                List<ExactLock> exact = new ArrayList<>();
                if (v1) {
                    if (section.has("byId")) for (JsonElement e : section.getAsJsonArray("byId")) byId.put(e.getAsString(), migratedItemGroup);
                    if (section.has("exact")) for (JsonElement e : section.getAsJsonArray("exact")) exact.add(new ExactLock(Reach.EXACT_ITEM, e));
                } else {
                    if (section.has("byId")) for (var e : section.getAsJsonObject("byId").entrySet()) byId.put(e.getKey(), e.getValue().getAsString());
                    if (section.has("exact")) {
                        for (JsonElement e : section.getAsJsonArray("exact")) {
                            JsonObject o = e.getAsJsonObject();
                            exact.add(new ExactLock(o.get("group").getAsString(), o.get("stack")));
                        }
                    }
                }
                STORE.load(world.getKey(), new ItemLocks(byId, exact));
            }
            InventoryPlusClient.LOGGER.info("[locked-items] read {} world section(s) from {}; decoding on world load",
                    perWorld.size(), path.getFileName());
            if (v1) {
                save();
                InventoryPlusClient.LOGGER.info("[locked-items] upgraded {} from version 1{}", path.getFileName(),
                        migratedItemGroup == null ? "" : "; item locks moved to group " + migratedItemGroup);
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException | NullPointerException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-items] failed to parse {}, starting with an empty list", path, e);
        }
    }

    private static boolean anyV1ItemLocks(JsonObject perWorld) {
        for (var world : perWorld.entrySet()) {
            JsonObject section = world.getValue().getAsJsonObject();
            if (section.has("byId") && !section.getAsJsonArray("byId").isEmpty()) return true;
        }
        return false;
    }

    /**
     * The "Locked items" group for 1.5.x item locks. It stops what its kind's
     * default stops; what 1.5.0 blocked is not carried over, since every
     * player moved to the current defaults.
     */
    private static LockGroup makeLockedItemsGroup() {
        return Reach.createGroup(LockKind.ITEM, "Locked items", "gray");
    }

    // The decoded view of the current world

    /** The current world's locks, decoded. Rebuilt when the stored value changes. */
    private record View(ItemLocks source, Map<Item, String> byId, List<Map.Entry<ItemStack, String>> exact) {}

    private static volatile @Nullable View view;

    /** The decoded locks for the world the player is in, or {@code null} outside one. */
    private static @Nullable View view() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        ItemLocks locks = STORE.get();
        View v = view;
        if (v != null && v.source() == locks) return v;

        Map<Item, String> byId = new HashMap<>();
        locks.byId().forEach((id, group) -> {
            Identifier rl = Identifier.tryParse(id);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item != null) byId.put(item, group);   // an unknown id is kept in the file, never matched
        });
        RegistryOps<JsonElement> ops = ops(mc);
        List<Map.Entry<ItemStack, String>> exact = new ArrayList<>();
        for (ExactLock e : locks.exact()) {
            ItemStack decoded = decode(ops, e.stack());
            if (decoded != null) exact.add(Map.entry(decoded, e.group()));
        }
        View built = new View(locks, byId, exact);
        view = built;
        return built;
    }

    private static RegistryOps<JsonElement> ops(Minecraft mc) {
        return mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    /** An exact entry's stack, or {@code null} if it no longer decodes. */
    private static @Nullable ItemStack decode(RegistryOps<JsonElement> ops, JsonElement encoded) {
        return ItemStack.CODEC.parse(ops, encoded)
                .resultOrPartial(err -> InventoryPlusClient.LOGGER.debug(
                        "[locked-items] undecodable exact entry, ignoring: {}", err))
                .orElse(null);
    }

    // Matching

    /** The group of the Item lock on {@code stack}'s type, or {@code null}. */
    public static @Nullable String itemGroup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        View v = view();
        return v == null ? null : v.byId().get(stack.getItem());
    }

    /** The group of the Exact lock on {@code stack}, or {@code null}. */
    public static @Nullable String exactGroup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        View v = view();
        if (v == null || v.exact().isEmpty()) return null;
        for (var e : v.exact()) if (exactRule(e.getKey()).matches(stack)) return e.getValue();
        return null;
    }

    /** True when this stack carries any item lock. */
    public static boolean isLocked(ItemStack stack) {
        return itemGroup(stack) != null || exactGroup(stack) != null;
    }

    /** True when this stack is locked exactly. Chooses the solid mark over the hollow one. */
    public static boolean isExactLocked(ItemStack stack) {
        return exactGroup(stack) != null;
    }

    /**
     * {@code stored} as the rule side of an Exact lock's comparison: the same
     * item and components, ignoring durability damage, with no count
     * (MenuKit's {@code ItemMatch}; see the class doc, "What exact ignores").
     */
    private static ItemMatch exactRule(ItemStack stored) {
        return new ItemMatch(stored, ItemMatch.Match.EXACT).ignoring(DataComponents.DAMAGE).ignoringCount();
    }

    // Applying locks

    /**
     * {@code L} with an Item or Exact group selected: the same group already
     * on the stack comes off, a different group of that kind is replaced, and
     * with none the group is added. Other kinds are untouched. Returns true
     * when something changed.
     */
    public static boolean apply(ItemStack stack, LockGroup group) {
        if (stack == null || stack.isEmpty() || !group.kind().locksItems()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;

        ItemLocks before = STORE.get();
        if (group.kind() == LockKind.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Map<String, String> byId = group.id().equals(before.byId().get(id))
                    ? WorldStore.withoutKey(before.byId(), id)
                    : WorldStore.withEntry(before.byId(), id, group.id());
            STORE.set(new ItemLocks(byId, before.exact()));
        } else {
            RegistryOps<JsonElement> ops = ops(mc);
            ItemStack probe = stack.copy();
            String current = exactGroup(stack);
            // Every entry but this item's stays; this item is re-added under
            // the new group, unless the press takes the same group off.
            List<ExactLock> exact = new ArrayList<>();
            for (ExactLock e : before.exact()) {
                ItemStack decoded = decode(ops, e.stack());
                if (decoded == null || !exactRule(decoded).matches(probe)) exact.add(e);
            }
            if (!group.id().equals(current)) {
                JsonElement encoded = ItemStack.CODEC.encodeStart(ops, probe).result().orElse(null);
                if (encoded == null) {
                    InventoryPlusClient.LOGGER.error("[locked-items] could not encode {}", probe);
                    return false;
                }
                exact.add(new ExactLock(group.id(), encoded));
            }
            STORE.set(new ItemLocks(before.byId(), exact));
        }
        boolean changed = !STORE.get().equals(before);
        InventoryPlusClient.LOGGER.debug("[locked-items] {} on {}: changed {}", group.id(), stack.getItem(), changed);
        return changed;
    }

    // A group's items, as the settings menu's item picker reads and writes them
    // (MenuKit's ItemPicker). The current world only: locks are per world, and
    // stacks need its registries. Outside a world there is nothing to show.

    /** Whether a world is open, so a group's items can be read and set. */
    public static boolean inWorld() {
        return view() != null;
    }

    /**
     * The items an Item group locks in this world, one stack each. In item id
     * order, as the file keeps them: a newly picked item takes its place by
     * id, not at the end.
     */
    public static List<ItemStack> itemsOf(String groupId) {
        View v = view();
        if (v == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        STORE.get().byId().forEach((id, group) -> {
            if (!groupId.equals(group)) return;
            Identifier rl = Identifier.tryParse(id);
            Item item = rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
            if (item != null) out.add(new ItemStack(item));
        });
        return out;
    }

    /**
     * Makes {@code stacks}' item types the Item group's whole set in this world.
     * A type locked by another Item group moves here: a type carries one Item
     * lock, as with the lock key.
     */
    public static void setItems(String groupId, List<ItemStack> stacks) {
        if (view() == null) return;
        ItemLocks before = STORE.get();
        Map<String, String> byId = new HashMap<>();
        before.byId().forEach((id, g) -> { if (!groupId.equals(g)) byId.put(id, g); });
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) byId.put(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), groupId);
        }
        STORE.set(new ItemLocks(byId, before.exact()));
    }

    /**
     * The items an Exact item group locks in this world, as stored (matching
     * ignores durability damage and count regardless; see {@link #exactRule}).
     * In the order added.
     */
    public static List<ItemStack> exactOf(String groupId) {
        View v = view();
        if (v == null) return List.of();
        List<ItemStack> out = new ArrayList<>();
        for (var e : v.exact()) if (groupId.equals(e.getValue())) out.add(e.getKey().copy());
        return out;
    }

    /**
     * Makes {@code stacks} the Exact item group's whole set in this world. An
     * item locked exactly by another group moves here: an item carries one
     * Exact lock, as with the lock key.
     */
    public static void setExact(String groupId, List<ItemStack> stacks) {
        Minecraft mc = Minecraft.getInstance();
        if (view() == null) return;
        RegistryOps<JsonElement> ops = ops(mc);
        List<ItemStack> wanted = new ArrayList<>();
        for (ItemStack s : stacks) if (!s.isEmpty()) wanted.add(s.copy());
        ItemLocks before = STORE.get();
        List<ExactLock> exact = new ArrayList<>();
        for (ExactLock e : before.exact()) {
            if (groupId.equals(e.group())) continue;
            ItemStack decoded = decode(ops, e.stack());
            boolean taken = decoded != null && wanted.stream().anyMatch(w -> exactRule(decoded).matches(w));
            if (!taken) exact.add(e);
        }
        for (ItemStack w : wanted) {
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, w).result().orElse(null);
            if (encoded == null) {
                InventoryPlusClient.LOGGER.error("[locked-items] could not encode {}", w);
                continue;
            }
            exact.add(new ExactLock(groupId, encoded));
        }
        STORE.set(new ItemLocks(before.byId(), exact));
    }

    // Every world at once (a group's delete, the Lock groups Reset)

    /** As {@code LockedSlots.count}: item and exact locks of {@code groupId}, or all. */
    public static int count(@Nullable String groupId, Set<String> worlds) {
        int[] n = {0};
        STORE.forEachWorld((world, locks) -> {
            int before = n[0];
            for (String g : locks.byId().values()) if (groupId == null || groupId.equals(g)) n[0]++;
            for (ExactLock e : locks.exact()) if (groupId == null || groupId.equals(e.group())) n[0]++;
            if (n[0] > before) worlds.add(world);
        });
        return n[0];
    }

    /** As {@code LockedSlots.reassign}: moves group {@code from}'s locks to {@code to}, or removes them. */
    public static void reassign(@Nullable String from, @Nullable String to) {
        STORE.replaceAll((world, locks) -> {
            Map<String, String> byId = new HashMap<>();
            locks.byId().forEach((id, g) -> {
                if (from != null && !from.equals(g)) byId.put(id, g);
                else if (to != null) byId.put(id, to);
            });
            List<ExactLock> exact = new ArrayList<>();
            for (ExactLock e : locks.exact()) {
                if (from != null && !from.equals(e.group())) exact.add(e);
                else if (to != null) exact.add(new ExactLock(to, e.stack()));
            }
            return new ItemLocks(byId, exact);
        });
    }

    private static void save() {
        JsonObject perWorld = new JsonObject();
        STORE.forEachWorld((world, locks) -> {
            JsonObject byId = new JsonObject();
            locks.byId().forEach(byId::addProperty);
            JsonArray exact = new JsonArray();
            for (ExactLock e : locks.exact()) {
                JsonObject o = new JsonObject();
                o.addProperty("group", e.group());
                o.add("stack", e.stack());
                exact.add(o);
            }
            JsonObject section = new JsonObject();
            section.add("byId", byId);
            section.add("exact", exact);
            perWorld.add(world, section);
        });
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        root.add("perWorld", perWorld);
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-items] failed to write {}, the change won't survive a restart", path, e);
        }
    }
}
