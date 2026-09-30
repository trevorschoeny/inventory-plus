package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.buttonmode.ModeGestures;
import com.trevorschoeny.inventoryplus.buttonmode.ModeState;
import com.trevorschoeny.inventoryplus.buttonmode.PressFeedback;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevlar.menukit.api.element.Button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Move Matching toolbar buttons. Each carries its own Move Matching mode
 * ({@code features/button-modes.md}): left-click runs the operation in the
 * mode in force, right-click changes the mode, shift+right-click goes
 * back, middle-click pins it to the open container.
 *
 * <p>The two buttons are independent: each carries its own mode and its
 * own pins, so a gesture on one leaves the other alone. See
 * {@link MoveMatchingModes} for why they are not one shared value.
 *
 * <p>Shown only on screens that pair the inventory with a simple
 * container; inert in locked-slots edit mode, gestures included.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    private static final Identifier TEXTURE_IN =
            Identifier.fromNamespaceAndPath("inventoryplus", "move_matching_in_button");
    private static final Identifier TEXTURE_OUT =
            Identifier.fromNamespaceAndPath("inventoryplus", "move_matching_out_button");

    public static final int SIZE = 9;

    public static Button toolbarOutButton(int x, int y) {
        return build(x, y, TEXTURE_OUT, "Move Matching Items Out", Direction.OUT);
    }

    public static Button toolbarInButton(int x, int y) {
        return build(x, y, TEXTURE_IN, "Move Matching Items In", Direction.IN);
    }

    private static Button build(int x, int y, Identifier texture, String what, Direction direction) {
        PressFeedback feedback = new PressFeedback();
        ModeState<MoveMatchingMode> state = MoveMatchingModes.state(direction);
        var gestures = ModeGestures.handler(state, MoveMatchingModes::currentIdentity, feedback);
        return Button.builder().sprite(texture).at(x, y).size(SIZE, SIZE)
                .onClick(() -> {
                    if (SettingsMenu.ctrlClickOpens(SettingsMenu.MOVE_MATCHING)) return;
                    feedback.press();
                    triggerMoveMatching(direction);
                })
                .tooltip(ModeGestures.tooltip(what, state, MoveMatchingModes::currentIdentity))
                .onSecondaryClick(gestures::accept)
                .tint(ModeGestures.tint(state, MoveMatchingModes::currentIdentity, feedback))
                .visibleWhen(MoveMatchingButtons::shouldShow)
                .build();
    }

    private static boolean shouldShow() {
        return IPConfig.moveMatchingEnabled() && IPConfig.moveMatchingShowButtons() && isMoveMatchingScreenNow();
    }

    private static boolean isMoveMatchingScreenNow() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && SlotGroupDetector.isMoveMatchingScreen(screen);
    }

    private static void triggerMoveMatching(Direction direction) {
        if (!IPConfig.moveMatchingEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        List<SlotGroup> groups = SlotGroupDetector.detect(screen);
        SlotGroup playerMainInv = findPlayerMainInv(groups);
        if (playerMainInv == null) return;
        MoveMatchingExecutor.execute(mc, playerMainInv, direction, MoveMatchingModes.current(direction));
    }

    public static @Nullable SlotGroup findPlayerMainInv(List<SlotGroup> groups) {
        for (SlotGroup g : groups) {
            if (g.role() == SlotRole.PLAYER_MAIN_INV) return g;
        }
        return null;
    }
}
