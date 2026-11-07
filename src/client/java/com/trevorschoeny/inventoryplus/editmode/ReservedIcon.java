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
 * A reserved slot's icon: its item at half size in the slot's top-left corner, so a
 * reserved slot reads as reserved whether it is empty or full. In edit mode it draws
 * above the mode's own fade. No veil: a square over the icon read as a box around it.
 */
public final class ReservedIcon {

    private ReservedIcon() {}

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
    }
}
