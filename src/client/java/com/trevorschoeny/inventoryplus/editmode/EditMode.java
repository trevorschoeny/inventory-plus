package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;
import com.trevorschoeny.inventoryplus.operations.CursorShelf;

import com.trevlar.menukit.api.panel.SlotDecorations;
import com.trevorschoeny.inventoryplus.rules.Reservations;

import com.trevlar.menukit.api.element.ItemMatch;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Edit mode: every slot on a container screen stops moving items and starts taking rules,
 * the inventory's and the container's alike. Toggled by the one edit button, left of Sort on
 * the inventory's toolbar ({@link EditToolbar}); one mode for the whole screen.
 *
 * <ul>
 *   <li><b>Keys on the slot under the mouse</b> (their own group in Controls):
 *       {@link IPKeybinds#EDIT_LOCK L} locks it or takes the lock off,
 *       {@link IPKeybinds#EDIT_RESERVE R} reserves it ({@link #reserve}), and
 *       {@link IPKeybinds#EDIT_CYCLE C} adds it to its column's cycle or takes it out
 *       (Column Cycler on). Hold L or C and sweep: every slot the mouse passes gets the
 *       first slot's new state.</li>
 *   <li><b>Clicks on slots do nothing</b>, nor does any other key or drag that would
 *       move an item ({@code AbstractContainerScreenEditModeMixin}); buttons and
 *       dialogs still take clicks.</li>
 *   <li><b>Entering</b> puts the cursor's item back in the inventory, so no click
 *       in the mode puts it down by surprise.</li>
 *   <li><b>Leaving</b>: Escape, the edit button again, or closing the screen. The
 *       mode belongs to the screen it was turned on for, so a screen that closes
 *       takes it with it, and the next screen opens out of it.</li>
 * </ul>
 * The look: the screen's frame glows gold, every slot is outlined and every item
 * fades, and the keys are named above the frame ({@link #HINT_PANEL}, MenuKit's
 * {@link OutsideRegion#TOP_CENTER}, so the region system keeps it above a screen's
 * own chrome like creative's item-tab row). Locks and reservations draw crisp
 * over the faded items (the padlock, SlotKit's corner icon). On creative's screen,
 * only its inventory tab.
 */
public final class EditMode {

    private EditMode() {}

    /** Gold, as the mockup has it: the frame, its glow and the slot outlines. */
    private static final int GOLD = 0xFFE8B230;
    private static final int GLOW = 0x00E8B230;
    private static final int OUTLINE = 0x90E8B230;
    /** Vanilla's slot grey, about 70% opaque: an item under it reads as faded. */
    private static final int VEIL = 0xB08B8B8B;

    /** The screen edit mode is on for, or null. Any other screen is out of it. */
    private static @Nullable Screen on;

    /** The keys named above the frame the whole time the mode is on. */
    private static final Panel HINT_PANEL = Panel.builder("inventoryplus:edit_mode_hint")
            .elements(List.of(TextLabel.builder().text(EditMode::hint).color(0xFFFFFFFF).shadow(true).build()))
            .style(PanelStyle.NONE)
            .visibleWhen(EditMode::isOn)
            .in(ModeScope.BOTH, Placement.screen(PanelPosition.region(OutsideRegion.TOP_CENTER)))
            .build();

    public static void register() {
        new ScreenPanelAdapter(HINT_PANEL);
        SlotDecorations.under("inventoryplus:edit_mode", DECORATION);
        // In the mode the rules take over: every item fades, and the reservations'
        // corner icons draw crisp above it (ReservedIcon).
        SlotDecorations.over("inventoryplus:edit_veil", (graphics, screen, slot, stack, x, y) -> {
            if (screen == on && !stack.isEmpty()) graphics.fill(x, y, x + 16, y + 16, VEIL);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> sweepTick());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            // Escape leaves the mode first, and the screen stays open. A dialog up
            // over the screen (the reserve popup) has
            // Escape for itself.
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (!event.isEscape() || !isOn() || ReservePopup.isOpen()) return true;
                leave();
                return false;
            });
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
                if (!isOn() || ReservePopup.isOpen() || sweep != null) return;
                Slot slot = ScreenLayout.hoveredSlot((AbstractContainerScreen<?>) s);
                if (slot == null || !slot.isActive()) return;
                if (IPKeybinds.EDIT_LOCK.matches(event)) {
                    if (!LockedSlots.isLockableHere(slot)) return;
                    startSweep(IPKeybinds.EDIT_LOCK, slot, LockedSlots.toggle(slot));
                } else if (IPKeybinds.EDIT_RESERVE.matches(event)) {
                    reserve(slot);
                } else if (IPKeybinds.EDIT_CYCLE.matches(event)) {
                    if (!IPConfig.columnCyclerEnabled() || !ColumnCycler.isCycleable(slot)) return;
                    ColumnCycler.toggle(slot);
                    startSweep(IPKeybinds.EDIT_CYCLE, slot, ColumnCycler.isCycleSlot(slot));
                }
            });
            // Right after the screen's background: over its texture, under everything
            // else on it (toolbars, slots, dialogs, the reserve popup).
            ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (s == on) drawFrame(graphics, (AbstractContainerScreen<?>) s);
            });
        });
    }

    // State

    /** Whether edit mode is on for the screen that is open now (off on a creative item tab). */
    public static boolean isOn() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen != null && screen == on && available();
    }

    /**
     * Whether the open screen can be edited: any container screen, its container's slots
     * included, and on creative's screen only its inventory tab (an item tab's slots are the
     * catalog, not the player's). A slot that can't take a rule says so on hover.
     */
    public static boolean available() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen instanceof CreativeModeInventoryScreen creative) return creative.isInventoryOpen();
        return screen instanceof AbstractContainerScreen<?>;
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
    }

    // Keys

    /** A held L or C being swept across slots: the key, the state each slot gets, and the slots done. */
    private record Sweep(KeyMapping key, boolean state, Set<Integer> done) {}

    private static @Nullable Sweep sweep;

    private static void startSweep(KeyMapping key, Slot first, boolean state) {
        Set<Integer> done = new HashSet<>();
        done.add(first.index);
        sweep = new Sweep(key, state, done);
    }

    /**
     * While the key is held, each slot the mouse enters gets the sweep's state. Polled on
     * the client tick, so a very fast sweep may skip a slot. Ends on release, or when the
     * mode or the screen goes.
     */
    private static void sweepTick() {
        Sweep now = sweep;
        if (now == null) return;
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!isOn() || !IPKeybinds.isHeld(now.key())) {
            sweep = null;
            return;
        }
        Slot slot = ScreenLayout.hoveredSlot((AbstractContainerScreen<?>) screen);
        if (slot == null || !slot.isActive() || !now.done().add(slot.index)) return;
        if (now.key() == IPKeybinds.EDIT_LOCK) {
            if (LockedSlots.isLockableHere(slot)) LockedSlots.setLocked(slot, now.state());
        } else if (ColumnCycler.isCycleable(slot)) {
            ColumnCycler.setCycle(slot.getContainerSlot(), now.state());
        }
    }

    /** R: open the reserve popup for {@code slot}. */
    static void reserve(Slot slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !LockedSlots.isLockableHere(slot)) return;
        ReservePopup.open(slot);
    }

    // The look

    /**
     * The keys, named over the panel the whole time the mode is on, as each is bound:
     * "L lock · R reserve · C cycle" (cycle only with Column Cycler on).
     */
    private static Component hint() {
        MutableComponent hint = Component.empty()
                .append(keyName(IPKeybinds.EDIT_LOCK)).append(" lock  ·  ")
                .append(keyName(IPKeybinds.EDIT_RESERVE)).append(" reserve");
        if (IPConfig.columnCyclerEnabled()) hint.append("  ·  ").append(keyName(IPKeybinds.EDIT_CYCLE)).append(" cycle");
        return hint;
    }

    private static Component keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW);
    }

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

    /** Every slot outlined, and its rules named on hover. Public for the self-checks. */
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

    /** "Gold Ingot (reserved)": the keys are in the hint over the panel. */
    public static List<Component> tooltipFor(Slot slot, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        LocalPlayer player = Minecraft.getInstance().player;
        ItemMatch reserved = player == null ? Reservations.NONE
                : Reservations.get(SlotRef.of(player.containerMenu, slot, player));
        ItemStack shown = !stack.isEmpty() ? stack : reserved.stack();
        Component name = shown.isEmpty() ? Component.literal("Empty slot") : shown.getHoverName();
        String marks = (Reservations.isNone(reserved) ? "" : "reserved") + (LockedSlots.isLockedSlot(slot)
                ? (Reservations.isNone(reserved) ? "locked" : ", locked") : "");
        lines.add(marks.isEmpty() ? name
                : name.copy().append(Component.literal(" (" + marks + ")").withStyle(ChatFormatting.GRAY)));
        if (!LockedSlots.isLockableHere(slot)) {
            lines.add(Component.literal("Can't be locked or reserved here").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
