package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.settings.SettingsMenu;

import com.trevlar.menukit.api.element.Toggle;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The inventory buttons for features that had none: the most basic kind, a
 * toggle that turns the feature on and off (Trev, 2026-09-27: every feature
 * has an in-inventory button). The icon inverts while the feature is on, like
 * the Column Cycler's. Ctrl+click (Cmd on a Mac) opens the feature's tab, as
 * on every Inventory Plus button. Each is hidden by its tab's button toggle.
 */
public final class FeatureToggleButtons {

    private FeatureToggleButtons() {}

    public static final int SIZE = 9;

    public static Toggle restock(int x, int y) {
        return toggle(x, y, "restock_button", "Restock", SettingsMenu.RESTOCK,
                IPConfig::restockEnabled, IPConfig::setRestockEnabled, IPConfig::restockShowButton);
    }

    public static Toggle autoToolSwitch(int x, int y) {
        return toggle(x, y, "auto_tool_switch_button", "Auto Tool Switch", SettingsMenu.AUTO_TOOL_SWITCH,
                IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled,
                IPConfig::autoToolSwitchShowButton);
    }

    private static Toggle toggle(int x, int y, String sprite, String name, String tab,
                                 BooleanSupplier on, Consumer<Boolean> set, BooleanSupplier shown) {
        return Toggle.builder().sprite(Identifier.fromNamespaceAndPath("inventoryplus", sprite))
                .at(x, y).size(SIZE, SIZE)
                .state(on, v -> {
                    if (SettingsMenu.ctrlClickOpens(tab)) return;
                    set.accept(v);
                })
                .tooltip(() -> Component.literal(name + ": " + (on.getAsBoolean() ? "on" : "off")))
                .visibleWhen(shown)
                .build();
    }
}
