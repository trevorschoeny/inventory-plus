package com.trevorschoeny.inventoryplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.trevorschoeny.menukit.input.MKKeybind;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone config for Inventory Plus.
 * Stored as {@code config/inventory-plus.json}.
 */
public class InventoryPlusConfig {

    // ── GSON TypeAdapter for MKKeybind ───────────────────────────────────────
    // Serializes as the string format from MKKeybind.serialize() (e.g.,
    // "key.keyboard.k:6") and deserializes back via MKKeybind.deserialize().
    // This lets GSON handle MKKeybind fields transparently in the config file.
    private static final TypeAdapter<MKKeybind> KEYBIND_ADAPTER = new TypeAdapter<>() {
        @Override
        public void write(JsonWriter out, MKKeybind value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.serialize());
            }
        }

        @Override
        public MKKeybind read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return MKKeybind.UNBOUND;
            }
            return MKKeybind.deserialize(in.nextString());
        }
    };

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(MKKeybind.class, KEYBIND_ADAPTER)
            .create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("inventory-plus.json");

    private static InventoryPlusConfig INSTANCE = new InventoryPlusConfig();

    // ── Equipment ─────────────────────────────────────────────────────────────

    /** Whether the elytra equipment slot provides passive flight */
    public boolean enableElytraSlot = true;

    /** Whether the totem equipment slot provides passive death protection */
    public boolean enableTotemSlot = true;

    /** When true, mending XP applies to ANY mending-enchanted item in the
     *  player's storage (unheld hotbar, main inventory, pockets, etc.), not
     *  just the hand/armor/equipment slots. Vanilla priority is still
     *  respected — hand, offhand, and armor get XP before anything else. */
    public boolean mendingInventoryWide = false;

    // ── Sorting ──────────────────────────────────────────────────────────────

    /** Master toggle — when off, the sort keybind does nothing and the sort
     *  button is hidden. Sorting is fully disabled. */
    public boolean enableSorting = true;

    /** Which sorting algorithm to use (MOST_ITEMS or BY_ID). */
    public SortMethod sortMethod = SortMethod.MOST_ITEMS;

    /** Whether to show the sort button in inventory screens. Sorting still
     *  works via keybind regardless of this setting. */
    public boolean showSortButton = true;

    /** Whether the move-matching feature includes hotbar items (slots 0-8)
     *  as sources. When false, only main inventory (slots 9-35) is used. */
    public boolean includeHotbarInMoveMatching = true;

    // ── Pockets ───────────────────────────────────────────────────────────────

    /** Master toggle — hides pocket panels, buttons, HUD, and disables keybinds */
    public boolean enablePockets = true;

    /** How many extra slots per hotbar position (1–3). Slots beyond this
     *  count are hidden from the panel and excluded from cycling/HUD. */
    public int pocketSlotCount = 3;

    /** Whether to render the A-shape pocket preview above the hotbar */
    public boolean showPocketHud = true;

    // ── Container Peek ────────────────────────────────────────────────────────

    /** Whether peeking into shulker boxes is enabled */
    public boolean enablePeekShulker = true;

    /** Whether peeking into bundles is enabled */
    public boolean enablePeekBundle = true;

    /** Whether peeking into ender chests is enabled */
    public boolean enablePeekEnderChest = true;

    // ── Keybinds ──────────────────────────────────────────────────────────────
    // Stored as MKKeybind records — supports modifier keys (Ctrl+K, Shift+F5).
    // Serialized to config via the KEYBIND_ADAPTER above. At runtime, these are
    // synced to KeyMapping instances via MKKeybindExt.updateFromKeybind().

    /** Sort Region keybind — press while hovering a slot to sort that region */
    public MKKeybind sortKeybind = MKKeybind.UNBOUND;

    /** Move Matching keybind — press while hovering a slot to move all matching items */
    public MKKeybind moveMatchingKeybind = MKKeybind.UNBOUND;

    /** Lock Slot keybind — hold and click a slot to lock/unlock it */
    public MKKeybind lockSlotKeybind = MKKeybind.UNBOUND;

    /** Container Peek keybind — press while hovering a bundle, shulker box,
     *  or ender chest in any container screen to open its peek panel.
     *  Default: Left Alt. Press again on the same item to close, or on a
     *  different peekable to switch. */
    public MKKeybind peekKeybind = MKKeybind.ofKeyAndModifiers(
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, 0, 0);

    /** Pocket cycle right keybind — default Right Arrow */
    public MKKeybind pocketCycleRightKeybind = MKKeybind.ofKeyAndModifiers(
            org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);

    /** Pocket cycle left keybind — default Left Arrow */
    public MKKeybind pocketCycleLeftKeybind = MKKeybind.ofKeyAndModifiers(
            org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, 0, 0);

    // ── API ───────────────────────────────────────────────────────────────────

    public static InventoryPlusConfig get() { return INSTANCE; }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                INSTANCE = GSON.fromJson(json, InventoryPlusConfig.class);
                if (INSTANCE == null) INSTANCE = new InventoryPlusConfig();
            } catch (IOException e) {
                InventoryPlus.LOGGER.error("[InventoryPlus] Failed to load config", e);
                INSTANCE = new InventoryPlusConfig();
            }
        }
    }

    public static void save() {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            InventoryPlus.LOGGER.error("[InventoryPlus] Failed to save config", e);
        }
    }
}
