package com.trevorschoeny.inventoryplus.movematching;

import com.mojang.blaze3d.platform.Window;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

/**
 * Custom-art subclass of MK {@link Button} for the move-matching action.
 * Renders the {@code move_matching_button.png} sprite (9×9) instead of
 * vanilla button chrome; no text content. A dim treatment overlays the
 * sprite when the button's slot group is set to
 * {@link MoveMatchingCycle#DISABLED} — single-PNG with state-driven tint
 * is the smoke-pass approach (multi-frame PNG is a polish option).
 *
 * <p>This is a subclass extension of MK's
 * {@link Button#renderBackground(RenderContext, int, int)} +
 * {@link Button#renderContent(RenderContext, int, int)} hooks per the
 * library's documented extension points — the rendering surface stays
 * inside MK's PanelElement model so input/hover behavior comes free.
 *
 * <p>The actual screen-space placement of this button is computed by
 * {@link MoveMatchingButtons} from the slot group's bounds — anchored
 * above the slot group, right-aligned with its right edge. The button's
 * {@code childX} / {@code childY} are zero here; the position resolves
 * via the {@link RenderContext}'s origin coordinates that the caller
 * threads in per-frame.
 */
public final class PngMoveMatchingButton extends Button {

    /** Texture identifier — points at {@code assets/inventoryplus/textures/gui/move_matching_button.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_button.png");

    /** Sprite + button bounds are both 9×9 — the PNG renders 1:1. */
    public static final int SIZE = 9;

    /** Supplier for the current cycle so we can dim when disabled. */
    private final java.util.function.Supplier<MoveMatchingCycle> cycleSupplier;

    public PngMoveMatchingButton(java.util.function.Supplier<MoveMatchingCycle> cycleSupplier,
                                 Consumer<Button> onClick) {
        // childX/childY = 0 — origin is supplied by the caller via the
        // RenderContext, so the button positions itself at (originX,
        // originY) which is the slot-group-anchored screen-space point.
        super(0, 0, SIZE, SIZE, Component.empty(), onClick);
        this.cycleSupplier = cycleSupplier;
    }

    /**
     * Background hook — paints the PNG sprite. The disabled state paints a
     * dim overlay on top for visual feedback that the button is in the
     * {@link MoveMatchingCycle#DISABLED} cycle stop.
     *
     * <p>We deliberately skip {@code super.renderBackground} (vanilla
     * button chrome) because the PNG IS the background per Trev's
     * direction.
     */
    @Override
    protected void renderBackground(RenderContext ctx, int sx, int sy) {
        GuiGraphics graphics = ctx.graphics();
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                sx, sy,
                /*u=*/ 0f, /*v=*/ 0f,
                /*w=*/ SIZE, /*h=*/ SIZE,
                /*tw=*/ SIZE, /*th=*/ SIZE);

        MoveMatchingCycle cycle = cycleSupplier.get();
        if (cycle == MoveMatchingCycle.DISABLED) {
            // 50% black overlay — quick visual ack of the disabled state.
            // Multi-frame PNG variant is the polish; this stays inline
            // for the smoke pass and matches what most vanilla widgets
            // do for disabled state (darken).
            graphics.fill(sx, sy, sx + SIZE, sy + SIZE, 0x80000000);
        }
    }

    /**
     * Content hook — explicitly empty. No text overlay; the PNG carries
     * the entire visual.
     */
    @Override
    protected void renderContent(RenderContext ctx, int sx, int sy) {
        // intentional no-op
    }

    /** Suppress unused-import lint warning. */
    @SuppressWarnings("unused")
    private static Window unused(Minecraft mc) { return mc.getWindow(); }
}
