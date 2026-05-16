package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.menukit.core.RenderContext;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-screen registry of {@link PngMoveMatchingButton}s — one per
 * targetable {@link SlotGroup} detected on a freshly opened screen.
 *
 * <h3>Why not MK's ScreenPanelAdapter regions</h3>
 *
 * MK regions ({@code RIGHT_ALIGN_TOP}, etc.) anchor to the menu's
 * <i>screen frame</i>. Trev's direction puts the button above the
 * <i>slot group's own bounds</i>, right-aligned with the group's right
 * edge — that's slot-anchored, not screen-anchored. The 18a audit
 * flagged exactly this as Gap #1 (per-slot decoration primitive). This
 * file is the smoke-pass workaround: hand-rolled button placement +
 * Fabric screen events for render/click dispatch, with MK's
 * {@link com.trevorschoeny.menukit.core.Button} (subclassed as
 * {@link PngMoveMatchingButton}) doing the actual element rendering. The
 * proper architectural fix is a slot-anchored primitive in MK; filed for
 * 18q.N.
 *
 * <h3>Per-screen state</h3>
 *
 * On {@link ScreenEvents#AFTER_INIT} for an eligible screen, we:
 * <ol>
 *   <li>Run {@link SlotGroupDetector#detect} to partition the menu.</li>
 *   <li>For each targetable group, build a {@link PngMoveMatchingButton}
 *       wired to the group's cycle via {@link MoveMatchingPrefs}.</li>
 *   <li>Store the (button, group) pairs in a per-screen list.</li>
 *   <li>Register {@link ScreenEvents#AFTER_RENDER} +
 *       {@link ScreenMouseEvents#ALLOW_MOUSE_CLICK} listeners that
 *       look up this screen's pairs and dispatch render / clicks.</li>
 * </ol>
 *
 * <h3>Hover queries</h3>
 *
 * {@link #buttonUnderMouse(Screen, double, double)} returns the slot
 * group whose button the mouse currently sits over (or null). Used by
 * {@link MoveMatchingKeybind} to route {@code M}-press: button-hover →
 * cycle, slot-hover → trigger.
 */
public final class MoveMatchingButtons {

    private MoveMatchingButtons() {}

    /**
     * Per-screen list of (button, slot group) pairs. Identity-keyed so
     * different screens with equal {@code equals} (rare) don't clobber
     * each other. Entries removed on screen remove.
     */
    private static final Map<Screen, List<Pair>> BY_SCREEN = new IdentityHashMap<>();

    /** Public init — call once from the client entrypoint. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!SlotGroupDetector.isMoveMatchingScreen(screen)) return;
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

            List<SlotGroup> groups = SlotGroupDetector.detect(screen);
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

            // Render dispatch — call each button's render() with a
            // freshly built RenderContext sized to the button's slot-
            // group-anchored origin.
            ScreenEvents.afterRender(screen).register(
                    (s, graphics, mouseX, mouseY, tickDelta) ->
                            renderAll(acs, graphics, mouseX, mouseY));

            // Click dispatch — ALLOW phase so we can consume the click
            // before vanilla treats it as a slot-click. 1.21.11's
            // MouseButtonEvent carries x()/y()/button() as record accessors.
            ScreenMouseEvents.allowMouseClick(screen).register(
                    (s, event) ->
                            handleClick(acs, event.x(), event.y(), event.button()));

            // Lifecycle: drop the per-screen state on remove. Fabric
            // doesn't expose AFTER_REMOVE in older versions; we rely on
            // the WeakReference-like cleanup via IdentityHashMap +
            // matching Screen instance lookup (closed screens stop
            // receiving events and the map entry is GC'd when the
            // Screen is collected).
            ScreenEvents.remove(screen).register(s -> BY_SCREEN.remove(s));
        });
    }

    /**
     * Looks up the slot group whose button is under the given mouse
     * coordinates on the given screen, or null if no button is under
     * the cursor (or the screen isn't tracked).
     */
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

    /**
     * Returns all (button, group) pairs for the given screen, or empty.
     * Exposed for the keybind path so it can iterate slot groups when
     * detecting "M over a target slot".
     */
    public static List<Pair> pairsFor(Screen screen) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        return pairs == null ? List.of() : pairs;
    }

    /**
     * Renders all buttons for the given screen. Computes each button's
     * screen-space top-left from its slot group's live bounds and
     * threads it into a synthetic {@link RenderContext}.
     */
    private static void renderAll(AbstractContainerScreen<?> screen, GuiGraphics graphics,
                                  int mouseX, int mouseY) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        if (pairs == null) return;
        for (Pair p : pairs) {
            int[] bounds = buttonScreenBounds(screen, p.group);
            // RenderContext's originX/originY is where the element's
            // childX/childY are added to; since PngMoveMatchingButton has
            // childX=childY=0, we set origin to the desired screen-space
            // top-left and the button paints there.
            RenderContext ctx = new RenderContext(graphics, bounds[0], bounds[1], mouseX, mouseY);
            p.button.render(ctx);
        }
    }

    /**
     * Dispatches a mouse click to any button under the cursor.
     * Returns true (allow) iff no button consumed the click — returning
     * false from {@code ALLOW_MOUSE_CLICK} cancels vanilla's slot-click
     * handling.
     */
    private static boolean handleClick(AbstractContainerScreen<?> screen,
                                       double mouseX, double mouseY, int button) {
        List<Pair> pairs = BY_SCREEN.get(screen);
        if (pairs == null) return true;
        // Only left-click triggers the move-matching action; other mouse
        // buttons pass through to vanilla. (Right-click on the button
        // doesn't cycle in this design — cycle is keybind-on-button per
        // Trev's redirect.)
        if (button != 0) return true;

        for (Pair p : pairs) {
            int[] bounds = buttonScreenBounds(screen, p.group);
            if (mouseX >= bounds[0] && mouseX < bounds[0] + PngMoveMatchingButton.SIZE
                    && mouseY >= bounds[1] && mouseY < bounds[1] + PngMoveMatchingButton.SIZE) {
                MoveMatchingCycle cycle = MoveMatchingPrefs.get(p.group.key());
                MoveMatchingExecutor.execute(Minecraft.getInstance(), p.group, cycle);
                return false; // consume — don't let vanilla treat as slot click
            }
        }
        return true;
    }

    /**
     * Computes the screen-space top-left of this slot group's button.
     * Anchored above the slot group, right-aligned with the group's
     * right edge.
     *
     * <p>{@code GAP_ABOVE} = 1 pixel between the button's bottom edge
     * and the slot group's top edge.
     */
    private static int[] buttonScreenBounds(AbstractContainerScreen<?> screen, SlotGroup group) {
        int leftPos = ScreenLayout.leftPos(screen);
        int topPos = ScreenLayout.topPos(screen);
        int groupRightX = leftPos + group.localRightX();
        int groupTopY   = topPos + group.localTopY();
        final int GAP_ABOVE = 1;
        int btnX = groupRightX - PngMoveMatchingButton.SIZE;
        int btnY = groupTopY - PngMoveMatchingButton.SIZE - GAP_ABOVE;
        return new int[] { btnX, btnY };
    }

    /**
     * Cycles the slot group's cycle setting one step and surfaces a
     * chat-overlay ack so the player sees the new state without needing
     * a tooltip refresh.
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
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("Move-matching: ").append(next.tooltip()),
                    /*overlay=*/ true);
        }
    }

    /** Pair carrier for the per-screen map. */
    public record Pair(PngMoveMatchingButton button, SlotGroup group) {}
}
