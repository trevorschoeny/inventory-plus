package com.trevorschoeny.inventoryplus.toolbar;

import com.trevorschoeny.inventoryplus.buttonmode.PressFeedback;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.settings.SettingsMenu;

import com.trevlar.menukit.api.element.Toggle;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The right column's buttons: one per feature, each the feature's on/off
 * switch. The same three gestures on every one:
 * <ul>
 *   <li><b>Left click</b> turns the feature on or off. The icon inverts while on.</li>
 *   <li><b>Right click</b> cycles, where the feature has something to cycle.
 *       Shift-right click goes back.</li>
 *   <li><b>Ctrl-click</b> (Cmd on a Mac) opens the feature's tab, and holding
 *       Ctrl says so in the tooltip.</li>
 * </ul>
 * Locks have no switch here: a lock always holds, and edit mode (the edit
 * button on every container toolbar) is where slots are locked. A feature that
 * acts on slots names its key in the tooltip. Each is hidden by its tab's "Show the button" setting.
 */
public final class FeatureToggleButtons {

    private FeatureToggleButtons() {}

    public static final int SIZE = 9;

    public static Toggle restock(int x, int y) {
        return builder(x, y, "restock_button", "Restock", SettingsMenu.RESTOCK,
                IPConfig::restockEnabled, IPConfig::setRestockEnabled, IPConfig::restockShowButton, null, null).build();
    }

    public static Toggle autoToolSwitch(int x, int y) {
        return builder(x, y, "auto_tool_switch_button", "Auto Tool Switch", SettingsMenu.AUTO_TOOL_SWITCH,
                IPConfig::autoToolSwitchEnabled, IPConfig::setAutoToolSwitchEnabled,
                IPConfig::autoToolSwitchShowButton, null, null).build();
    }

    /** Column Cycler: slots join a column's cycle by edit mode's cycle key. */
    public static Toggle columnCycler(int x, int y) {
        return builder(x, y, "cycle_slot_edit", "Column Cycler", SettingsMenu.COLUMN_CYCLER,
                IPConfig::columnCyclerEnabled, IPConfig::setColumnCyclerEnabled, IPConfig::columnCyclerShowButton,
                null, () -> keyHint("In edit mode, hover over a slot", IPKeybinds.EDIT_CYCLE)).build();
    }

    /** Hotbar Sets: hold the key, scroll to a row or a saved set, let go. */
    public static Toggle hotbarSets(int x, int y) {
        return builder(x, y, "hotbar_cycler_button", "Hotbar Sets", SettingsMenu.HOTBAR_SETS,
                IPConfig::hotbarSetsEnabled, IPConfig::setHotbarSetsEnabled, IPConfig::hotbarSetsShowButton,
                null, () -> "Hold " + IPKeybinds.HOTBAR_SETS.getTranslatedKeyMessage().getString() + ", scroll to a row, and let go").build();
    }

    /** A companion's switch ({@code InventoryPlusApi.addFeatureSwitch}). */
    public record Contributed(String tabId, String name, Identifier sprite, BooleanSupplier on,
                              Consumer<Boolean> set, BooleanSupplier shown, @Nullable String hint) {}

    private static final List<Contributed> CONTRIBUTED = new ArrayList<>();

    public static void contribute(Contributed c) {
        CONTRIBUTED.add(c);
    }

    /** Every companion's switch, in the order they were added; {@code step} spaces them down from {@code y}. */
    static List<Toggle> contributed(int x, int y, int step) {
        List<Toggle> out = new ArrayList<>();
        for (Contributed c : CONTRIBUTED) {
            out.add(builder(x, y + out.size() * step, c.sprite(), c.name(), c.tabId(), c.on(), c.set(), c.shown(),
                    null, c.hint() == null ? null : c::hint).build());
        }
        return out;
    }

    /** "Hover over a slot and press C", with the key as bound now. */
    private static String keyHint(String where, KeyMapping key) {
        return where + " and press " + key.getTranslatedKeyMessage().getString();
    }

    /**
     * A feature's switch: "Restock: On", then {@code detail} (state worth
     * knowing) and {@code hints} (how to use it) in
     * gray when given, left click toggling {@code on} through {@code set}.
     * With Compact tooltips on (General) it is one line, the detail after a
     * dot and no hints: "Restock: On · Items".
     */
    private static Toggle.Builder builder(int x, int y, String sprite, String name, String tab,
                                          BooleanSupplier on, Consumer<Boolean> set, BooleanSupplier shown,
                                          @Nullable Supplier<String> detail, @Nullable Supplier<String> hints) {
        return builder(x, y, Identifier.fromNamespaceAndPath("inventoryplus", sprite), name, tab,
                on, set, shown, detail, hints);
    }

    private static Toggle.Builder builder(int x, int y, Identifier sprite, String name, String tab,
                                          BooleanSupplier on, Consumer<Boolean> set, BooleanSupplier shown,
                                          @Nullable Supplier<String> detail, @Nullable Supplier<String> hints) {
        Supplier<Component> tooltip = () -> {
            String first = name + ": " + (on.getAsBoolean() ? "On" : "Off");
            if (IPConfig.compactTooltips()) {
                return Component.literal(detail == null ? first : first + " · " + detail.get());
            }
            StringBuilder more = new StringBuilder();
            if (detail != null) more.append("\nGroup: ").append(detail.get());
            if (hints != null) more.append("\n").append(hints.get());
            return Component.literal(first).append(Component.literal(more.toString()).withStyle(ChatFormatting.GRAY));
        };
        return Toggle.builder().sprite(sprite)
                .at(x, y).size(SIZE, SIZE)
                .state(on, v -> {
                    if (SettingsMenu.ctrlClickOpens(tab)) return;
                    set.accept(v);
                })
                .tooltip(SettingsMenu.tooltip(name, tooltip))
                .visibleWhen(shown);
    }
}
