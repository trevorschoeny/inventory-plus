package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.inventoryplus.network.PeekS2CPayload;
import com.trevorschoeny.menukit.MKContainer;
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
 * <p>Handles container registration, packet registration, server-side binding,
 * and item detection. Client-side logic (panel, rendering, recipe book) lives
 * in {@link ContainerPeekClient} to avoid loading client-only classes on the
 * server classloader.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class ContainerPeek {

    // ── Constants (package-visible for ContainerPeekClient) ──────────────────

    public static final String CONTAINER_NAME = "peek";
    public static final String PANEL_NAME = "peek";
    static final int MAX_COLS = 8;
    static final int ROWS = 9;
    static final int MAX_SLOTS = 64;
    static final int SLOT_SIZE = 18;

    // ── Registration (common entrypoint) ────────────────────────────────────

    /** Registers the ephemeral container. Called from common init. */
    public static void registerContainer() {
        MenuKit.container(CONTAINER_NAME).ephemeral().size(MAX_SLOTS).register();
    }

    /** Registers network packets and server handlers. Called from common init. */
    public static void registerPackets() {
        PayloadTypeRegistry.playC2S().register(PeekC2SPayload.TYPE, PeekC2SPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PeekS2CPayload.TYPE, PeekS2CPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PeekC2SPayload.TYPE,
                (payload, context) -> handlePeekRequest(context.player(), payload.slotIndex()));

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    MKContainer container = MenuKit.getContainerForPlayer(
                            CONTAINER_NAME, handler.getPlayer().getUUID(), true);
                    if (container != null && container.isBound()) {
                        container.unbind();
                    }
                });
    }

    // ── Item Detection ──────────────────────────────────────────────────────

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

    // ── Server-Side Handler ─────────────────────────────────────────────────

    private static void handlePeekRequest(ServerPlayer player, int slotIndex) {
        MKContainer container = MenuKit.getContainerForPlayer(
                CONTAINER_NAME, player.getUUID(), true);
        if (container == null) {
            InventoryPlus.LOGGER.warn("[ContainerPeek] Peek container not found");
            return;
        }

        // Close request
        if (slotIndex < 0) {
            container.unbind();
            ServerPlayNetworking.send(player, PeekS2CPayload.closed());
            return;
        }

        // Validate slot index
        var menu = player.containerMenu;
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            InventoryPlus.LOGGER.warn("[ContainerPeek] Invalid slot index: {}", slotIndex);
            return;
        }

        ItemStack stack = menu.slots.get(slotIndex).getItem();
        if (stack.isEmpty()) return;

        // Unbind any existing peek first
        if (container.isBound()) {
            container.unbind();
        }

        // Determine source type and bind
        int srcType;
        int slots;
        Component title;
        MKContainerSource source;

        if (isShulkerBox(stack)) {
            srcType = PeekS2CPayload.SOURCE_ITEM_CONTAINER;
            source = MKContainerSource.ofItemContainer(stack);
            slots = 27;
            title = stack.getHoverName();
        } else if (isBundle(stack)) {
            srcType = PeekS2CPayload.SOURCE_BUNDLE;
            source = MKContainerSource.ofBundle(stack);
            BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
            int itemCount = 0;
            if (contents != null) {
                for (ItemStack item : contents.items()) {
                    if (!item.isEmpty()) itemCount++;
                }
            }
            boolean isFull = contents != null
                    && new BundleContents.Mutable(contents).tryInsert(ItemStack.EMPTY) == 0;
            slots = isFull ? itemCount : itemCount + 1;
            slots = Math.max(slots, 1);
            title = stack.getHoverName();
        } else if (isEnderChest(stack)) {
            srcType = PeekS2CPayload.SOURCE_ENDER_CHEST;
            source = MKContainerSource.ofContainer(player.getEnderChestInventory());
            slots = 27;
            title = Component.translatable("container.enderchest");
        } else {
            return;
        }

        container.bind(source);
        ServerPlayNetworking.send(player,
                new PeekS2CPayload(slotIndex, srcType, slots, title));
    }
}
