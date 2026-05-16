package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.menukit.core.RenderContext;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-screen registry of {@link PngMoveMatchingButton}s — one per
 * targetable {@link SlotGroup} on a freshly opened screen, gated by the
 * <b>2+ traditional containers</b> rule (Trev 2026-05-16).
 *
 * <h3>Activation rule</h3>
 *
 * After {@link SlotGroupDetector#detect}, we count how many of the
 * returned groups are {@link SlotGroup#targetable}. Buttons are only
 * registered when that count is {@code ≥ 2}. The standalone survival /
 * creative inventory yields exactly one targetable group (the main inv
 * 3×9), so no buttons render. A chest / shulker / barrel / hopper /
 * dispenser screen yields two targetable groups (the non-player container
 * + the player main inv), so two buttons render. A future feature that
 * surfaces a second traditional container inside the inventory screen
 * (e.g., Shulker Peek opening a real {@code ShulkerBoxBlockEntity})
 * promotes the count to 2 and buttons appear automatically — no change
 * needed here.
 *
 * <h3>Button placement (Trev 2026-05-16 tweak)</h3>
 *
 * The button sits above the slot group, right-aligned with the group's
 * right edge. Offsets relative to the group's right-edge + top-edge:
 * {@code +1 px right, -3 px up} (the previous value was {@code -1 up};
 * Trev requested {@code +2} more, totaling 3 px of gap above the slot
 * group's top edge).
 *
 * <h3>Render + click dispatch</h3>
 *
 * Lambda-style: a per-screen list of buttons + groups. {@link
 * ScreenEvents#afterRender} paints each button with a synthetic
 * {@link RenderContext}; {@link ScreenMouseEvents#allowMouseClick}
 * consumes the click before vanilla treats it as a slot click.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    private static final Map<Screen, List<Pair>> BY_SCREEN = new IdentityHashMap<>();

    /** Public init — call once from the client entrypoint. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            List<SlotGroup> groups = SlotGroupDetector.detect(screen);

            // 2+ rule: count targetable groups; only register when ≥ 2.
            int targetableCount = 0;
            for (SlotGroup g : groups) if (g.targetable()) targetableCount++;
            if (targetableCount < 2) {
                InventoryPlusClient.LOGGER.debug(
                        "[move-matching] only {} targetable group(s) on {} — no buttons",
                        targetableCount, screen.getClass().getSimpleName());
                return;
            }

            List<Pair> pairs = new ArrayList<>();
            for (SlotGroup group : groups) {
                if (!group.targetable()) continue;
                PngMoveMatchingButton btn = new PngMoveMatchingButton(
                        () -> MoveMatchingPrefs.get(group.key()),
                        clickedBtn -> {
                            MoveMatchingCycle cycle = MoveMatchingPrefs.get(group.key());
                            MoveMatchingExecutor.execute(Minecraft.getInstance(), group, cycle);
                        });
                pairs.add(new Pair(btn, group));
            }
            if (pairs.isEmpty()) return;
            BY_SCREEN.put(screen, pairs);

            ScreenEvents.afterRender(screen).register(
                    (s, graphics, mouseX, mouseY, tickDelta) ->
                            renderAll(acs, graphics, mouseX, mouseY));
            ScreenMouseEvents.allowMouseClick(screen).register(
                    (s, event) ->
                            handleClick(acs, event.x(), event.y(), event.button()));
            ScreenEvents.remove(screen).register(s -> BY_SCREEN.remove(s));
        });
    }

    public static @Nullable SlotGroup buttonUnderMouse(Screen screen, double mouseX, double mouseY) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        if (pairs == null) return null;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        for (Pair p : pairs) {
            int[] bounds = buttonScreenBounds(acs, p.group);
            int bx = bounds[0], by = bounds[1];
            if (mouseX >= bx && mouseX < bx + PngMoveMatchingButton.SIZE
                    && mouseY >= by && mouseY < by + PngMoveMatchingButton.SIZE) {
                return p.group;
            }
        }
        return null;
    }

    public static List<Pair> pairsFor(Screen screen) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        return pairs == null ? List.of() : pairs;
    }

    private static void renderAll(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                                  int mouseX, int mouseY) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        if (pairs == null) return;
        for (Pair p : pairs) {
            int[] bounds = buttonScreenBounds(screen, p.group);
            RenderContext ctx = new RenderContext(graphics, bounds[0], bounds[1], mouseX, mouseY);
            p.button.render(ctx);
        }
    }

    private static boolean handleClick(AbstractContainerScreen<?> screen,
                                       double mouseX, double mouseY, int button) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        if (pairs == null) return true;
        if (button != 0) return true;
        for (Pair p : pairs) {
            int[] bounds = buttonScreenBounds(screen, p.group);
            if (mouseX >= bounds[0] && mouseX < bounds[0] + PngMoveMatchingButton.SIZE
                    && mouseY >= bounds[1] && mouseY < bounds[1] + PngMoveMatchingButton.SIZE) {
                MoveMatchingCycle cycle = MoveMatchingPrefs.get(p.group.key());
                MoveMatchingExecutor.execute(Minecraft.getInstance(), p.group, cycle);
                return false;
            }
        }
        return true;
    }

    /**
     * Screen-space top-left of the button for this slot group.
     * {@code OFFSET_RIGHT = +1 px} pushes the button 1 px to the right of
     * the group's right edge alignment; {@code GAP_ABOVE = 3 px} sits
     * the button's bottom edge 3 px above the slot group's top edge.
     * Per Trev's 2026-05-16 tweak.
     */
    private static int[] buttonScreenBounds(AbstractContainerScreen<?> screen, SlotGroup group) {
        int leftPos = ScreenLayout.leftPos(screen);
        int topPos = ScreenLayout.topPos(screen);
        int groupRightX = leftPos + group.localRightX();
        int groupTopY   = topPos + group.localTopY();
        final int OFFSET_RIGHT = 1;
        final int GAP_ABOVE = 3;
        int btnX = groupRightX - PngMoveMatchingButton.SIZE + OFFSET_RIGHT;
        int btnY = groupTopY - PngMoveMatchingButton.SIZE - GAP_ABOVE;
        return new int[] { btnX, btnY };
    }

    /**
     * Cycle the slot group's setting. Tooltip refresh provides the
     * visual feedback for the new state (no chat overlay — per Trev
     * 2026-05-16 the tooltip is the surface, since the HUD overlay
     * isn't visible while a screen is open).
     */
    public static void cycle(SlotGroup group) {
        if (!group.targetable()) return;
        ContainerKey key = group.key();
        if (key == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[move-matching] cycle on a no-key group — not persisting");
            return;
        }
        MoveMatchingCycle current = MoveMatchingPrefs.get(key);
        MoveMatchingCycle next = current.next();
        MoveMatchingPrefs.set(key, next);
    }

    public record Pair(PngMoveMatchingButton button, SlotGroup group) {}
}
