package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Checkbox;
import com.trevlar.menukit.core.Divider;
import com.trevlar.menukit.core.Dropdown;
import com.trevlar.menukit.core.Flow;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.Section;
import com.trevlar.menukit.core.Slider;
import com.trevlar.menukit.core.TextLabel;
import com.trevlar.menukit.core.Toggle;
import com.trevorschoeny.keybindery.chord.ChordButton;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Lays out one settings tab's body top to bottom: section headings, lines of
 * text, and controls, each on its own row. A body is a list of MenuKit
 * elements positioned from the body's top-left, so this class only keeps a
 * running {@code y} and hands out rows.
 *
 * <p><b>Half wired.</b> A control with a real setting behind it reads that
 * setting every frame and saves on change ({@link Bool}, choices, sliders,
 * and keys, which are Keybindery's). A control whose setting does not exist
 * yet is a {@link Bool#placeholder}: it shows its planned default, greyed.
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

    /**
     * Whether the tab's feature is on. Set by the frame's on/off checkbox;
     * every control made after it greys and disables while this reads false
     * (Trev, 2026-09-27: when a feature is off, every setting in its tab is).
     * Captured per control when it is made, so the frame above the line is
     * never gated.
     */
    private BooleanSupplier featureOn = () -> true;

    SettingsBody(boolean greyed) {
        this.greyed = greyed;
    }

    SettingsBody() {
        this(false);
    }

    // ── Text ────────────────────────────────────────────────────────────

    /**
     * The top of every tab (Trev, 2026-09-27): the title, bold at twice size;
     * the description; a row with Reset to Defaults (greyed until reset
     * is built); then a line, with the tab's settings below it. For a tab
     * with no feature to turn off (General, Reach).
     */
    SettingsBody frame(String title, String description) {
        header(title, description, null);
        return rule();
    }

    /**
     * The same, with the feature's on/off toggle, reading "On" or "Off", left
     * of Reset to Defaults in that row. Everything below the line greys while
     * it is off.
     */
    SettingsBody frame(String title, String description, Bool use) {
        header(title, description, Toggle.linked(0, 0, 40, 14, use.get(), use.set(), use.unavailable())
                .label(() -> Component.literal(use.get().getAsBoolean() ? "On" : "Off")));
        featureOn = use.get();
        return rule();
    }

    private void header(String title, String description, @Nullable PanelElement onOff) {
        int titleColor = greyed ? GREYED_COLOR : HEADING_COLOR;
        out.add(new TextLabel(0, y, Component.literal(title).withStyle(ChatFormatting.BOLD), titleColor, false)
                .scale(2f));
        y += 2 * TEXT_ROW;
        out.add(new TextLabel(0, y, Component.literal(description), greyed ? GREYED_COLOR : TEXT_COLOR, false));
        y += TEXT_ROW + 4;
        List<PanelElement> row = new ArrayList<>();
        if (onOff != null) row.add(onOff);
        row.add(new Button(0, 0, width("Reset to Defaults"), 16, Component.literal("Reset to Defaults"),
                b -> {}, DISABLED));
        out.add(Flow.of(row).gap(10, 4).at(0, y));
        y += CONTROL_ROW + 2;
    }

    /** The line between a tab's frame and its settings, as wide as the body. */
    private SettingsBody rule() {
        // ponytail: a long divider; MenuKit caps it to the body's width.
        out.add(Divider.horizontal(0, y, 4000, 0xFF8B8B8B, 1));
        y += 6;
        return this;
    }

    /** {@code unavailable}, and also while the tab's feature is off. */
    private BooleanSupplier gated(BooleanSupplier unavailable) {
        BooleanSupplier on = featureOn;
        return () -> !on.getAsBoolean() || unavailable.getAsBoolean();
    }

    /** Settings text in {@code color}, turning grey while the tab's feature is off. */
    private TextLabel settingText(int x, int y, Component text, int color) {
        BooleanSupplier on = featureOn;
        return new TextLabel(x, y, () -> on.getAsBoolean() ? text : text.copy().withColor(GREYED_COLOR),
                color, false);
    }

    /** A section heading, with a gap above it unless it opens the body. */
    SettingsBody heading(String text) {
        if (y > 0) y += SECTION_GAP;
        out.add(settingText(0, y, Component.literal(text), greyed ? GREYED_COLOR : HEADING_COLOR));
        y += TEXT_ROW;
        out.add(Divider.horizontal(0, y - 2, 160, greyed ? GREYED_COLOR : 0xFF8B8B8B, 1));
        y += 2;
        return this;
    }

    /**
     * A collapsible section: MenuKit's {@link Section}, closed to start. Its
     * header is an arrow, an optional colour swatch, the title and a grey
     * {@code summary} read every frame; a click opens it. {@code content}
     * fills a fresh body laid out from the section's own top-left.
     *
     * <p>Rows after it are placed as if it were closed. Opening it reports
     * its content height to the panel's reflow, which pushes them down.
     */
    SettingsBody section(String title, Supplier<Component> summary, @Nullable Integer swatch,
                         Consumer<SettingsBody> content) {
        if (y > 0) y += 4;
        SettingsBody inner = new SettingsBody(greyed);
        inner.featureOn = featureOn;
        content.accept(inner);
        Section.Builder section = Section.builder(Component.literal(title))
                .at(0, y)
                .summary(summary)
                .content(inner.build());
        if (swatch != null) section.swatch(swatch);
        if (greyed) section.colors(GREYED_COLOR, GREYED_COLOR);
        out.add(section.build());
        y += Section.HEADER_HEIGHT + 2;
        return this;
    }

    /** A line of explanatory text. */
    SettingsBody line(String text) {
        return line(0, Component.literal(text));
    }

    SettingsBody line(int indent, Component text) {
        out.add(settingText(indent, y, text, greyed ? GREYED_COLOR : TEXT_COLOR));
        y += TEXT_ROW;
        return this;
    }

    // ── Placeholder controls (all disabled) ─────────────────────────────

    /**
     * A boolean setting as a control sees it: where it reads, where it
     * writes, and when it is greyed. The control reads {@code get} every
     * frame, so a setting changed elsewhere (another tab, the old screen, a
     * setter that turns a sibling off) shows at once.
     */
    record Bool(BooleanSupplier get, Consumer<Boolean> set, BooleanSupplier unavailable) {

        /** A real setting, always available. */
        static Bool of(BooleanSupplier get, Consumer<Boolean> set) {
            return new Bool(get, set, () -> false);
        }

        /** The same setting, greyed while {@code available} is false (its parent is off). */
        Bool onlyWhen(BooleanSupplier available) {
            return new Bool(get, set, () -> !available.getAsBoolean());
        }

        /** A control with no setting behind it yet: shows {@code value}, always greyed. */
        static Bool placeholder(boolean value) {
            return new Bool(() -> value, v -> {}, DISABLED);
        }
    }

    SettingsBody checkbox(String label, Bool setting) {
        return checkbox(0, label, setting);
    }

    /** A sub-setting, indented under the setting it belongs to. */
    SettingsBody subCheckbox(String label, Bool setting) {
        return checkbox(INDENT, label, setting);
    }

    /** A checkbox with no setting behind it yet, shown at {@code on} and greyed. */
    SettingsBody checkbox(String label, boolean on) {
        return checkbox(0, label, Bool.placeholder(on));
    }

    private SettingsBody checkbox(int x, String label, Bool setting) {
        out.add(Checkbox.linked(x, y, setting.get(), Component.literal(label), setting.set(),
                gated(setting.unavailable())));
        y += TEXT_ROW + 2;
        return this;
    }

    /** One checkbox in a reach list: what it names, and whether it starts checked. */
    record Place(Component name, boolean on) {}

    /**
     * A reach list: one checkbox per place, flowing left to right and wrapping
     * to the body width. Titled when a feature has more than one list.
     */
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
        out.add(settingText(INDENT, y + 4, text, greyed ? GREYED_COLOR : TEXT_COLOR));
        int x = INDENT + Minecraft.getInstance().font.width(text) + 6;
        for (String label : buttons) {
            int w = width(label);
            out.add(new Button(x, y, w, 16, Component.literal(label), b -> {}, DISABLED));
            x += w + 4;
        }
        y += CONTROL_ROW;
        return this;
    }

    /** A row of disabled buttons, wrapping when the body is narrow. */
    SettingsBody buttons(String... labels) {
        List<PanelElement> row = new ArrayList<>();
        for (String label : labels) {
            row.add(new Button(0, 0, width(label), 16, Component.literal(label), b -> {}, DISABLED));
        }
        out.add(Flow.of(row).gap(4, 4).at(0, y));
        y += CONTROL_ROW;
        return this;
    }

    /** A button that already works, for the few actions the scaffold keeps. */
    SettingsBody button(String label, Runnable action) {
        out.add(new Button(0, y, width(label), 16, Component.literal(label), b -> action.run()));
        y += CONTROL_ROW;
        return this;
    }

    /**
     * A setting with a few named values, bound to its config: the dropdown
     * reads {@code get} every frame and hands a pick to {@code set}, which
     * saves. {@code unavailable} greys it while a parent setting is off.
     */
    <T> SettingsBody choice(String label, List<T> values, Function<T, String> name,
                            Supplier<T> get, Consumer<T> set, BooleanSupplier unavailable) {
        int labelW = Minecraft.getInstance().font.width(label);
        out.add(settingText(INDENT, y + 4, Component.literal(label), greyed ? GREYED_COLOR : TEXT_COLOR));
        out.add(Dropdown.<T>builder()
                .at(INDENT + labelW + 6, y)
                .triggerSize(110, 16)
                .items(values)
                .label(v -> Component.literal(name.apply(v)))
                .selection(get, set)
                .disabledWhen(gated(unavailable))
                .build());
        y += CONTROL_ROW;
        return this;
    }

    /** A dropdown shown at a fixed value and disabled (an Inventory Max stand-in). */
    <T> SettingsBody choice(String label, List<T> values, Function<T, String> name, T current) {
        return choice(label, values, name, () -> current, v -> {}, DISABLED);
    }

    /**
     * A whole-number setting on a slider, bound to its config the same way.
     *
     * <p>ponytail: every drag step calls {@code set}, and so saves the config
     * file; fine for a small JSON file. Save on release if it ever shows.
     */
    SettingsBody slider(String label, int min, int max, IntSupplier get, IntConsumer set, BooleanSupplier unavailable) {
        out.add(Slider.builder()
                .at(0, y)
                .size(180, 16)
                .value(() -> (get.getAsInt() - min) / (double) (max - min),
                        v -> set.accept(min + (int) Math.round(v * (max - min))))
                .label(v -> Component.literal(label + ": " + get.getAsInt()))
                .disabledWhen(gated(unavailable))
                .build());
        y += CONTROL_ROW;
        return this;
    }

    /** A keybind: its name, the key it is on now, and a change button for later. */
    SettingsBody key(KeyMapping key) {
        return key(key, Component.translatable(key.getName()));
    }

    /** A key under a label of its own ("Key" in a lock group's section). */
    SettingsBody key(KeyMapping key, Component label) {
        out.add(new ChordButton(key).label(label).disabledWhen(gated(() -> false)).at(0, y));
        y += CONTROL_ROW;
        return this;
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
