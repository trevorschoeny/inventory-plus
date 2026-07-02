package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import com.trevorschoeny.menukit.core.Button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Move Matching toolbar-button factories. Each method returns a
 * configured MK {@link Button} ready to drop into the IP toolbar
 * panel (see {@code toolbar/Toolbar.java}).
 *
 * <h3>Per-button visibility</h3>
 *
 * Both buttons attach {@code .showWhen(() -> isMoveMatchingScreen)} so
 * they appear only on screens that pair the player inventory with an
 * external simple container (chest, shulker, hopper, dispenser,
 * dropper). On pure inventory or specialized-UI screens the toolbar
 * stays visible (because the lock-edit toggle is always-on) but the
 * MM buttons hide — the toolbar's panel width auto-collapses to fit
 * just the visible elements.
 *
 * <h3>SlotGroup resolution at click time</h3>
 *
 * MK doesn't pass the IP-side {@link SlotGroup} into the click
 * callback. The button onClick resolves the player main inv via
 * {@link SlotGroupDetector} just-in-time, since
 * {@link MoveMatchingExecutor#execute} takes the IP SlotGroup. Cheap
 * (one menu walk) and keeps the executor unchanged.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    private static final Identifier TEXTURE_IN =
            Identifier.fromNamespaceAndPath("inventoryplus", "move_matching_in_button");
    private static final Identifier TEXTURE_OUT =
            Identifier.fromNamespaceAndPath("inventoryplus", "move_matching_out_button");

    public static final int SIZE = 9;

    /**
     * Move-Matching-OUT button. Triggers an OUT operation (inventory →
     * external container) on click.
     */
    public static Button toolbarOutButton(int x, int y) {
        return Button.sprite(x, y, SIZE, SIZE,
                        TEXTURE_OUT,
                        btn -> triggerMoveMatching(Direction.OUT))
                .tooltip(Component.literal("Move Matching Items Out"))
                .showWhen(MoveMatchingButtons::shouldShow);
    }

    /**
     * Move-Matching-IN button. Triggers an IN operation (external
     * container → inventory) on click.
     */
    public static Button toolbarInButton(int x, int y) {
        return Button.sprite(x, y, SIZE, SIZE,
                        TEXTURE_IN,
                        btn -> triggerMoveMatching(Direction.IN))
                .tooltip(Component.literal("Move Matching Items In"))
                .showWhen(MoveMatchingButtons::shouldShow);
    }

    /** Combined visibility: the user-config toggle AND the screen-scope check. */
    private static boolean shouldShow() {
        return IPConfig.moveMatchingShowButtons() && isMoveMatchingScreenNow();
    }

    private static boolean isMoveMatchingScreenNow() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && SlotGroupDetector.isMoveMatchingScreen(screen);
    }

    /** Edit-mode-gated executor trigger. */
    private static void triggerMoveMatching(Direction direction) {
        if (LockEditMode.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return;
        List<SlotGroup> groups = SlotGroupDetector.detect(screen);
        SlotGroup playerMainInv = findPlayerMainInv(groups);
        if (playerMainInv == null) return;
        MoveMatchingExecutor.execute(mc, playerMainInv, direction);
    }

    /** Returns the first {@link SlotRole#PLAYER_MAIN_INV} group, or null. */
    public static @Nullable SlotGroup findPlayerMainInv(List<SlotGroup> groups) {
        for (SlotGroup g : groups) {
            if (g.role() == SlotRole.PLAYER_MAIN_INV) return g;
        }
        return null;
    }
}
