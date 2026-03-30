package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.inventoryplus.features.autofill.AutoFill;
import com.trevorschoeny.inventoryplus.network.AutoFillC2SPayload;
import com.trevorschoeny.inventoryplus.network.BulkMoveC2SPayload;
import com.trevorschoeny.inventoryplus.network.MoveMatchingC2SPayload;
import com.trevorschoeny.inventoryplus.network.PocketCycleC2SPayload;
import com.trevorschoeny.inventoryplus.network.SortC2SPayload;
import com.trevorschoeny.inventoryplus.network.SortLockC2SPayload;
import com.trevorschoeny.menukit.GeneralOption;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MKContainerSort;
import com.trevorschoeny.menukit.MKContainerType;
import com.trevorschoeny.menukit.MKMoveMatching;
import com.trevorschoeny.menukit.MKRegion;
import com.trevorschoeny.menukit.MKRegionGroup;
import com.trevorschoeny.menukit.MKEvent;
import com.trevorschoeny.menukit.MKEventResult;
import com.trevorschoeny.menukit.MKRegionRegistry;
import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Inventory;
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

        // Unbind peek containers when the menu closes (player closes inventory).
        // Server-side: ensures peek containers don't stay bound across menu cycles.
        MenuKit.on(MKEvent.Type.MENU_CLOSE)
                .slotHandler(event -> {
                    if (event.getPlayer() instanceof ServerPlayer sp) {
                        // Remove dynamic regions (InventoryMenu is reused across sessions)
                        if (sp.containerMenu != null) {
                            for (String name : ContainerPeek.ALL_CONTAINERS) {
                                MKRegionRegistry.removeDynamicRegion(sp.containerMenu, name);
                            }
                        }
                        ContainerPeek.unbindAll(sp.getUUID(), true);
                    }
                    return MKEventResult.PASS;
                });

        // Feature 4: Sort Region — C2S packet for keybind-triggered sorting.
        // The keybind and KEY_PRESS handler live in InventoryPlusClient (client).
        // Here we register the packet type and the server-side handler that
        // performs the actual sort.
        registerSortPacket();

        // Feature 4b: Sort Lock — C2S packet to sync sort-lock state to the server.
        // The toggle handler lives in InventoryPlusClient (client). Here we register
        // the packet type and the server-side handler that sets sort-lock on the
        // server's Slot object so sorting and shift-click routing respect it.
        registerSortLockPacket();

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

        // Feature 7: Pocket Cycle — keybind-triggered rotation of items
        // through a hotbar slot's pocket. The keybind and animation trigger
        // live in PocketCycler (client). Here we register the packet type
        // and the server-side handler that rotates the items.
        registerPocketCyclePacket();

        // Feature 8: Move Matching — button-triggered transfer of matching items
        // from player inventory into the open container. The button panel lives
        // in InventoryPlusClient (client). Here we register the packet type,
        // server-side handler, and the region groups needed for resolution.
        registerMoveMatchingPacket();
        registerContainerGroups();

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

    // ── Sort Lock Packet Registration ────────────────────────────────────

    /**
     * Registers the Sort Lock C2S packet type and its server-side handler.
     *
     * <p>Sort-lock state is toggled client-side (keybind handler in
     * InventoryPlusClient), but sorting and shift-click routing run on the
     * server. This packet syncs the lock state so the server's
     * {@link MKSlotStateRegistry} has the same sort-lock flags as the client.
     *
     * <p>The server validates the slot index, then sets or clears the
     * sort-lock flag on the corresponding Slot's MKSlotState.
     */
    private static void registerSortLockPacket() {
        PayloadTypeRegistry.playC2S().register(SortLockC2SPayload.TYPE, SortLockC2SPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SortLockC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    int slotIndex = payload.slotIndex();
                    boolean locked = payload.locked();

                    // Validate: player must have an open menu
                    if (player.containerMenu == null) {
                        LOGGER.warn("[InventoryPlus] Sort-lock request from {} but no menu open",
                                player.getName().getString());
                        return;
                    }

                    // Validate: slot index must be in range
                    AbstractContainerMenu menu = player.containerMenu;
                    if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                        LOGGER.warn("[InventoryPlus] Sort-lock: invalid slot index {} (menu has {} slots)",
                                slotIndex, menu.slots.size());
                        return;
                    }

                    // Set the sort-lock state on the server's Slot object so that
                    // MKContainerSort and shift-click routing respect it.
                    Slot slot = menu.slots.get(slotIndex);
                    MKSlotState state = MKSlotStateRegistry.getOrCreate(slot);
                    state.setSortLocked(locked);

                    LOGGER.debug("[InventoryPlus] Sort-lock set: slot {} -> {} for {}",
                            slotIndex, locked, player.getName().getString());
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

                        // Respect locked and sort-locked slots — don't bulk-move
                        // items the player has explicitly pinned in place.
                        MKSlotState slotState = MKSlotStateRegistry.get(slot);
                        if (slotState != null && (slotState.isLocked() || slotState.isSortLocked())) continue;

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

    // ── Pocket Cycle Packet Registration ─────────────────────────────────

    /**
     * Registers the Pocket Cycle C2S packet type and its server-side handler.
     *
     * <p>When the client sends a pocket-cycle request (triggered by the cycle
     * keybind during gameplay), the server:
     * <ol>
     *   <li>Validates the hotbar slot index (0-8)</li>
     *   <li>Looks up the pocket container for that slot</li>
     *   <li>Collects enabled positions (hotbar + non-disabled pocket slots)</li>
     *   <li>Rotates items through the enabled positions</li>
     * </ol>
     *
     * <p>No explicit sync is needed — vanilla's {@code broadcastChanges()}
     * detects the inventory modifications on the next tick and sends slot
     * update packets to the client automatically.
     */
    private static void registerPocketCyclePacket() {
        PayloadTypeRegistry.playC2S().register(PocketCycleC2SPayload.TYPE, PocketCycleC2SPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PocketCycleC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    int slot = payload.hotbarSlot();
                    boolean forward = payload.forward();

                    // Validate hotbar slot range (0-8). Reject out-of-range values
                    // from a modded/spoofed client to prevent array index issues.
                    if (slot < 0 || slot > 8) return;

                    // Guard: respect the pockets-enabled config toggle.
                    // The client also gates on this before sending, but we check
                    // server-side too so a spoofed client cannot bypass the setting.
                    if (!InventoryPlusConfig.get().enablePockets) return;

                    String containerName = "pocket_" + slot;
                    Inventory inventory = player.getInventory();
                    MKContainer pocket = MenuKit.getContainerForPlayer(
                            containerName, player.getUUID(), true);
                    if (pocket == null) return;

                    Set<Integer> disabled = PocketsPanel.getDisabledSlots(slot);
                    int maxSlots = InventoryPlusConfig.get().pocketSlotCount;

                    // Collect the indices of enabled positions.
                    // Position 0 = hotbar (always enabled), positions 1-3 = pocket slots.
                    // Slots beyond the configured count are treated as disabled.
                    List<Integer> enabledIndices = new ArrayList<>();
                    enabledIndices.add(0); // hotbar is always in the rotation
                    for (int i = 0; i < PocketCycler.POCKET_SIZE; i++) {
                        if (i < maxSlots && !disabled.contains(i)) {
                            enabledIndices.add(i + 1); // pocket index i -> position i+1
                        }
                    }

                    // Need at least 2 enabled positions to cycle
                    if (enabledIndices.size() < 2) return;

                    // Read items at enabled positions (copy to avoid reference issues)
                    ItemStack[] allItems = new ItemStack[1 + PocketCycler.POCKET_SIZE];
                    allItems[0] = inventory.getItem(slot).copy();
                    for (int i = 0; i < PocketCycler.POCKET_SIZE; i++) {
                        allItems[i + 1] = pocket.getItem(i).copy();
                    }

                    // Extract the enabled items into a list, rotate, write back
                    List<ItemStack> enabledItems = new ArrayList<>();
                    for (int idx : enabledIndices) {
                        enabledItems.add(allItems[idx]);
                    }

                    // Rotate the enabled items
                    if (forward) {
                        // Forward: first item wraps to end
                        ItemStack first = enabledItems.remove(0);
                        enabledItems.add(first);
                    } else {
                        // Backward: last item wraps to front
                        ItemStack last = enabledItems.remove(enabledItems.size() - 1);
                        enabledItems.add(0, last);
                    }

                    // Write rotated items back to their original enabled positions
                    for (int i = 0; i < enabledIndices.size(); i++) {
                        int pos = enabledIndices.get(i);
                        ItemStack item = enabledItems.get(i).copy();
                        if (pos == 0) {
                            inventory.setItem(slot, item);
                        } else {
                            pocket.setItem(pos - 1, item);
                        }
                    }
                });
    }

    // ── Move Matching Packet Registration ────────────────────────────────

    /**
     * Registers the Move Matching C2S packet type and its server-side handler.
     *
     * <p>When the client sends a move-matching request (triggered by clicking
     * the "Move Matching" button near a container), the server:
     * <ol>
     *   <li>Validates the player has an open menu</li>
     *   <li>Resolves both source and destination region groups</li>
     *   <li>Delegates to {@link MKMoveMatching#moveMatching} which scans the
     *       destination for item types, then shift-clicks matching items from
     *       the source</li>
     * </ol>
     *
     * <p>No explicit sync is needed — vanilla's {@code broadcastChanges()}
     * detects the container modifications on the next tick and sends slot
     * update packets to the client automatically.
     */
    private static void registerMoveMatchingPacket() {
        PayloadTypeRegistry.playC2S().register(
                MoveMatchingC2SPayload.TYPE, MoveMatchingC2SPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MoveMatchingC2SPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    String sourceGroupName = payload.sourceGroupName();
                    String destGroupName = payload.destGroupName();
                    String destRegionName = payload.destRegionName();
                    boolean includeHotbar = payload.includeHotbar();

                    // DoS prevention — reject oversized strings from the network
                    if (sourceGroupName.length() > 256 || destGroupName.length() > 256
                            || destRegionName.length() > 256) return;

                    // Validate: player must have an open menu
                    AbstractContainerMenu menu = player.containerMenu;
                    if (menu == null) {
                        LOGGER.warn("[InventoryPlus] Move matching request from {} but no menu open",
                                player.getName().getString());
                        return;
                    }

                    // Resolve both region groups from the player's current menu.
                    // Returns null if either group doesn't exist (invalid packet,
                    // stale client state, or the menu doesn't have those regions).
                    MKRegionGroup source = MKRegionRegistry.getGroup(menu, sourceGroupName);
                    MKRegionGroup dest = MKRegionRegistry.getGroup(menu, destGroupName);

                    if (source == null) {
                        LOGGER.debug("[InventoryPlus] Move matching: source group '{}' not found for {}",
                                sourceGroupName, player.getName().getString());
                        return;
                    }
                    if (dest == null) {
                        LOGGER.debug("[InventoryPlus] Move matching: dest group '{}' not found for {}",
                                destGroupName, player.getName().getString());
                        return;
                    }

                    // Supplement the source group with any other sortable regions
                    // that exist in the menu but aren't in either the source or
                    // dest group. This ensures dynamically-registered containers
                    // (e.g., peek) are included as sources when moving items.
                    // A region is "sortable" if it has SIMPLE type and >= 4 slots.
                    List<MKRegion> sourceRegions = new ArrayList<>(source.regions());
                    for (MKRegion r : MKRegionRegistry.getRegions(menu)) {
                        if (r.containerType() == MKContainerType.SIMPLE
                                && r.size() >= 4
                                && !source.contains(r.name())
                                && !dest.contains(r.name())) {
                            sourceRegions.add(r);
                        }
                    }
                    source = new MKRegionGroup(source.name(), sourceRegions);

                    // If the "Include Hotbar" setting is off, filter out the
                    // hotbar region from the source so only main inventory
                    // (slots 9-35) and pockets are used as item sources.
                    if (!includeHotbar) {
                        List<MKRegion> filtered = new ArrayList<>();
                        for (MKRegion r : source.regions()) {
                            if (!r.name().equals("mk:hotbar")) {
                                filtered.add(r);
                            }
                        }
                        source = new MKRegionGroup(source.name(), filtered);
                    }

                    // Perform the move. When a specific destination region is
                    // given, use direct transfer with ALL other SIMPLE regions
                    // as sources — not just the source group. This matches the
                    // user's mental model: "pull matching items from everywhere
                    // else into THIS container."
                    int moved;
                    MKRegion targetRegion = destRegionName.isEmpty()
                            ? null : MKRegionRegistry.getRegion(menu, destRegionName);
                    if (targetRegion != null) {
                        // Build source from ALL SIMPLE regions except the target.
                        // Hotbar is excluded — it's not SIMPLE type and users
                        // don't expect hotbar items to be pulled by move matching.
                        List<MKRegion> allSources = new ArrayList<>();
                        for (MKRegion r : MKRegionRegistry.getRegions(menu)) {
                            if (r.name().equals(destRegionName)) continue;
                            if (r.containerType() != MKContainerType.SIMPLE) continue;
                            allSources.add(r);
                        }
                        MKRegionGroup allSourceGroup = new MKRegionGroup("_all_sources", allSources);
                        moved = MKMoveMatching.moveMatchingDirect(
                                menu, player, allSourceGroup, dest, targetRegion);
                    } else {
                        moved = MKMoveMatching.moveMatching(menu, player, source, dest);
                    }
                    // broadcastChanges() fires on the next server tick
                    LOGGER.debug("[InventoryPlus] Move matching: {} items from '{}' -> '{}' ({}) for {}",
                            moved, sourceGroupName, destGroupName,
                            destRegionName.isEmpty() ? "quickMoveStack" : destRegionName,
                            player.getName().getString());
                });
    }

    // ── Container Region Group Registration ─────────────────────────────

    /**
     * Registers region groups for container-based screens and the extended
     * player inventory (including pockets).
     *
     * <p>These groups are resolved at menu construction time — only members
     * whose regions actually exist in the menu are included. So listing
     * mk:chest, mk:shulker, mk:hopper, and mk:dispenser in one group is
     * safe: only the relevant one resolves for any given screen.
     *
     * <p>"container_storage" is used as the destination for Move Matching.
     * "player_extended" includes pockets alongside the base player storage,
     * giving Move Matching access to pocket items as sources.
     */
    private static void registerContainerGroups() {
        // container_storage: whichever container is open (chest, shulker, hopper, etc.)
        // All four region names are listed, but only one will resolve per menu.
        // peek is included so move-matching works when peeking into a
        // shulker box — only the peek region resolves when peek is open,
        // the others resolve for their respective container screens.
        MenuKit.regionGroup("container_storage")
                .region("mk:chest", 1)
                .region("mk:shulker", 1)
                .region("mk:hopper", 1)
                .region("mk:dispenser", 1)
                .region(ContainerPeek.SHULKER, 1)
                .region(ContainerPeek.ENDER, 1)
                .region(ContainerPeek.BUNDLE, 1)
                .register();

        // player_extended: hotbar + main inventory + all 9 pocket containers.
        // Pockets have lower fill priority (3) so items are drawn from pockets
        // last when used as a source. This group gives Move Matching access
        // to items stored in pockets, not just the visible inventory.
        var builder = MenuKit.regionGroup("player_extended")
                .region(MenuKit.PANEL_HOTBAR, 1)
                .region(MenuKit.PANEL_MAIN_INVENTORY, 2);
        for (int i = 0; i < 9; i++) {
            builder.region("pocket_" + i, 3);
        }
        builder.register();
    }
}
