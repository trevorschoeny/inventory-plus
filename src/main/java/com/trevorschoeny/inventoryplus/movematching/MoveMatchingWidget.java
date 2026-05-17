package com.trevorschoeny.inventoryplus.movematching;

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

import java.util.List;

/**
 * Move Matching button — vanilla {@link AbstractWidget} subclass for
 * either the IN or OUT direction.
 *
 * <h3>Interaction (Trev / Lead 2026-05-16 simplification)</h3>
 *
 * <ul>
 *   <li><b>Left click</b> → trigger Move Matching in this widget's
 *       {@link Direction}.</li>
 *   <li><b>Right click</b> → no-op (the per-container cycle was removed
 *       in the simplification; Locked Slots / Locked Items become the
 *       protection mechanism when they land).</li>
 *   <li><b>Shift + left click</b> → same as plain left click (no Magic
 *       Move distinction this round per spec §"Magic Move").</li>
 * </ul>
 *
 * <p>The widgets only render when a supported container is open — see
 * {@link MoveMatchingButtons}.
 *
 * <h3>Per-direction texture</h3>
 *
 * <ul>
 *   <li>IN  → down arrow → {@code move_matching_in_button.png}</li>
 *   <li>OUT → up arrow   → {@code move_matching_out_button.png}</li>
 * </ul>
 *
 * <h3>Layout</h3>
 *
 * Both widgets sit above the player inventory's 3×9 grid, right-aligned
 * with its right edge. The IN widget takes the rightmost position; the
 * OUT widget sits one button width + 1 px to its left (so the visible
 * order left-to-right is OUT then IN — the up-arrow appears first when
 * reading left-to-right, matching Trev's 2026-05-16 layout direction).
 *
 * <h3>Tooltip</h3>
 *
 * Single line per Lead 2026-05-16 spec — no cycle hint:
 * <ul>
 *   <li>IN  → "Move Matching Items in"</li>
 *   <li>OUT → "Move Matching Items out"</li>
 * </ul>
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
    private static final Identifier TEXTURE_IN_PRESSED =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_in_button_pressed.png");
    private static final Identifier TEXTURE_OUT_PRESSED =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_out_button_pressed.png");

    public static final int SIZE = 9;

    /** {@code +1 px right} of the slot group's right edge. */
    public static final int OFFSET_RIGHT = 1;

    /** {@code 3 px gap} between the button's bottom and the slot group's top edge. */
    public static final int GAP_ABOVE = 3;

    /** {@code 1 px gap} between the IN and OUT buttons. */
    public static final int BUTTON_GAP = 1;

    private final SlotGroup group;
    private final AbstractContainerScreen<?> screen;
    private final Direction direction;
    private final Identifier texture;
    private final Identifier texturePressed;
    private final List<Component> tooltipLines;
    /** True while LMB is held with the cursor still on the widget — drives the pressed sprite. */
    private boolean isPressed;

    public MoveMatchingWidget(SlotGroup group, AbstractContainerScreen<?> screen,
                              Direction direction) {
        super(0, 0, SIZE, SIZE,
                Component.translatable(
                        "inventoryplus.movematching.button.narration." + direction.storageKey()));
        this.group = group;
        this.screen = screen;
        this.direction = direction;
        this.texture = direction == Direction.IN ? TEXTURE_IN : TEXTURE_OUT;
        this.texturePressed = direction == Direction.IN ? TEXTURE_IN_PRESSED : TEXTURE_OUT_PRESSED;
        // Tooltip text — title-case "In" / "Out" per Trev's 2026-05-16
        // follow-up. (The spec said lowercase; Trev iterated to caps after
        // seeing it in-game.)
        String suffix = direction == Direction.IN ? "In" : "Out";
        this.tooltipLines = List.of(
                Component.literal("Move Matching Items " + suffix));
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

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                isPressed ? texturePressed : texture,
                getX(), getY(),
                /*u=*/ 0f, /*v=*/ 0f,
                SIZE, SIZE,
                SIZE, SIZE);

        if (isHovered()) {
            graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    tooltipLines,
                    mouseX, mouseY);
        }
    }

    /**
     * Only left click is meaningful here. Right click is explicitly
     * dropped (no cycle to advance); vanilla's default
     * {@code isValidClickButton} already narrows to left-only so we
     * could omit this override, but keep it explicit for documentation
     * of the intent.
     */
    @Override
    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Set pressed state before delegating to super (which routes to
        // onClick). The pressed sprite paints on the next render frame
        // while the user holds LMB. mouseReleased clears it.
        if (active && visible && isHovered() && event.button() == 0) {
            isPressed = true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        isPressed = false;
        return super.mouseReleased(event);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return;
        // Edit mode for Locked Slots is exclusive — no MM during edit
        // mode (Trev 2026-05-16). The Lock-edit click interceptor cancels
        // mouse clicks on slots, but widget clicks go through onClick
        // directly. Defensive check.
        if (com.trevorschoeny.inventoryplus.lockedslots.LockEditMode.isOn()) return;
        // Shift+left and plain left both trigger the same operation per
        // spec §"Open questions" + Trev 2026-05-16. When Magic Move's
        // re-think lands, the shift case becomes the Magic Move hook.
        MoveMatchingExecutor.execute(Minecraft.getInstance(), group, direction);
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
