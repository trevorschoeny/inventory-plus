package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.lwjgl.glfw.GLFW;

/**
 * Move-matching button — a vanilla {@link AbstractWidget} subclass that
 * renders the {@code move_matching_button.png} sprite, handles
 * {@link Screen#hasShiftDown shift-click} to cycle vs left-click to
 * trigger, and provides a hover tooltip via vanilla's
 * {@link GuiGraphics#setTooltipForNextFrame}.
 *
 * <h3>Why a vanilla widget rather than MK Button</h3>
 *
 * MK's {@link com.trevorschoeny.menukit.core.Button} supports custom
 * background painting via subclassing — the v3 button used that. The
 * tooltip queue, however, must be primed during the screen's renderables
 * iteration (inside {@link Screen#render}, before
 * {@code Screen.renderWithTooltipAndSubtitles} flushes deferred elements
 * via {@code GuiGraphics.renderDeferredElements}). Fabric's
 * {@code ScreenEvents.afterRender} fires <i>after</i> that flush — the
 * deferred-tooltip Runnable is gone by the time my afterRender registers
 * the new one, so the tooltip never renders.
 *
 * <p>Adding a vanilla widget via {@link net.fabricmc.fabric.api.client.screen.v1.Screens#getButtons}
 * makes the widget participate in {@code Screen.render}'s renderables
 * iteration. Tooltips queued from inside {@code renderWidget} reach the
 * deferred queue in time for the same frame's flush.
 *
 * <p>This is the right architectural answer — vanilla's widget pipeline
 * is the integration point for screen-anchored interactive UI. The MK
 * Button + ScreenPanelAdapter path is for panels that compose into MK's
 * region system; slot-anchored decorations that need vanilla's
 * widget-render lifecycle want the vanilla path.
 *
 * <h3>Live position</h3>
 *
 * The widget's screen-space position depends on the slot group's bounds
 * (which depend on the screen's {@code leftPos} / {@code topPos}). Both
 * recompute on resize / gui-scale changes. We re-derive the position
 * each {@code renderWidget} so resize handling is automatic — vanilla's
 * resize path re-renders the widget at the next frame, where we read
 * the new {@code leftPos} / {@code topPos} via {@link ScreenLayout}.
 *
 * <h3>Interaction (Trev 2026-05-16 #2)</h3>
 *
 * <ul>
 *   <li><b>Left click</b> → {@link MoveMatchingExecutor#execute} with the
 *       group's current cycle setting.</li>
 *   <li><b>Shift + left click</b> → advance the group's cycle one stop.
 *       Replaces the prior keybind-on-button cycle.</li>
 *   <li>Other mouse buttons → no-op.</li>
 * </ul>
 */
public final class MoveMatchingWidget extends AbstractWidget {

    /** PNG texture identifier — {@code assets/inventoryplus/textures/gui/move_matching_button.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_button.png");

    public static final int SIZE = 9;

    /** {@code +1 px right} (per Trev 2026-05-16) — pushes the button right of the slot group's right edge alignment. */
    public static final int OFFSET_RIGHT = 1;

    /** {@code 3 px gap} between the button's bottom edge and the slot group's top edge. */
    public static final int GAP_ABOVE = 3;

    private final SlotGroup group;
    private final AbstractContainerScreen<?> screen;

    public MoveMatchingWidget(SlotGroup group, AbstractContainerScreen<?> screen) {
        super(0, 0, SIZE, SIZE,
                net.minecraft.network.chat.Component.translatable("inventoryplus.movematching.button.narration"));
        this.group = group;
        this.screen = screen;
    }

    /**
     * Recomputes position from the slot group's live bounds, paints the
     * PNG (dimmed when DISABLED), and queues the hover tooltip.
     */
    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Position math: anchored above the slot group, right-aligned
        // with its right edge, offset per Trev's 2026-05-16 tweak.
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
            // 50% black overlay — visual cue for the disabled state.
            graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, 0x80000000);
        }

        // Tooltip — per-cycle text lives on MoveMatchingCycle. Vanilla
        // splits the Component on embedded newlines so a single Component
        // with \n renders as multi-line.
        if (isHovered()) {
            graphics.setTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    cycle.tooltip(),
                    mouseX, mouseY);
        }
    }

    /**
     * Vanilla calls this on the button when the user clicks within its
     * bounds. We dispatch on {@link Screen#hasShiftDown}: shift → cycle,
     * else → trigger.
     */
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return; // left mouse only
        boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        Minecraft mc = Minecraft.getInstance();
        if (shift) {
            cycle();
        } else {
            MoveMatchingExecutor.execute(mc, group, MoveMatchingPrefs.get(group.key()));
        }
    }

    private void cycle() {
        ContainerKey key = group.key();
        if (key == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] cycle on a no-key group — not persisting");
            return;
        }
        MoveMatchingCycle next = MoveMatchingPrefs.get(key).next();
        MoveMatchingPrefs.set(key, next);
    }

    /** No narration text customization for the smoke pass — uses the default. */
    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    /** Public accessor — used by the keybind path to detect "M over button" (no-op now, but kept for symmetry). */
    public SlotGroup group() {
        return group;
    }
}
