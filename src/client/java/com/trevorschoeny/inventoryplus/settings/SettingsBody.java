package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Divider;
import com.trevlar.menukit.api.element.Dropdown;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Section;
import com.trevlar.menukit.api.element.Slider;
import com.trevlar.menukit.api.element.TextField;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.TextRole;
import com.trevlar.menukit.api.element.Toggle;
import com.trevorschoeny.keybindery.chord.ChordButton;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * <p>A control with a real setting behind it reads that setting every frame and saves on change ({@link Bool}, choices, sliders,
 * and keys, which are Keybindery's). A control whose setting does not exist
 * yet is a {@link Bool#placeholder}: it shows its planned default, greyed.

 *
 * <p>No text here is hand-coloured: each label names its {@link TextRole}
 * (heading, body, disabled) and the kind of screen the menu is made as turns
 * that into a colour, so one body reads on the options look and the inventory
 * look alike (MenuKit 6.1.0).
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

    /**
     * A divider's colour on the inventory look; the options look draws vanilla's
     * separator and ignores it.
     */
    private static final int LINE_COLOR = 0xFF8B8B8B;

    /**
     * Which sections are open, by title, for the session. Keyed by title, so
     * two sections with one title share a state. Key by id if that ever
     * shows.
     */
    private static final Map<String, Boolean> OPEN = new HashMap<>();

    private final List<PanelElement> out = new ArrayList<>();
    private int y = 0;

    /**
     * Whether the tab's feature is on. Set by the frame's on/off checkbox;
     * every control made after it greys and disables while this reads false,
     * since when a feature is off, every setting in its tab is too.
     * Captured per control when it is made, so the frame above the line is
     * never gated.
     */
    private BooleanSupplier featureOn = () -> true;

    SettingsBody() {}

    // Text

    /**
     * The heading alone, with no Reset or On/Off row: for General, small
     * enough to need no reset of its own. Reset everything
     * still lives in its settings.
     */
    SettingsBody frame(String title, String description) {
        header(title, description);
        return rule();
    }

    /**
     * The top of every tab: the tab's title, its description, one row at the
     * left with Reset to Defaults and (on a feature tab) the On/Off switch
     * after it, then the line, so the row sits right under the description.
     * The settings follow. For a tab with no feature to turn off (Reach),
     * Reset stands alone. {@code onReset} runs on the button,
     * normally a confirm ({@link SettingsMenu#confirm}); {@code null} leaves
     * it greyed.
     */
    SettingsBody frame(String title, String description, @Nullable Runnable onReset) {
        header(title, description);
        return controls(null, onReset).rule();
    }

    /**
     * The same, with the feature's On/Off switch right of Reset to Defaults.
     * Everything below the switch greys while it is off.
     */
    SettingsBody frame(String title, String description, Bool use, @Nullable Runnable onReset) {
        header(title, description);
        PanelElement onOff = Toggle.builder().size(40, 16)
                .state(use.get(), use.set())
                .disabledWhen(use.unavailable())
                .label(() -> Component.literal(use.get().getAsBoolean() ? "On" : "Off"))
                .build();
        controls(onOff, onReset).rule();
        featureOn = use.get();
        return this;
    }

    private void header(String title, String description) {
        // The mod's name is not here: it is the screen's title on the options
        // look, and one bar across the menu panel on the inventory look
        // (SettingsMenu.nameBar).
        out.add(TextLabel.builder().at(0, y).text(Component.literal(title).withStyle(ChatFormatting.BOLD))
                .role(TextRole.HEADING).build());
        y += TEXT_ROW + 2;
        out.add(TextLabel.builder().at(0, y).text(Component.literal(description))
                .role(TextRole.BODY).build());
        y += TEXT_ROW + 4;
    }

    /** The line between the tab's heading and everything it controls. */
    private SettingsBody rule() {
        out.add(Divider.horizontal().at(0, y).color(LINE_COLOR).build());
        y += 6;
        return this;
    }

    /**
     * Under the description, above the line: Reset to Defaults furthest
     * left, then the On/Off switch when the tab has one, left-aligned.
     */
    private SettingsBody controls(@Nullable PanelElement onOff, @Nullable Runnable onReset) {
        Flow.Builder row = Flow.builder().at(0, y).gap(10, 4);
        row.add(Button.builder().label(Component.literal("Reset to Defaults")).size(0, 16)
                .onClick(() -> { if (onReset != null) onReset.run(); })
                .disabledWhen(onReset == null ? DISABLED : () -> false)
                .build());
        if (onOff != null) row.add(onOff);
        out.add(row.build());
        y += CONTROL_ROW;
        return this;
    }

    /** {@code unavailable}, and also while the tab's feature is off. */
    private BooleanSupplier gated(BooleanSupplier unavailable) {
        BooleanSupplier on = featureOn;
        return () -> !on.getAsBoolean() || unavailable.getAsBoolean();
    }


    /** Settings text in {@code role}, drawn in the disabled colour while the tab's feature is off. */
    private TextLabel settingText(int x, int y, Component text, TextRole role) {
        return settingText(x, y, text, role, null);
    }

    /** The same, with a hover tooltip when {@code tooltip} is given. */
    private TextLabel settingText(int x, int y, Component text, TextRole role, @Nullable Component tooltip) {
        BooleanSupplier on = featureOn;
        TextLabel.Builder label = TextLabel.builder().at(x, y).text(text).role(role)
                .disabledWhen(() -> !on.getAsBoolean());
        if (tooltip != null) label.tooltip(tooltip);
        return label.build();
    }

    /**
     * A category: its title and a short line under it, with a gap above.
     * Every setting in a tab sits in one. Settings that would each be a
     * category of one go headerless at the top of the tab, straight under the
     * line, rather than under a "Misc." header.
     */
    SettingsBody heading(String text) {
        if (y > 0) y += SECTION_GAP;
        out.add(settingText(0, y, Component.literal(text), TextRole.HEADING));
        y += TEXT_ROW;
        out.add(Divider.horizontal().at(0, y - 2).size(160, 1).color(LINE_COLOR).build());
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
        SettingsBody inner = new SettingsBody();
        inner.featureOn = featureOn;
        content.accept(inner);
        Section.Builder section = Section.builder(Component.literal(title))
                .at(0, y)
                // Open state lives here, not in the Section, so a rebuild of the
                // menu (a lock group added or recoloured) leaves open what was.
                .state(() -> OPEN.getOrDefault(title, false), v -> OPEN.put(title, v))
                .summary(summary)
                .content(inner.build());
        if (swatch != null) section.swatch(swatch);
        out.add(section.build());
        y += Section.HEADER_HEIGHT + 2;
        return this;
    }

    /** A line of explanatory text. */
    SettingsBody line(String text) {
        return line(0, Component.literal(text));
    }

    SettingsBody line(int indent, Component text) {
        return line(indent, text, null);
    }

    /** A line that explains more on hover (a lock group's kind). */
    SettingsBody line(int indent, Component text, @Nullable Component tooltip) {
        out.add(settingText(indent, y, text, TextRole.BODY, tooltip));
        y += TEXT_ROW;
        return this;
    }

    // Placeholder controls (all disabled)

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

    // A setting is its text on one line and its control below it, except a
    // checkbox, which is small enough to sit inline with its text to the
    // right. A sub-setting indents.

    /** A setting's text, on its own line, greying while the feature is off. */
    private void label(int x, Component text) {
        label(x, text, null);
    }

    private void label(int x, Component text, @Nullable Component tooltip) {
        out.add(settingText(x, y, text, TextRole.BODY, tooltip));
        y += TEXT_ROW;
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
        out.add(Checkbox.builder().at(x, y).label(Component.literal(label))
                .state(setting.get(), setting.set())
                .disabledWhen(gated(setting.unavailable()))
                .build());
        y += TEXT_ROW + 4;
        return this;
    }

    /**
     * One checkbox in a reach list, bound to the record: checked while the
     * operation may use this group ({@code on}), and {@code set} writes it.
     * {@code unavailable} greys a box the player cannot change yet.
     */
    record Place(Component name, BooleanSupplier on, Consumer<Boolean> set, BooleanSupplier unavailable) {

        /** A box with nothing behind it yet, shown at {@code on} and greyed. */
        static Place fixed(Component name, boolean on) {
            return new Place(name, () -> on, v -> {}, DISABLED);
        }
    }

    /**
     * A reach list: one checkbox per place, flowing left to right and wrapping
     * to the body width. Titled when a feature has more than one list.
     */
    SettingsBody reach(@Nullable String title, Collection<Place> places) {
        if (title != null) line(0, Component.literal(title));
        Flow.Builder boxes = Flow.builder().at(INDENT, y).gap(10, 4);
        for (Place place : places) {
            boxes.add(Checkbox.builder().label(place.name()).state(place.on(), place.set())
                    .disabledWhen(gated(place.unavailable())).build());
        }
        out.add(boxes.build());
        y += TEXT_ROW + 2;
        return this;
    }

    /** A setting whose control is a row of buttons, greyed until they are built. */
    SettingsBody actions(String label, String... buttons) {
        label(0, Component.literal(label));
        return buttons(buttons);
    }

    /** A row of disabled buttons, wrapping when the body is narrow. */
    SettingsBody buttons(String... labels) {
        Flow.Builder row = Flow.builder().at(0, y).gap(4, 4);
        for (String label : labels) {
            row.add(Button.builder().label(Component.literal(label)).size(0, 16).disabledWhen(DISABLED).build());
        }
        out.add(row.build());
        y += CONTROL_ROW;
        return this;
    }

    /** A button that works, with its action. */
    record Action(String label, Runnable run, @Nullable Component tooltip) {
        Action(String label, Runnable run) {
            this(label, run, null);
        }
    }

    /** A setting whose control is a row of working buttons: its text above them. */
    SettingsBody actions(String label, Action... actions) {
        label(0, Component.literal(label));
        return actions(actions);
    }

    /** A row of working buttons, wrapping when the body is narrow. */
    SettingsBody actions(Action... actions) {
        Flow.Builder row = Flow.builder().at(0, y).gap(4, 4);
        for (Action a : actions) {
            Button.Builder button = Button.builder().label(Component.literal(a.label())).size(0, 16)
                    .onClick(a.run()).disabledWhen(gated(() -> false));
            if (a.tooltip() != null) button.tooltip(a.tooltip());
            row.add(button.build());
        }
        out.add(row.build());
        y += CONTROL_ROW;
        return this;
    }

    /**
     * A setting that is a line of text: its label above, then a field holding
     * {@code value} and a button that hands the field's text to {@code apply}.
     */
    SettingsBody textSetting(String label, String value, String button, Consumer<String> apply) {
        label(0, Component.literal(label));
        // The field is a lens (MenuKit 6.0.0): its draft lives here, starting at
        // the saved value, and the button hands the draft on.
        String[] draft = {value};
        out.add(TextField.builder().at(0, y).size(120, 16).state(() -> draft[0], v -> draft[0] = v).build());
        out.add(Button.builder().at(124, y).label(Component.literal(button)).size(0, 16)
                .onClick(() -> apply.accept(draft[0])).disabledWhen(gated(() -> false)).build());
        y += CONTROL_ROW;
        return this;
    }

    /** A button that already works, for the few actions the scaffold keeps. */
    SettingsBody button(String label, Runnable action) {
        out.add(Button.builder().at(0, y).label(Component.literal(label)).size(0, 16).onClick(action).build());
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
        label(0, Component.literal(label));
        out.add(Dropdown.<T>builder()
                .at(0, y)
                .size(110, 16)
                .items(values)
                .label(v -> Component.literal(name.apply(v)))
                .state(get, set)
                .disabledWhen(gated(unavailable))
                .build());
        y += CONTROL_ROW;
        return this;
    }

    /**
     * A whole-number setting on a slider, bound to its config the same way.
     *
     * <p>Every drag step calls {@code set}, and so saves the config file. That
     * is fine for a small JSON file. Save on release if it ever shows.
     */
    SettingsBody slider(String label, int min, int max, IntSupplier get, IntConsumer set, BooleanSupplier unavailable) {
        label(0, Component.literal(label));
        out.add(Slider.ofInts(min, max)
                .at(0, y)
                .size(180, 16)
                .state(get::getAsInt, set::accept)
                .disabledWhen(gated(unavailable))
                .build());
        y += CONTROL_ROW;
        return this;
    }

    /** A key: its name, then Keybindery's button, which binds, resets and saves on its own. */
    SettingsBody key(KeyMapping key) {
        return key(key, Component.translatable(key.getName()));
    }

    /** A key under text of its own ("Key" in a lock group's section). */
    SettingsBody key(KeyMapping key, Component label) {
        return key(key, label, null);
    }

    /** A key whose text explains more on hover (Sort's: which container it sorts). */
    SettingsBody key(KeyMapping key, Component label, @Nullable Component tooltip) {
        return key(key, label, tooltip, () -> false);
    }

    /** The same, greyed while {@code unavailable} (its checkbox above is off). */
    SettingsBody key(KeyMapping key, Component label, @Nullable Component tooltip, BooleanSupplier unavailable) {
        label(0, label, tooltip);
        out.add(ChordButton.builder(key).at(0, y).disabledWhen(gated(unavailable)).build());
        y += CONTROL_ROW;
        return this;
    }

    /** Any other row: {@code atY} builds its elements at the row's y; {@code height} is what it takes up. */
    SettingsBody row(IntFunction<List<PanelElement>> atY, int height) {
        out.addAll(atY.apply(y));
        y += height;
        return this;
    }

    List<PanelElement> build() {
        return List.copyOf(out);
    }
}
