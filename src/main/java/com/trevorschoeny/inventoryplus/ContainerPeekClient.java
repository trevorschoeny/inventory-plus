package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKPanel;
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
 * <p>Separated from {@link ContainerPeek} because this class imports client-only
 * classes ({@link Minecraft}) that cannot be loaded on the server-side classloader.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class ContainerPeekClient {

    // ── Client-Side Peek State ──────────────────────────────────────────────

    /** Which inventory slot is currently being peeked (-1 = none). */
    private static int peekedSlot = -1;
    /** Source type of the current peek (see PeekS2CPayload constants). */
    private static int sourceType = 0;
    /** How many slots are active (visible) in the current peek. */
    private static int activeSlots = 0;
    /** Display name for the panel title. */
    private static Component peekTitle = Component.empty();
    /** Whether the recipe book was open when we started peeking. */
    private static boolean wasRecipeBookOpen = false;

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Registers the peek panel with MenuKit. Called from client init.
     */
    public static void registerPanel() {
        // Panel config
        var column = MKPanel.builder(ContainerPeek.PANEL_NAME)
                .showIn(MKContext.ALL)
                .posLeft()
                .hidden()
                .exclusive()
                .autoSize()
                .style(MKPanel.Style.RAISED)
                .shiftClickIn(true)              // allow shift-clicking items into peek container
                .shiftClickOut(true)             // allow shift-clicking items out of peek container
                // Root layout: column (title above slot grid)
                .column();

        // Child 1: title text
        column = column.text()
                .content(ContainerPeekClient::getPeekTitle)
                .done();

        // Child 2: grid of 64 slots
        //   - 9 rows per column, columns extend right-to-left
        //   - index 0 in rightmost column (closest to inventory)
        var grid = column.grid()
                .cellSize(ContainerPeek.SLOT_SIZE)
                .rows(ContainerPeek.ROWS)
                .fillRight();

        for (int i = 0; i < ContainerPeek.MAX_SLOTS; i++) {
            final int slotIndex = i;
            grid = grid.slot()
                    .container(ContainerPeek.CONTAINER_NAME, i)
                    .disabledWhen(() -> slotIndex >= getEffectiveActiveSlots())
                    .done();
        }

        // Close grid → close column → build panel
        grid.done().build();
    }

    /**
     * Registers the client-side S2C handler.
     */
    public static void registerClientHandler() {
        ClientPlayNetworking.registerGlobalReceiver(PeekS2CPayload.TYPE,
                (payload, context) -> handlePeekResponse(payload));
    }

    // ── Client-Side Handler ─────────────────────────────────────────────────

    private static void handlePeekResponse(PeekS2CPayload payload) {
        if (payload.slotIndex() < 0) {
            peekedSlot = -1;
            activeSlots = 0;
            peekTitle = Component.empty();
            MenuKit.hidePanel(ContainerPeek.PANEL_NAME);
            if (wasRecipeBookOpen) {
                MenuKitClient.setRecipeBookOpen(true);
            }
            wasRecipeBookOpen = false;
        } else {
            peekedSlot = payload.slotIndex();
            sourceType = payload.sourceType();
            activeSlots = payload.activeSlots();
            peekTitle = payload.title();
            wasRecipeBookOpen = MenuKitClient.isRecipeBookOpen();
            if (wasRecipeBookOpen) {
                MenuKitClient.setRecipeBookOpen(false);
            }
            MenuKit.showPanel(ContainerPeek.PANEL_NAME);
        }
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public static int getPeekedSlot() { return peekedSlot; }
    public static Component getPeekTitle() { return peekTitle; }
    public static boolean isPeeking() { return peekedSlot >= 0; }

    /**
     * Effective active slot count. For bundles, dynamically recomputes from
     * the client container so the panel grows as items are added.
     */
    static int getEffectiveActiveSlots() {
        if (!isPeeking()) return 0;
        if (sourceType != PeekS2CPayload.SOURCE_BUNDLE) return activeSlots;

        var mc = Minecraft.getInstance();
        if (mc.player == null) return activeSlots;

        MKContainer container = MenuKit.getContainerForPlayer(
                ContainerPeek.CONTAINER_NAME, mc.player.getUUID(), false);
        if (container == null) return activeSlots;

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
        return Math.min(Math.max(occupied + 1, 1), ContainerPeek.MAX_SLOTS);
    }

}
