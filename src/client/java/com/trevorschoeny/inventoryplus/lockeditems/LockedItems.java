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
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockgroups.LockGroup;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;

import net.fabricmc.loader.api.FabricLoader;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Locks that belong to the item rather than the slot (`plans/lock-groups.md`):
 * Item locks cover every item of a type, Exact locks one item as it is. Each
 * lock names its lock group, and an item carries at most one lock of each
 * kind (`lock-groups.md`, "One group per kind per target").
 *
 * <h3>What "exact" ignores, and why that is the whole feature</h3>
 *
 * <p>Exact match compares the full component set <b>except durability
 * damage</b>, and never stack count. A lock that included damage would
 * release the moment the player used the item, which is precisely the item
 * they cared most about protecting. {@link #normalize} is where that
 * exclusion lives, applied to both sides of every comparison.
 *
 * <h3>Storage: a {@link WorldStore} of the encoded form</h3>
 *
 * <p>Per world or server, like every per-world lock (`plans/reach.md`, "The
 * storage standard"). The store holds what the file holds: item ids as text
 * and exact stacks as their encoded JSON. That form needs no registries, so it
 * loads at client init, and it has value equality, so the store's
 * notify-on-change works. Matching needs real stacks, which need the level's
 * registries; {@link #view} decodes the current world's value on first use and
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
 * Loading one is the 1.5.x migration (`lock-groups.md`, "Migration"): exact
 * locks become Exact item locks, and item locks move into a custom Item group
 * named "Locked items", made only if any exist, whose reach reproduces 1.5.0.
 */
public final class LockedItems {

    private LockedItems() {}

    private static final int CURRENT_VERSION = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One exact lock: its group and its normalized stack, encoded. */
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

    // ── Loading, and the 1.5.x migration ────────────────────────────────

    /** Reads the file. Registry-free; the decode waits for a world ({@link #view}). */
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
                    "[locked-items] failed to parse {} — starting with an empty list", path, e);
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
     * The "Locked items" group for 1.5.x item locks (`lock-groups.md`,
     * "Migration"). 1.5.0 blocked sort, Move Matching and the cyclers, and
     * restock and Auto Tool Switch only if the player had turned off "use
     * locked items" for them; nothing else. Its kind's default (Exact item's
     * table) differs from that, so the differences are written as entries,
     * once. Restock is one operation here but was three settings then; it is
     * blocked if any of the three said so, since protection wins.
     */
    private static LockGroup makeLockedItemsGroup() {
        LockGroup group = Reach.createGroup(LockKind.ITEM, "Locked items", "gray");
        Set<String> blocked = new HashSet<>(Set.of(
                "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
                "inventoryplus:column_cycle", "inventoryplus:hotbar_cycle"));
        // The settings themselves are gone; the file's last values are kept for this.
        IPConfig.LegacyUsesLockedItems legacy = IPConfig.legacyUsesLockedItems();
        if (!(legacy.restockArmor() && legacy.restockTool() && legacy.restockItem())) {
            blocked.add("inventoryplus:restock_take");
        }
        if (!legacy.autoToolSwitch()) blocked.add("inventoryplus:auto_tool_switch");
        Set<String> ops = new HashSet<>(blocked);
        ops.addAll(Reach.EXACT_ITEM_DENIES);
        for (String op : ops) Reach.setLockDenies(group.id(), op, blocked.contains(op));
        return group;
    }

    // ── The decoded view of the current world ───────────────────────────

    /** The current world's locks, decoded; rebuilt when the stored value changes. */
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

    /** An exact entry's stack, normalized, or {@code null} if it no longer decodes. */
    private static @Nullable ItemStack decode(RegistryOps<JsonElement> ops, JsonElement encoded) {
        return ItemStack.CODEC.parse(ops, encoded)
                .resultOrPartial(err -> InventoryPlusClient.LOGGER.debug(
                        "[locked-items] undecodable exact entry, ignoring: {}", err))
                .map(LockedItems::normalize).orElse(null);
    }

    // ── Matching ────────────────────────────────────────────────────────

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
        ItemStack probe = normalize(stack);
        for (var e : v.exact()) if (ItemStack.isSameItemSameComponents(probe, e.getKey())) return e.getValue();
        return null;
    }

    /** True when this stack carries any item lock. */
    public static boolean isLocked(ItemStack stack) {
        return itemGroup(stack) != null || exactGroup(stack) != null;
    }

    /** True when this stack is locked exactly; chooses the solid mark over the hollow one. */
    public static boolean isExactLocked(ItemStack stack) {
        return exactGroup(stack) != null;
    }

    /**
     * A stack reduced to what an exact lock compares: one item, no
     * durability damage. Applied to stored entries and candidates alike.
     */
    private static ItemStack normalize(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(DataComponents.DAMAGE);
        return copy;
    }

    // ── Applying locks ──────────────────────────────────────────────────

    /**
     * {@code L} with an Item or Exact group selected (`lock-groups.md`,
     * "Applying locks"): the same group already on the stack comes off; a
     * different group of that kind is replaced; none is added. Other kinds
     * are untouched. Returns true when something changed.
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
            ItemStack probe = normalize(stack);
            String current = exactGroup(stack);
            // Every entry but this item's stays; this item is re-added under
            // the new group, unless the press takes the same group off.
            List<ExactLock> exact = new ArrayList<>();
            for (ExactLock e : before.exact()) {
                ItemStack decoded = decode(ops, e.stack());
                if (decoded == null || !ItemStack.isSameItemSameComponents(probe, decoded)) exact.add(e);
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

    // ── Every world at once (a group's delete, the Lock groups Reset) ─────

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

    // ── Saving ──────────────────────────────────────────────────────────

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
                    "[locked-items] failed to write {} — the change won't survive a restart", path, e);
        }
    }
}
