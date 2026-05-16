package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Move-matching button — vanilla {@link AbstractWidget} subclass.
 *
 * <h3>Interaction</h3>
 *
 * <ul>
 *   <li><b>Left click</b> → {@link MoveMatchingExecutor#execute} with
 *       the group's current cycle stop.</li>
 *   <li><b>Cycle</b> → press the {@code M} keybind while hovering this
 *       widget. The cycle action lives on
 *       {@link MoveMatchingKeybind} so the keybind-event flow owns the
 *       state transition; this widget's {@link #cycle()} method is the
 *       hook the keybind calls.</li>
 * </ul>
 *
 * <p>Earlier iterations of this widget used shift-click for cycling.
 * Trev 2026-05-16 reverted that to the keybind-on-button shape; both
 * options were tried, the keybind-on-button approach won out.
 *
 * <h3>Render lifecycle</h3>
 *
 * Added to the screen via
 * {@link net.fabricmc.fabric.api.client.screen.v1.Screens#getButtons},
 * so vanilla's {@code Screen.render} renderables iteration invokes this
 * widget's {@link #renderWidget}. Tooltips queued from there reach
 * {@code GuiGraphics.renderDeferredElements} in the same frame, which
 * is why this widget shape (vs. an MK Button rendered from
 * {@code ScreenEvents.afterRender}) is the right integration point —
 * see MoveMatchingWidget v5 commit / DEFERRED.md for the diagnosis.
 *
 * <h3>Live position</h3>
 *
 * Screen-space coordinates are recomputed each {@code renderWidget} from
 * the slot group's current bounds + the screen's {@code leftPos} /
 * {@code topPos}. Resize / GUI scale changes propagate next frame
 * automatically.
 */
public final class MoveMatchingWidget extends AbstractWidget {

    /** PNG texture identifier — {@code assets/inventoryplus/textures/gui/move_matching_button.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_button.png");

    public static final int SIZE = 9;

    /** {@code +1 px right} of the slot group's right edge (Trev 2026-05-16). */
    public static final int OFFSET_RIGHT = 1;

    /** {@code 3 px gap} between the button's bottom and the slot group's top edge. */
    public static final int GAP_ABOVE = 3;

    private final SlotGroup group;
    private final AbstractContainerScreen<?> screen;

    public MoveMatchingWidget(SlotGroup group, AbstractContainerScreen<?> screen) {
        super(0, 0, SIZE, SIZE,
                Component.translatable("inventoryplus.movematching.button.narration"));
        this.group = group;
        this.screen = screen;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Position math: above the slot group, right-aligned with its
        // right edge + 1 px right + 3 px gap above.
        int leftPos = ScreenLayout.leftPos(screen);
        int topPos = ScreenLayout.topPos(screen);
        int groupRightX = leftPos + group.localRightX();
        int groupTopY = topPos + group.localTopY();
        setX(groupRightX - SIZE + OFFSET_RIGHT);
        setY(groupTopY - SIZE - GAP_ABOVE);

        MoveMatchingCycle cycle = MoveMatchingPrefs.get(group.key());

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                getX(), getY(),
                /*u=*/ 0f, /*v=*/ 0f,
                SIZE, SIZE,
                SIZE, SIZE);

        if (cycle == MoveMatchingCycle.DISABLED) {
            // 50% black overlay — visual cue for the off state.
            graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x80000000);
        }

        // Tooltip — list-form is required for multi-line in 1.21.11
        // (\n in a single Component renders as a literal LF glyph, not
        // a line break). setComponentTooltipForNextFrame takes a list
        // of Components, one per line.
        if (isHovered()) {
            graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    cycle.tooltipLines(),
                    mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return; // left mouse only
        // Left click = trigger. Cycling is handled by the keybind path —
        // pressing M while hovering this widget (see MoveMatchingKeybind).
        MoveMatchingExecutor.execute(
                Minecraft.getInstance(),
                group,
                MoveMatchingPrefs.get(group.key()));
    }

    /**
     * Advance this widget's slot group to the next cycle stop and
     * persist. Called from {@link MoveMatchingKeybind} when M is
     * pressed while hovering the widget.
     */
    public void cycle() {
        ContainerKey key = group.key();
        if (key == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] cycle on a no-key group — not persisting");
            return;
        }
        MoveMatchingCycle next = MoveMatchingPrefs.get(key).next();
        MoveMatchingPrefs.set(key, next);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    /** Public accessor for the keybind path. */
    public SlotGroup group() {
        return group;
    }
}
