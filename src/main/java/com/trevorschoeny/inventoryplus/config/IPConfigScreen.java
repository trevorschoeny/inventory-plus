package com.trevorschoeny.inventoryplus.config;

import com.trevorschoeny.inventoryplus.autotoolswitch.AutoSwitchReturnMode;
import com.trevorschoeny.inventoryplus.autotoolswitch.WeaponPreference;
import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.columncycler.hud.HudMode;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds IP's config screen using YACL.
 *
 * <h3>The two-tab story (Trev, 2026-07-01)</h3>
 *
 * <ul>
 *   <li><b>Features</b> — what the mod does: each feature's master switch and
 *       its behavior choices. Reads top-to-bottom as a feature tour; the
 *       always-on tools (Sort, Move Matching, Locked Slots) get a keybind
 *       cheat-sheet entry since their trigger IS the feature.</li>
 *   <li><b>Advanced</b> — how exactly, and the chrome: numeric tuning
 *       (thresholds, windows), toolbar-button visibility (consolidated in one
 *       group — it's one concern), and the power-user refinements.</li>
 * </ul>
 *
 * Availability (grey-out) wiring crosses tabs: flipping a master on Features
 * live-updates its dependents on Advanced — the Option instances are shared
 * across both categories of the one YACL instance.
 *
 * <p>Keybindery auto-appends a Keybinds tab. IPP keeps its own ModMenu screen
 * (one-way IPP→IP dependency); unifying is deferred (Trev 2026-06-04).
 *
 * <h3>Save semantics</h3>
 *
 * YACL runs every binding's setter on "Save &amp; Quit"; {@link IPConfig}
 * setters persist immediately to {@code config/inventoryplus/config.json}.
 */
public final class IPConfigScreen {

    private IPConfigScreen() {}

    public static Screen create(Screen parent) {
        // ── Options built once, distributed across the two tabs, so the
        //    availability listeners can wire across them ──────────────────

        // ─── Auto-Restock (Features) ─────────────────────────────────────
        Option<Boolean> armor = booleanOption(
                "Armor Restock",
                "Replace armor pieces when they break. Also gates IM Equipment Slots restock.",
                true, IPConfig::autoRestockArmor, IPConfig::setAutoRestockArmor);
        Option<Boolean> armorBeforeBreak = booleanOption(
                "    Restock Before Break",
                "Swap armor with a fresh piece from inventory before it breaks "
                        + "(at the durability threshold — tune it under Advanced).",
                false, IPConfig::autoRestockArmorBeforeBreak, IPConfig::setAutoRestockArmorBeforeBreak);
        Option<Boolean> tool = booleanOption(
                "Tool Restock",
                "Replace tools and weapons in the active hand or offhand when they break.",
                true, IPConfig::autoRestockTool, IPConfig::setAutoRestockTool);
        Option<Boolean> toolBeforeBreak = booleanOption(
                "    Restock Before Break",
                "Swap tools with a fresh one from inventory before they break "
                        + "(at the durability threshold — tune it under Advanced).",
                false, IPConfig::autoRestockToolBeforeBreak, IPConfig::setAutoRestockToolBeforeBreak);
        Option<Boolean> item = booleanOption(
                "Item Restock",
                "Refill the active hand or offhand stack when it runs out (food, blocks, arrows, etc.).",
                true, IPConfig::autoRestockItem, IPConfig::setAutoRestockItem);
        Option<Boolean> shulker = booleanOption(
                "Pull from Shulker Boxes",
                "Search shulker boxes in your inventory for restock items.",
                false, IPConfig::autoRestockShulker, IPConfig::setAutoRestockShulker);
        Option<Boolean> shulkerAmmo = booleanOption(
                "    Pull Ammo When Shooting",
                "Draw arrows (and crossbow ammo) directly from shulker boxes per shot, "
                        + "without populating an offhand/inventory stack.",
                false, IPConfig::autoRestockShulkerAmmo, IPConfig::setAutoRestockShulkerAmmo);

        // ─── Auto-Restock tuning (Advanced) ──────────────────────────────
        Option<Integer> beforeBreakThreshold = Option.<Integer>createBuilder()
                .name(Component.literal("Before-Break Threshold"))
                .description(OptionDescription.of(Component.literal(
                        "Durability remaining at or below which the Before-Break swaps fire "
                        + "(armor and tools share it). Higher = swap earlier.")))
                .binding(10,
                        IPConfig::autoRestockBeforeBreakThreshold,
                        IPConfig::setAutoRestockBeforeBreakThreshold)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(2, 50).step(1))
                .build();

        // ─── Auto Tool Switch (Features) ─────────────────────────────────
        Option<Boolean> switchEnabled = booleanOption(
                "Enable Auto Tool Switch",
                "When you hit a block (or attack a mob, with the Weapons sub-toggle on), "
                        + "the mod auto-swaps the right tool into your active hand. "
                        + "Sneak (Shift) suppresses the switch.",
                false, IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled);
        Option<AutoSwitchReturnMode> returnMode = Option.<AutoSwitchReturnMode>createBuilder()
                .name(Component.literal("    Return To Previous"))
                .description(OptionDescription.of(Component.literal(
                        "When/how the hotbar snaps back after a switch. "
                        + "Off keeps the new tool. Automatic returns after the window, once you've stopped. "
                        + "Hotkey (timed): press the Return keybind within the window (else the tool stays). "
                        + "Hotkey (anytime): press the Return keybind whenever. "
                        + "The Return keybind defaults to Sneak (Shift) — rebind under Controls > Inventory Plus. "
                        + "Window length is tunable under Advanced.")))
                .binding(AutoSwitchReturnMode.OFF,
                        IPConfig::autoToolSwitchReturnMode, IPConfig::setAutoToolSwitchReturnMode)
                .controller(opt -> EnumControllerBuilder.create(opt)
                        .enumClass(AutoSwitchReturnMode.class)
                        .valueFormatter(m -> Component.literal(m.displayName())))
                .build();
        Option<Boolean> weapons = booleanOption(
                "    Switch Weapons Too",
                "Also auto-switch to a weapon when attacking a mob.",
                false, IPConfig::autoToolSwitchWeapons, IPConfig::setAutoToolSwitchWeapons);
        Option<Boolean> allMobs = booleanOption(
                "        All Mobs (not just hostile)",
                "Off (default): the weapon switch only fires for hostile mobs. "
                        + "On: fires for any mob, including cows, sheep, villagers.",
                false, IPConfig::autoToolSwitchAllMobs, IPConfig::setAutoToolSwitchAllMobs);
        Option<WeaponPreference> weaponPref = Option.<WeaponPreference>createBuilder()
                .name(Component.literal("        Preferred Weapon"))
                .description(OptionDescription.of(Component.literal(
                        "Which weapon type the auto-switch prefers. The preferred type wins regardless "
                        + "of material — a preferred Axe in iron beats a non-preferred Sword in "
                        + "netherite. Within the preferred type, best material wins.")))
                .binding(WeaponPreference.SWORD,
                        IPConfig::autoToolSwitchWeaponPreference, IPConfig::setAutoToolSwitchWeaponPreference)
                .controller(opt -> EnumControllerBuilder.create(opt).enumClass(WeaponPreference.class))
                .build();

        // ─── Auto Tool Switch tuning (Advanced) ──────────────────────────
        Option<Integer> returnCooldown = Option.<Integer>createBuilder()
                .name(Component.literal("Return Window (seconds)"))
                .description(OptionDescription.of(Component.literal(
                        "Auto Tool Switch: how long after you stop before Automatic returns / the "
                        + "Hotkey (timed) window stays open. Unused by Hotkey (anytime).")))
                .binding(3,
                        IPConfig::autoToolSwitchReturnCooldownSeconds,
                        IPConfig::setAutoToolSwitchReturnCooldownSeconds)
                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 10).step(1))
                .build();

        // ─── Column Cycler (Features) ────────────────────────────────────
        Option<Boolean> cyclerEnabled = booleanOption(
                "Enable Column Cycler",
                "Cycle items along a vertical column of toggled inventory slots — "
                        + "press C while hovering a slot to add it to its column's cycle; "
                        + "] and [ rotate. More refinements under Advanced.",
                false, IPConfig::columnCyclerEnabled, IPConfig::setColumnCyclerEnabled);
        Option<Boolean> cyclerShowHud = booleanOption(
                "    Show HUD",
                "Show the mini-hotbar overlay to the right of your hotbar when on a cycle slot. "
                        + "Cycling still works when off.",
                true,
                () -> IPConfig.columnCyclerHudMode() != HudMode.NONE,
                v -> IPConfig.setColumnCyclerHudMode(v ? HudMode.MINI_HOTBAR : HudMode.NONE));

        // ─── Column Cycler refinements (Advanced) ────────────────────────
        Option<Boolean> lockCycleSlots = Option.<Boolean>createBuilder()
                .name(Component.literal("Lock Cycle Slots"))
                .description(OptionDescription.of(Component.literal(
                        "Cycle slots are automatically and always locked while they're in the cycle. "
                        + "When this is off, cycle and lock are fully independent and can be "
                        + "toggled separately.")))
                .binding(true, IPConfig::cycleSlotsLocked, v -> {
                    boolean wasOn = IPConfig.cycleSlotsLocked();
                    IPConfig.setCycleSlotsLocked(v);
                    if (v && !wasOn) {
                        // OFF → ON: retroactively lock cycle slots that got
                        // unlocked while the config was off.
                        ColumnCycler.enforceCycleLockingInvariant();
                    }
                })
                .controller(BooleanControllerBuilder::create)
                .build();
        Option<Boolean> scrollToCycle = booleanOption(
                "Scroll to Cycle",
                "Use the scroll wheel as an alternative to the ]/[ keybinds. On a hotbar slot "
                        + "whose column has cycle members, scrolling rotates the cycle instead of "
                        + "switching hotbar slots.",
                false, IPConfig::columnCyclerScrollToCycle, IPConfig::setColumnCyclerScrollToCycle);

        // ─── Toolbar buttons (Advanced — one concern, one group) ─────────
        Option<Boolean> sortButton = booleanOption(
                "Sort",
                "Show Sort buttons in container toolbars. The S keybind still works when off.",
                true, IPConfig::sortShowButton, IPConfig::setSortShowButton);
        Option<Boolean> mmButtons = booleanOption(
                "Move Matching",
                "Show the IN/OUT buttons when a container is open. I/O keybinds still work when off.",
                true, IPConfig::moveMatchingShowButtons, IPConfig::setMoveMatchingShowButtons);
        Option<Boolean> lockButton = booleanOption(
                "Locked Slots",
                "Show the lock-edit toggle in the inventory toolbar. The L keybind still works when off.",
                true, IPConfig::lockedSlotsShowButton, IPConfig::setLockedSlotsShowButton);
        Option<Boolean> cyclerButton = booleanOption(
                "Column Cycler",
                "Show the cycle-edit toggle in the inventory toolbar. The C keybind still works when off.",
                true, IPConfig::columnCyclerShowButton, IPConfig::setColumnCyclerShowButton);

        // ─── Availability wiring (crosses the two tabs) ──────────────────
        armorBeforeBreak.setAvailable(IPConfig.autoRestockArmor());
        armor.addListener((opt, val) -> {
            armorBeforeBreak.setAvailable(val);
            beforeBreakThreshold.setAvailable(
                    (val && armorBeforeBreak.pendingValue())
                    || (tool.pendingValue() && toolBeforeBreak.pendingValue()));
        });
        toolBeforeBreak.setAvailable(IPConfig.autoRestockTool());
        tool.addListener((opt, val) -> {
            toolBeforeBreak.setAvailable(val);
            beforeBreakThreshold.setAvailable(
                    (armor.pendingValue() && armorBeforeBreak.pendingValue())
                    || (val && toolBeforeBreak.pendingValue()));
        });
        shulkerAmmo.setAvailable(IPConfig.autoRestockShulker());
        shulker.addListener((opt, val) -> shulkerAmmo.setAvailable(val));
        // The threshold slider matters only while a Before-Break swap is on.
        Runnable thresholdAvail = () -> beforeBreakThreshold.setAvailable(
                (armor.pendingValue() && armorBeforeBreak.pendingValue())
                || (tool.pendingValue() && toolBeforeBreak.pendingValue()));
        beforeBreakThreshold.setAvailable(
                (IPConfig.autoRestockArmor() && IPConfig.autoRestockArmorBeforeBreak())
                || (IPConfig.autoRestockTool() && IPConfig.autoRestockToolBeforeBreak()));
        armorBeforeBreak.addListener((opt, val) -> thresholdAvail.run());
        toolBeforeBreak.addListener((opt, val) -> thresholdAvail.run());

        returnMode.setAvailable(IPConfig.autoToolSwitchEnabled());
        returnCooldown.setAvailable(IPConfig.autoToolSwitchEnabled()
                && IPConfig.autoToolSwitchReturnMode().isWindowed());
        weapons.setAvailable(IPConfig.autoToolSwitchEnabled());
        allMobs.setAvailable(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons());
        weaponPref.setAvailable(IPConfig.autoToolSwitchEnabled() && IPConfig.autoToolSwitchWeapons());
        switchEnabled.addListener((opt, val) -> {
            returnMode.setAvailable(val);
            returnCooldown.setAvailable(val && returnMode.pendingValue().isWindowed());
            weapons.setAvailable(val);
            allMobs.setAvailable(val && weapons.pendingValue());
            weaponPref.setAvailable(val && weapons.pendingValue());
        });
        returnMode.addListener((opt, val) ->
                returnCooldown.setAvailable(switchEnabled.pendingValue() && val.isWindowed()));
        weapons.addListener((opt, val) -> {
            allMobs.setAvailable(switchEnabled.pendingValue() && val);
            weaponPref.setAvailable(switchEnabled.pendingValue() && val);
        });

        cyclerShowHud.setAvailable(IPConfig.columnCyclerEnabled());
        lockCycleSlots.setAvailable(IPConfig.columnCyclerEnabled());
        scrollToCycle.setAvailable(IPConfig.columnCyclerEnabled());
        cyclerButton.setAvailable(IPConfig.columnCyclerEnabled());
        cyclerEnabled.addListener((opt, val) -> {
            cyclerShowHud.setAvailable(val);
            lockCycleSlots.setAvailable(val);
            scrollToCycle.setAvailable(val);
            cyclerButton.setAvailable(val);
        });

        // ─── Tab 1: Features ─────────────────────────────────────────────
        ConfigCategory features = ConfigCategory.createBuilder()
                .name(Component.literal("Features"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Auto-Restock"))
                        .description(OptionDescription.of(Component.literal(
                                "Refill items in your active hand, offhand, and armor when they break or run out.")))
                        .option(armor).option(armorBeforeBreak)
                        .option(tool).option(toolBeforeBreak)
                        .option(item)
                        .option(shulker).option(shulkerAmmo)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Auto Tool Switch"))
                        .description(OptionDescription.of(Component.literal(
                                "Auto-swap to the right tool when you hit a block. Optionally extend to weapons on mobs.")))
                        .option(switchEnabled).option(returnMode)
                        .option(weapons).option(allMobs).option(weaponPref)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Column Cycler"))
                        .description(OptionDescription.of(Component.literal(
                                "Extend the effective hotbar by cycling items along inventory columns.")))
                        .option(cyclerEnabled).option(cyclerShowHud)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Always-On Tools"))
                        .description(OptionDescription.of(Component.literal(
                                "Manual tools that are always ready — their trigger is the feature.")))
                        .option(LabelOption.create(Component.literal(
                                "§7These work out of the box, in any inventory or container:")))
                        .option(LabelOption.create(Component.literal(
                                "§f  Sort §7— press §fS§7 or the toolbar button")))
                        .option(LabelOption.create(Component.literal(
                                "§f  Move Matching §7— §fI§7 pulls matching items in, §fO§7 sends them out")))
                        .option(LabelOption.create(Component.literal(
                                "§f  Locked Slots §7— hover a slot and press §fL§7 to protect it from all of the above")))
                        .option(LabelOption.create(Component.literal(
                                "§7Rebind under Controls > Inventory Plus. Hide their buttons under Advanced.")))
                        .build())
                .build();

        // ─── Tab 2: Advanced ─────────────────────────────────────────────
        ConfigCategory advanced = ConfigCategory.createBuilder()
                .name(Component.literal("Advanced"))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Fine-Tuning"))
                        .description(OptionDescription.of(Component.literal(
                                "Numeric knobs behind the Features tab's behaviors.")))
                        .option(beforeBreakThreshold)
                        .option(returnCooldown)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Toolbar Buttons"))
                        .description(OptionDescription.of(Component.literal(
                                "Which buttons appear in the inventory/container toolbars. "
                                + "Keybinds keep working when a button is hidden.")))
                        .option(sortButton)
                        .option(mmButtons)
                        .option(lockButton)
                        .option(cyclerButton)
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Column Cycler"))
                        .description(OptionDescription.of(Component.literal(
                                "Power-user refinements for the cycler.")))
                        .option(lockCycleSlots)
                        .option(scrollToCycle)
                        .build())
                .build();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Inventory Plus"))
                .category(features)
                .category(advanced)
                .build()
                .generateScreen(parent);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /** Builds a single boolean toggle option (the workhorse widget). */
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
