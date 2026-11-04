package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.inventoryplus.operations.CursorShelf;

import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.ReservationEditor;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Edit mode: a container screen's slots stop moving items and start taking rules.
 * Toggled by the edit button left of Sort ({@link EditToolbar}); one mode for the
 * whole screen.
 *
 * <ul>
 *   <li><b>Left-click a slot</b> locks it, or takes its lock off.</li>
 *   <li><b>Right-click a slot</b> reserves it ({@link #reserve}).</li>
 *   <li>Nothing else on a slot does anything: every click, key and drag that would
 *       move an item is swallowed ({@code AbstractContainerScreenEditModeMixin}).</li>
 *   <li><b>Entering</b> puts the cursor's item back in the inventory, so no click
 *       in the mode puts it down by surprise.</li>
 *   <li><b>Leaving</b>: Escape, the edit button again, or closing the screen. The
 *       mode belongs to the screen it was turned on for, so a screen that closes
 *       takes it with it, and the next screen opens out of it.</li>
 * </ul>
 * The look: the screen's frame glows gold and every slot is outlined, and hovering
 * a slot names both clicks. Locks and reservations draw as they always do (the
 * padlock, SlotKit's ghost). Not on creative's screen, whose slots click by their
 * own path.
 */
public final class EditMode {

    private EditMode() {}

    /** Gold, as the mockup has it: the frame, its glow and the slot outlines. */
    private static final int GOLD = 0xFFE8B230;
    private static final int GLOW = 0x00E8B230;
    private static final int OUTLINE = 0x90E8B230;

    /** The screen edit mode is on for, or null. Any other screen is out of it. */
    private static @Nullable Screen on;

    public static void register() {
        SlotDecorations.under("inventoryplus:edit_mode", DECORATION);
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            // Escape leaves the mode first, and the screen stays open. A dialog up
            // over the screen (the blueprint prompts, SlotKit's reserve popup) has
            // Escape for itself.
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!event.isEscape() || !isOn() || BlueprintDialogs.open() || ReservationEditor.isOpen()) return true;
                leave();
                return false;
            });
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (s == on) drawFrame(graphics, (AbstractContainerScreen<?>) s);
            });
        });
    }

    // State

    /** Whether edit mode is on for the screen that is open now. */
    public static boolean isOn() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen == on;
    }

    /** Whether the open screen can be edited: a container screen, not creative's. */
    public static boolean available() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> && !(screen instanceof CreativeModeInventoryScreen);
    }

    /** The edit button's lens. */
    public static void set(boolean value) {
        if (value) enter();
        else leave();
    }

    private static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (!available() || mc.player == null || mc.gameMode == null) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
        // The held item goes back into the inventory. With nowhere to put it, the
        // mode still opens: no click in it can put the item down.
        CursorShelf.putAway(screen.getMenu(), mc.gameMode, mc.player);
        on = screen;
    }

    public static void leave() {
        on = null;
        BlueprintDialogs.close();
    }

    // Clicks

    /** A click on {@code slot} in edit mode, from the screen's own click: 0 locks, 1 reserves. */
    public static void click(Slot slot, int button) {
        if (button == 0) {
            if (LockedSlots.isLockableHere(slot)) LockedSlots.toggle(slot);
        } else if (button == 1) {
            reserve(slot);
        }
    }

    /** Right-click: open SlotKit's reserve popup for {@code slot}. */
    static void reserve(Slot slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !LockedSlots.isLockableHere(slot)) return;
        ReservationEditor.open(SlotRef.of(player.containerMenu, slot, player));
    }

    // The look

    /** The frame and a glow fading out from it, outside the screen's image so nothing on it is covered. */
    private static void drawFrame(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        int x = a.inventoryPlus$getLeftPos();
        int y = a.inventoryPlus$getTopPos();
        int w = a.inventoryPlus$getImageWidth();
        int h = a.inventoryPlus$getImageHeight();
        graphics.outline(x - 1, y - 1, w + 2, h + 2, GOLD);
        for (int i = 2; i <= 4; i++) {
            int alpha = 0x90 >> (i - 2);
            graphics.outline(x - i, y - i, w + 2 * i, h + 2 * i, GLOW | alpha << 24);
        }
    }

    /** Every slot outlined, and the two clicks named on hover. Public for the self-checks. */
    public static final SlotDecorations.Decoration DECORATION = new SlotDecorations.Decoration() {
        @Override
        public void draw(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, Slot slot,
                         ItemStack stack, int x, int y) {
            if (screen == on) graphics.outline(x - 1, y - 1, 18, 18, OUTLINE);
        }

        @Override
        public @Nullable List<Component> tooltip(AbstractContainerScreen<?> screen, Slot slot, ItemStack stack,
                                                  int mouseX, int mouseY) {
            return screen == on ? tooltipFor(slot, stack) : null;
        }
    };

    /** "Gold Ingot (reserved)", then what each click does on this slot. */
    public static List<Component> tooltipFor(Slot slot, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        Reservation reserved = player == null ? Reservation.NONE
                : Reservations.get(SlotRef.of(player.containerMenu, slot, player));
        ItemStack shown = !stack.isEmpty() ? stack : reserved.item();
        Component name = shown.isEmpty() ? Component.literal("Empty slot") : shown.getHoverName();
        String marks = (reserved.isNone() ? "" : "reserved") + (LockedSlots.isLockedSlot(slot)
                ? (reserved.isNone() ? "locked" : ", locked") : "");
        lines.add(marks.isEmpty() ? name
                : name.copy().append(Component.literal(" (" + marks + ")").withStyle(ChatFormatting.GRAY)));
        if (!LockedSlots.isLockableHere(slot)) {
            lines.add(Component.literal("Can't be locked or reserved here").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.literal("Left-click: " + (LockedSlots.isLockedSlot(slot) ? "Unlock" : "Lock")));
        lines.add(Component.literal("Right-click: " + (reserved.isNone() ? "Reserve…" : "Edit the reservation")));
        return lines;
    }
}
