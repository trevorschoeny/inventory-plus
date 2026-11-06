package com.trevorschoeny.inventoryplus.hotbarsets;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevlar.menukit.api.element.AbstractPanelElement;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Floating;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.ListView;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenOrigin;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.SlotScreenRect;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Creative's sets window. The stack symbol left of the hotbar opens it: the saved sets in the
 * HUD list's order (oldest at the top, newest nearest the hotbar), each with move up, move
 * down and delete floating off the panel's right side ({@code ListView.controlsOutside}). Save,
 * floating beside the hotbar, adds the hotbar as it is now at the bottom. The screen dims
 * behind the window, and the hotbar stays lit, since Save takes it. Escape closes it.
 */
public final class HotbarSetsWindow {

    private HotbarSetsWindow() {}

    private static final Identifier SYMBOL = Identifier.fromNamespaceAndPath("inventoryplus", "hotbar_sets_button");
    private static final int PADDING = 4;

    private static boolean open = false;
    private static @Nullable Panel window;

    public static boolean isOpen() { return open; }

    public static void setOpen(boolean on) { open = on; }

    public static void register() {
        registerSymbol();
        registerWindow();
        // Closing the screen shuts the window, so the next one opens without it. Not on init:
        // a screen is initialized again on a resize, and the window should stay up through it.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof CreativeModeInventoryScreen) ScreenEvents.remove(screen).register(s -> open = false);
        });
    }

    /** The creative screen and its hotbar's first item box, or null where there's no hotbar to sit by. */
    private static @Nullable SlotScreenRect hotbar() {
        if (!(Minecraft.getInstance().gui.screen() instanceof CreativeModeInventoryScreen cs)) return null;
        if (!IPConfig.hotbarSetsEnabled()) return null;
        return HotbarSetsScreens.slot(cs, 0);
    }

    // The symbol

    private static void registerSymbol() {
        PanelElement button = Button.builder().sprite(SYMBOL).size(11, 10)
                .onClick(() -> open = !open)
                .tooltip(Component.literal("Hotbar sets"))
                .build();
        Panel panel = Panel.builder("inventoryplus:hotbar_sets_symbol")
                .elements(List.of(button))
                .style(PanelStyle.NONE)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(HotbarSetsWindow::symbolOrigin)))
                .build()
                .opaque(false);
        new ScreenPanelAdapter(panel, 0).onPlayerInventory();
    }

    /** Left of the hotbar's frame, centred on the row. */
    private static @Nullable ScreenOrigin symbolOrigin() {
        SlotScreenRect h = hotbar();
        return h == null ? null : new ScreenOrigin(h.frameX() - 4 - 11, h.frameY() + 4);
    }

    // The window

    private static void registerWindow() {
        ListView<Integer> sets = ListView.<Integer>builder()
                // Keyed by place: a row reads its set by index every frame, so a move or a
                // delete shows the right items in every row.
                .items(() -> IntStream.range(0, HotbarSets.savedSets().size()).boxed().toList())
                .key(i -> i)
                .row(SetRow::new)
                .gap(2)
                .move((i, to) -> HotbarSets.move(i, to))
                .remove(HotbarSets::delete)
                .controlsOutside()
                .empty(TextLabel.builder().text(Component.literal("No saved sets yet.")).build())
                .build();
        PanelElement save = Floating.at(HotbarSetsWindow::saveOrigin,
                Button.builder().label(Component.literal("Save")).size(0, 16)
                        .onClick(HotbarSets::saveHotbar)
                        .tooltip(Component.literal("Save the hotbar as a new set"))
                        .build());
        Panel panel = Panel.builder("inventoryplus:hotbar_sets_window")
                .elements(List.of(
                        Flow.builder().vertical().gap(4)
                                .add(TextLabel.builder().text(Component.literal("Hotbar sets")).build())
                                .add(sets)
                                .build(),
                        save))
                .style(PanelStyle.RAISED)
                // Shown only while open: a modal counts while visible, and would hold the screen's input.
                .visibleWhen(() -> open && hotbar() != null)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.pixel(HotbarSetsWindow::windowOrigin)))
                .build()
                // As wide as the rows, so an empty list doesn't shrink it to its message.
                .pinnedWidth(9 * 18)
                .modal()
                .onEscape(() -> open = false)
                .litWhileDimmed(HotbarSetsWindow::litHotbar);
        new ScreenPanelAdapter(panel, PADDING).onPlayerInventory();
        window = panel;
    }

    /** Above the hotbar, its left edge with the hotbar's, its bottom just over it. */
    private static @Nullable ScreenOrigin windowOrigin() {
        SlotScreenRect h = hotbar();
        if (!open || h == null || window == null) return null;
        // The height it was placed at last frame, so the bottom sits on the hotbar as the list grows.
        var placed = window.placedBounds();
        int height = placed != null ? placed.imageHeight() : window.getHeight() + 2 * PADDING;
        return new ScreenOrigin(h.frameX() - PADDING, h.frameY() - 4 - height);
    }

    /** Right of the creative screen's frame, level with the hotbar. */
    private static ScreenOrigin saveOrigin() {
        SlotScreenRect h = hotbar();
        if (h == null) return new ScreenOrigin(-1000, -1000);
        // The hotbar's frame starts 8 px into creative's 195-wide frame.
        return new ScreenOrigin(h.frameX() - 8 + 195 + 3, h.frameY() + 1);
    }

    /** The hotbar row, lit through the dim. */
    private static List<ScreenRectangle> litHotbar() {
        SlotScreenRect h = hotbar();
        return h == null ? List.of() : List.of(new ScreenRectangle(h.frameX(), h.frameY(), 9 * 18, 18));
    }

    /** One saved set: nine slots, reading set {@code index} every frame. */
    private static final class SetRow extends AbstractPanelElement {

        private final int index;

        SetRow(int index) {
            this.index = index;
            this.width = 9 * 18;
            this.height = 18;
            this.authoredWidth = width;
        }

        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }

        @Override
        public void render(RenderContext ctx) {
            List<List<ItemStack>> sets = HotbarSets.savedSets();
            if (index >= sets.size()) return;
            HotbarSetsScreens.row(ctx.graphics(), Minecraft.getInstance().font,
                    ctx.originX() + getChildX(), ctx.originY() + getChildY(), sets.get(index));
        }
    }
}
