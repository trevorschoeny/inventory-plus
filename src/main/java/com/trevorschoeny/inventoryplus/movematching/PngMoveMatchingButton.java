package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.menukit.core.Button;
import com.trevorschoeny.menukit.core.RenderContext;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Custom-art subclass of MK {@link Button} for the move-matching action.
 * Renders the {@code move_matching_button.png} sprite (9×9) instead of
 * vanilla button chrome; no text content. A dim treatment overlays the
 * sprite when the slot group is in {@link MoveMatchingCycle#DISABLED}.
 *
 * <p>Hover tooltip (per Trev 2026-05-16) reads:
 * <pre>
 * Move matching: &lt;current state&gt;
 * Press M to cycle
 * </pre>
 *
 * <p>The tooltip is wired via MK's {@link Button#tooltip(Supplier)} so
 * the state line refreshes per-frame from the cycle supplier.
 *
 * <p>Screen-space placement is computed by {@link MoveMatchingButtons}
 * from the slot group's bounds — anchored above the group, right-edge-
 * aligned, offset per Trev's 2026-05-16 tweak. The button's
 * {@code childX} / {@code childY} are zero here; the actual position
 * comes through the {@link RenderContext}'s origin coords supplied by
 * the caller.
 */
public final class PngMoveMatchingButton extends Button {

    /** Texture identifier — points at {@code assets/inventoryplus/textures/gui/move_matching_button.png}. */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "textures/gui/move_matching_button.png");

    /** Sprite + button bounds are both 9×9 — the PNG renders 1:1. */
    public static final int SIZE = 9;

    private final Supplier<MoveMatchingCycle> cycleSupplier;

    public PngMoveMatchingButton(Supplier<MoveMatchingCycle> cycleSupplier,
                                 Consumer<Button> onClick) {
        // childX/childY = 0 — origin is supplied by the caller via the
        // RenderContext.
        super(0, 0, SIZE, SIZE, Component.empty(), onClick);
        this.cycleSupplier = cycleSupplier;

        // Hover tooltip — refreshes each frame from the cycle supplier
        // so the displayed state matches the current setting.
        tooltip(() -> tooltipFor(cycleSupplier.get()));
    }

    /**
     * Two-line tooltip:
     * <ul>
     *   <li>"Move matching: &lt;state&gt;"</li>
     *   <li>"Press M to cycle"</li>
     * </ul>
     *
     * <p>Vanilla's {@code GuiGraphics.setTooltipForNextFrame(Font, Component, x, y)}
     * splits the Component on {@code \n} when rendering, so a single
     * Component with an embedded newline gives the multi-line layout
     * without us having to construct a {@code List<Component>}.
     */
    private static Component tooltipFor(MoveMatchingCycle cycle) {
        return Component.literal("Move matching: ")
                .append(cycle.tooltip())
                .append(Component.literal("\nPress M to cycle"));
    }

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

        if (cycleSupplier.get() == MoveMatchingCycle.DISABLED) {
            // 50% black overlay for the disabled cycle stop. Single-PNG
            // with state-tint is the smoke-pass shape; if we want
            // per-stop variants, the PNG becomes a sprite sheet and
            // this renderBackground picks the appropriate UV rect.
            graphics.fill(sx, sy, sx + SIZE, sy + SIZE, 0x80000000);
        }
    }

    @Override
    protected void renderContent(RenderContext ctx, int sx, int sy) {
        // No text content — the PNG carries the entire visual.
    }
}
