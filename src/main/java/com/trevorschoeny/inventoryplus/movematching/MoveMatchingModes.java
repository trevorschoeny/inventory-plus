package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.buttonmode.ModeState;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;
import com.trevorschoeny.inventoryplus.sort.SortButton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Move Matching's mode state: <b>one per direction</b>, because the two
 * directions do not describe one operation seen from two sides.
 *
 * <h3>Why the directions differ, and why only one of them pins</h3>
 *
 * <p>The stops (All, But-one, But-one-stack) are quantity rules about
 * what is left behind in the <em>source</em>. Which side the source is on
 * flips with the direction, and that is what decides the scope:
 *
 * <ul>
 *   <li>{@link Direction#IN} pulls out of the chest into the player. What
 *       gets left behind is left <em>in that chest</em>, so "leave a stack
 *       of cobble in this one" is a fact about a particular chest.
 *       Global by default, <b>pinnable</b>.</li>
 *   <li>{@link Direction#OUT} pushes out of the player into the chest.
 *       What gets left behind stays <em>on the player</em>, who is the
 *       same player at every chest in the world. There is nothing for a
 *       pin to vary, so this one is <b>global only</b> and middle-click
 *       does nothing on it.</li>
 * </ul>
 *
 * <p>The two globals are independent: changing the pull rule does not
 * touch the push rule. This reverses `features/move-matching.md` ("IN and
 * OUT share one mode") and `features/button-modes.md`, on Trev's call
 * 2026-09-06, after pinning one button was found to pin both.
 *
 * <p>Beware the naming: {@code IN} and {@code OUT} are relative to the
 * player inventory, not to the chest, so {@code IN} is the button a
 * player would describe as taking things <em>out of</em> the chest.
 *
 * <p>"This container" is the open external container, resolved the way
 * Sort resolves it, so a chest pinned for one is the same chest for the
 * other.
 */
public final class MoveMatchingModes {

    private MoveMatchingModes() {}

    /** Chest to player. Pinnable: its rule is about what stays in that chest. */
    public static final ModeState<MoveMatchingMode> IN =
            ModeState.of("move-matching-in", MoveMatchingMode.class, MoveMatchingMode.ALL);

    /** Player to chest. Global only: its rule is about what stays on the player. */
    public static final ModeState<MoveMatchingMode> OUT =
            ModeState.globalOnly("move-matching-out", MoveMatchingMode.class, MoveMatchingMode.ALL);

    public static void load() {
        IN.load();
        OUT.load();
    }

    /** The mode state a button or keybind for {@code direction} reads and cycles. */
    public static ModeState<MoveMatchingMode> state(Direction direction) {
        return direction == Direction.IN ? IN : OUT;
    }

    /** Identity of the open external container, or null if none is open or it cannot be identified. */
    public static @Nullable ContainerIdentity currentIdentity() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        AbstractContainerMenu menu = acs.getMenu();
        Slot anchor = SortButton.findExternalAnchor(menu);
        return anchor == null ? null : ContainerIdentity.fromHoveredSlot(anchor, menu);
    }

    /**
     * The mode in force for {@code direction} right now: the open
     * container's pin if that direction has one, else its global.
     */
    public static MoveMatchingMode current(Direction direction) {
        return state(direction).effective(currentIdentity());
    }
}
