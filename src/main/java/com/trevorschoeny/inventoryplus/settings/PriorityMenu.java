package com.trevorschoeny.inventoryplus.settings;

import com.trevlar.menukit.core.Button;
import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.core.PanelElement;
import com.trevlar.menukit.core.PanelPosition;
import com.trevlar.menukit.core.PanelStyle;
import com.trevlar.menukit.core.TextLabel;
import com.trevlar.menukit.inject.SlotGroups;
import com.trevlar.menukit.screen.MKScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Shift-click in's priority: the order a shift-click tries the slot groups it
 * may land in. Opened from the Moving Items tab; Escape and Done go back to it.
 *
 * <p><b>Scaffold stage.</b> The list is MenuKit's slot group listing in its own
 * order, and the up and down buttons are disabled. Making the order real needs
 * MenuKit to route shift-clicks itself, which it only predicts on vanilla
 * screens today; that is a later decision.
 */
final class PriorityMenu extends MKScreen {

    private static final BooleanSupplier DISABLED = () -> true;
    private static final int ROW = 20;

    private PriorityMenu(Screen parent) {
        super(Component.literal("Shift-click in priority"), List.of(panel(parent)));
        setReturnAction(() -> Minecraft.getInstance().gui.setScreen(parent));
    }

    /** Opens the priority list over {@code parent} (the settings menu), which it returns to. */
    static void open(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new PriorityMenu(parent));
    }

    private static Panel panel(Screen parent) {
        List<PanelElement> out = new ArrayList<>();
        out.add(new TextLabel(0, 0, Component.literal("Shift-click in priority"), TextLabel.COLOR_DARK, false));
        out.add(new TextLabel(0, 14,
                Component.literal("A shift-click fills the first group on this list that has room, then the next."),
                0xFF555555, false));

        int y = 34;
        int n = 1;
        for (SlotGroups.Entry e : SlotGroups.listing()) {
            out.add(new Button(0, y, 16, 16, Component.literal("↑"), b -> {}, DISABLED));
            out.add(new Button(18, y, 16, 16, Component.literal("↓"), b -> {}, DISABLED));
            out.add(new TextLabel(40, y + 4, Component.literal(n++ + ". ").append(e.name()), 0xFF555555, false));
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
