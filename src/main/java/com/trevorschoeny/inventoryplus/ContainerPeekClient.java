package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.panel.MKPanel;
import com.trevorschoeny.menukit.region.MKRegionRegistry;
import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.menukit.MenuKitClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side logic for Container Peek — panel registration, S2C handler,
 * recipe book management, and dynamic slot count computation.
 *
 * <p>Registers three separate grid panels:
 * <ul>
 *   <li>{@code peek_grid_shulker} — 3×9 fixed grid (27 slots)</li>
 *   <li>{@code peek_grid_ender} — 3×9 fixed grid (27 slots)</li>
 *   <li>{@code peek_grid_bundle} — variable grid (up to 64 slots)</li>
 * </ul>
 *
 * <p>Each grid panel includes a vertical title label on the left.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class ContainerPeekClient {

    // ── Client-Side Peek State ───────────────────────────────────────────────

    /** Which inventory slot is currently being peeked (-1 = none). */
    private static int peekedSlot = -1;
    /** Source type of the current peek (see PeekS2CPayload constants). */
    private static int sourceType = 0;
    /** How many slots are active (visible) in the bundle peek. */
    private static int bundleActiveSlots = 0;
    /** Display name for the panel title. */
    private static Component peekTitle = Component.empty();
    /** Whether the recipe book was open when we started peeking. */
    private static boolean wasRecipeBookOpen = false;

    // ── Panel Names ──────────────────────────────────────────────────────────

    /** Grid panels — one per container type. */
    static final String GRID_SHULKER = "peek_grid_shulker";
    static final String GRID_ENDER   = "peek_grid_ender";
    static final String GRID_BUNDLE  = "peek_grid_bundle";
    /** All grid panel names for iteration. */
    private static final String[] ALL_GRIDS = { GRID_SHULKER, GRID_ENDER, GRID_BUNDLE };

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers all peek panels with MenuKit. Called from client init.
     *
     * <p>Three panels total: one grid per peek type, each with an
     * integrated vertical title label. All use LEFT_AUTO stacking
     * and are exclusive.
     */
    public static void registerPanel() {
        // ── Shulker grid: vertical title + 3×9 fixed (27 slots) ─────────────
        registerFixedGrid(GRID_SHULKER, ContainerPeek.SHULKER, ContainerPeek.FIXED_SLOTS);

        // ── Ender chest grid: vertical title + 3×9 fixed (27 slots) ─────────
        registerFixedGrid(GRID_ENDER, ContainerPeek.ENDER, ContainerPeek.FIXED_SLOTS);

        // ── Bundle grid: vertical title + variable slots (up to 64) ─────────
        // Uses disabledWhen to hide unused slots dynamically.
        // Sort/move buttons are injected automatically by the button attachment
        // system at build time (see InventoryPlusClient.registerSortAttachment).
        var bundleSlots = MKPanel.builder(GRID_BUNDLE)
                .showIn(MKContext.ALL)
                .posLeft()
                .hidden()
                .exclusive()
                .autoSize()
                .style(MKPanel.Style.RAISED)
                .shiftClickIn(true)
                .shiftClickOut(true)
                .row()
                    .text()
                        .content(ContainerPeekClient::getPeekTitle)
                        .vertical()
                        .done()
                    .column()
                        .slotGroup(ContainerPeek.BUNDLE, MKContainerType.SIMPLE)
                            .grid()
                                .cellSize(ContainerPeek.SLOT_SIZE)
                                .rows(ContainerPeek.BUNDLE_ROWS)
                                .fillRight();

        for (int i = 0; i < ContainerPeek.BUNDLE_MAX_SLOTS; i++) {
            final int slotIndex = i;
            bundleSlots = bundleSlots.slot()
                    .container(ContainerPeek.BUNDLE, i)
                    .disabledWhen(() -> slotIndex >= getEffectiveBundleSlots())
                    .done();
        }

        bundleSlots.done()    // close grid
                .done()       // close slotGroup
                .done()       // close column
                .build();     // close row → build panel
    }

    /**
     * Registers a fixed-size grid panel with a vertical title label on the left.
     * Row layout: vertical text | slotGroup (column → grid).
     * Sort/move buttons are injected automatically by the button attachment
     * system at build time (see InventoryPlusClient.registerSortAttachment).
     */
    private static void registerFixedGrid(String panelName, String containerName, int slotCount) {
        // Row layout: vertical text on the left, column (buttons + slotGroup) on the right.
        // The column wrapper ensures button attachments insert above the grid, not beside it.
        var grid = MKPanel.builder(panelName)
                .showIn(MKContext.ALL)
                .posLeft()
                .hidden()
                .exclusive()
                .autoSize()
                .style(MKPanel.Style.RAISED)
                .shiftClickIn(true)
                .shiftClickOut(true)
                .row()
                    .text()
                        .content(ContainerPeekClient::getPeekTitle)
                        .vertical()
                        .done()
                    .column()
                        .slotGroup(containerName, MKContainerType.SIMPLE)
                            .grid()
                                .cellSize(ContainerPeek.SLOT_SIZE)
                                .rows(ContainerPeek.FIXED_COLS)
                                .fillRight();

        for (int i = 0; i < slotCount; i++) {
            grid = grid.slot()
                    .container(containerName, i)
                    .done();
        }

        grid.done()    // close grid
                .done()    // close slotGroup
                .done()    // close column
                .build();  // close row → build panel
    }

    /**
     * Single close path for client-side peek cleanup. ALL client-side close
     * triggers must call this — it hides panels, resets state, removes
     * dynamic regions, unbinds client containers, and restores recipe book.
     */
    public static void closePeekClient() {
        if (!isPeeking()) return;

        peekedSlot = -1;
        bundleActiveSlots = 0;
        peekTitle = Component.empty();

        for (String grid : ALL_GRIDS) {
            MenuKit.hidePanel(grid);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (mc.player.containerMenu != null) {
                for (String name : ContainerPeek.ALL_CONTAINERS) {
                    MKRegionRegistry.removeDynamicRegion(mc.player.containerMenu, name);
                }
            }
            ContainerPeek.unbindAll(mc.player.getUUID(), false);
        }

        if (wasRecipeBookOpen) {
            MenuKitClient.setRecipeBookOpen(true);
        }
        wasRecipeBookOpen = false;
    }

    /**
     * Registers a MENU_CLOSE listener to clean up client-side peek state
     * when the screen closes.
     */
    public static void registerCloseHandler() {
        // AFTER phase — cleanup that reacts to the close, not prevention.
        MenuKit.on(com.trevorschoeny.menukit.event.MKEvent.Type.MENU_CLOSE)
                .after()
                .handler(event -> {
                    closePeekClient();
                    return com.trevorschoeny.menukit.event.MKEventResult.PASS;
                });
    }

    /**
     * Registers the client-side S2C handler.
     */
    public static void registerClientHandler() {
        ClientPlayNetworking.registerGlobalReceiver(PeekS2CPayload.TYPE,
                (payload, context) -> handlePeekResponse(payload));
    }

    // ── Client-Side Handler ──────────────────────────────────────────────────

    private static void handlePeekResponse(PeekS2CPayload payload) {
        if (payload.slotIndex() < 0) {
            // ── Close peek ───────────────────────────────────────────────────
            closePeekClient();
        } else {
            // ── Open peek ────────────────────────────────────────────────────
            // Close any previously-open peek on the client side first
            closePeekClient();

            peekedSlot = payload.slotIndex();
            sourceType = payload.sourceType();
            bundleActiveSlots = payload.activeSlots();
            peekTitle = payload.title();

            wasRecipeBookOpen = MenuKitClient.isRecipeBookOpen();
            if (wasRecipeBookOpen) {
                MenuKitClient.setRecipeBookOpen(false);
            }

            // Register client-side dynamic region so keybinds (sort, move-matching)
            // can resolve the peek panel's slots to a region. Without this,
            // event.getRegion() returns null for peek slots and keybinds silently fail.
            String containerName = ContainerPeek.containerNameForSourceType(sourceType);
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.containerMenu != null) {
                MKContainer container = MenuKit.getContainerForPlayer(
                        containerName, mc.player.getUUID(), false);
                if (container != null) {
                    com.trevorschoeny.menukit.container.MKContainerDef containerDef = MenuKit.getContainerDef(containerName);
                    if (containerDef != null) {
                        MKRegionRegistry.registerDynamicRegion(
                                mc.player.containerMenu, containerName,
                                container.getDelegate(),
                                containerDef.size(), containerDef.persistence(),
                                true, true);
                    }
                }
            }

            // Hide any previously-visible grid, then show the correct one
            for (String grid : ALL_GRIDS) {
                MenuKit.hidePanel(grid);
            }

            MenuKit.showPanel(gridPanelForSourceType(sourceType));
        }
    }

    // ── Panel / Container Mapping ────────────────────────────────────────────

    /** Returns the grid panel name for the given source type. */
    private static String gridPanelForSourceType(int srcType) {
        return switch (srcType) {
            case PeekS2CPayload.SOURCE_ITEM_CONTAINER -> GRID_SHULKER;
            case PeekS2CPayload.SOURCE_ENDER_CHEST -> GRID_ENDER;
            case PeekS2CPayload.SOURCE_BUNDLE -> GRID_BUNDLE;
            default -> GRID_SHULKER;
        };
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public static int getPeekedSlot() { return peekedSlot; }
    public static int getSourceType() { return sourceType; }
    public static Component getPeekTitle() { return peekTitle; }
    public static boolean isPeeking() { return peekedSlot >= 0; }

    /**
     * Effective active slot count for bundles. Dynamically recomputes from
     * the client container so the panel grows as items are added.
     */
    static int getEffectiveBundleSlots() {
        if (!isPeeking()) return 0;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return bundleActiveSlots;

        MKContainer container = MenuKit.getContainerForPlayer(
                ContainerPeek.BUNDLE, mc.player.getUUID(), false);
        if (container == null) return bundleActiveSlots;

        // Count occupied slots and compute bundle weight
        int occupied = 0;
        int totalWeight = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack item = container.getItem(i);
            if (!item.isEmpty()) {
                occupied++;
                // Bundle weight: each item contributes 64/maxStackSize per unit
                totalWeight += (64 / item.getMaxStackSize()) * item.getCount();
            }
        }

        // If the bundle is full (weight >= 64), no extra empty slot
        boolean isFull = totalWeight >= 64;
        if (isFull) {
            return occupied;
        }
        return Math.min(Math.max(occupied + 1, 1), ContainerPeek.BUNDLE_MAX_SLOTS);
    }
}
