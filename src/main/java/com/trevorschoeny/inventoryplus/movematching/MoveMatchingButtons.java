package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.core.SlotGroupRegion;
import com.trevorschoeny.menukit.inject.SlotGroupPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registers the Move Matching IN + OUT buttons as a MenuKit panel
 * anchored above the player's main inventory's 3×9 grid, right-aligned
 * to the grid's right edge.
 *
 * <h3>Activation</h3>
 *
 * Visible only on screens that pair the player inventory with an
 * external simple container (chest, shulker, hopper, dispenser,
 * dropper) — see {@link SlotGroupDetector#isMoveMatchingScreen}. On
 * pure inventory screens or specialized UIs the panel hides via its
 * {@link Panel#showWhen} predicate.
 *
 * <h3>Why MK Button.sprite + SlotGroupPanelAdapter</h3>
 *
 * Phase 18q in MenuKit (2026-05-16) added custom-sprite Button +
 * Toggle, switched render integration to {@code Screen.addRenderableOnly}
 * (tooltips now work via {@code setTooltipForNextFrame} from within
 * render), and made panel-level press + hover affordances first-class.
 * The pre-MK-18q implementation (a vanilla {@link
 * net.minecraft.client.gui.components.AbstractWidget} subclass with a
 * separate pressed-state PNG, hand-coded position math, and manual
 * tooltip queuing) is replaced by a {@link Button#sprite} call plus a
 * one-PNG sprite. MK provides hover overlay, HSL-inverted press
 * affordance, and tooltip wiring out of the box.
 *
 * <h3>Layout ordering across panels</h3>
 *
 * {@link SlotGroupRegion#TOP_ALIGN_RIGHT} flows leftward — the first
 * adapter registered sits rightmost, subsequent adapters stack to the
 * left. {@link com.trevorschoeny.inventoryplus.InventoryPlusClient}
 * registers MM first then lock-edit, giving the visual order
 * {@code [LockEdit] [MM Out] [MM In]} with MM In flush against the
 * grid's right edge.
 *
 * <h3>SlotGroup resolution at click time</h3>
 *
 * MK's {@code SlotGroupCategory.PLAYER_INVENTORY} anchors the panel
 * but doesn't expose the IP-side {@link SlotGroup}. The button onClick
 * callbacks resolve the player main inv via {@link SlotGroupDetector}
 * just-in-time, since {@link MoveMatchingExecutor#execute} takes the
 * IP SlotGroup. Cheap (~one menu walk) and keeps the executor
 * unchanged.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    private static final Identifier TEXTURE_IN =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_in_button.png");
    private static final Identifier TEXTURE_OUT =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_out_button.png");

    /** Side length of each button sprite. */
    public static final int SIZE = 9;

    /** Gap between adjacent buttons in the row. */
    public static final int BUTTON_GAP = 1;

    /**
     * Gap between the panel's outer bounds and the slot group's top
     * edge. Passed as the {@link SlotGroupPanelAdapter} padding.
     * Matches the visual previously hand-coded in the old widget.
     */
    public static final int GAP_ABOVE = 3;

    public static void register() {
        Button mmOut = Button.sprite(0, 0, SIZE, SIZE,
                        TEXTURE_OUT,
                        btn -> triggerMoveMatching(Direction.OUT))
                .tooltip(Component.literal("Move Matching Items Out"));

        Button mmIn = Button.sprite(SIZE + BUTTON_GAP, 0, SIZE, SIZE,
                        TEXTURE_IN,
                        btn -> triggerMoveMatching(Direction.IN))
                .tooltip(Component.literal("Move Matching Items In"));

        List<PanelElement> elements = List.of(mmOut, mmIn);

        Panel panel = new Panel("inventoryplus.movematching",
                elements,
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);
        panel.showWhen(() -> {
            Screen screen = Minecraft.getInstance().screen;
            return screen != null && SlotGroupDetector.isMoveMatchingScreen(screen);
        });

        new SlotGroupPanelAdapter(panel, SlotGroupRegion.TOP_ALIGN_RIGHT, GAP_ABOVE)
                .on(SlotGroupCategory.PLAYER_INVENTORY);
    }

    /**
     * Edit-mode-gated executor trigger. Pulled out of the button
     * callback so the gate logic stays single-sourced.
     */
    private static void triggerMoveMatching(Direction direction) {
        if (LockEditMode.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
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
