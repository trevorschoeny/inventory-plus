package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

/**
 * Move Matching button — vanilla {@link AbstractWidget} subclass for
 * either the IN or OUT direction.
 *
 * <h3>Interaction model (Trev 2026-05-16)</h3>
 *
 * <ul>
 *   <li><b>Left click</b> → trigger Move Matching in this widget's
 *       {@link Direction} with the group's current cycle stop.</li>
 *   <li><b>Right click</b> → cycle to the next stop and persist
 *       (per-direction cycle — IN and OUT cycles are independent).</li>
 *   <li><b>Shift + left click</b> → reserved for Magic Move IN / OUT
 *       (future). Currently a no-op with a debug log so the press is
 *       visible in logs.</li>
 * </ul>
 *
 * <p>The {@code I} / {@code O} keybinds trigger their respective
 * direction's Move Matching when hovering a slot in a targetable group
 * (see {@link MoveMatchingKeybind}).
 *
 * <h3>Per-direction texture</h3>
 *
 * Each direction has its own PNG:
 * <ul>
 *   <li>IN  → {@code assets/inventoryplus/textures/gui/move_matching_in_button.png}</li>
 *   <li>OUT → {@code assets/inventoryplus/textures/gui/move_matching_out_button.png}</li>
 * </ul>
 *
 * <h3>Layout</h3>
 *
 * Both widgets sit above the slot group, right-aligned with its right
 * edge. The IN widget takes the rightmost position; the OUT widget sits
 * one button width + 1px gap to its left. Layout math lives in
 * {@link MoveMatchingButtons}.
 *
 * <h3>Render lifecycle</h3>
 *
 * Added to the screen via
 * {@link net.fabricmc.fabric.api.client.screen.v1.Screens#getButtons},
 * so vanilla's {@code Screen.render} renderables iteration invokes
 * {@link #renderWidget}. Tooltips queued from there reach
 * {@code GuiGraphics.renderDeferredElements} in the same frame.
 */
public final class MoveMatchingWidget extends AbstractWidget {

    private static final Identifier TEXTURE_IN =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_in_button.png");
    private static final Identifier TEXTURE_OUT =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_out_button.png");

    public static final int SIZE = 9;

    /** {@code +1 px right} of the slot group's right edge (Trev 2026-05-16). */
    public static final int OFFSET_RIGHT = 1;

    /** {@code 3 px gap} between the button's bottom and the slot group's top edge. */
    public static final int GAP_ABOVE = 3;

    /** {@code 1 px gap} between the IN and OUT buttons when both are visible. */
    public static final int BUTTON_GAP = 1;

    private final SlotGroup group;
    private final AbstractContainerScreen<?> screen;
    private final Direction direction;
    private final Identifier texture;

    public MoveMatchingWidget(SlotGroup group, AbstractContainerScreen<?> screen,
                              Direction direction) {
        super(0, 0, SIZE, SIZE,
                Component.translatable(
                        "inventoryplus.movematching.button.narration." + direction.storageKey()));
        this.group = group;
        this.screen = screen;
        this.direction = direction;
        this.texture = direction == Direction.IN ? TEXTURE_IN : TEXTURE_OUT;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Position math: anchored above the slot group, right edge of
        // the IN button aligns with the group's right edge + OFFSET_RIGHT.
        // OUT button sits one button width + BUTTON_GAP to the left of
        // the IN button.
        int leftPos = ScreenLayout.leftPos(screen);
        int topPos = ScreenLayout.topPos(screen);
        int groupRightX = leftPos + group.localRightX();
        int groupTopY = topPos + group.localTopY();
        int inX = groupRightX - SIZE + OFFSET_RIGHT;
        int outX = inX - SIZE - BUTTON_GAP;
        setX(direction == Direction.IN ? inX : outX);
        setY(groupTopY - SIZE - GAP_ABOVE);

        MoveMatchingCycle cycle = MoveMatchingPrefs.get(group.key(), direction);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                getX(), getY(),
                /*u=*/ 0f, /*v=*/ 0f,
                SIZE, SIZE,
                SIZE, SIZE);

        if (cycle == MoveMatchingCycle.DISABLED) {
            // 50% black overlay — visual cue for the off state.
            graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x80000000);
        }

        if (isHovered()) {
            graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    cycle.tooltipLines(direction),
                    mouseX, mouseY);
        }
    }

    /**
     * Accept both left (0) and right (1) mouse buttons. Vanilla's
     * default narrows clicks to left-only; we widen so right-click
     * cycle reaches {@link #onClick}.
     */
    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0 || info.button() == 1;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        Minecraft mc = Minecraft.getInstance();

        if (button == 1) {
            // Right-click — cycle this direction's stop.
            cycle();
            return;
        }

        if (button == 0) {
            if (shift) {
                // Shift+left click — reserved for Magic Move (future round).
                InventoryPlusClient.LOGGER.debug(
                        "[move-matching {}] Shift+left-click — Magic Move {} pending (future round)",
                        direction, direction);
                return;
            }
            // Left click — trigger Move Matching in this direction.
            MoveMatchingExecutor.execute(
                    mc, group, direction, MoveMatchingPrefs.get(group.key(), direction));
        }
    }

    private void cycle() {
        ContainerKey key = group.key();
        if (key == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching {}] cycle on a no-key group — not persisting", direction);
            return;
        }
        MoveMatchingCycle next = MoveMatchingPrefs.get(key, direction).next();
        MoveMatchingPrefs.set(key, direction, next);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    public SlotGroup group() {
        return group;
    }

    public Direction direction() {
        return direction;
    }
}
