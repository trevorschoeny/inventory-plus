package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MKContainerDef;
import com.trevorschoeny.menukit.MKContainerType;
import com.trevorschoeny.menukit.MKInventory;
import com.trevorschoeny.menukit.MKRegionRegistry;
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

    /** Registers separate ephemeral containers for each peek type. */
    public static void registerContainer() {
        MenuKit.container(SHULKER).ephemeral().type(MKContainerType.SIMPLE).size(FIXED_SLOTS).register();
        MenuKit.container(ENDER).ephemeral().type(MKContainerType.SIMPLE).size(FIXED_SLOTS).register();
        MenuKit.container(BUNDLE).ephemeral().type(MKContainerType.SIMPLE).size(BUNDLE_MAX_SLOTS).register();
    }

    /**
     * Unbinds all peek containers for the given player. Called when the
     * menu closes (screen closed, player disconnect, etc.) to ensure
     * peek state doesn't persist across screen open/close cycles.
     */
    public static void unbindAll(java.util.UUID playerUuid, boolean isServer) {
        for (String name : ALL_CONTAINERS) {
            MKContainer container = MenuKit.getContainerForPlayer(name, playerUuid, isServer);
            if (container != null && container.isBound()) {
                container.unbind();
            }
        }
    }

    /** Registers network packets and server handlers. Called from common init. */
    public static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(PeekC2SPayload.TYPE, PeekC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PeekS2CPayload.TYPE, PeekS2CPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PeekC2SPayload.TYPE,
                (payload, context) -> handlePeekRequest(context.player(), payload.slotIndex()));

        // Clean up all peek containers on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    ServerPlayer disconnecting = handler.getPlayer();
                    for (String name : ALL_CONTAINERS) {
                        MKContainer container = MenuKit.getContainerForPlayer(
                                name, disconnecting.getUUID(), true);
                        if (container != null && container.isBound()) {
                            container.unbind();
                            if (disconnecting.containerMenu != null) {
                                MKRegionRegistry.removeDynamicRegion(
                                        disconnecting.containerMenu, name);
                            }
                        }
                    }
                });
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
        return isShulkerBox(stack) || isBundle(stack) || isEnderChest(stack);
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

    private static void handlePeekRequest(ServerPlayer player, int unifiedPos) {
        // Close request — unbind whichever container is currently bound
        if (unifiedPos < 0) {
            for (String name : ALL_CONTAINERS) {
                MKContainer container = MenuKit.getContainerForPlayer(
                        name, player.getUUID(), true);
                if (container != null && container.isBound()) {
                    container.unbind();
                    MKRegionRegistry.removeDynamicRegion(player.containerMenu, name);
                }
            }
            ServerPlayNetworking.send(player, PeekS2CPayload.closed());
            return;
        }

        // Validate unified position — must be within player inventory range (0-40)
        if (unifiedPos > MKInventory.OFFHAND) {
            InventoryPlus.LOGGER.warn("[ContainerPeek] Invalid unified position: {}", unifiedPos);
            return;
        }

        // Read directly from player's inventory — bypasses menu slot system entirely
        ItemStack stack = MKInventory.getPlayerItem(player, unifiedPos);
        if (stack.isEmpty()) return;

        // Determine source type, container name, and source
        int srcType;
        int activeSlots;
        Component title;
        MKContainerSource source;
        String containerName;

        // Create a live supplier that always reads the CURRENT ItemStack at
        // this inventory position. Vanilla may REPLACE the ItemStack object in
        // the slot during interactions (e.g., bundle scroll+right-click rebuilds
        // the BundleContents into a new ItemStack). A direct reference would go
        // stale; the supplier ensures poll/sync always see the live item.
        final ServerPlayer peekPlayer = player;
        final int peekPos = unifiedPos;
        java.util.function.Supplier<ItemStack> liveStack =
                () -> MKInventory.getPlayerItem(peekPlayer, peekPos);

        if (isShulkerBox(stack)) {
            srcType = PeekS2CPayload.SOURCE_ITEM_CONTAINER;
            source = MKContainerSource.ofItemContainer(liveStack);
            activeSlots = FIXED_SLOTS;
            title = stack.getHoverName();
            containerName = SHULKER;
        } else if (isBundle(stack)) {
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
        } else {
            return;
        }

        // Unbind any previously-bound peek container (of any type)
        for (String name : ALL_CONTAINERS) {
            MKContainer prev = MenuKit.getContainerForPlayer(name, player.getUUID(), true);
            if (prev != null && prev.isBound()) {
                prev.unbind();
                MKRegionRegistry.removeDynamicRegion(player.containerMenu, name);
            }
        }

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
                new PeekS2CPayload(unifiedPos, srcType, activeSlots, title));
    }
}
