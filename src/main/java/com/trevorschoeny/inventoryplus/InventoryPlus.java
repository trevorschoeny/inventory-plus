package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.features.autofill.AutoFill;
import com.trevorschoeny.inventoryplus.network.AutoFillC2SPayload;
import com.trevorschoeny.inventoryplus.network.BulkMoveC2SPayload;
import com.trevorschoeny.inventoryplus.network.SortC2SPayload;
import com.trevorschoeny.menukit.GeneralOption;
import com.trevorschoeny.menukit.MKContainerSort;
import com.trevorschoeny.menukit.MKRegion;
import com.trevorschoeny.menukit.MKRegionRegistry;
import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inventory Plus — a set of features that enhance the player's inventory.
 *
 * <p><b>Equipment:</b> Two passive equipment slots (elytra + totem) that provide
 * their effects without occupying armor or hand slots.
 *
 * <p><b>Pockets:</b> Each hotbar slot can store up to 3 extra items that cycle
 * via keybind, extending the hotbar's effective capacity.
 *
 * <p>All UI is built on the MenuKit API — panels, slots, buttons, and HUD
 * elements are declared via builder chains. Persistence, sync, and creative
 * mode support are handled automatically by MenuKit.
 */
public class InventoryPlus implements ModInitializer {

    public static final String MOD_ID = "inventory-plus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // Load config from disk
        InventoryPlusConfig.load();

        // Container: 2-slot storage for passive equipment (elytra + totem)
        // Registered before the panel so the panel can reference it by name.
        MenuKit.container("equipment").playerBound().size(2).register();

        // Feature 1: Equipment panel (UI for the equipment container)
        EquipmentPanel.register();

        // Feature 2: Pockets (hotbar extension — extra items per hotbar slot)
        PocketsPanel.register();

        // Feature 3: Container Peek — common registration (container + packets)
        // Panel registration is in InventoryPlusClient (client-only classes)
        ContainerPeek.registerContainer();
        ContainerPeek.registerPackets();

        // Feature 4: Sort Region — C2S packet for keybind-triggered sorting.
        // The keybind and KEY_PRESS handler live in InventoryPlusClient (client).
        // Here we register the packet type and the server-side handler that
        // performs the actual sort.
        registerSortPacket();

        // Feature 5: Bulk Move — Shift+double-click to move all matching items.
        // The DOUBLE_CLICK handler lives in InventoryPlusClient (client).
        // Here we register the packet type and the server-side handler that
        // iterates the region and shift-clicks each matching slot.
        registerBulkMovePacket();

        // Feature 6: Autofill — keybind-triggered refill from shulker boxes.
        // The keybind and tick handler live in InventoryPlusClient (client).
        // Here we register the packet type and the server-side handler that
        // scans the inventory and extracts items from shulkers.
        registerAutoFillPacket();

        LOGGER.info("[InventoryPlus] Initialized");
    }

    // ── Sort Packet Registration ──────────────────────────────────────────

    /**
     * Registers the Sort C2S packet type and its server-side handler.
     *
     * <p>When the client sends a sort request (triggered by the sort keybind
     * while hovering a slot), the server:
     * <ol>
     *   <li>Validates the player has an open menu</li>
     *   <li>Looks up the named region in the menu's region registry</li>
     *   <li>Calls {@link MKContainerSort#sortRegion} to sort in-place</li>
     * </ol>
     *
     * <p>No explicit sync is needed — vanilla's {@code broadcastChanges()}
     * detects the container modifications on the next tick and sends slot
     * update packets to the client automatically.
     */
    private static void registerSortPacket() {
        PayloadTypeRegistry.playC2S().register(SortC2SPayload.TYPE, SortC2SPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SortC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    String regionName = payload.regionName();

                    // DoS prevention — reject oversized strings that could waste memory.
                    // Normal region names are short identifiers like "mk:main_inventory".
                    if (regionName.length() > 256) return;

                    // Validate: player must have an open menu
                    if (player.containerMenu == null) {
                        LOGGER.warn("[InventoryPlus] Sort request from {} but no menu open",
                                player.getName().getString());
                        return;
                    }

                    // Look up the region by name in the player's current menu.
                    // Returns null if the region doesn't exist (invalid packet or
                    // stale client state — the player closed/changed menus).
                    MKRegion region = MKRegionRegistry.getRegion(
                            player.containerMenu, regionName);
                    if (region == null) {
                        LOGGER.debug("[InventoryPlus] Sort: region '{}' not found in menu for {}",
                                regionName, player.getName().getString());
                        return;
                    }

                    // Sort the region. broadcastChanges() will sync the result
                    // to the client on the next server tick.
                    MKContainerSort.sortRegion(region, player.containerMenu);
                });
    }

    // ── Bulk Move Packet Registration ─────────────────────────────────────

    /**
     * Registers the Bulk Move C2S packet type and its server-side handler.
     *
     * <p>When the client sends a bulk-move request (triggered by Shift+double-click),
     * the server:
     * <ol>
     *   <li>Validates the player has an open menu</li>
     *   <li>Resolves the item from its registry ID</li>
     *   <li>Looks up the named region in the menu's region registry</li>
     *   <li>Iterates every slot in that region and calls {@code quickMoveStack}
     *       on each slot containing the matching item</li>
     * </ol>
     *
     * <p>{@code quickMoveStack} is vanilla's shift-click logic — it already
     * knows how to route items to the correct target region based on the
     * menu's {@code moveItemStackTo} override. We just call it for every
     * matching slot instead of one at a time.
     *
     * <p>No explicit sync is needed — vanilla's {@code broadcastChanges()}
     * detects the container modifications on the next tick and sends slot
     * update packets to the client automatically.
     */
    private static void registerBulkMovePacket() {
        PayloadTypeRegistry.playC2S().register(BulkMoveC2SPayload.TYPE, BulkMoveC2SPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BulkMoveC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    String regionName = payload.regionName();
                    String itemId = payload.itemId();

                    // DoS prevention — reject oversized strings from the network
                    if (regionName.length() > 256 || itemId.length() > 256) return;

                    // Validate: player must have an open menu
                    AbstractContainerMenu menu = player.containerMenu;
                    if (menu == null) {
                        LOGGER.warn("[InventoryPlus] Bulk move request from {} but no menu open",
                                player.getName().getString());
                        return;
                    }

                    // Resolve the item from its registry ID. If the ID is invalid
                    // (tampered packet or mod mismatch), bail out gracefully.
                    Identifier itemIdentifier = Identifier.tryParse(itemId);
                    if (itemIdentifier == null || !BuiltInRegistries.ITEM.containsKey(itemIdentifier)) {
                        LOGGER.debug("[InventoryPlus] Bulk move: unknown item '{}' from {}",
                                itemId, player.getName().getString());
                        return;
                    }
                    Item targetItem = BuiltInRegistries.ITEM.getValue(itemIdentifier);

                    // Look up the source region by name in the player's current menu.
                    // Returns null if the region doesn't exist (stale client state).
                    MKRegion region = MKRegionRegistry.getRegion(menu, regionName);
                    if (region == null) {
                        LOGGER.debug("[InventoryPlus] Bulk move: region '{}' not found in menu for {}",
                                regionName, player.getName().getString());
                        return;
                    }

                    // Iterate every menu slot in the region and shift-click each one
                    // that contains the target item. We use menu slot indices because
                    // quickMoveStack operates on menu-level slot IDs (not container indices).
                    //
                    // Iterate backwards so that items shifting down within the same
                    // region (e.g., compacting gaps) don't cause us to skip slots.
                    int start = region.getMenuSlotStart();
                    int end = region.getMenuSlotEnd();
                    for (int i = end; i >= start; i--) {
                        // Bounds check — menu may have fewer slots than expected
                        // if a mod removed slots dynamically (defensive).
                        if (i >= menu.slots.size()) continue;

                        Slot slot = menu.slots.get(i);
                        ItemStack stack = slot.getItem();

                        // Only move slots that contain the target item type
                        if (stack.isEmpty() || !stack.is(targetItem)) continue;

                        // Respect locked slots — don't bulk-move items the player
                        // has explicitly pinned in place via Ctrl+click lock.
                        MKSlotState slotState = MKSlotStateRegistry.get(slot);
                        if (slotState != null && slotState.isLocked()) continue;

                        // quickMoveStack is vanilla's shift-click implementation.
                        // It handles routing to the correct target region, partial
                        // moves when the target is nearly full, and returns the
                        // remainder (which stays in the source slot).
                        menu.quickMoveStack(player, i);
                    }

                    // No explicit sync needed — broadcastChanges() fires on the
                    // next server tick and sends slot updates to the client.
                });
    }

    // ── AutoFill Packet Registration ─────────────────────────────────────

    /**
     * Registers the AutoFill C2S packet type and its server-side handler.
     *
     * <p>When the client sends an autofill request (triggered by the autofill
     * keybind during gameplay), the server:
     * <ol>
     *   <li>Scans the player's inventory for partial stacks</li>
     *   <li>Searches shulker boxes in the inventory for matching items</li>
     *   <li>Extracts items from shulkers to top off partial stacks</li>
     *   <li>Fills empty hotbar slots with items matching existing hotbar items</li>
     * </ol>
     *
     * <p>No explicit sync is needed — vanilla's {@code broadcastChanges()}
     * detects the inventory modifications on the next tick and sends slot
     * update packets to the client automatically.
     */
    private static void registerAutoFillPacket() {
        PayloadTypeRegistry.playC2S().register(AutoFillC2SPayload.TYPE, AutoFillC2SPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AutoFillC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();

                    // Guard: respect the family-wide "autofill_enabled" toggle.
                    // The client also gates on this before sending, but we check
                    // server-side too so a modded/spoofed client cannot bypass the
                    // setting and force a server-side inventory scan. Matches the
                    // pattern used by AutoRestock and AutoReplace mixins.
                    boolean enabled = MenuKit.family("trevmods")
                            .getGeneral(new GeneralOption<>("autofill_enabled", true, Boolean.class));
                    if (!enabled) return;

                    // Delegate to AutoFill — all logic lives there as pure functions.
                    // The inventory is the server's authoritative copy, so all
                    // modifications are immediately real. broadcastChanges() syncs
                    // the result to the client on the next tick.
                    AutoFill.execute(player);
                });
    }
}
