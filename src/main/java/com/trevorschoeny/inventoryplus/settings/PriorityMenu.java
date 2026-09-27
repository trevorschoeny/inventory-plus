package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.PanelPosition;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.core.TextLabel;
import com.trevlar.menukit.screen.MKScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A priority list: the order a move tries the places it may put items in.
 * Shift-click in (Moving Items tab) and Move Matching (its own tab) each open
 * one; Escape and Done go back to the tab.
 *
 * <p><b>Scaffold stage.</b> The list is the places in their listing order, and
 * the up and down buttons are disabled. Making shift-click in's order real
 * needs MenuKit to route shift-clicks itself, which it only predicts on
 * vanilla screens today; that is a later decision.
 */
final class PriorityMenu extends MKScreen {

    private static final BooleanSupplier DISABLED = () -> true;
    private static final int ROW = 20;

    private PriorityMenu(Screen parent, String title, List<Component> places) {
        super(Component.literal(title), List.of(panel(parent, title, places)));
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    /** Opens a priority list of {@code places} over {@code parent} (the settings menu), which it returns to. */
    static void open(Screen parent, String title, List<Component> places) {
        Minecraft.getInstance().gui.setScreen(new PriorityMenu(parent, title, places));
    }

    private static Panel panel(Screen parent, String title, List<Component> places) {
        List<PanelElement> out = new ArrayList<>();
        out.add(new TextLabel(0, 0, Component.literal(title), TextLabel.COLOR_DARK, false));
        out.add(new TextLabel(0, 14,
                Component.literal("Items go to the first place on this list that has room, then the next."),
                0xFF555555, false));

        int y = 34;
        int n = 1;
        for (Component place : places) {
            out.add(new Button(0, y, 16, 16, Component.literal("↑"), b -> {}, DISABLED));
            out.add(new Button(18, y, 16, 16, Component.literal("↓"), b -> {}, DISABLED));
            out.add(new TextLabel(40, y + 4, Component.literal(n++ + ". ").append(place), 0xFF555555, false));
            y += ROW;
        }

        out.add(new Button(0, y + 6, 60, 16, Component.literal("Done"),
                b -> Minecraft.getInstance().gui.setScreen(parent)));
        return Panel.builder("inventoryplus:settings_priority")
                .style(PanelStyle.RAISED)
                .position(PanelPosition.main())
                .elements(out)
                .build();
    }
}
