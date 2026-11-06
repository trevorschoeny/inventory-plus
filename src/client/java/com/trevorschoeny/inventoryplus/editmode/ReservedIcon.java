package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.rules.Reservations;

import com.trevlar.menukit.api.element.ItemMatch;
import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A reserved slot's icon: its item at half size in the slot's top-left corner, under a
 * faint veil, so a reserved slot reads as reserved whether it is empty or full. In edit
 * mode the veil comes off and the icon draws crisp, above the mode's own fade.
 */
public final class ReservedIcon {

    private ReservedIcon() {}

    // Vanilla's slot background (0x8B8B8B), about 45% opaque.
    private static final int VEIL = 0x738B8B8B;

    /** Once, at client init. The id sorts after edit mode's veil and the padlocks, so the icon lands on top. */
    public static void register() {
        SlotDecorations.over("inventoryplus:slot_reservation", ReservedIcon::draw);
    }

    private static void draw(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, Slot slot, ItemStack stack, int x, int y) {
        ItemMatch rule = Reservations.get(SlotRef.of(screen.getMenu(), slot, Minecraft.getInstance().player));
        if (Reservations.isNone(rule)) return;
        // Half size, its 8 px box from the slot frame's top-left pixel.
        g.pose().pushMatrix();
        g.pose().translate(x - 1, y - 1);
        g.pose().scale(0.5f, 0.5f);
        g.item(rule.stack(), 0, 0);
        g.pose().popMatrix();
        if (!EditMode.isOn()) g.fill(x - 1, y - 1, x + 7, y + 7, VEIL);
    }
}
