package com.trevorschoeny.inventoryplus.config;

import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds IP's config screen using YACL.
 *
 * <h3>Tab structure</h3>
 *
 * One tab — <b>IP</b> — holding every IP feature: Auto-Restock, Auto Tool
 * Switch, Sort, Move Matching, Locked Slots, Column Cycler. Keybindery
 * auto-appends a second <b>Keybinds</b> tab.
 *
 * <p>IPP keeps its own separate ModMenu screen — the one-way IPP→IP
 * dependency means IP can't host an IPP tab. Unifying the two into one
 * screen is deferred (Trev 2026-06-04).
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
                .build()
                .generateScreen(parent);
    }

    // ─── IP tab ──────────────────────────────────────────────────────

    private static ConfigCategory ipCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.literal("IP"))
                .group(autoRestockGroup())
                .group(autoToolSwitchGroup())
                .group(sortGroup())
                .group(moveMatchingGroup())
                .group(lockedSlotsGroup())
                .group(columnCyclerGroup())
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

    private static OptionGroup autoToolSwitchGroup() {
        Option<Boolean> enabled = booleanOption(
                "Enable Auto Tool Switch",
                "When you hit a block (or attack a mob, with the Weapons sub-toggle on), the mod auto-swaps the right tool into your active hand. Sneak (Shift) suppresses the switch.",
                false,
                IPConfig::autoToolSwitchEnabled,
                IPConfig::setAutoToolSwitchEnabled);

        Option<Boolean> returnAfter = booleanOption(
                "    Auto-Return After Action",
                "Restore your previous slot (and any cycling) after the action completes — on LMB release for mining; after attack-cooldown for combat.",
                false,
                IPConfig::autoToolSwitchReturn,
                IPConfig::setAutoToolSwitchReturn);

        Option<Boolean> weapons = booleanOption(
                "    Switch Weapons Too",
                "Also auto-switch to a weapon (sword > axe > mace > trident) when attacking a mob.",
                false,
                IPConfig::autoToolSwitchWeapons,
                IPConfig::setAutoToolSwitchWeapons);

        Option<Boolean> allMobs = booleanOption(
                "        All Mobs (not just hostile)",
                "Off (default): the weapon switch only fires for hostile mobs (zombies, skeletons, creepers, etc.). On: fires for any mob, including cows, sheep, villagers.",
                false,
                IPConfig::autoToolSwitchAllMobs,
                IPConfig::setAutoToolSwitchAllMobs);

        // Preferred Weapon — cycles through Sword / Axe / Mace / Trident.
        // The preferred type wins regardless of material, so e.g., a
        // preferred Axe in iron beats a non-preferred Sword in netherite.
        Option<WeaponPreference> weaponPref = Option.<WeaponPreference>createBuilder()
                .name(Component.literal("        Preferred Weapon"))
                .description(OptionDescription.of(Component.literal(
                        "Which weapon type the auto-switch prefers. The preferred type wins regardless of material — a preferred Axe in iron beats a non-preferred Sword in netherite. Within the preferred type, best material wins.")))
                .binding(WeaponPreference.SWORD,
                        IPConfig::autoToolSwitchWeaponPreference,
                        IPConfig::setAutoToolSwitchWeaponPreference)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(WeaponPreference.class))
                .build();

        // Parent-gating: sub-toggles greyed out when master is off;
        // Weapons sub-toggles (All Mobs + Preferred Weapon) additionally
        // require Switch Weapons Too.
        returnAfter.setAvailable(IPConfig.autoToolSwitchEnabled());
        weapons.setAvailable(IPConfig.autoToolSwitchEnabled());
        allMobs.setAvailable(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons());
        weaponPref.setAvailable(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons());
        enabled.addListener((opt, val) -> {
            returnAfter.setAvailable(val);
            weapons.setAvailable(val);
            allMobs.setAvailable(val && IPConfig.autoToolSwitchWeapons());
            weaponPref.setAvailable(val && IPConfig.autoToolSwitchWeapons());
        });
        weapons.addListener((opt, val) -> {
            allMobs.setAvailable(IPConfig.autoToolSwitchEnabled() && val);
            weaponPref.setAvailable(IPConfig.autoToolSwitchEnabled() && val);
        });

        return OptionGroup.createBuilder()
                .name(Component.literal("Auto Tool Switch"))
                .description(OptionDescription.of(Component.literal(
                        "Auto-swap to the right tool when you hit a block. Optionally extend to weapons on mobs.")))
                .option(enabled)
                .option(returnAfter)
                .option(weapons)
                .option(allMobs)
                .option(weaponPref)
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

    // ─── Column Cycler ───────────────────────────────────────────────
    // Was its own "Power Users" tab; folded into the IP tab as just
    // another group per Trev's single-tab cleanup (2026-06-04). Master
    // enable toggle plus per-feature sub-toggles.

    private static OptionGroup columnCyclerGroup() {
        Option<Boolean> enabled = booleanOption(
                "Enable Column Cycler",
                "Cycle items along a vertical column of toggled inventory slots — "
                        + "press C while hovering a slot to add it to its column's cycle.",
                false,
                IPConfig::columnCyclerEnabled,
                IPConfig::setColumnCyclerEnabled);

        Option<Boolean> showButton = booleanOption(
                "    Show Toolbar Button",
                "Show the cycle-edit toggle button in the inventory. "
                        + "The C keybind still works when off.",
                true,
                IPConfig::columnCyclerShowButton,
                IPConfig::setColumnCyclerShowButton);

        // Show HUD — boolean toggle bridging to the HudMode enum. When
        // future Diamond Indicators ship, this gets replaced with an
        // enum-cycler control; the underlying HudMode field stays the
        // same. Legacy config files with a HudMode the boolean doesn't
        // know about (e.g., a future DIAMOND_INDICATORS) still read OK
        // — the boolean just renders as ON for any non-NONE value.
        Option<Boolean> showHud = booleanOption(
                "    Show HUD",
                "Show the mini-hotbar overlay to the right of your hotbar when on a cycle slot. "
                        + "Cycling still works when off.",
                true,
                () -> IPConfig.columnCyclerHudMode() != HudMode.NONE,
                v -> IPConfig.setColumnCyclerHudMode(v ? HudMode.MINI_HOTBAR : HudMode.NONE));

        // Lock Cycle Slots — bind cycle ⇔ lock as one unit. Default ON.
        // Custom setter detects OFF→ON transition and retroactively
        // locks any cycle slots that aren't (enforces the invariant for
        // existing cycle slots that lost their lock while the config was
        // off).
        Option<Boolean> lockCycleSlots = Option.<Boolean>createBuilder()
                .name(Component.literal("    Lock Cycle Slots"))
                .description(OptionDescription.of(Component.literal(
                        "Cycle slots are automatically and always locked while they're in the cycle. "
                                + "When this is off, cycle and lock are fully independent and can be "
                                + "toggled separately.")))
                .binding(true, IPConfig::cycleSlotsLocked, v -> {
                    boolean wasOn = IPConfig.cycleSlotsLocked();
                    IPConfig.setCycleSlotsLocked(v);
                    if (v && !wasOn) {
                        // OFF → ON: retroactively lock cycle slots that
                        // got unlocked while the config was off.
                        ColumnCycler.enforceCycleLockingInvariant();
                    }
                })
                .controller(BooleanControllerBuilder::create)
                .build();

        Option<Boolean> scrollToCycle = booleanOption(
                "    Scroll to Cycle",
                "Use the scroll wheel as an alternative to the forward/backward keybinds. "
                        + "On a hotbar slot whose column has cycle members, scrolling rotates the cycle "
                        + "instead of switching hotbar slots. Scrolling on non-cycle columns still "
                        + "switches slots normally.",
                false,
                IPConfig::columnCyclerScrollToCycle,
                IPConfig::setColumnCyclerScrollToCycle);

        // Parent-gating: sub-toggles greyed out when master is off.
        showButton.setAvailable(IPConfig.columnCyclerEnabled());
        showHud.setAvailable(IPConfig.columnCyclerEnabled());
        lockCycleSlots.setAvailable(IPConfig.columnCyclerEnabled());
        scrollToCycle.setAvailable(IPConfig.columnCyclerEnabled());
        enabled.addListener((opt, val) -> {
            showButton.setAvailable(val);
            showHud.setAvailable(val);
            lockCycleSlots.setAvailable(val);
            scrollToCycle.setAvailable(val);
        });

        return OptionGroup.createBuilder()
                .name(Component.literal("Column Cycler"))
                .description(OptionDescription.of(Component.literal(
                        "Extend the effective hotbar by cycling items along inventory columns.")))
                .option(enabled)
                .option(showButton)
                .option(showHud)
                .option(lockCycleSlots)
                .option(scrollToCycle)
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
