package com.trevorschoeny.inventoryplus.buttonmode;

import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;

import com.trevlar.menukit.core.Click;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The gesture, tooltip and tint wiring every mode-carrying button shares,
 * per {@code features/button-modes.md}. A button supplies its mode state,
 * a way to resolve the container it is acting on, and its own
 * {@link PressFeedback}; this returns the three suppliers MenuKit's
 * {@code Button} takes.
 *
 * <ul>
 *   <li>Right-click: next stop. Shift+right-click: previous. Middle-click:
 *       pin or release. None of them performs the action.</li>
 *   <li>Tooltip: what the button does, the current mode, "This container"
 *       only when pinned, then one gesture line naming only the gestures
 *       that button supports. A button with a single stop shows neither
 *       the mode line nor the gesture line.</li>
 *   <li>Tint: the press flash while it lasts, else pink when pinned.</li>
 * </ul>
 */
public final class ModeGestures {

    private ModeGestures() {}

    /** Pink, semi-transparent, inside the button border. */
    public static final int PIN_TINT = 0x66FF6EC7;

    public static <E extends Enum<E> & ModeStop> Consumer<Click> handler(
            ModeState<E> state, Supplier<@Nullable ContainerIdentity> identity, PressFeedback feedback) {
        return click -> {
            if (!state.hasCycle()) return;
            feedback.press();
            ContainerIdentity id = identity.get();
            if (click.isMiddle()) {
                state.togglePin(id);
            } else if (click.isShiftRight()) {
                state.cycle(id, -1);
            } else if (click.isRight()) {
                state.cycle(id, +1);
            }
        };
    }

    public static <E extends Enum<E> & ModeStop> Supplier<Component> tooltip(
            String what, ModeState<E> state, Supplier<@Nullable ContainerIdentity> identity) {
        return tooltip(() -> what, state, identity);
    }

    /**
     * As above, for a button whose own first line changes with its state.
     * The lock toggle is the case: it reads "Edit Locked Slots" or "Finish
     * Editing" depending on edit mode, and carries a mode underneath either.
     */
    public static <E extends Enum<E> & ModeStop> Supplier<Component> tooltip(
            Supplier<String> what, ModeState<E> state, Supplier<@Nullable ContainerIdentity> identity) {
        return () -> {
            List<String> lines = new ArrayList<>(4);
            lines.add(what.get());
            if (state.hasCycle()) {
                ContainerIdentity id = identity.get();
                lines.add(state.effective(id).label());
                if (state.isPinned(id)) lines.add("This container");
                // button-modes.md: list only the gestures this button supports.
                lines.add(state.pinnable()
                        ? "Right-click: change. Middle-click: pin."
                        : "Right-click: change.");
            }
            return Component.literal(String.join("\n", lines));
        };
    }

    public static <E extends Enum<E> & ModeStop> IntSupplier tint(
            ModeState<E> state, Supplier<@Nullable ContainerIdentity> identity, PressFeedback feedback) {
        return () -> {
            int flash = feedback.tint();
            if (flash != 0) return flash;
            return state.isPinned(identity.get()) ? PIN_TINT : 0;
        };
    }
}
