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
 * Move Matching IN button — vanilla {@link AbstractWidget} subclass.
 *
 * <h3>Interaction model (Trev 2026-05-16, click-cycle revision)</h3>
 *
 * <ul>
 *   <li><b>Left click</b> → trigger Move Matching IN with the group's
 *       current cycle stop.</li>
 *   <li><b>Right click</b> → cycle to the next stop and persist.</li>
 *   <li><b>Shift + left click</b> → reserved for Magic Move IN (future).
 *       Currently a no-op with a debug log so the press is visible in
 *       logs.</li>
 * </ul>
 *
 * <p>The {@code I} keybind no longer cycles when hovering the widget —
 * it now only triggers when hovering a slot in a targetable group (see
 * {@link MoveMatchingKeybind}). One unified interaction model across
 * IN / OUT / Sort buttons: click = trigger, right-click = cycle,
 * shift-click = magic.
 *
 * <h3>Right-click dispatch in AbstractWidget</h3>
 *
 * Vanilla {@code AbstractWidget.isValidClickButton} returns {@code true}
 * only for the left mouse button — the default narrows clicks to
 * left-only. We override it to accept both left and right buttons so
 * both flow through {@link #onClick}; the dispatch on
 * {@link MouseButtonEvent#button()} then routes to trigger vs cycle.
 *
 * <h3>Render lifecycle</h3>
 *
 * Added to the screen via
 * {@link net.fabricmc.fabric.api.client.screen.v1.Screens#getButtons},
 * so vanilla's {@code Screen.render} renderables iteration invokes
 * {@link #renderWidget}. Tooltips queued from there reach
 * {@code GuiGraphics.renderDeferredElements} in the same frame.
 *
 * <h3>Live position</h3>
 *
 * Screen-space coordinates are recomputed each {@code renderWidget}
 * from the slot group's current bounds + the screen's {@code leftPos}
 * / {@code topPos}. Resize / GUI scale changes propagate next frame
 * automatically.
 */
public final class MoveMatchingWidget extends AbstractWidget {

    /** PNG texture identifier — {@code assets/inventoryplus/textures/gui/move_matching_button.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_button.png");

    public static final int SIZE = 9;

    /** {@code +1 px right} of the slot group's right edge. */
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

        if (isHovered()) {
            graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    cycle.tooltipLines(),
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
            // Right-click — cycle to the next stop.
            cycle();
            return;
        }

        if (button == 0) {
            if (shift) {
                // Shift + left click — reserved for Magic Move IN (future
                // round). Logged so the press is visible in dev logs;
                // no Magic Move implementation exists yet.
                InventoryPlusClient.LOGGER.debug(
                        "[move-matching] Shift+left-click on Move Matching IN button — "
                        + "Magic Move IN trigger pending (future round)");
                return;
            }
            // Left click — trigger Move Matching IN with current cycle.
            MoveMatchingExecutor.execute(
                    mc, group, MoveMatchingPrefs.get(group.key()));
        }
    }

    /**
     * Advance this widget's slot group to the next cycle stop and
     * persist. Called from {@link #onClick} on right-click.
     */
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

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    /** Public accessor for the keybind path. */
    public SlotGroup group() {
        return group;
    }
}
