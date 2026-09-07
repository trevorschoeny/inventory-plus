package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.buttonmode.ModeGestures;
import com.trevorschoeny.inventoryplus.buttonmode.ModeState;
import com.trevorschoeny.inventoryplus.buttonmode.PressFeedback;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import com.trevlar.menukit.core.Button;

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
 * <p>The two buttons are independent. A gesture on one leaves the other
 * alone, and only IN can be pinned at all, for the reasons in
 * {@link MoveMatchingModes}. OUT drops the middle-click line from its
 * tooltip rather than offering a gesture that does nothing.
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
        return Button.sprite(x, y, SIZE, SIZE, texture,
                        btn -> {
                            feedback.press();
                            triggerMoveMatching(direction);
                        })
                .tooltip(ModeGestures.tooltip(what, state, MoveMatchingModes::currentIdentity))
                .onSecondaryClick(click -> {
                    if (LockEditMode.isOn()) return;
                    gestures.accept(click);
                })
                .tint(ModeGestures.tint(state, MoveMatchingModes::currentIdentity, feedback))
                .showWhen(MoveMatchingButtons::shouldShow);
    }

    private static boolean shouldShow() {
        return IPConfig.moveMatchingShowButtons() && isMoveMatchingScreenNow();
    }

    private static boolean isMoveMatchingScreenNow() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && SlotGroupDetector.isMoveMatchingScreen(screen);
    }

    private static void triggerMoveMatching(Direction direction) {
        if (LockEditMode.isOn()) return;
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
