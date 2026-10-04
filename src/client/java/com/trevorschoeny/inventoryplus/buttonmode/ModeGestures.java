package com.trevorschoeny.inventoryplus.buttonmode;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;

import com.trevlar.menukit.api.element.Click;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The gesture, tooltip and tint wiring every mode-carrying button shares,
 * per {@code features/button-modes.md}. A button supplies its mode state,
 * a way to resolve the container it is acting on, and its own
 * {@link PressFeedback}; this returns the suppliers MenuKit's
 * {@code Button} takes.
 *
 * <ul>
 *   <li>Right-click: next stop. Shift+right-click: previous (kept, not
 *       advertised). Middle-click: pin or release, on a button that pins
 *       here ({@code pinHere}), or to release a pin made before.</li>
 *   <li>Tooltip (Trev, 2026-10-03): the action with its mode in one line
 *       ("Sort by Category"), "Right click: cycle", and only on a button
 *       that pins here, "Middle click: pin to container". Compact
 *       tooltips (General) show one short line ("Sort: Category").</li>
 *   <li>Tint: the press flash while it lasts, else pink when pinned.</li>
 * </ul>
 */
public final class ModeGestures {

    private ModeGestures() {}

    /** Pink, semi-transparent, inside the button border. */
    public static final int PIN_TINT = 0x66FF6EC7;

    public static <E extends Enum<E> & ModeStop> Consumer<Click> handler(
            ModeState<E> state, Supplier<@Nullable ContainerIdentity> identity, boolean pinHere,
            PressFeedback feedback) {
        return click -> {
            if (!state.hasCycle()) return;
            ContainerIdentity id = identity.get();
            if (click.isMiddle()) {
                if (!pinHere && !state.isPinned(id)) return;
                feedback.press();
                state.togglePin(id);
            } else if (click.isRight()) {
                feedback.press();
                state.cycle(id, click.isShiftRight() ? -1 : +1);
            }
        };
    }

    /**
     * {@code action} turns the mode in force into the first line ("Sort by
     * Category"); the gesture lines follow. With Compact tooltips on
     * (General) the tooltip is {@code compact}'s one short line instead
     * ("Sort: Category"), so it takes little room.
     */
    public static <E extends Enum<E> & ModeStop> Supplier<Component> tooltip(
            Function<E, String> action, Function<E, String> compact, ModeState<E> state,
            Supplier<@Nullable ContainerIdentity> identity, boolean pinHere) {
        return () -> {
            ContainerIdentity id = identity.get();
            if (IPConfig.compactTooltips()) return Component.literal(compact.apply(state.effective(id)));
            List<String> lines = new ArrayList<>(3);
            lines.add(action.apply(state.effective(id)));
            if (state.hasCycle()) {
                lines.add("Right click: cycle");
                if (state.isPinned(id)) lines.add("Middle click: unpin");
                else if (pinHere && state.pinnable() && id != null) lines.add("Middle click: pin to container");
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
