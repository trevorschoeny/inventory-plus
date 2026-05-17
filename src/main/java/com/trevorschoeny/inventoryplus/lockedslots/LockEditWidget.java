package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;
import com.trevorschoeny.inventoryplus.movematching.SlotGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Lock-edit toggle button — vanilla {@link AbstractWidget} subclass.
 *
 * <h3>Interaction</h3>
 *
 * <ul>
 *   <li><b>Left click</b> → toggle {@link LockEditMode}.</li>
 *   <li>Tooltip (off): "Click to edit locked slots"</li>
 *   <li>Tooltip (on): "Click to finish editing"</li>
 * </ul>
 *
 * <h3>Layout</h3>
 *
 * Sits above the player main inventory's 3×9 grid, right-aligned to
 * its right edge. Position depends on whether the Move Matching widgets
 * are present alongside:
 *
 * <ul>
 *   <li>When MM widgets present (chest / shulker / hopper / dispenser
 *       screens): {@code slotIndex = 2} (leftmost of three buttons,
 *       layout {@code [LOCK] [OUT] [IN]}).</li>
 *   <li>When MM widgets absent (any other screen with player main inv):
 *       {@code slotIndex = 0} (alone at the rightmost spot).</li>
 * </ul>
 *
 * <p>The {@code slotIndex} is passed at construction by
 * {@link LockedSlotsButtons} which knows the context.
 */
public final class LockEditWidget extends AbstractWidget {

    private static final Identifier TEXTURE_OFF =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_slot_edit_off.png");
    private static final Identifier TEXTURE_ON =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/locked_slot_edit_on.png");

    public static final int SIZE = 9;

    /** Reuses MM widget's spacing constants for layout consistency. */
    public static final int OFFSET_RIGHT =
            com.trevorschoeny.inventoryplus.movematching.MoveMatchingWidget.OFFSET_RIGHT;
    public static final int GAP_ABOVE =
            com.trevorschoeny.inventoryplus.movematching.MoveMatchingWidget.GAP_ABOVE;
    public static final int BUTTON_GAP =
            com.trevorschoeny.inventoryplus.movematching.MoveMatchingWidget.BUTTON_GAP;

    private static final List<Component> TOOLTIP_OFF = List.of(
            Component.literal("Click to edit locked slots"));
    private static final List<Component> TOOLTIP_ON = List.of(
            Component.literal("Click to finish editing"));

    private final SlotGroup playerMainInv;
    private final AbstractContainerScreen<?> screen;
    private final int slotIndex;

    public LockEditWidget(SlotGroup playerMainInv, AbstractContainerScreen<?> screen, int slotIndex) {
        super(0, 0, SIZE, SIZE,
                Component.translatable("inventoryplus.lockedslots.button.narration"));
        this.playerMainInv = playerMainInv;
        this.screen = screen;
        this.slotIndex = slotIndex;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Position math — same formula as MM widgets, with slotIndex
        // determining offset from the right edge.
        int leftPos = ScreenLayout.leftPos(screen);
        int topPos = ScreenLayout.topPos(screen);
        int groupRightX = leftPos + playerMainInv.localRightX();
        int groupTopY = topPos + playerMainInv.localTopY();
        int baseX = groupRightX - SIZE + OFFSET_RIGHT;
        setX(baseX - slotIndex * (SIZE + BUTTON_GAP));
        setY(groupTopY - SIZE - GAP_ABOVE);

        Identifier texture = LockEditMode.isOn() ? TEXTURE_ON : TEXTURE_OFF;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                getX(), getY(),
                /*u=*/ 0f, /*v=*/ 0f,
                SIZE, SIZE,
                SIZE, SIZE);

        if (isHovered()) {
            graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    LockEditMode.isOn() ? TOOLTIP_ON : TOOLTIP_OFF,
                    mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return;
        LockEditMode.toggle();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
