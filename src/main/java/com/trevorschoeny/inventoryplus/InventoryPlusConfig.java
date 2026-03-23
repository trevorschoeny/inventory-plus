package com.trevorschoeny.inventoryplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Standalone config for Inventory Plus.
 * Stored as {@code config/inventory-plus.json}.
 */
public class InventoryPlusConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("inventory-plus.json");

    private static InventoryPlusConfig INSTANCE = new InventoryPlusConfig();

    // ── Equipment ─────────────────────────────────────────────────────────────

    /** Whether the elytra equipment slot provides passive flight */
    public boolean enableElytraSlot = true;

    /** Whether the totem equipment slot provides passive death protection */
    public boolean enableTotemSlot = true;

    // ── Pockets ───────────────────────────────────────────────────────────────

    /** Master toggle — hides pocket panels, buttons, HUD, and disables keybinds */
    public boolean enablePockets = true;

    /** How many extra slots per hotbar position (1–3). Slots beyond this
     *  count are hidden from the panel and excluded from cycling/HUD. */
    public int pocketSlotCount = 3;

    /** Whether to render the A-shape pocket preview above the hotbar */
    public boolean showPocketHud = true;

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
