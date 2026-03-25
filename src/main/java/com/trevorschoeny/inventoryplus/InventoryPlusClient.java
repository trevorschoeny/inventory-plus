package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.menukit.GeneralOption;
import com.trevorschoeny.menukit.MKEvent;
import com.trevorschoeny.menukit.MKEventResult;
import com.trevorschoeny.menukit.MKFamily;
import com.trevorschoeny.menukit.MKItemTips;
import com.trevorschoeny.menukit.MKRegion;
import com.trevorschoeny.menukit.MKSlotEvent;
import com.trevorschoeny.menukit.MenuKit;
import com.trevorschoeny.inventoryplus.network.AutoFillC2SPayload;
import com.trevorschoeny.inventoryplus.network.BulkMoveC2SPayload;
import com.trevorschoeny.inventoryplus.network.PeekC2SPayload;
import com.trevorschoeny.inventoryplus.network.SortC2SPayload;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKPanel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

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

    // Sort keybind — default unbound, user assigns in Controls.
    // Accessible from the KEY_PRESS handler closure.
    private static KeyMapping sortRegionKey;

    // Autofill keybind — default unbound, user assigns in Controls.
    // Works from gameplay (no inventory screen needed).
    private static KeyMapping autoFillKey;

    // Move Matching keybind — default unbound, user assigns in Controls.
    // When pressed while hovering a slot, moves ALL items of that type
    // out of the hovered region (reuses BulkMoveC2SPayload).
    private static KeyMapping moveMatchingKey;

    @Override
    public void onInitializeClient() {
        // Join the trevmods family — creates it if we're the first mod loaded
        MKFamily family = MenuKit.family("trevmods")
                .displayName("Trev's Mods")
                .description("Quality-of-life mods for creative builders.")
                .modId(InventoryPlus.MOD_ID);

        // Get the shared keybind category from the family
        KeyMapping.Category category = family.getKeybindCategory();

        // Pocket cycling keybinds (left/right arrow)
        PocketCycler.registerKeybinds(category);

        // Sort Region keybind — default unbound. When pressed while hovering
        // a slot in an inventory screen, sends a C2S packet to sort the region
        // that slot belongs to.
        sortRegionKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trevs-mod.sort_region",
                GLFW.GLFW_KEY_UNKNOWN,  // unbound by default — user assigns in Controls
                category
        ));

        // Move Matching Items keybind — default unbound. When pressed while
        // hovering a slot, moves ALL items of the same type out of that region.
        // Reuses the BulkMoveC2SPayload so no new server-side code is needed.
        moveMatchingKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trevs-mod.move_matching",
                GLFW.GLFW_KEY_UNKNOWN,  // unbound by default — user assigns in Controls
                category
        ));

        // Autofill keybind — default unbound. Works from gameplay (no inventory
        // screen required). When pressed, sends a C2S packet to trigger
        // server-side autofill from shulker boxes in the player's inventory.
        autoFillKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.trevs-mod.autofill",
                GLFW.GLFW_KEY_UNKNOWN,  // unbound by default — user assigns in Controls
                category
        ));

        // Autofill — tick-based keybind handler (works outside inventory screens).
        // Unlike sort/bulk-move which use the MenuKit event system (because they
        // need a hovered slot), autofill operates on the entire inventory and
        // doesn't require any screen to be open. So we use ClientTickEvents,
        // matching the pattern used by PocketCycler.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // consumeClick() drains the key press queue — must be called even
            // when we skip, so presses don't accumulate and fire later.
            while (autoFillKey.consumeClick()) {
                // Gate: must be connected to a server (singleplayer or multiplayer)
                if (client.player == null) continue;

                // Gate: check the family-wide toggle. This is a client-side config
                // check — if the user disabled autofill in settings, we don't send
                // the packet at all. The server trusts packets it receives.
                if (!family.getGeneral(AUTOFILL_ENABLED)) continue;

                // Send the trigger packet — the server does all the work
                ClientPlayNetworking.send(new AutoFillC2SPayload());
            }
        });

        // Sort Region — KEY_PRESS handler via MenuKit event system.
        // Fires on every key press while hovering a slot. We check if the
        // pressed key matches our sort keybind, resolve the hovered slot's
        // region, and send the sort request to the server.
        MenuKit.on(MKEvent.Type.KEY_PRESS)
                .slotHandler(event -> {
                    // Check if the pressed key matches the sort keybind.
                    // KEY_PRESS fires at the moment the key goes down, so
                    // isDown() will return true for the key that triggered
                    // this event. This respects rebinds and handles modifiers
                    // (e.g., Ctrl+S if the user binds that) correctly.
                    // isUnbound() gate: skip if no key is assigned (default).
                    if (sortRegionKey.isUnbound() || !sortRegionKey.isDown()) {
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
        // Fires on every key press while hovering a slot. We check if the
        // pressed key matches our move-matching keybind, then send the same
        // BulkMoveC2SPayload that Shift+double-click uses. The server handler
        // already iterates the region and quickMoveStacks every matching item,
        // so we get the full behavior for free — no new packet or server code.
        MenuKit.on(MKEvent.Type.KEY_PRESS)
                .slotHandler(event -> {
                    // Check if the pressed key matches the move-matching keybind.
                    // Same pattern as the sort handler: isUnbound() gate prevents
                    // firing when no key is assigned (the default state).
                    if (moveMatchingKey.isUnbound() || !moveMatchingKey.isDown()) {
                        return MKEventResult.PASS;
                    }

                    // Need an item to match against — empty slot means nothing to move
                    ItemStack slotStack = event.getSlotStack();
                    if (slotStack.isEmpty()) {
                        return MKEventResult.PASS;
                    }

                    // Need a source region to move from. Slots outside any region
                    // (e.g., crafting output) don't participate in bulk move.
                    MKRegion region = event.getRegion();
                    if (region == null) {
                        return MKEventResult.PASS;
                    }

                    // Resolve the item's registry ID (e.g., "minecraft:cobblestone").
                    // The server matches items by registry ID — same as the
                    // Shift+double-click bulk move handler.
                    String itemId = BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString();

                    // Reuse the existing BulkMoveC2SPayload — the server handler
                    // already does exactly what we need: iterate the region's slots
                    // and quickMoveStack each matching item.
                    ClientPlayNetworking.send(new BulkMoveC2SPayload(region.name(), itemId));

                    // CONSUMED prevents vanilla from processing this key press
                    // (e.g., if the user binds this to a number key, we don't
                    // want it to also swap hotbar slots).
                    return MKEventResult.CONSUMED;
                });

        // Pocket HUD overlay (shows pocket items next to hotbar)
        PocketHud.register();

        // Container Peek — client-side panel + packet handler
        ContainerPeekClient.registerPanel();
        ContainerPeekClient.registerClientHandler();

        // Container Peek — right-click handler via MenuKit event system.
        // This single handler replaces both IPPeekClickMixin (standard screens)
        // and IPCreativePeekMixin (creative inventory). The event system
        // normalizes slot detection across all screen types, so we don't need
        // separate mixins for creative vs. survival.
        MenuKit.on(MKEvent.Type.RIGHT_CLICK)
                .playerInventory()  // only fire for slots in the player's own inventory
                .slotHandler(event -> {
                    // Only peek items that are peekable (shulker boxes, bundles, ender chests)
                    if (!ContainerPeek.isPeekable(event.getSlotStack())) {
                        return MKEventResult.PASS;
                    }

                    // Resolve the unified player inventory position.
                    // This works identically for survival, creative, and any other
                    // screen type — the event system handles the mapping for us.
                    int unifiedPos = event.getUnifiedPlayerPos();
                    if (unifiedPos < 0) {
                        return MKEventResult.PASS;
                    }

                    // Toggle: if already peeking at this position, close the peek;
                    // otherwise open a peek at the new position.
                    if (ContainerPeekClient.getPeekedSlot() == unifiedPos) {
                        ClientPlayNetworking.send(new PeekC2SPayload(-1));
                    } else {
                        ClientPlayNetworking.send(new PeekC2SPayload(unifiedPos));
                    }

                    // CONSUMED cancels vanilla right-click behavior (which would
                    // normally pick up half the stack)
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
        MenuKit.on(MKEvent.Type.DOUBLE_CLICK)
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

        // General option: toggle autofill from shulker boxes. When disabled,
        // the autofill keybind does nothing. Checked client-side before sending
        // the C2S packet, so the server never receives the request.
        family.generalOption(AUTOFILL_ENABLED,
                Option.<Boolean>createBuilder()
                        .name(Component.literal("Enable Autofill"))
                        .description(OptionDescription.of(
                                Component.literal("When enabled, the Autofill keybind tops off partial " +
                                        "stacks in your inventory from shulker boxes. Also fills empty " +
                                        "hotbar slots with items matching what's already in the hotbar.")))
                        .binding(true,
                                () -> family.getGeneral(AUTOFILL_ENABLED),
                                val -> family.setGeneral(AUTOFILL_ENABLED, val))
                        .controller(TickBoxControllerBuilder::create)
                        .build());

        // General option: auto-restock depleted hotbar items from inventory.
        // When the last item in a hotbar slot is consumed (block placed, food eaten,
        // projectile thrown), the same item type is pulled from the main inventory
        // to refill that slot. Server-side feature — the toggle is checked each tick.
        family.generalOption(AUTO_RESTOCK,
                Option.<Boolean>createBuilder()
                        .name(Component.literal("Auto-Restock"))
                        .description(OptionDescription.of(
                                Component.literal("Automatically refill hotbar slots when the last item " +
                                        "is used. Pulls matching items from your main inventory.")))
                        .binding(true,
                                () -> family.getGeneral(AUTO_RESTOCK),
                                val -> family.setGeneral(AUTO_RESTOCK, val))
                        .controller(TickBoxControllerBuilder::create)
                        .build());

        // General option: auto-replace broken tools with the same type from inventory.
        // When a tool's durability is fully depleted, a replacement tool of the same
        // category (pickaxe, axe, shovel, sword, hoe) is moved into the hotbar slot.
        // Prefers higher-tier tools (netherite > diamond > iron > etc.).
        family.generalOption(AUTO_REPLACE_TOOLS,
                Option.<Boolean>createBuilder()
                        .name(Component.literal("Auto-Replace Tools"))
                        .description(OptionDescription.of(
                                Component.literal("Automatically equip a replacement tool when your " +
                                        "current tool breaks. Searches your inventory for the same tool " +
                                        "type (pickaxe, axe, etc.) and prefers higher-tier replacements.")))
                        .binding(true,
                                () -> family.getGeneral(AUTO_REPLACE_TOOLS),
                                val -> family.setGeneral(AUTO_REPLACE_TOOLS, val))
                        .controller(TickBoxControllerBuilder::create)
                        .build());

        // General option: toggle enriched item tooltips (durability, food stats,
        // inventory totals). The descriptor lives in MKItemTips so MenuKitClient
        // can read it when the callback fires. We register the YACL option here
        // because InventoryPlus owns the config UI for this family.
        family.generalOption(MKItemTips.SHOW_ITEM_TIPS,
                Option.<Boolean>createBuilder()
                        .name(Component.literal("Show Item Tips"))
                        .description(OptionDescription.of(
                                Component.literal("Show extra info in item tooltips: durability with " +
                                        "color coding, food nutrition and saturation, and total count " +
                                        "of the item across your inventory.")))
                        .binding(true,
                                () -> family.getGeneral(MKItemTips.SHOW_ITEM_TIPS),
                                val -> family.setGeneral(MKItemTips.SHOW_ITEM_TIPS, val))
                        .controller(TickBoxControllerBuilder::create)
                        .build());

        // Register config category with the family (mod ID enables tab auto-focus)
        family.configCategory(InventoryPlus.MOD_ID, "Inventory Plus",
                InventoryPlusClient::buildConfigCategory,
                () -> { InventoryPlusConfig.save(); PocketsPanel.applyConfig(); });

        // Settings button — shared across the family, hidden when general option is off
        family.sharedPanel("trevmods_settings", () -> registerSettingsButton(family));
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
                        .label("⚙")
                        .onClick(btn -> {
                            var mc = Minecraft.getInstance();
                            var screen = family.buildConfigScreen(mc.screen);
                            if (screen != null) mc.setScreen(screen);
                        })
                        .done()
                .build();
    }

    // ── Config ──────────────────────────────────────────────────────────────

    /**
     * Builds the YACL config category for Inventory Plus.
     * Called each time the config screen opens to read fresh values.
     */
    static ConfigCategory buildConfigCategory() {
        InventoryPlusConfig cfg = InventoryPlusConfig.get();

        return ConfigCategory.createBuilder()
                .name(Component.literal("Inventory Plus"))
                .tooltip(Component.literal("Extra equipment slots and pocket storage"))

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
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Totem Slot"))
                                .description(OptionDescription.of(Component.literal(
                                        "Adds a passive totem equipment slot to the inventory. " +
                                        "A Totem of Undying placed here saves you from death without " +
                                        "needing to hold it in your hand.")))
                                .binding(true, () -> cfg.enableTotemSlot, val -> cfg.enableTotemSlot = val)
                                .controller(BooleanControllerBuilder::create)
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
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Slots per Pocket"))
                                .description(OptionDescription.of(Component.literal(
                                        "How many extra slots each hotbar position gets (1–3). " +
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
                                .controller(BooleanControllerBuilder::create)
                                .build())
                        .build())

                .build();
    }
}
