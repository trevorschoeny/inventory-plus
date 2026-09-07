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
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Protection that belongs to the item rather than the slot
 * (`plans/locked-items.md`). A protected item is skipped by every piece
 * of automation the mod runs, wherever it happens to be sitting.
 *
 * <h3>The kinds are independent, not alternatives</h3>
 *
 * <p>An item can carry any combination of locks at once: locked by id,
 * locked exactly, and slot-locked over in {@link
 * com.trevorschoeny.inventoryplus.lockedslots.LockedSlots}. Each is its
 * own dimension and {@code L} only ever touches the one the lock button's
 * stop names.
 *
 * <p>This was not the first design. Until 2026-09-06 a press removed
 * every lock on the item at once, so switching to Exact and locking an
 * already-id-locked pickaxe read as "already locked" and cleared it
 * instead. The old rule justified itself as never "revealing a second
 * lock underneath", which is exactly what a player with two locks needs
 * to see (Trev).
 *
 * <h3>Two kinds, and why they are not one</h3>
 *
 * <ul>
 *   <li><b>By id</b> ({@link LockKind#ITEM}) protects an item type.
 *       Locking one diamond pickaxe protects every diamond pickaxe.
 *       Coarse deliberately: it is a sentence a player can hold, and it
 *       fails by over-protecting.</li>
 *   <li><b>Exact</b> ({@link LockKind#EXACT}) protects one item as it
 *       is, components and all, for when a stack's identity matters.</li>
 * </ul>
 *
 * <h3>What "exact" ignores, and why that is the whole feature</h3>
 *
 * <p>Exact match compares the full component set <b>except durability
 * damage</b>, and never stack count. Damage has to go: a lock that
 * included it would release the moment the player used the item, which
 * is precisely the item they cared most about protecting. Everything
 * else that distinguishes one stack from another (enchantments, custom
 * name, potion contents, dye, trim, stored contents) still counts, so
 * "exact" means what a player expects: this item, however worn it gets.
 *
 * <p>{@link #normalize} is where that exclusion lives, and it is applied
 * to both sides of every comparison, so a stored entry and a candidate
 * are always compared on equal terms.
 *
 * <h3>Locked Items does not constrain the player</h3>
 *
 * <p>Unlike {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlots},
 * which also blocks manual shift-clicks and auto-pickup destinations,
 * this class is consulted <b>only</b> by the mod's own automation. The
 * player can always pick a locked item up, drop it, or shift-click it by
 * hand. Wiring {@link #isLocked} into a manual-interaction path would be
 * a bug, not an extension.
 *
 * <h3>Persistence</h3>
 *
 * <p>{@code config/inventoryplus/locked-items.json}, per world or server
 * (Trev 2026-09-06), so a hardcore run and a creative build keep separate
 * lists:
 *
 * <pre>{@code
 * { "version": 1,
 *   "perWorld": {
 *     "singleplayer:World": {
 *       "byId": ["minecraft:diamond_pickaxe"],
 *       "exact": [ { "id": "minecraft:diamond_pickaxe", "components": {...} } ] } } }
 * }</pre>
 *
 * <p>The file is parsed at client init but <b>decoded lazily</b>, on the
 * first query inside a world. Exact entries are {@link ItemStack}s, and
 * decoding one needs the world's registries, which do not exist yet when
 * the client starts. {@link #ensureDecoded} therefore holds the raw JSON
 * until a world is loaded and then decodes just that world's section.
 */
public final class LockedItems {

    private LockedItems() {}

    private static final int CURRENT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Whole file as parsed, including worlds we are not in. Decoding waits for registries. */
    private static JsonObject raw = new JsonObject();

    /** The world whose section is currently decoded into the two sets below, or null. */
    private static @Nullable String decodedWorld;

    /** Item types locked by id. Held as {@link Item} so the hot path is a reference hash. */
    private static final Set<Item> BY_ID = new HashSet<>();

    /** Normalized stacks locked exactly. Small by nature: one entry per item a player marked. */
    private static final List<ItemStack> EXACT = new ArrayList<>();

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("inventoryplus")
                .resolve("locked-items.json");
    }

    /** Reads the file into memory. Cheap, registry-free; the decode happens later. */
    public static void load() {
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            raw = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int worlds = raw.has("perWorld") ? raw.getAsJsonObject("perWorld").size() : 0;
            InventoryPlusClient.LOGGER.info(
                    "[locked-items] read {} world section(s) from {}; decoding on world load",
                    worlds, path.getFileName());
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-items] failed to parse {} — starting with an empty list", path, e);
            raw = new JsonObject();
        }
    }

    // ── Matching ────────────────────────────────────────────────────────

    /**
     * True when this stack carries any item lock. The predicate for the
     * automations that honour locks unconditionally: Sorting sorts around
     * it, Move Matching will not take it in either direction, and the
     * cyclers rotate past it.
     *
     * <p>Auto Tool Switch and Auto-Restock call {@link #blocks} instead,
     * since the player can let those two use a locked item anyway.
     */
    public static boolean isLocked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!ensureDecoded()) return false;
        return BY_ID.contains(stack.getItem()) || matchesExact(stack);
    }

    /**
     * True when this stack is locked <i>exactly</i>, which is only used to
     * choose between the hollow and solid marks. An item can satisfy both
     * kinds at once (a type locked by id, one of whose stacks was also
     * locked exactly); the solid mark wins there, being the more specific
     * statement of the two.
     */
    public static boolean isExactLocked(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!ensureDecoded()) return false;
        return matchesExact(stack);
    }

    private static boolean matchesExact(ItemStack stack) {
        if (EXACT.isEmpty()) return false;
        ItemStack probe = normalize(stack);
        for (ItemStack entry : EXACT) {
            if (ItemStack.isSameItemSameComponents(probe, entry)) return true;
        }
        return false;
    }

    /**
     * A stack reduced to what an exact lock compares: one item, no
     * durability damage. Applied to stored entries and to candidates
     * alike, so "the pickaxe I locked" keeps matching itself as it wears
     * down. Count goes too, since a lock is about identity, not quantity.
     */
    private static ItemStack normalize(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(DataComponents.DAMAGE);
        return copy;
    }

    // ── Locking and unlocking ───────────────────────────────────────────

    /**
     * Adds or removes the {@code kind} lock on {@code stack}, leaving
     * every other kind exactly as it was. Returns true when something
     * changed, so the caller can decide whether the press did anything.
     *
     * <p>Only this one dimension is consulted. An item that is locked by
     * id and then locked exactly carries both, and unlocking one leaves
     * the other standing. That is the point: the mark drops from solid to
     * hollow rather than disappearing, which is how the player sees a
     * lock still remains.
     */
    public static boolean toggle(ItemStack stack, LockKind kind) {
        if (stack == null || stack.isEmpty()) return false;
        if (!ensureDecoded()) return false;

        boolean locked;
        switch (kind) {
            case ITEM -> {
                locked = !BY_ID.remove(stack.getItem());
                if (locked) BY_ID.add(stack.getItem());
            }
            case EXACT -> {
                ItemStack probe = normalize(stack);
                locked = !EXACT.removeIf(entry -> ItemStack.isSameItemSameComponents(probe, entry));
                if (locked) EXACT.add(probe);
            }
            // The slot stop never reaches here; the keybind routes it to LockedSlots.
            default -> { return false; }
        }
        save();
        InventoryPlusClient.LOGGER.debug("[locked-items] {} {} ({})",
                locked ? "locked" : "unlocked", stack.getItem(), kind);
        return true;
    }

    /**
     * True when {@code user} must leave this stack alone: it carries an
     * item lock and that feature is set to honour locks. The cheap
     * boolean is read first so a player who left the defaults alone never
     * pays for the set lookup.
     *
     * @see LockedItemUser for why these two features get a say and the
     *      others do not
     */
    public static boolean blocks(LockedItemUser user, ItemStack stack) {
        return !user.usesLockedItems() && isLocked(stack);
    }

    // ── Per-world decode / encode ───────────────────────────────────────

    /**
     * Makes {@link #BY_ID} and {@link #EXACT} describe the world the
     * player is in, decoding that world's section on first use. Returns
     * false when there is nothing to answer with yet: outside a world, or
     * before the level's registries exist.
     */
    private static boolean ensureDecoded() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        String world = WorldIdentity.current(mc);
        if (world == null) return false;
        if (world.equals(decodedWorld)) return true;

        BY_ID.clear();
        EXACT.clear();
        decodedWorld = world;

        JsonObject section = sectionFor(world);
        if (section == null) {
            InventoryPlusClient.LOGGER.info("[locked-items] {}: no entries", world);
            return true;
        }
        if (section.has("byId")) {
            for (JsonElement e : section.getAsJsonArray("byId")) {
                Identifier id = Identifier.tryParse(e.getAsString());
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    // An entry for a mod that is no longer installed. Inert by
                    // design (locked-items.md: orphans cost a line in a file).
                    InventoryPlusClient.LOGGER.debug("[locked-items] unknown item {}, ignoring", e);
                    continue;
                }
                BY_ID.add(item);
            }
        }
        if (section.has("exact")) {
            RegistryOps<JsonElement> ops = mc.level.registryAccess()
                    .createSerializationContext(JsonOps.INSTANCE);
            for (JsonElement e : section.getAsJsonArray("exact")) {
                ItemStack.CODEC.parse(ops, e)
                        .resultOrPartial(err -> InventoryPlusClient.LOGGER.debug(
                                "[locked-items] undecodable exact entry, ignoring: {}", err))
                        .ifPresent(s -> EXACT.add(normalize(s)));
            }
        }
        InventoryPlusClient.LOGGER.info(
                "[locked-items] {}: {} by id, {} exact", world, BY_ID.size(), EXACT.size());
        return true;
    }

    private static @Nullable JsonObject sectionFor(String world) {
        if (!raw.has("perWorld")) return null;
        JsonObject perWorld = raw.getAsJsonObject("perWorld");
        return perWorld.has(world) ? perWorld.getAsJsonObject(world) : null;
    }

    /**
     * Writes the current world's section back and saves the file. Other
     * worlds' sections ride along untouched inside {@link #raw}, which is
     * why they are never decoded: nothing here needs to understand them.
     */
    private static void save() {
        if (decodedWorld == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        JsonArray byId = new JsonArray();
        for (Item item : BY_ID) byId.add(BuiltInRegistries.ITEM.getKey(item).toString());

        RegistryOps<JsonElement> ops = mc.level.registryAccess()
                .createSerializationContext(JsonOps.INSTANCE);
        JsonArray exact = new JsonArray();
        for (ItemStack entry : EXACT) {
            ItemStack.CODEC.encodeStart(ops, entry)
                    .resultOrPartial(err -> InventoryPlusClient.LOGGER.error(
                            "[locked-items] could not encode an exact entry: {}", err))
                    .ifPresent(exact::add);
        }

        JsonObject section = new JsonObject();
        section.add("byId", byId);
        section.add("exact", exact);

        if (!raw.has("perWorld")) raw.add("perWorld", new JsonObject());
        raw.getAsJsonObject("perWorld").add(decodedWorld, section);
        raw.addProperty("version", CURRENT_VERSION);

        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(raw));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error(
                    "[locked-items] failed to write {} — the change won't survive a restart", path, e);
        }
    }
}
