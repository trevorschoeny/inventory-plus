package com.trevorschoeny.inventoryplus.hotbarsets;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.HOTBAR_CYCLE;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.api.WorldStore;
import com.trevorschoeny.inventoryplus.autorestock.AutoRestockSuppression;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCyclerRotator;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hotbar Sets: hold a key, scroll to another row of items, let go, and it's in the hotbar
 * (design: Inventory Suite drive, {@code mods/inventory-plus/features/hotbar-sets.md}).
 *
 * <p>This class is the model and the two actions. The list's entries, bottom up, after the
 * hotbar itself (entry 0, never one of the sets):
 * <ul>
 *   <li><b>The three inventory rows</b>, the bottom row first, as the inventory reads going
 *       up. Letting go on one <b>swaps</b> it with the hotbar.</li>
 *   <li><b>Creative only: the saved sets</b>, newest nearest the hotbar. Letting go on one
 *       <b>replaces</b> the hotbar with it. In creative the rows go above the saved sets by
 *       default; a setting moves them below or leaves them out.</li>
 * </ul>
 *
 * <p>Both actions go column by column and follow the slot rules: a column whose slot is
 * locked stays as it is, and one where an item would land in a reserved slot that doesn't
 * take it doesn't move at all. The swap gets both from the shared rotation engine
 * ({@link ColumnCyclerRotator#rotateSlots}, a ring of two); the load checks them itself.
 *
 * <p>Saved sets are per world, client-side, in {@code config/inventoryplus/hotbar-sets.json}.
 * Items keep their components, so they're encoded with the world's registries: a world's
 * sets are read from the file the first time they're needed in that world.
 */
public final class HotbarSets {

    private HotbarSets() {}

    public static final int COLUMNS = 9;
    /** Inventory rows, top to bottom: row r is container slots {@code 9 + 9r} to {@code 17 + 9r}. */
    public static final int ROWS = 3;

    // The list

    /** One entry above the hotbar: inventory row {@code index} (0 top), or saved set {@code index}. */
    public record Entry(boolean saved, int index) {

        /** What the entry shows: the row's items as they are now, or the saved set's. */
        public List<ItemStack> items(Player player) {
            if (saved) return savedSets().get(index);
            List<ItemStack> items = new ArrayList<>(COLUMNS);
            for (int c = 0; c < COLUMNS; c++) items.add(player.getInventory().getItem(firstSlotOf(index) + c));
            return items;
        }
    }

    /** Where the inventory rows go in creative's list. */
    public enum CreativeRows { TOP, BOTTOM, OFF }

    public static int firstSlotOf(int row) {
        return 9 + row * COLUMNS;
    }

    /** The list's entries above the hotbar, bottom up. */
    public static List<Entry> entries(Player player) {
        List<Entry> rows = new ArrayList<>(ROWS);
        for (int r = ROWS - 1; r >= 0; r--) rows.add(new Entry(false, r));
        if (!player.isCreative()) return rows;
        List<Entry> saved = new ArrayList<>();
        // The newest set (saved last, at the end of the list) sits nearest the hotbar.
        for (int s = savedSets().size() - 1; s >= 0; s--) saved.add(new Entry(true, s));
        List<Entry> out = new ArrayList<>(rows.size() + saved.size());
        switch (IPConfig.hotbarSetsCreativeRows()) {
            case TOP -> { out.addAll(saved); out.addAll(rows); }
            case BOTTOM -> { out.addAll(rows); out.addAll(saved); }
            case OFF -> out.addAll(saved);
        }
        return out;
    }

    /** Applies {@code entry}: swaps its row with the hotbar, or loads its saved set. */
    public static void apply(Entry entry) {
        if (entry.saved()) load(savedSets().get(entry.index()));
        else swap(entry.index());
    }

    /**
     * Swaps inventory row {@code row} with the hotbar, one ring of two per column through the
     * shared engine: by clicks on any server, by creative's own slot packet on an item tab.
     * The engine sits a column out when a slot is locked, or when a reserved slot would get an
     * item it doesn't take.
     */
    public static boolean swap(int row) {
        boolean any = false;
        for (int c = 0; c < COLUMNS; c++) {
            any |= ColumnCyclerRotator.rotateSlots(List.of(firstSlotOf(row) + c, c),
                    ColumnCyclerRotator.Direction.FORWARD, "hotbar-sets", HOTBAR_CYCLE, HOTBAR_CYCLE);
        }
        return any;
    }

    /**
     * Puts {@code set} in the hotbar, creative only: each changed slot set on the client and
     * sent with creative's slot packet, as the creative screen sets slots itself. A locked
     * hotbar slot keeps its item, and a reserved one takes the set's item only if it fits.
     */
    public static boolean load(List<ItemStack> set) {
        LocalPlayer player = Minecraft.getInstance().player;
        var gameMode = Minecraft.getInstance().gameMode;
        if (player == null || gameMode == null || !player.isCreative()) return false;
        AbstractContainerMenu menu = player.inventoryMenu;
        Inventory inv = player.getInventory();
        boolean any = false;
        for (int c = 0; c < COLUMNS; c++) {
            ItemStack now = c < set.size() ? set.get(c) : ItemStack.EMPTY;
            if (ItemStack.matches(now, inv.getItem(c))) continue;
            int menuIdx = PlayerMenuSlots.menuIndexOf(menu, player, c);
            if (menuIdx < 0) continue;
            Slot slot = menu.getSlot(menuIdx);
            if (!SlotOperations.allows(menu, slot, player, HOTBAR_CYCLE)) continue;
            if (!now.isEmpty() && !Reservations.get(SlotRef.of(menu, slot, player)).matches(now)) continue;
            AutoRestockSuppression.markExternalChange(c);
            inv.setItem(c, now.copy());
            gameMode.handleCreativeModeItemAdd(now.copy(), menuIdx);
            any = true;
        }
        return any;
    }

    // Saved sets

    /** The current world's saved sets, oldest first. Each set is nine stacks; never modify them. */
    public static List<List<ItemStack>> savedSets() {
        readWorld();
        return SETS.get();
    }

    /** Saves the hotbar as it is now as a new set, at the end of the list. */
    public static void saveHotbar() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        List<ItemStack> set = new ArrayList<>(COLUMNS);
        for (int c = 0; c < COLUMNS; c++) set.add(player.getInventory().getItem(c).copy());
        readWorld();
        SETS.modify(sets -> {
            List<List<ItemStack>> next = new ArrayList<>(sets);
            next.add(List.copyOf(set));
            return next;
        });
    }

    /** Moves set {@code from} to position {@code to}. */
    public static void move(int from, int to) {
        readWorld();
        SETS.modify(sets -> {
            if (from < 0 || from >= sets.size()) return sets;
            List<List<ItemStack>> next = new ArrayList<>(sets);
            next.add(Math.clamp(to, 0, next.size() - 1), next.remove(from));
            return next;
        });
    }

    public static void delete(int index) {
        readWorld();
        SETS.modify(sets -> {
            if (index < 0 || index >= sets.size()) return sets;
            List<List<ItemStack>> next = new ArrayList<>(sets);
            next.remove(index);
            return next;
        });
    }

    // Persistence

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<List<ItemStack>> NONE = List.of();
    private static final WorldStore<List<List<ItemStack>>> SETS = WorldStore.of(NONE, List::copyOf, HotbarSets::save);

    /**
     * The file as read, per world, so a world whose sets haven't been decoded this session
     * (it needs that world's registries) is written back untouched.
     */
    private static final Map<String, JsonElement> RAW = new HashMap<>();
    private static boolean loaded = false;

    private static Path filePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("inventoryplus").resolve("hotbar-sets.json");
    }

    private static void loadFile() {
        if (loaded) return;
        loaded = true;
        Path path = filePath();
        if (!Files.exists(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject perWorld = root.has("perWorld") ? root.getAsJsonObject("perWorld") : new JsonObject();
            perWorld.entrySet().forEach(e -> RAW.put(e.getKey(), e.getValue()));
        } catch (IOException | RuntimeException e) {
            InventoryPlusClient.LOGGER.error("[hotbar-sets] failed to read {}, starting empty", path, e);
        }
    }

    /** Decodes the current world's sets from the file, once per world per session. */
    private static void readWorld() {
        loadFile();
        Minecraft mc = Minecraft.getInstance();
        String world = WorldIdentity.current(mc);
        if (world == null || mc.level == null || SETS.has() || DECODED.contains(world)) return;
        DECODED.add(world);
        JsonElement raw = RAW.get(world);
        if (raw == null || !raw.isJsonArray()) return;
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
        List<List<ItemStack>> sets = new ArrayList<>();
        // A hand-edited file reads what it can: an entry that isn't a list of stacks is skipped,
        // and a stack this world can't read (a removed mod's item) comes back empty.
        for (JsonElement setJson : raw.getAsJsonArray()) {
            if (!setJson.isJsonArray()) continue;
            List<ItemStack> set = new ArrayList<>(COLUMNS);
            for (JsonElement stack : setJson.getAsJsonArray()) {
                set.add(ItemStack.OPTIONAL_CODEC.parse(ops, stack).result().orElse(ItemStack.EMPTY));
            }
            while (set.size() < COLUMNS) set.add(ItemStack.EMPTY);
            sets.add(List.copyOf(set.subList(0, COLUMNS)));
        }
        SETS.load(world, sets);
    }

    private static final Set<String> DECODED = new HashSet<>();

    private static void save() {
        if (forgetting) return;
        Minecraft mc = Minecraft.getInstance();
        String world = WorldIdentity.current(mc);
        if (world != null && mc.level != null) {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, mc.level.registryAccess());
            JsonArray sets = new JsonArray();
            for (List<ItemStack> set : SETS.get()) {
                JsonArray stacks = new JsonArray();
                for (ItemStack stack : set) stacks.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).result().orElse(new JsonObject()));
                sets.add(stacks);
            }
            if (sets.isEmpty()) RAW.remove(world);
            else RAW.put(world, sets);
        }
        Path path = filePath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject perWorld = new JsonObject();
            RAW.forEach(perWorld::add);
            root.add("perWorld", perWorld);
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            InventoryPlusClient.LOGGER.error("[hotbar-sets] failed to write {}, change won't survive a restart", path, e);
        }
    }

    /** For the checks: forget what was read, so the next read comes from the file. */
    public static void reloadForChecks() {
        forgetting = true;
        try {
            SETS.replaceAll((w, v) -> null);
        } finally {
            forgetting = false;
        }
        loaded = false;
        RAW.clear();
        DECODED.clear();
    }

    /** Set while the checks forget the sets in memory, which mustn't write the file. */
    private static boolean forgetting = false;

    static @Nullable Entry entryAt(Player player, int selected) {
        List<Entry> entries = entries(player);
        return selected >= 1 && selected <= entries.size() ? entries.get(selected - 1) : null;
    }
}
