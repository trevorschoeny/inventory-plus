package com.trevorschoeny.inventoryplus.hotbarcycler;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.element.Toggle;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.SlotScreenRect;
import com.trevlar.slotkit.api.group.VanillaSlotResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The per-row cycle buttons: one small toggle hanging off the left edge
 * of each inventory row, plus a fourth on the hotbar that only reports
 * state.
 *
 * <h3>One rectangle, no state machine</h3>
 *
 * Reveal is a pure function of the cursor, evaluated per frame: the
 * button shows while the cursor is anywhere in the row's nine slots, the
 * button itself, or the gap between them, which is one rectangle (the
 * row's slot span extended left by {@link #BUTTON_SIZE} + {@link #GAP}).
 * Because the trigger zone and the sustain zone are the same rectangle,
 * "the button survives the cursor crossing the gap" needs no sustain
 * timer and no remembered hover target. Inventory Max's {@code
 * PocketHover} carried that machinery because its zones differed in shape.
 * This feature does not need it.
 *
 * <p>A pressed button ignores hover entirely and is always drawn, which
 * is what makes the toggled set readable at a glance without a per-slot
 * overlay.
 *
 * <h3>Hiding by null origin</h3>
 *
 * MenuKit's pixel-positioned panels treat a null origin as "skip this
 * frame", so visibility and placement are the same decision, computed
 * in one place from live slot geometry. There is no separate visible
 * flag to keep in sync, and the panel follows the inventory at any GUI
 * scale because the origin is re-resolved from the row's real slot rect
 * every frame.
 */
public final class HotbarCyclerRowButtons {

    private HotbarCyclerRowButtons() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "cycle_slot_edit");

    /** Button edge length, matching the other 9x9 IP toolbar buttons. */
    public static final int BUTTON_SIZE = 9;
    /** Gap between the button and the row's first slot frame. */
    public static final int GAP = 2;
    /** Vanilla slot pitch, used to span the row for the hover rectangle. */
    private static final int SLOT_PITCH = 18;

    public static void register() {
        for (int row = 0; row < HotbarCycler.ROW_COUNT; row++) {
            registerRow(row);
        }
        registerHotbarIndicator();
    }

    // Toggleable rows

    // Row 0's panel, for the self-check to ask whether it shows (placedBounds).
    private static Panel row0Panel;

    /** Row 0's button panel; null before {@link #register}. */
    public static Panel row0Panel() {
        return row0Panel;
    }

    private static void registerRow(int row) {
        final int r = row;
        PanelElement button = Toggle.builder().sprite(TEXTURE).size(BUTTON_SIZE, BUTTON_SIZE)
                .state(() -> HotbarCycler.isRowToggled(r), on -> {
                    if (SettingsMenu.ctrlClickOpens(SettingsMenu.HOTBAR_CYCLER)) return;
                    HotbarCycler.setRow(r, on);
                })
                // Compact tooltips (General): the short form.
                .tooltip(() -> Component.literal(HotbarCycler.isRowToggled(r)
                        ? (IPConfig.compactTooltips() ? "Remove row" : "Remove row from cycle")
                        : (IPConfig.compactTooltips() ? "Add row" : "Add row to cycle")))
                .build();

        Panel panel = Panel.builder("inventoryplus:hotbarcycler_row_" + r)
                .elements(List.of(button))
                .visible(true)
                .style(PanelStyle.NONE)
                // Both modes: the origin is null wherever the row isn't on the
                // menu (creative's item tabs have no inventory rows), which hides it.
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(() -> rowButtonOrigin(r))))
                .build()
                // The button floats in the frame margin beside the grid. Clicks
                // that miss it must reach vanilla, or the margin would swallow a
                // carried item the player meant to drop outside the panel.
                .opaque(false);
        new ScreenPanelAdapter(panel, 0).onPlayerInventory();
        if (r == 0) row0Panel = panel;
    }

    /**
     * Origin for {@code row}'s button, or null to hide it this frame.
     * Shown when the row is already in the cycle, or while the cursor is
     * inside the row's reveal rectangle.
     */
    private static @Nullable ScreenOrigin rowButtonOrigin(int row) {
        if (!buttonsVisible()) return null;
        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null) return null;
        SlotScreenRect first = slotRect(screen, HotbarCycler.firstSlotOf(row));
        if (first == null) return null;
        if (!HotbarCycler.isRowToggled(row) && !cursorInRevealZone(first)) return null;
        return buttonOriginFor(first);
    }

    // The hotbar's state-only twin

    /**
     * The hotbar carries the same button in its pressed state whenever any
     * row is on, saying "the hotbar is always in the cycle". It reports
     * state and is not clickable, so it is wired to a no-op setter rather
     * than given click behavior to suppress.
     */
    private static void registerHotbarIndicator() {
        PanelElement indicator = Toggle.builder().sprite(TEXTURE).size(BUTTON_SIZE, BUTTON_SIZE)
                .state(() -> true, on -> { /* not clickable: the hotbar's membership isn't a choice */ })
                .tooltip(() -> Component.literal(IPConfig.compactTooltips()
                        ? "Always cycled" : "The hotbar is always in the cycle"))
                .build();

        Panel panel = Panel.builder("inventoryplus:hotbarcycler_hotbar")
                .elements(List.of(indicator))
                .visible(true)
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(HotbarCyclerRowButtons::hotbarIndicatorOrigin)))
                .build()
                .opaque(false);
        new ScreenPanelAdapter(panel, 0).onPlayerInventory();
    }

    private static @Nullable ScreenOrigin hotbarIndicatorOrigin() {
        if (!buttonsVisible()) return null;
        // Only meaningful once something is actually cycling.
        if (!HotbarCycler.hasActiveCycle()) return null;
        AbstractContainerScreen<?> screen = containerScreen();
        if (screen == null) return null;
        SlotScreenRect first = slotRect(screen, 0);
        if (first == null) return null;
        return buttonOriginFor(first);
    }

    // Shared geometry

    /**
     * Button top-left for a row whose first slot is {@code first}: to the
     * left of the slot frame by the gap, and vertically centered on the
     * 16px slot so it reads as belonging to that row.
     */
    private static ScreenOrigin buttonOriginFor(SlotScreenRect first) {
        return new ScreenOrigin(
                first.frameX() - GAP - BUTTON_SIZE,
                first.y() + (16 - BUTTON_SIZE) / 2);
    }

    /**
     * The single reveal rectangle: the row's nine slots, the button, and
     * the gap between them, as one span. Trigger and sustain are the same
     * shape, which is why no hover state is kept.
     */
    private static boolean cursorInRevealZone(SlotScreenRect first) {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos()
                * (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos()
                * (double) mc.getWindow().getGuiScaledHeight()
                / (double) mc.getWindow().getScreenHeight();
        int left = first.frameX() - GAP - BUTTON_SIZE;
        int right = first.x() + HotbarCycler.COLUMNS * SLOT_PITCH;
        int top = first.frameY();
        int bottom = first.y() + 16 + 1;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private static boolean buttonsVisible() {
        return IPConfig.hotbarCyclerEnabled()
                && IPConfig.hotbarCyclerShowButtons();
    }

    private static @Nullable SlotScreenRect slotRect(AbstractContainerScreen<?> screen, int containerSlot) {
        return VanillaSlotResolver.resolve(screen, containerSlot).orElse(null);
    }

    private static @Nullable AbstractContainerScreen<?> containerScreen() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> acs ? acs : null;
    }
}
