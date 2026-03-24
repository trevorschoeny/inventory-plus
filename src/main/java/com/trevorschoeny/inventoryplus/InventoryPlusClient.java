package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.menukit.MKFamily;
import com.trevorschoeny.menukit.MenuKit;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side entry point for Inventory Plus.
 *
 * <p>Joins the "trevmods" family for unified config and keybind grouping,
 * then registers keybinds, HUD, and config categories.
 */
public class InventoryPlusClient implements ClientModInitializer {

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

        // Pocket HUD overlay (shows pocket items next to hotbar)
        PocketHud.register();

        // Container Peek — client-side panel + packet handler
        ContainerPeekClient.registerPanel();
        ContainerPeekClient.registerClientHandler();

        // General option: toggle the settings gear button visibility
        family.generalOption("show_settings_button",
                Option.<Boolean>createBuilder()
                        .name(Component.literal("Show Settings Button"))
                        .description(OptionDescription.of(
                                Component.literal("Show the ⚙ button above the inventory screen.")))
                        .binding(true,
                                () -> family.getGeneralBool("show_settings_button", true),
                                val -> family.setGeneral("show_settings_button", val))
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
                .disabledWhen(() -> !family.getGeneralBool("show_settings_button", true))
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
