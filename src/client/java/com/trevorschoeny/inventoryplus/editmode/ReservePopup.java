package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.rules.Reservations;

import com.trevlar.menukit.api.element.ItemMatch;
import com.trevlar.menukit.api.element.ItemPicker;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

/**
 * The reserve popup, R over a slot in edit mode: MenuKit's item picker with the slot's
 * reservation as a draft. Search and pick the item; under advanced settings the name,
 * enchantments (a level or any level), other components and the count, each checked only
 * when filled in. Done reserves the slot as the draft says, Remove clears it, Cancel and
 * Escape leave it as it was.
 *
 * <p>The server stores and enforces reservations ({@link Reservations}), so the popup
 * opens only with Inventory Plus on the server ({@link #available}).
 */
public final class ReservePopup {

    private ReservePopup() {}

    /** The slot the popup is open for, as a click names it; -1 while closed. */
    private static int menuSlot = -1;
    private static int containerId = -1;
    /** What the picker edits: the slot's reservation when it opened, then the player's changes. */
    private static ItemMatch draft = Reservations.NONE;

    private static ItemPicker picker;

    /** Whether reservations can be set here: Inventory Plus on the server. */
    public static boolean available() {
        return ClientPlayNetworking.canSend(Reservations.Reserve.TYPE);
    }

    public static boolean isOpen() {
        return picker != null && picker.isDialogOpen();
    }

    /** Once, at client init: MenuKit builds the picker's dialog then. */
    public static void register() {
        picker = ItemPicker.builder()
                .label(Component.literal("Reserve slot"))
                .rule(() -> draft, r -> draft = r)
                .name().enchantments().components().count()
                .button(Component.literal("Remove"), ReservePopup::remove)
                .onDone(ReservePopup::send)
                .build();
    }

    /** Opens the popup for {@code slot} of the open screen, with its reservation as the draft. */
    public static void open(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen() == null || !available()) return;
        AbstractContainerMenu menu = player.containerMenu;
        menuSlot = menu.slots.indexOf(slot);
        containerId = menu.containerId;
        draft = Reservations.get(SlotRef.of(menu, slot, player));
        // An unreserved slot holding something starts from that item's kind alone, as a
        // pick from the grid does: every field blank, so nothing past the item is checked
        // until the player fills it in.
        if (Reservations.isNone(draft) && !slot.getItem().isEmpty()) {
            draft = new ItemMatch(slot.getItem().getItem().getDefaultInstance(), ItemMatch.NONE);
        }
        picker.open(mc.gui.screen());
    }

    /** What the popup would reserve now. */
    public static ItemMatch draft() {
        return draft;
    }

    /** Done: reserves the slot as the draft says, and closes. */
    public static void done() {
        picker.done();
    }

    /** Remove: clears the slot's reservation, and closes. */
    public static void remove() {
        draft = Reservations.NONE;
        send();
        picker.cancel();   // closes it; the slot is already cleared
    }

    /** Cancel: closes, leaving the slot as it was. */
    public static void close() {
        picker.cancel();
    }

    /** Asks the server to reserve the slot as the draft says (an empty draft clears it). */
    private static void send() {
        @Nullable LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || menuSlot < 0 || player.containerMenu.containerId != containerId) return;
        request(player.containerMenu, menuSlot, draft);
    }

    /**
     * Asks the server to reserve slot {@code menuSlot} of {@code menu} with {@code rule}
     * ({@link Reservations#NONE} clears it). False without Inventory Plus on the server.
     */
    public static boolean request(AbstractContainerMenu menu, int menuSlot, ItemMatch rule) {
        if (!available()) return false;
        ClientPlayNetworking.send(new Reservations.Reserve(menu.containerId, menuSlot, rule));
        return true;
    }
}
