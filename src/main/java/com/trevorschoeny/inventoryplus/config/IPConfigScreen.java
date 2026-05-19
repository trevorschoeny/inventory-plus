package com.trevorschoeny.inventoryplus.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds IP's config screen using YACL.
 *
 * <h3>Tab structure</h3>
 *
 * Three tabs across the top, per Trev 2026-05-17:
 *
 * <ul>
 *   <li><b>IP</b> — default-scope IP feature configs, one section per
 *       feature (Auto-Restock / Sort / Move Matching / Locked Slots).</li>
 *   <li><b>IPP</b> — Inventory Plus Plus configs. Empty until IPP ships.</li>
 *   <li><b>Power Users</b> — opt-in advanced configs that span both IP
 *       and IPP. Empty until the first Power User feature ships.</li>
 * </ul>
 *
 * <p>Empty tabs intentionally show as such (placeholder label) — gives
 * a stable shape that doesn't reflow as IPP / PU features land.
 *
 * <h3>Save semantics</h3>
 *
 * YACL collects {@code .binding(default, getter, setter)} entries and
 * runs every setter when the player clicks "Save & Quit".
 * {@link IPConfig}'s setters persist immediately to
 * {@code config/inventoryplus/config.json}.
 */
public final class IPConfigScreen {

    private IPConfigScreen() {}

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Inventory Plus"))
                .category(ipCategory())
                .category(ippCategory())
                .category(powerUsersCategory())
                .build()
                .generateScreen(parent);
    }

    // ─── IP tab ──────────────────────────────────────────────────────

    private static ConfigCategory ipCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("IP"))
                .group(autoRestockGroup())
                .group(sortGroup())
                .group(moveMatchingGroup())
                .group(lockedSlotsGroup())
                .build();
    }

    private static OptionGroup autoRestockGroup() {
        Option<Boolean> armor = booleanOption(
                "Armor Restock",
                "Replace armor pieces when they break. Also gates IPP Equipment Slots.",
                true,
                IPConfig::autoRestockArmor,
                IPConfig::setAutoRestockArmor);

        Option<Boolean> tool = booleanOption(
                "Tool Restock",
                "Replace tools and weapons in the active hand or offhand when they break.",
                true,
                IPConfig::autoRestockTool,
                IPConfig::setAutoRestockTool);

        Option<Boolean> item = booleanOption(
                "Item Restock",
                "Refill the active hand or offhand stack when it runs out (food, blocks, arrows, etc.).",
                true,
                IPConfig::autoRestockItem,
                IPConfig::setAutoRestockItem);

        Option<Boolean> shulker = booleanOption(
                "Pull from Shulker Boxes",
                "Search shulker boxes in your inventory for restock items.",
                false,
                IPConfig::autoRestockShulker,
                IPConfig::setAutoRestockShulker);

        // ─── Sub-toggles ─────────────────────────────────────────────
        // Greyed out when their parent is off, interactive when on.
        // YACL has no native hide-on-dependency; grey-out is the
        // standard idiom. Whitespace prefix marks them visually as
        // sub-toggles since YACL renders all options full-width.
        Option<Boolean> armorBeforeBreak = booleanOption(
                "    Restock Before Break",
                "Swap armor with a fresh piece from inventory before it breaks (≤10 durability).",
                false,
                IPConfig::autoRestockArmorBeforeBreak,
                IPConfig::setAutoRestockArmorBeforeBreak);

        Option<Boolean> toolBeforeBreak = booleanOption(
                "    Restock Before Break",
                "Swap tools with a fresh one from inventory before they break (≤10 durability).",
                false,
                IPConfig::autoRestockToolBeforeBreak,
                IPConfig::setAutoRestockToolBeforeBreak);

        Option<Boolean> shulkerAmmo = booleanOption(
                "    Pull Ammo When Shooting",
                "Draw arrows (and crossbow ammo) directly from shulker boxes per shot, "
                        + "without populating an offhand/inventory stack.",
                false,
                IPConfig::autoRestockShulkerAmmo,
                IPConfig::setAutoRestockShulkerAmmo);

        // ─── Parent-gating wiring ────────────────────────────────────
        // Initial state mirrors current parent value; listeners keep
        // the sub-toggle's availability in sync as the parent flips.
        armorBeforeBreak.setAvailable(IPConfig.autoRestockArmor());
        armor.addListener((opt, val) -> armorBeforeBreak.setAvailable(val));

        toolBeforeBreak.setAvailable(IPConfig.autoRestockTool());
        tool.addListener((opt, val) -> toolBeforeBreak.setAvailable(val));

        shulkerAmmo.setAvailable(IPConfig.autoRestockShulker());
        shulker.addListener((opt, val) -> shulkerAmmo.setAvailable(val));

        return OptionGroup.createBuilder()
                .name(Component.literal("Auto-Restock"))
                .description(OptionDescription.of(Component.literal(
                        "Refill items in your active hand, offhand, and armor when they break or run out.")))
                .option(armor)
                .option(armorBeforeBreak)
                .option(tool)
                .option(toolBeforeBreak)
                .option(item)
                .option(shulker)
                .option(shulkerAmmo)
                .build();
    }

    private static OptionGroup sortGroup() {
        return OptionGroup.createBuilder()
                .name(Component.literal("Sort"))
                .option(booleanOption(
                        "Show Button",
                        "Show Sort buttons in container toolbars. Keybind still works when off.",
                        true,
                        IPConfig::sortShowButton,
                        IPConfig::setSortShowButton))
                .build();
    }

    private static OptionGroup moveMatchingGroup() {
        return OptionGroup.createBuilder()
                .name(Component.literal("Move Matching"))
                .option(booleanOption(
                        "Show Buttons",
                        "Show IN/OUT buttons in the inventory toolbar when a container is open. "
                                + "Keybinds still work when off.",
                        true,
                        IPConfig::moveMatchingShowButtons,
                        IPConfig::setMoveMatchingShowButtons))
                .build();
    }

    private static OptionGroup lockedSlotsGroup() {
        return OptionGroup.createBuilder()
                .name(Component.literal("Locked Slots"))
                .option(booleanOption(
                        "Show Button",
                        "Show the lock-edit toggle in the inventory toolbar. Keybind still works when off.",
                        true,
                        IPConfig::lockedSlotsShowButton,
                        IPConfig::setLockedSlotsShowButton))
                .build();
    }

    // ─── IPP tab ─────────────────────────────────────────────────────
    // Empty for now — IPP features are deferred. The tab is visible to
    // give the UI a stable shape; LabelOption acts as a placeholder
    // (YACL categories with no entries render awkwardly).

    private static ConfigCategory ippCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("IPP"))
                .option(LabelOption.create(Component.literal(
                        "Inventory Plus Plus configs will appear here once IPP ships.")))
                .build();
    }

    // ─── Power Users tab ─────────────────────────────────────────────
    // Empty for now — Power-user configs (Pull from Bundles / Ender
    // Chest, Include Hotbar, etc.) ship as their features land.

    private static ConfigCategory powerUsersCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Power Users"))
                .option(LabelOption.create(Component.literal(
                        "Power User configs will appear here as their features ship.")))
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Builds a single boolean toggle option — the only widget type IP
     * uses today. Name + description shown in the YACL pane; binding
     * connects YACL's value flow to {@link IPConfig}'s getter/setter
     * via the standard YACL save-on-Save-button flow.
     */
    private static Option<Boolean> booleanOption(
            String name,
            String description,
            boolean defaultValue,
            java.util.function.Supplier<Boolean> getter,
            java.util.function.Consumer<Boolean> setter) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(description)))
                .binding(defaultValue, getter, setter)
                .controller(BooleanControllerBuilder::create)
                .build();
    }
}
