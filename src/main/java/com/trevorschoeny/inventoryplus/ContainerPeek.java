package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.container.MKContainerDef;
import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.region.MKRegionRegistry;
import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.menukit.source.MKContainerSource;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Container Peek — server-side and common logic.
 *
 * <p>Three separate containers for each peekable type:
 * <ul>
 *   <li>{@code peek_shulker} — 27 fixed slots for shulker boxes</li>
 *   <li>{@code peek_ender} — 27 fixed slots for ender chests</li>
 *   <li>{@code peek_bundle} — 64 max slots for bundles (variable active count)</li>
 * </ul>
 *
 * <p>Client-side logic (panels, rendering, recipe book) lives in
 * {@link ContainerPeekClient} to avoid loading client-only classes on the
 * server classloader.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class ContainerPeek {

    // ── Container Names ──────────────────────────────────────────────────────

    public static final String SHULKER = "peek_shulker";
    public static final String ENDER   = "peek_ender";
    public static final String BUNDLE  = "peek_bundle";

    // ── Container-specific constants ─────────────────────────────────────────

    static final int FIXED_SLOTS = 27;          // shulker and ender chest
    static final int BUNDLE_MAX_SLOTS = 64;     // max possible bundle items
    static final int SLOT_SIZE = 18;
    static final int FIXED_COLS = 9;            // 9 columns for 3×9 grid
    static final int FIXED_ROWS = 3;            // 3 rows for 27 slots
    static final int BUNDLE_ROWS = 9;           // up to 9 rows for bundles

    // ── All container names (for iteration) ──────────────────────────────────

    static final String[] ALL_CONTAINERS = { SHULKER, ENDER, BUNDLE };

    // ── Registration (common entrypoint) ─────────────────────────────────────

    /** Registers separate ephemeral slot groups for each peek type. */
    public static void registerContainer() {
        MenuKit.slotGroup(SHULKER).slots(FIXED_SLOTS).ephemeral().type(MKContainerType.SIMPLE).register();
        MenuKit.slotGroup(ENDER).slots(FIXED_SLOTS).ephemeral().type(MKContainerType.SIMPLE).register();
        MenuKit.slotGroup(BUNDLE).slots(BUNDLE_MAX_SLOTS).ephemeral().type(MKContainerType.SIMPLE).register();
    }

    /**
     * Unbinds all peek containers for the given player (no region cleanup).
     * Used by client-side close path where regions are managed separately.
     */
    public static void unbindAll(java.util.UUID playerUuid, boolean isServer) {
        for (String name : ALL_CONTAINERS) {
            MKContainer container = MenuKit.getContainerForPlayer(name, playerUuid, isServer);
            if (container != null && container.isBound()) {
                container.unbind();
            }
        }
    }

    /**
     * Single close path for server-side peek cleanup. ALL server-side close
     * triggers must call this — it unbinds containers (triggering final sync
     * for deferred sources like shulker boxes), removes dynamic regions, and
     * sends the close response to the client.
     *
     * @param player      The server player whose peek to close.
     * @param sendPacket  Whether to send the S2C close packet. False when
     *                    called from disconnect (no connection to send on).
     */
    public static void closePeek(ServerPlayer player, boolean sendPacket) {
        for (String name : ALL_CONTAINERS) {
            MKContainer container = MenuKit.getContainerForPlayer(name, player.getUUID(), true);
            if (container != null && container.isBound()) {
                container.unbind();
            }
            if (player.containerMenu != null) {
                MKRegionRegistry.removeDynamicRegion(player.containerMenu, name);
            }
        }
        if (sendPacket) {
            ServerPlayNetworking.send(player, PeekS2CPayload.closed());
        }
    }

    /** Registers network packets and server handlers. Called from common init. */
    public static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(PeekC2SPayload.TYPE, PeekC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PeekS2CPayload.TYPE, PeekS2CPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PeekC2SPayload.TYPE,
                (payload, context) -> handlePeekRequest(context.player(), payload.slotIndex()));

        // Clean up all peek containers on disconnect (no packet — connection is gone)
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> closePeek(handler.getPlayer(), false));
    }

    // ── Item Detection ───────────────────────────────────────────────────────

    public static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    public static boolean isBundle(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    public static boolean isEnderChest(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof EnderChestBlock;
    }

    public static boolean isPeekable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        InventoryPlusConfig cfg = InventoryPlusConfig.get();
        // Only bundles, ender chests, and shulker boxes are peekable — no
        // generic CONTAINER catch-all. Each type is gated by its config toggle.
        if (cfg.enablePeekBundle && isBundle(stack)) return true;
        if (cfg.enablePeekEnderChest && isEnderChest(stack)) return true;
        if (cfg.enablePeekShulker && isShulkerBox(stack)) return true;
        return false;
    }

    /**
     * Returns the container name for the given source type constant.
     * Used by client-side code to determine which container to look up.
     */
    public static String containerNameForSourceType(int sourceType) {
        return switch (sourceType) {
            case PeekS2CPayload.SOURCE_ITEM_CONTAINER -> SHULKER;
            case PeekS2CPayload.SOURCE_ENDER_CHEST -> ENDER;
            case PeekS2CPayload.SOURCE_BUNDLE -> BUNDLE;
            default -> SHULKER;
        };
    }

    // ── Server-Side Handler ──────────────────────────────────────────────────

    private static void handlePeekRequest(ServerPlayer player, int menuSlotIndex) {
        // Close request
        if (menuSlotIndex < 0) {
            closePeek(player, true);
            return;
        }

        // Validate menu slot index against the player's current container menu
        if (player.containerMenu == null
                || menuSlotIndex >= player.containerMenu.slots.size()) {
            InventoryPlus.LOGGER.warn("[ContainerPeek] Invalid menu slot index: {}", menuSlotIndex);
            return;
        }

        // Read from the menu slot — works for ANY container (player inv, chest, etc.)
        net.minecraft.world.inventory.Slot slot = player.containerMenu.slots.get(menuSlotIndex);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return;

        // Determine source type, container name, and source
        int srcType;
        int activeSlots;
        Component title;
        MKContainerSource source;
        String containerName;

        // Create a live supplier that reads from the menu slot. Vanilla may
        // REPLACE the ItemStack object during interactions — the supplier
        // ensures poll/sync always see the live item. Safe because MENU_CLOSE
        // calls closePeek() before the menu changes, so stale indices are never used.
        final ServerPlayer peekPlayer = player;
        final int capturedSlotIndex = menuSlotIndex;
        java.util.function.Supplier<ItemStack> liveStack = () -> {
            var menu = peekPlayer.containerMenu;
            if (menu == null || capturedSlotIndex >= menu.slots.size()) return ItemStack.EMPTY;
            return menu.slots.get(capturedSlotIndex).getItem();
        };

        if (isBundle(stack)) {
            srcType = PeekS2CPayload.SOURCE_BUNDLE;
            source = MKContainerSource.ofBundle(liveStack);
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            int itemCount = 0;
            if (contents != null) {
                for (ItemStack item : contents.items()) {
                    if (!item.isEmpty()) itemCount++;
                }
            }
            boolean isFull = contents != null
                    && new BundleContents.Mutable(contents).tryInsert(ItemStack.EMPTY) == 0;
            activeSlots = isFull ? itemCount : itemCount + 1;
            activeSlots = Math.max(activeSlots, 1);
            title = stack.getHoverName();
            containerName = BUNDLE;
        } else if (isEnderChest(stack)) {
            srcType = PeekS2CPayload.SOURCE_ENDER_CHEST;
            source = MKContainerSource.ofContainer(player.getEnderChestInventory());
            activeSlots = FIXED_SLOTS;
            title = Component.translatable("container.enderchest");
            containerName = ENDER;
        } else if (isShulkerBox(stack)) {
            // Shulker boxes only — no generic CONTAINER catch-all.
            srcType = PeekS2CPayload.SOURCE_ITEM_CONTAINER;
            source = MKContainerSource.ofItemContainer(liveStack);
            activeSlots = FIXED_SLOTS;
            title = stack.getHoverName();
            containerName = SHULKER;
        } else {
            return;
        }

        // Close any previously-open peek (unbind + region cleanup)
        closePeek(player, false);

        // Bind the correct container
        MKContainer container = MenuKit.getContainerForPlayer(
                containerName, player.getUUID(), true);
        if (container == null) {
            InventoryPlus.LOGGER.warn("[ContainerPeek] Container '{}' not found", containerName);
            return;
        }
        container.bind(source);

        // Register the server-side region so sorting and move-matching work
        MKContainerDef containerDef = MenuKit.getContainerDef(containerName);
        if (containerDef != null && player.containerMenu != null) {
            MKRegionRegistry.registerDynamicRegion(
                    player.containerMenu, containerName,
                    container.getDelegate(),
                    containerDef.size(), containerDef.persistence(),
                    true, true);  // shiftClickIn, shiftClickOut
        }

        ServerPlayNetworking.send(player,
                new PeekS2CPayload(menuSlotIndex, srcType, activeSlots, title));
    }
}
