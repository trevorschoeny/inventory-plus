package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Checkbox;
import com.trevlar.menukit.core.Divider;
import com.trevlar.menukit.core.Dropdown;
import com.trevlar.menukit.core.Flow;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.Slider;
import com.trevlar.menukit.core.TextLabel;
import com.trevlar.menukit.core.Toggle;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Lays out one settings tab's body top to bottom: section headings, lines of
 * text, and controls, each on its own row. A body is a list of MenuKit
 * elements positioned from the body's top-left, so this class only keeps a
 * running {@code y} and hands out rows.
 *
 * <p><b>Scaffold stage.</b> Every control here is disabled and wired to
 * nothing: it shows a setting's shape and its current default, and neither
 * reads nor writes config. The one exception is {@link #button(String, Runnable)},
 * for the few buttons that already do something (the old settings screen).
 *
 * <p>A {@code greyed} body is an Inventory Max stand-in: headings and text are
 * drawn in the disabled grey as well, so the whole tab reads as unavailable.
 *
 * <p>Rows are positioned assuming single-line text. A label that wraps at a
 * narrow width pushes the rows below it down; MenuKit's panel reflow does that,
 * not this class.
 */
final class SettingsBody {

    /** Every placeholder control is disabled until the menu is wired. */
    private static final BooleanSupplier DISABLED = () -> true;

    private static final int INDENT = 12;
    private static final int TEXT_ROW = 12;
    private static final int CONTROL_ROW = 20;
    private static final int SECTION_GAP = 8;

    private static final int HEADING_COLOR = TextLabel.COLOR_DARK;
    private static final int TEXT_COLOR = 0xFF555555;
    private static final int GREYED_COLOR = 0xFF8B8B8B;

    private final List<PanelElement> out = new ArrayList<>();
    private final boolean greyed;
    private int y = 0;

    SettingsBody(boolean greyed) {
        this.greyed = greyed;
    }

    SettingsBody() {
        this(false);
    }

    // ── Text ────────────────────────────────────────────────────────────

    /** A section heading, with a gap above it unless it opens the body. */
    SettingsBody heading(String text) {
        if (y > 0) y += SECTION_GAP;
        out.add(new TextLabel(0, y, Component.literal(text), greyed ? GREYED_COLOR : HEADING_COLOR, false));
        y += TEXT_ROW;
        out.add(Divider.horizontal(0, y - 2, 160, greyed ? GREYED_COLOR : 0xFF8B8B8B, 1));
        y += 2;
        return this;
    }

    /**
     * A collapsible section's header row: an arrow, the title, and a grey
     * summary of what is inside. The rows that follow, when {@code open},
     * are the section's contents.
     *
     * <p>ponytail: drawn, not working. MenuKit has no disclosure element yet
     * (asked for 2026-09-26), and a body's rows sit at fixed heights, so the
     * scaffold shows one section open and the rest closed. Swap in MenuKit's
     * element when it lands.
     */
    SettingsBody section(String title, String summary, boolean open) {
        return section(title, summary, open, null);
    }

    /** A section whose header carries a colour swatch after the arrow (a lock group's colour). */
    SettingsBody section(String title, String summary, boolean open, @Nullable Integer swatch) {
        if (y > 0) y += 4;
        String arrow = open ? "▼ " : "▶ ";
        int x = 0;
        out.add(new TextLabel(x, y, Component.literal(arrow), greyed ? GREYED_COLOR : HEADING_COLOR, false));
        x += Minecraft.getInstance().font.width(arrow);
        if (swatch != null) {
            out.add(Divider.horizontal(x, y, 8, swatch, 8));
            x += 12;
        }
        out.add(new TextLabel(x, y, Component.literal(title), greyed ? GREYED_COLOR : HEADING_COLOR, false));
        x += Minecraft.getInstance().font.width(title) + 8;
        out.add(new TextLabel(x, y, Component.literal(summary), GREYED_COLOR, false));
        y += TEXT_ROW + 2;
        return this;
    }

    /** A line of explanatory text. */
    SettingsBody line(String text) {
        return line(0, Component.literal(text));
    }

    SettingsBody line(int indent, Component text) {
        out.add(new TextLabel(indent, y, text, greyed ? GREYED_COLOR : TEXT_COLOR, false));
        y += TEXT_ROW;
        return this;
    }

    // ── Placeholder controls (all disabled) ─────────────────────────────

    /** A feature's on/off switch, shown at its default. */
    SettingsBody onOff(String label, boolean on) {
        out.add(new Toggle(0, y, 40, 14, on, v -> {}, DISABLED).label(Component.literal(label)));
        y += CONTROL_ROW - 2;
        return this;
    }

    SettingsBody checkbox(String label, boolean on) {
        return checkbox(0, label, on);
    }

    /** A sub-setting, indented under the setting it belongs to. */
    SettingsBody subCheckbox(String label, boolean on) {
        return checkbox(INDENT, label, on);
    }

    private SettingsBody checkbox(int x, String label, boolean on) {
        out.add(new Checkbox(x, y, on, Component.literal(label), v -> {}, DISABLED));
        y += TEXT_ROW + 2;
        return this;
    }

    /**
     * A reach list: one checkbox per place, flowing left to right and wrapping
     * to the body width. Titled when a feature has more than one list.
     */
    SettingsBody reach(@Nullable String title, List<Component> places) {
        return reach(title, places.stream().map(name -> new Place(name, true)).toList());
    }

    /** One checkbox in a reach list: what it names, and whether it starts checked. */
    record Place(Component name, boolean on) {}

    SettingsBody reach(@Nullable String title, Collection<Place> places) {
        if (title != null) line(0, Component.literal(title));
        List<PanelElement> boxes = new ArrayList<>();
        for (Place place : places) boxes.add(new Checkbox(0, 0, place.on(), place.name(), v -> {}, DISABLED));
        out.add(Flow.of(boxes).gap(10, 4).at(INDENT, y));
        y += TEXT_ROW + 2;
        return this;
    }

    /**
     * Indented text followed straight away by disabled buttons, for a value
     * with actions ("Key: L [Change]"). Laid left to right from the text's own
     * width, not at fixed columns, so a narrow body does not crush it.
     */
    SettingsBody valueRow(Component text, String... buttons) {
        out.add(new TextLabel(INDENT, y + 4, text, greyed ? GREYED_COLOR : TEXT_COLOR, false));
        int x = INDENT + Minecraft.getInstance().font.width(text) + 6;
        for (String label : buttons) {
            int w = width(label);
            out.add(new Button(x, y, w, 16, Component.literal(label), b -> {}, DISABLED));
            x += w + 4;
        }
        y += CONTROL_ROW;
        return this;
    }

    /** A row of disabled buttons. */
    SettingsBody buttons(String... labels) {
        int x = 0;
        for (String label : labels) {
            int w = width(label);
            out.add(new Button(x, y, w, 16, Component.literal(label), b -> {}, DISABLED));
            x += w + 4;
        }
        y += CONTROL_ROW;
        return this;
    }

    /** A button that already works, for the few actions the scaffold keeps. */
    SettingsBody button(String label, Runnable action) {
        out.add(new Button(0, y, width(label), 16, Component.literal(label), b -> action.run()));
        y += CONTROL_ROW;
        return this;
    }

    /** A setting with a few named values, shown on its current one. */
    <T> SettingsBody choice(String label, List<T> values, Function<T, String> name, T current) {
        int labelW = Minecraft.getInstance().font.width(label);
        out.add(new TextLabel(INDENT, y + 4, Component.literal(label), greyed ? GREYED_COLOR : TEXT_COLOR, false));
        out.add(Dropdown.<T>builder()
                .at(INDENT + labelW + 6, y)
                .triggerSize(110, 16)
                .items(values)
                .label(v -> Component.literal(name.apply(v)))
                .selection(() -> current, v -> {})
                .disabledWhen(DISABLED)
                .build());
        y += CONTROL_ROW;
        return this;
    }

    /** A whole-number setting on a slider, shown at its current value. */
    SettingsBody slider(String label, int min, int max, int value) {
        out.add(Slider.builder()
                .at(0, y)
                .size(180, 16)
                .value(() -> (value - min) / (double) (max - min), v -> {})
                .label(v -> Component.literal(label + ": " + value))
                .disabledWhen(DISABLED)
                .build());
        y += CONTROL_ROW;
        return this;
    }

    /** A keybind: its name, the key it is on now, and a change button for later. */
    SettingsBody key(KeyMapping key) {
        return key(Component.translatable(key.getName()), key.getTranslatedKeyMessage());
    }

    /** A keybind this mod cannot read, written out (an Inventory Max stand-in). */
    SettingsBody key(Component name, Component current) {
        Component text = name.copy().append(": ").append(current);
        out.add(new TextLabel(0, y + 4, text, greyed ? GREYED_COLOR : TEXT_COLOR, false));
        int x = Math.max(150, Minecraft.getInstance().font.width(text) + 8);
        out.add(new Button(x, y, 50, 16, Component.literal("Change"), b -> {}, DISABLED));
        y += CONTROL_ROW;
        return this;
    }

    /** Any other row: {@code atY} builds its elements at the row's y; {@code height} is what it takes up. */
    SettingsBody row(IntFunction<List<PanelElement>> atY, int height) {
        out.addAll(atY.apply(y));
        y += height;
        return this;
    }

    boolean greyed() {
        return greyed;
    }

    int textColor() {
        return greyed ? GREYED_COLOR : TEXT_COLOR;
    }

    List<PanelElement> build() {
        return List.copyOf(out);
    }

    private static int width(String label) {
        return Minecraft.getInstance().font.width(label) + 12;
    }
}
