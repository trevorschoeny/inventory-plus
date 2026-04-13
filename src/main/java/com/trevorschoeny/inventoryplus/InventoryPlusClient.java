package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.menukit.config.GeneralOption;
import com.trevorschoeny.menukit.widget.MKButton;
import com.trevorschoeny.menukit.widget.MKButtonDef;
import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.event.MKEvent;
import com.trevorschoeny.menukit.event.MKEventResult;
import com.trevorschoeny.menukit.config.MKFamily;
import com.trevorschoeny.menukit.panel.MKGroupChild;
import com.trevorschoeny.menukit.MKItemTips;
import com.trevorschoeny.menukit.input.MKKeybind;
import com.trevorschoeny.menukit.input.MKKeybindController;
import com.trevorschoeny.menukit.input.MKKeybindSync;
import com.trevorschoeny.menukit.input.MKKeybindExt;
import com.trevorschoeny.menukit.panel.MKPanel;
import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.region.MKRegionGroup;
import com.trevorschoeny.menukit.region.MKRegionRegistry;
import com.trevorschoeny.menukit.event.MKSlotEvent;
import com.trevorschoeny.menukit.widget.MKSlotState;
import com.trevorschoeny.menukit.widget.MKSlotStateRegistry;
import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.inventoryplus.network.BulkMoveC2SPayload;
import com.trevorschoeny.inventoryplus.network.MoveMatchingC2SPayload;
import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.inventoryplus.network.SortC2SPayload;
import com.trevorschoeny.inventoryplus.network.SortLockC2SPayload;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side entry point for Inventory Plus.
 *
 * <p>Joins the "trevmods" family for unified config and keybind grouping,
 * then registers keybinds, HUD, and config categories.
 */
public class InventoryPlusClient implements ClientModInitializer {

    // Typed descriptor for the family-wide "show settings button" toggle.
    // Defined once, referenced wherever this option is read or written.
    static final GeneralOption<Boolean> SHOW_SETTINGS_BUTTON =
            new GeneralOption<>("show_settings_button", true, Boolean.class);

    // Typed descriptor for the family-wide "auto restock" toggle.
    // Checked server-side in IPAutoRestockMixin each tick. Defined here so
    // we can register the YACL option alongside the other general options.
    static final GeneralOption<Boolean> AUTO_RESTOCK =
            new GeneralOption<>("auto_restock", true, Boolean.class);

    // Typed descriptor for the family-wide "auto replace tools" toggle.
    // Checked server-side in IPAutoReplaceMixin when a tool breaks.
    static final GeneralOption<Boolean> AUTO_REPLACE_TOOLS =
            new GeneralOption<>("auto_replace_tools", true, Boolean.class);

    // Typed descriptor for the family-wide "autofill enabled" toggle.
    // Checked on the client before sending the C2S packet — the server
    // always executes if it receives the packet (client is the gatekeeper).
    static final GeneralOption<Boolean> AUTOFILL_ENABLED =
            new GeneralOption<>("autofill_enabled", true, Boolean.class);

    // Typed descriptor for the family-wide "deep arrow search" toggle.
    // Checked server-side in IPDeepArrowMixin when getProjectile() is called.
    // When enabled, bows and crossbows search bundles, shulker boxes, and
    // the ender chest for arrows when none are loose in the inventory.
    static final GeneralOption<Boolean> DEEP_ARROW_SEARCH =
            new GeneralOption<>("deep_arrow_search", true, Boolean.class);

    // Sort keybind — default unbound, user configures in YACL settings.
    // Uses vanilla KeyMapping + MKKeybindExt duck interface for multi-key combos.
    private static KeyMapping sortRegionKey;

    // Move Matching keybind — default unbound, user configures in YACL settings.
    // When pressed while hovering a slot, moves ALL items of that type
    // out of the hovered region (reuses BulkMoveC2SPayload).
    private static KeyMapping moveMatchingKey;

    // Lock Slot keybind — default unbound. Press while hovering a slot to
    // toggle sort-lock. Sort-locked slots are excluded from sorting and
    // shift-click-in, but can still be interacted with normally.
    private static KeyMapping lockSlotKey;

    // Container Peek keybind — default Left Alt. Press while hovering a
    // bundle, shulker box, or ender chest in ANY container screen to open
    // its peek panel. Replaces the old right-click trigger so right-click
    // can return to vanilla bundle extraction and Easy Shulker Boxes.
    private static KeyMapping peekContainerKey;

    /** Returns the lock slot key mapping so other code can check if it's held. */
    public static KeyMapping getLockSlotKey() { return lockSlotKey; }

    @Override
    public void onInitializeClient() {
        // Join the trevmods family — creates it if we're the first mod loaded
        MKFamily family = MenuKit.family("trevmods")
                .displayName("Trev's Mods")
                .description("Quality-of-life mods for creative builders.")
                .modId(InventoryPlus.MOD_ID);

        // Get the shared keybind category from the family
        KeyMapping.Category category = family.getKeybindCategory();

        // Load config so keybind values are available for KeyMapping creation
        InventoryPlusConfig cfg = InventoryPlusConfig.get();

        // Pocket cycling keybinds (left/right arrow by default, configurable)
        PocketCycler.registerKeybinds(category, cfg);

        // Sort Region keybind — default unbound. When pressed while hovering
        // a slot in an inventory screen, sends a C2S packet to sort the region
        // that slot belongs to. Created via MKKeybindExt.fromKeybind so the
        // multi-key combo is applied via the duck interface.
        sortRegionKey = KeyBindingHelper.registerKeyBinding(
                MKKeybindExt.fromKeybind(cfg.sortKeybind,
                        "key.inventory-plus.sort_region", category));

        // Move Matching Items keybind — default unbound. When pressed while
        // hovering a slot, moves ALL items of the same type out of that region.
        // Reuses the BulkMoveC2SPayload so no new server-side code is needed.
        moveMatchingKey = KeyBindingHelper.registerKeyBinding(
                MKKeybindExt.fromKeybind(cfg.moveMatchingKeybind,
                        "key.inventory-plus.move_matching", category));

        // Lock Slot keybind — default unbound. Press while hovering a slot to
        // toggle sort-lock. Sort-locked slots skip sorting and shift-click-in.
        lockSlotKey = KeyBindingHelper.registerKeyBinding(
                MKKeybindExt.fromKeybind(cfg.lockSlotKeybind,
                        "key.inventory-plus.lock_slot", category));

        // Container Peek keybind — default Left Alt. When pressed while
        // hovering a peekable item (bundle / shulker box / ender chest) in
        // any container screen, sends a C2S peek request. Replaces the old
        // right-click trigger that clashed with vanilla bundle extraction
        // and Easy Shulker Boxes.
        peekContainerKey = KeyBindingHelper.registerKeyBinding(
                MKKeybindExt.fromKeybind(cfg.peekKeybind,
                        "key.inventory-plus.peek_container", category));

        // Register Controls → config sync for all IP keybinds. When the user
        // changes a keybind in the vanilla Controls screen and closes it, these
        // callbacks write the new combo back to config and persist to disk.
        // Without this, keybind changes from Controls are lost on restart.
        MKKeybindSync.register(sortRegionKey, combo -> {
            InventoryPlusConfig.get().sortKeybind = combo;
            InventoryPlusConfig.save();
        });
        MKKeybindSync.register(moveMatchingKey, combo -> {
            InventoryPlusConfig.get().moveMatchingKeybind = combo;
            InventoryPlusConfig.save();
        });
        MKKeybindSync.register(lockSlotKey, combo -> {
            InventoryPlusConfig.get().lockSlotKeybind = combo;
            InventoryPlusConfig.save();
        });
        MKKeybindSync.register(peekContainerKey, combo -> {
            InventoryPlusConfig.get().peekKeybind = combo;
            InventoryPlusConfig.save();
        });
        PocketCycler.registerKeybindSync();

        // Sort Region — KEY_PRESS handler via MenuKit event system.
        // Fires on every key press while hovering a slot. We check if the
        // pressed key matches our sort keybind, resolve the hovered slot's
        // region, and send the sort request to the server.
        // ALLOW phase: needs to cancel vanilla key handling when consumed.
        MenuKit.on(MKEvent.Type.KEY_PRESS)
                .allow()
                .slotHandler(event -> {
                    // Gate: sorting disabled in config — skip entirely
                    if (!InventoryPlusConfig.get().enableSorting) return MKEventResult.PASS;

                    // Check if the pressed key matches the sort keybind.
                    // Uses MKKeybindExt.matchesEvent() instead of isDown() because
                    // vanilla does NOT update KeyMapping state when a screen is open —
                    // key events go through Screen.keyPressed(), not the GLFW
                    // callback that drives KeyMapping.set(). matchesEvent() checks
                    // the full multi-key combo via GLFW polling.
                    if (sortRegionKey.isUnbound()
                            || !MKKeybindExt.matchesEvent(sortRegionKey, event.getKeyCode(), event.getModifiers())) {
                        return MKEventResult.PASS;
                    }

                    // Resolve which region the hovered slot belongs to.
                    // If the slot isn't in a region (e.g., hovering a crafting
                    // output slot with no region), there's nothing to sort.
                    MKRegion region = event.getRegion();
                    if (region == null) {
                        return MKEventResult.PASS;
                    }

                    // Send the sort request to the server. The server validates
                    // the region, performs the sort, and broadcastChanges syncs
                    // the result back to the client automatically.
                    ClientPlayNetworking.send(new SortC2SPayload(region.name()));

                    // CONSUMED prevents vanilla from processing this key press
                    // (e.g., if the user binds sort to a number key, we don't
                    // want it to also swap hotbar slots).
                    return MKEventResult.CONSUMED;
                });

        // Move Matching Items — KEY_PRESS handler via MenuKit event system.
        // Fires on every key press while hovering a slot. When the move-matching
        // keybind is pressed, moves items from OTHER open regions into the hovered
        // region — same behavior as clicking the move-matching button.
        // ALLOW phase: needs to cancel vanilla key handling when consumed.
        MenuKit.on(MKEvent.Type.KEY_PRESS)
                .allow()
                .slotHandler(event -> {
                    // Gate: sorting disabled in config — move-matching is part of the sorting feature
                    if (!InventoryPlusConfig.get().enableSorting) return MKEventResult.PASS;

                    // Check if the pressed key matches the move-matching keybind.
                    if (moveMatchingKey.isUnbound()
                            || !MKKeybindExt.matchesEvent(moveMatchingKey, event.getKeyCode(), event.getModifiers())) {
                        return MKEventResult.PASS;
                    }

                    // Need a destination region — the region the cursor is hovering.
                    // Items from other regions will be moved INTO this one.
                    MKRegion region = event.getRegion();
                    if (region == null) {
                        return MKEventResult.PASS;
                    }

                    // Delegate to the same logic as the move-matching button click.
                    // Resolves source/dest groups and sends MoveMatchingC2SPayload.
                    onMoveMatchingClick(region.name());

                    return MKEventResult.CONSUMED;
                });

        // Lock Slot — KEY_PRESS handler via MenuKit event system.
        // When the lock keybind is pressed while hovering a slot, we toggle
        // that slot's sort-lock state. Sort-locked slots are excluded from
        // sorting and from receiving shift-clicked items, but can still be
        // interacted with normally (pick up, place, etc.). This is separate
        // from the Ctrl+click full lock in MKSlotClickBusMixin which blocks
        // ALL interactions.
        // ALLOW phase: needs to cancel vanilla key handling when consumed.
        MenuKit.on(MKEvent.Type.KEY_PRESS)
                .allow()
                .slotHandler(event -> {
                    // Gate: lock keybind not configured
                    if (lockSlotKey.isUnbound()) return MKEventResult.PASS;

                    // Check if the pressed key matches the lock keybind.
                    // Uses MKKeybindExt.matchesEvent() because vanilla doesn't
                    // update KeyMapping state when a screen is open.
                    if (!MKKeybindExt.matchesEvent(lockSlotKey, event.getKeyCode(), event.getModifiers())) {
                        return MKEventResult.PASS;
                    }

                    // Need a slot under the cursor to lock
                    if (event.getSlot() == null) return MKEventResult.PASS;

                    // Toggle the slot's sort-lock state (not the full lock).
                    // We toggle client-side for immediate visual feedback (overlay),
                    // then send a C2S packet so the server's MKSlotStateRegistry
                    // has the same state — sorting and shift-click routing run
                    // server-side and need to see the lock.
                    MKSlotState lockState = MKSlotStateRegistry.getOrCreate(event.getSlot());
                    lockState.toggleSortLocked();

                    // Sync to server — use getMenuSlotIndex() for creative tabs safety
                    int serverSlotIndex = event.getMenuSlotIndex();
                    if (serverSlotIndex < 0) return MKEventResult.PASS;
                    ClientPlayNetworking.send(
                            new SortLockC2SPayload(serverSlotIndex, lockState.isSortLocked()));

                    InventoryPlus.LOGGER.debug("[InventoryPlus] Sort-lock toggled: slot {} -> {}",
                            serverSlotIndex, lockState.isSortLocked());

                    // Audio feedback — subtle click so the player knows it toggled
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

                    // CONSUMED prevents vanilla from processing this key press
                    return MKEventResult.CONSUMED;
                });

        // Pocket HUD overlay (shows pocket items next to hotbar)
        PocketHud.register();

        // Register sort/move button attachment BEFORE panels, so build() can inject
        registerSortAttachment();

        // Container Peek — client-side panel + packet handler
        ContainerPeekClient.registerPanel();
        ContainerPeekClient.registerClientHandler();
        ContainerPeekClient.registerCloseHandler();

        // Container Peek — KEY_PRESS handler via MenuKit event system.
        // Fires on every key press while hovering a slot. Works on ANY slot
        // in ANY container screen (player inventory, chests, hoppers, modded
        // containers, etc.) — uses raw menu slot index for addressing.
        //
        // This REPLACES the old right-click-to-peek trigger. Right-click now
        // falls through to vanilla so bundle extraction and other mods like
        // Easy Shulker Boxes work as intended.
        //
        // ALLOW phase: needs to cancel vanilla key handling when consumed.
        MenuKit.on(MKEvent.Type.KEY_PRESS)
                .allow()
                .slotHandler(event -> {
                    // Gate: peek keybind not configured — user chose to disable it.
                    if (peekContainerKey.isUnbound()) return MKEventResult.PASS;

                    // Check if the pressed key matches the peek keybind.
                    // matchesEvent() checks the full multi-key combo via GLFW
                    // polling because vanilla doesn't update KeyMapping.isDown()
                    // while a screen is open (key events go through Screen.keyPressed,
                    // not the GLFW callback that drives KeyMapping.set()).
                    if (!MKKeybindExt.matchesEvent(peekContainerKey,
                            event.getKeyCode(), event.getModifiers())) {
                        return MKEventResult.PASS;
                    }

                    // Only peek peekable items (bundle / shulker / ender chest),
                    // gated individually by the three enablePeek* config toggles
                    // inside ContainerPeek.isPeekable().
                    if (!ContainerPeek.isPeekable(event.getSlotStack())) {
                        return MKEventResult.PASS;
                    }

                    // Skip while the cursor is dragging an item. Peeking mid-drag
                    // would be disorienting — and the user probably meant to drop
                    // the cursor stack into the hovered slot, not peek it.
                    var mc = Minecraft.getInstance();
                    if (mc.player == null
                            || mc.player.containerMenu == null
                            || !mc.player.containerMenu.getCarried().isEmpty()) {
                        return MKEventResult.PASS;
                    }

                    // Resolve the server-safe menu slot index. getMenuSlotIndex()
                    // handles creative tabs, where the client's ItemPickerMenu has
                    // different indices than the server's InventoryMenu — returns
                    // -1 for client-only fake slots so we don't send bogus indices.
                    int menuSlotIndex = event.getMenuSlotIndex();
                    if (menuSlotIndex < 0) return MKEventResult.PASS;

                    // Toggle: if the hovered slot is already the one being peeked,
                    // close the peek. Otherwise (new item, or switch-on-move)
                    // open a peek at the new slot — the server's handlePeekRequest
                    // unbinds any previous source before binding the new one, so
                    // switch-on-move is handled server-side automatically.
                    if (ContainerPeekClient.getPeekedSlot() == menuSlotIndex) {
                        ClientPlayNetworking.send(new PeekC2SPayload(-1));
                    } else {
                        ClientPlayNetworking.send(new PeekC2SPayload(menuSlotIndex));
                    }

                    // CONSUMED cancels vanilla key handling. Important when peek
                    // is bound to something like 'E' — we don't want the peek
                    // keybind to also close the inventory screen.
                    return MKEventResult.CONSUMED;
                });

        // Bulk Move — Shift+double-click handler via MenuKit event system.
        // When a player Shift+double-clicks a slot, we move ALL matching items
        // from that region to wherever shift-click normally routes them (the
        // "opposite" region). This is a power-user shortcut: instead of
        // shift-clicking 27 stacks of cobblestone one by one, one gesture
        // moves them all at once.
        //
        // Without Shift held, vanilla's double-click (collect-to-cursor)
        // proceeds normally — we only intercept when Shift is active.
        // ALLOW phase: needs to cancel vanilla double-click collect when consumed.
        MenuKit.on(MKEvent.Type.DOUBLE_CLICK)
                .allow()
                .slotHandler(event -> {
                    // Only activate when Shift is held. The modifiers bitfield
                    // is sampled from GLFW at event construction time, so
                    // isShiftPressed() reflects the real key state at click time.
                    if (!event.isShiftPressed()) {
                        return MKEventResult.PASS;
                    }

                    // Need an item to match against — empty slot means nothing to move
                    ItemStack slotStack = event.getSlotStack();
                    if (slotStack.isEmpty()) {
                        return MKEventResult.PASS;
                    }

                    // Need a source region to iterate over. Slots outside any
                    // region (e.g., crafting output) don't participate in bulk move.
                    MKRegion region = event.getRegion();
                    if (region == null) {
                        return MKEventResult.PASS;
                    }

                    // Resolve the item's registry ID (e.g., "minecraft:cobblestone").
                    // This is what the server uses to match items — comparing by
                    // registry ID is simpler and safer than serializing full ItemStacks
                    // across the network (no NBT edge cases).
                    String itemId = BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString();

                    // Send the bulk-move request to the server. The server will
                    // iterate the region's slots and quickMoveStack each matching
                    // item. broadcastChanges() syncs the results automatically.
                    ClientPlayNetworking.send(new BulkMoveC2SPayload(region.name(), itemId));

                    // CONSUMED cancels vanilla's double-click collect-to-cursor,
                    // which would conflict with our bulk move.
                    return MKEventResult.CONSUMED;
                });

        // General option: toggle the settings gear button visibility
        family.generalOption(SHOW_SETTINGS_BUTTON,
                Option.<Boolean>createBuilder()
                        .name(Component.literal("Show Settings Button"))
                        .description(OptionDescription.of(
                                Component.literal("Show the ⚙ button above the inventory screen.")))
                        .binding(true,
                                () -> family.getGeneral(SHOW_SETTINGS_BUTTON),
                                val -> family.setGeneral(SHOW_SETTINGS_BUTTON, val))
                        .controller(TickBoxControllerBuilder::create)
                        .build());

        // NOTE: The 4 IP-specific general options (autofill, auto-restock,
        // auto-replace tools, show item tips) are stored via family.getGeneral()
        // / family.setGeneral() but their UI lives in the Inventory Plus config
        // category tab — see buildConfigCategory(). Only SHOW_SETTINGS_BUTTON
        // stays in the family General tab because it's a family-wide UI toggle.

        // Register config category with the family (mod ID enables tab auto-focus).
        // We pass the family reference so buildConfigCategory can create YACL
        // bindings for general options (stored in the family config file) alongside
        // IP-specific options (stored in inventory-plus.json).
        family.configCategory(InventoryPlus.MOD_ID, "Inventory Plus",
                () -> buildConfigCategory(family),
                () -> {
                    // Persist config to disk
                    InventoryPlusConfig.save();
                    PocketsPanel.applyConfig();

                    // Sync YACL keybind values to runtime KeyMapping instances via
                    // the MKKeybindExt duck interface. This updates both the combo
                    // and the vanilla base key, then rebuilds vanilla's lookup table.
                    InventoryPlusConfig saved = InventoryPlusConfig.get();
                    MKKeybindExt.updateFromKeybind(sortRegionKey, saved.sortKeybind);
                    MKKeybindExt.updateFromKeybind(moveMatchingKey, saved.moveMatchingKeybind);
                    MKKeybindExt.updateFromKeybind(lockSlotKey, saved.lockSlotKeybind);
                    MKKeybindExt.updateFromKeybind(peekContainerKey, saved.peekKeybind);
                    PocketCycler.syncKeybinds(saved);
                });

        // Settings button — shared across the family, hidden when general option is off
        family.sharedPanel("trevmods_settings", () -> registerSettingsButton(family));
    }

    // ── Sort / Move Matching Button Attachment ──────────────────────────────

    /**
     * Registers a unified button attachment for sort + move matching.
     * One call handles ALL SIMPLE containers:
     * <ul>
     *   <li>MenuKit panels (peek, custom): buttons injected into tree at build time</li>
     *   <li>Vanilla panels (player inv, chests): overlay panels at resolution time</li>
     * </ul>
     *
     * <p>Must be called BEFORE any panels with SIMPLE SlotGroups are built.
     */
    private static void registerSortAttachment() {
        MenuKit.buttonAttachment("ip_sort")
                .forContainerType(MKContainerType.SIMPLE)
                .above()
                .gap(2)
                .overlayOffset(1, -2)
                .excludeRegion(name -> name.startsWith("pocket_"))
                .disabledWhen(() -> !InventoryPlusConfig.get().enableSorting
                        || !InventoryPlusConfig.get().showSortButton)
                .buttons(regionName -> java.util.List.of(
                        // Move matching button (left) — hides when < 2 SIMPLE containers
                        new MKGroupChild.Button(new MKButtonDef(
                                0, 0, 9, 9,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("inventory-plus",
                                        "move_matching"),
                                null, 9,
                                Component.empty(),
                                false, false, null,
                                btn -> onMoveMatchingClick(regionName),
                                null,
                                Component.literal("Move Matching Items Here"),
                                null, null, null,
                                MKButton.ButtonStyle.NONE,
                                false, () -> countOpenSimpleContainers() <= 1, null, null, null
                        ), "ip_move:" + regionName),
                        // Sort button (right)
                        new MKGroupChild.Button(new MKButtonDef(
                                0, 0, 9, 9,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("inventory-plus",
                                        "sort_items"),
                                null, 9,
                                Component.empty(),
                                false, false, null,
                                btn -> ClientPlayNetworking.send(new SortC2SPayload(regionName)),
                                null,
                                Component.literal("Sort Items"),
                                null, null, null,
                                MKButton.ButtonStyle.NONE,
                                false, null, null, null, null
                        ), "ip_sort:" + regionName)
                ))
                .register();
    }

    // ── Settings Button ────────────────────────────────────────────────────

    /**
     * Registers a small gear button above the personal inventory, right-aligned.
     * Opens the family's unified YACL config screen when clicked.
     */
    private static void registerSettingsButton(MKFamily family) {
        MKPanel.builder("trevmods_settings")
                .showIn(MKContext.PERSONAL)
                .posAboveRight()
                .autoSize()
                .style(MKPanel.Style.NONE)
                // Hide when the general option is toggled off
                .disabledWhen(() -> !family.getGeneral(SHOW_SETTINGS_BUTTON))
                .column()
                    .button()
                        .icon(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                "inventory-plus", "settings"))
                        .iconSize(11)
                        .size(11, 11)
                        .tooltip("TrevMod Settings")
                        .onClick(btn -> {
                            var mc = Minecraft.getInstance();
                            // Focus on the Inventory Plus tab specifically
                            var screen = family.buildConfigScreen(mc.screen,
                                    InventoryPlus.MOD_ID);
                            if (screen != null) mc.setScreen(screen);
                        })
                        .done()
                .build();
    }

    // ── Conditional Element Helpers ─────────────────────────────────────────

    /**
     * Handles the move-matching button click for a given region. Resolves
     * the source and destination groups at click time:
     * <ul>
     *   <li>Destination = the first group containing this region</li>
     *   <li>Source = the first group that does NOT contain this region
     *       but has at least one SIMPLE region (sortable storage)</li>
     * </ul>
     *
     * @param regionName the destination region (the one whose button was clicked)
     */
    static void onMoveMatchingClick(String regionName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Use the active menu (screen menu in creative, containerMenu otherwise)
        // so group lookups work on creative item tabs where regions are on the
        // ItemPickerMenu, not the InventoryMenu.
        var menu = MKRegionRegistry.getActiveMenu();
        if (menu == null) return;

        // Find the first group that contains this region (= destination).
        // Fall back to containerMenu if the active menu doesn't have this region
        // (e.g., peek regions live on containerMenu even when screen menu differs).
        List<MKRegionGroup> destGroups =
                MKRegionRegistry.getGroupsForRegion(menu, regionName);
        if (destGroups.isEmpty() && menu != mc.player.containerMenu) {
            menu = mc.player.containerMenu;
            destGroups = MKRegionRegistry.getGroupsForRegion(menu, regionName);
        }
        if (destGroups.isEmpty()) return;

        String destGroupName = destGroups.get(0).name();

        // Find the "opposite" group: a group that does NOT contain this region.
        // For chest regions, this finds player_storage. For player regions, this
        // finds container_storage. The source must have at least one SIMPLE
        // region (we don't want to pull from armor/crafting groups).
        List<MKRegionGroup> allGroups = MKRegionRegistry.getGroups(menu);
        String sourceGroupName = null;
        for (MKRegionGroup group : allGroups) {
            if (group.name().equals(destGroupName)) continue;

            boolean hasSortable = false;
            for (MKRegion r : group.regions()) {
                if (r.containerType() == MKContainerType.SIMPLE && r.size() >= 4) {
                    hasSortable = true;
                    break;
                }
            }
            if (hasSortable) {
                sourceGroupName = group.name();
                break;
            }
        }

        // Can't move matching without both a source and destination
        if (sourceGroupName == null) return;

        // Pass the specific region name so the server can direct items into
        // exactly this region (instead of relying on quickMoveStack routing,
        // which may target a different container in the same group).
        boolean includeHotbar = InventoryPlusConfig.get().includeHotbarInMoveMatching;
        ClientPlayNetworking.send(
                new MoveMatchingC2SPayload(sourceGroupName, destGroupName, regionName, includeHotbar));
    }

    /**
     * Counts how many SIMPLE-typed regions are currently resolved for the
     * player's active menu. Used by the move-matching conditional rule to
     * auto-disable when there's only one SIMPLE container (e.g., just the
     * player inventory with no chest/peek open) — move matching needs both
     * a source and destination.
     *
     * <p>Evaluated at render time via {@code disabledWhen()}, so the button
     * appears/disappears dynamically as containers open and close.
     *
     * @return the number of SIMPLE regions in the current menu, or 0 if
     *         the player or menu is unavailable
     */
    static int countOpenSimpleContainers() {
        // Delegates to MenuKit's general-purpose utility, which uses the
        // correct active menu (screen menu in creative, containerMenu otherwise).
        // Excludes pocket_ regions — those are internal and shouldn't count
        // toward "another container is open" for move-matching purposes.
        return MKRegionRegistry.countRegionsByType(MKContainerType.SIMPLE, "pocket_");
    }

    // ── Config ──────────────────────────────────────────────────────────────

    /**
     * Builds the YACL config category for Inventory Plus.
     * Called each time the config screen opens to read fresh values.
     *
     * <p>Contains both IP-specific options (stored in inventory-plus.json)
     * and general options (stored in menukit-family-trevmods.json) whose
     * UI belongs in the IP tab rather than the family General tab.
     *
     * @param family the family instance for reading/writing general options
     */
    static ConfigCategory buildConfigCategory(MKFamily family) {
        InventoryPlusConfig cfg = InventoryPlusConfig.get();

        return ConfigCategory.createBuilder()
                .name(Component.literal("Inventory Plus"))
                .tooltip(Component.literal("Inventory automation, sorting, equipment, and pockets"))

                // ── Item Movement group ─────────────────────────────────────
                // These options control automatic item movement behaviors.
                // Storage uses family general config (menukit-family-trevmods.json)
                // so server-side mixins can read them without depending on IP config.
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Item Movement"))
                        .description(OptionDescription.of(Component.literal(
                                "Automatic item movement and inventory management features.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Auto-Fill"))
                                .description(OptionDescription.of(Component.literal(
                                        "Items you pick up will automatically fill shulker boxes " +
                                        "and bundles in your inventory with that item.")))
                                .binding(true,
                                        () -> family.getGeneral(AUTOFILL_ENABLED),
                                        val -> family.setGeneral(AUTOFILL_ENABLED, val))
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Auto-Restock"))
                                .description(OptionDescription.of(Component.literal(
                                        "Automatically refill hotbar slots when the last item is used. " +
                                        "Pulls matching items from your inventory, including from " +
                                        "shulker boxes and bundles.")))
                                .binding(true,
                                        () -> family.getGeneral(AUTO_RESTOCK),
                                        val -> family.setGeneral(AUTO_RESTOCK, val))
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Auto-Replace Tools"))
                                .description(OptionDescription.of(Component.literal(
                                        "Automatically equip a replacement tool from your inventory " +
                                        "when your current one breaks.")))
                                .binding(true,
                                        () -> family.getGeneral(AUTO_REPLACE_TOOLS),
                                        val -> family.setGeneral(AUTO_REPLACE_TOOLS, val))
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Deep Arrow Search"))
                                .description(OptionDescription.of(Component.literal(
                                        "Search bundles, shulker boxes, and ender chests for arrows " +
                                        "when shooting a bow or crossbow. Arrows in your inventory " +
                                        "are always prioritized first.")))
                                .binding(true,
                                        () -> family.getGeneral(DEEP_ARROW_SEARCH),
                                        val -> family.setGeneral(DEEP_ARROW_SEARCH, val))
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Show Item Tips"))
                                .description(OptionDescription.of(Component.literal(
                                        "Show extra info in item tooltips.")))
                                .binding(true,
                                        () -> family.getGeneral(MKItemTips.SHOW_ITEM_TIPS),
                                        val -> family.setGeneral(MKItemTips.SHOW_ITEM_TIPS, val))
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Include Hotbar"))
                                .description(OptionDescription.of(Component.literal(
                                        "Include hotbar items when moving matching items " +
                                        "from your inventory.")))
                                .binding(true,
                                        () -> cfg.includeHotbarInMoveMatching,
                                        val -> cfg.includeHotbarInMoveMatching = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Move Matching keybind — inline capture via MKKeybindController.
                        .option(Option.<MKKeybind>createBuilder()
                                .name(Component.literal("Move Matching"))
                                .description(OptionDescription.of(Component.literal(
                                        "Hover a slot and press this keybind to move all matching items " +
                                        "out of that region. Click to set a key, Delete to clear.")))
                                .binding(MKKeybind.UNBOUND,
                                        () -> cfg.moveMatchingKeybind,
                                        val -> cfg.moveMatchingKeybind = val)
                                .customController(opt -> new MKKeybindController(opt, moveMatchingKey))
                                .build())
                        // Lock Slot keybind — inline capture via MKKeybindController.
                        .option(Option.<MKKeybind>createBuilder()
                                .name(Component.literal("Lock Slot"))
                                .description(OptionDescription.of(Component.literal(
                                        "Press while hovering a slot to lock/unlock it. Locked slots " +
                                        "are excluded from sorting and shift-click-in, but can still " +
                                        "be interacted with normally. Click to set a key, Delete to clear.")))
                                .binding(MKKeybind.UNBOUND,
                                        () -> cfg.lockSlotKeybind,
                                        val -> cfg.lockSlotKeybind = val)
                                .customController(opt -> new MKKeybindController(opt, lockSlotKey))
                                .build())
                        .build())

                // ── Sorting group ───────────────────────────────────────────
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Sorting"))
                        .description(OptionDescription.of(Component.literal(
                                "Container and inventory sorting options.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Sorting"))
                                .description(OptionDescription.of(Component.literal(
                                        "Master toggle for sorting. When off, the sort keybind " +
                                        "does nothing and the sort button is hidden.")))
                                .binding(true, () -> cfg.enableSorting, val -> cfg.enableSorting = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Sort Region keybind — inline capture via MKKeybindController.
                        // Click the option to enter capture mode, then press any key
                        // (with optional modifiers) to set the binding. Supports Ctrl+K,
                        // Shift+F5, etc. Delete/Backspace clears to unbound.
                        .option(Option.<MKKeybind>createBuilder()
                                .name(Component.literal("Sort Region"))
                                .description(OptionDescription.of(Component.literal(
                                        "Hover over a container and press this keybind to sort items. " +
                                        "Click to set a key, Delete to clear.")))
                                .binding(MKKeybind.UNBOUND,
                                        () -> cfg.sortKeybind,
                                        val -> cfg.sortKeybind = val)
                                .customController(opt -> new MKKeybindController(opt, sortRegionKey))
                                .build())
                        .option(Option.<SortMethod>createBuilder()
                                .name(Component.literal("Sorting Method"))
                                .description(OptionDescription.of(Component.literal(
                                        "How items are ordered after sorting. 'Sort by Most Items' " +
                                        "groups your bulk materials first, then sorts by ID. " +
                                        "'Sort by ID' sorts alphabetically by Minecraft registry ID.")))
                                .binding(SortMethod.MOST_ITEMS,
                                        () -> cfg.sortMethod,
                                        val -> cfg.sortMethod = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(SortMethod.class)
                                        .formatValue(v -> Component.literal(v.displayName())))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Show Sort Button"))
                                .description(OptionDescription.of(Component.literal(
                                        "Show a sort button in inventory screens. Sorting still " +
                                        "works via keybind regardless of this setting.")))
                                .binding(true, () -> cfg.showSortButton, val -> cfg.showSortButton = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())

                // ── Equipment group ─────────────────────────────────────────
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Equipment"))
                        .description(OptionDescription.of(Component.literal(
                                "Passive equipment slots that work without being held or worn.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Elytra Slot"))
                                .description(OptionDescription.of(Component.literal(
                                        "Adds a passive elytra equipment slot to the inventory. " +
                                        "An elytra placed here grants flight without using the chest armor slot, " +
                                        "so you can wear armor and fly at the same time.")))
                                .binding(true, () -> cfg.enableElytraSlot, val -> cfg.enableElytraSlot = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Totem Slot"))
                                .description(OptionDescription.of(Component.literal(
                                        "Adds a passive totem equipment slot to the inventory. " +
                                        "A Totem of Undying placed here saves you from death without " +
                                        "needing to hold it in your hand.")))
                                .binding(true, () -> cfg.enableTotemSlot, val -> cfg.enableTotemSlot = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Inventory-Wide Mending"))
                                .description(OptionDescription.of(Component.literal(
                                        "When enabled, XP mending applies to ANY mending-enchanted " +
                                        "item anywhere in your inventory — unheld hotbar slots, main " +
                                        "inventory, and pockets — not just the held/armor/equipment " +
                                        "slots. Vanilla priority is still respected: hand, offhand, " +
                                        "armor, and equipment slots get repaired first, and only " +
                                        "leftover XP falls through to the rest of your inventory.")))
                                .binding(false, () -> cfg.mendingInventoryWide, val -> cfg.mendingInventoryWide = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())

                // ── Container Peek group ────────────────────────────────────
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Container Peek"))
                        .description(OptionDescription.of(Component.literal(
                                "Hover an item in any container screen and press the " +
                                "Peek keybind to view its contents.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Peek Shulker Boxes"))
                                .description(OptionDescription.of(Component.literal(
                                        "Allow the peek keybind to open shulker box contents.")))
                                .binding(true, () -> cfg.enablePeekShulker, val -> cfg.enablePeekShulker = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Peek Bundles"))
                                .description(OptionDescription.of(Component.literal(
                                        "Allow the peek keybind to open bundle contents.")))
                                .binding(true, () -> cfg.enablePeekBundle, val -> cfg.enablePeekBundle = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Peek Ender Chests"))
                                .description(OptionDescription.of(Component.literal(
                                        "Allow the peek keybind to open your ender chest inventory.")))
                                .binding(true, () -> cfg.enablePeekEnderChest, val -> cfg.enablePeekEnderChest = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Peek Container keybind — inline capture via MKKeybindController.
                        // Click the option to enter capture mode, then press any key
                        // (with optional modifiers) to set the binding. Delete/Backspace
                        // clears to unbound; if unbound, peek is unreachable until rebind.
                        .option(Option.<MKKeybind>createBuilder()
                                .name(Component.literal("Peek Container"))
                                .description(OptionDescription.of(Component.literal(
                                        "Hover a bundle, shulker box, or ender chest in any " +
                                        "container screen and press this keybind to open its " +
                                        "peek panel. Press again on the same item to close, or " +
                                        "on a different peekable to switch. Click to set a key, " +
                                        "Delete to clear.")))
                                .binding(MKKeybind.ofKeyAndModifiers(
                                                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, 0, 0),
                                        () -> cfg.peekKeybind,
                                        val -> cfg.peekKeybind = val)
                                .customController(opt -> new MKKeybindController(opt, peekContainerKey))
                                .build())
                        .build())

                // ── Pockets group ───────────────────────────────────────────
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Pockets"))
                        .description(OptionDescription.of(Component.literal(
                                "Extra storage slots behind each hotbar position, cycled with keybinds.")))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Pockets"))
                                .description(OptionDescription.of(Component.literal(
                                        "Adds 3 extra storage slots behind each hotbar position. " +
                                        "Cycle items in and out of the hotbar with left/right arrow keys. " +
                                        "Disabling hides all pocket UI — items are preserved and return " +
                                        "when re-enabled.")))
                                .binding(true, () -> cfg.enablePockets, val -> cfg.enablePockets = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Slots per Pocket"))
                                .description(OptionDescription.of(Component.literal(
                                        "How many extra slots each hotbar position gets (1-3). " +
                                        "Slots beyond this count are hidden and excluded from cycling. " +
                                        "Items in hidden slots are preserved.")))
                                .binding(3, () -> cfg.pocketSlotCount, val -> cfg.pocketSlotCount = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 3)
                                        .step(1))
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Show Pocket HUD"))
                                .description(OptionDescription.of(Component.literal(
                                        "Shows a small preview of pocket contents above the hotbar. " +
                                        "Disabling only hides the overlay — cycling still works.")))
                                .binding(true, () -> cfg.showPocketHud, val -> cfg.showPocketHud = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        // Pocket cycle keybinds — inline capture via MKKeybindController.
                        .option(Option.<MKKeybind>createBuilder()
                                .name(Component.literal("Cycle Right"))
                                .description(OptionDescription.of(Component.literal(
                                        "Keybind to cycle the selected hotbar slot's pocket forward. " +
                                        "Click to set a key, Delete to clear.")))
                                .binding(MKKeybind.ofKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT),
                                        () -> cfg.pocketCycleRightKeybind,
                                        val -> cfg.pocketCycleRightKeybind = val)
                                .customController(opt -> new MKKeybindController(opt, PocketCycler.getCycleRightKey()))
                                .build())
                        .option(Option.<MKKeybind>createBuilder()
                                .name(Component.literal("Cycle Left"))
                                .description(OptionDescription.of(Component.literal(
                                        "Keybind to cycle the selected hotbar slot's pocket backward. " +
                                        "Click to set a key, Delete to clear.")))
                                .binding(MKKeybind.ofKey(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT),
                                        () -> cfg.pocketCycleLeftKeybind,
                                        val -> cfg.pocketCycleLeftKeybind = val)
                                .customController(opt -> new MKKeybindController(opt, PocketCycler.getCycleLeftKey()))
                                .build())
                        .build())

                .build();
    }
}
