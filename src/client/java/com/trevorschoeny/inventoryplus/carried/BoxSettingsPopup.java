package com.trevorschoeny.inventoryplus.carried;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.rules.BoxSettings;
import com.trevorschoeny.inventoryplus.toolbar.FeatureToggleButtons;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Divider;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Radio;
import com.trevlar.menukit.api.element.RadioGroup;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A shulker box's settings popup, opened from the button on the box's toolbar. Box
 * settings, not slot settings, so the button shows outside edit mode.
 *
 * <p>Each section shows while its global setting is on: "Pickups go into this box" (the
 * box's Intake) with "Fill shulker boxes with pickups", "Restock from this box" with
 * "Restock from shulker boxes". The button shows only where the popup has something to
 * set: Inventory Plus on the server, which stores the settings on the box, and at least
 * one of the two globals on.
 *
 * <p>Every change is sent at once; the popup reads the box as the server synced it, so
 * it always shows what the box holds. Done or Escape closes it.
 */
public final class BoxSettingsPopup {

    private BoxSettingsPopup() {}

    private static final Identifier SPRITE = Identifier.fromNamespaceAndPath("inventoryplus", "box_settings_button");

    /** The box the popup is open for, as its toolbar names it; null while closed. */
    private static @Nullable Supplier<@Nullable ResolvedSlotGroup> open;

    public static boolean isOpen() {
        return open != null;
    }

    /** Whether the popup has anything to set here. */
    private static boolean available() {
        return BoxSettingsClient.available()
                && (IPConfig.fillShulkersWithPickups() || IPConfig.restockFromShulkers());
    }

    /** The toolbar button of a shulker box's toolbar, its group read on each click. */
    public static List<PanelElement> button(Supplier<@Nullable ResolvedSlotGroup> group) {
        return List.of(Button.builder().sprite(SPRITE).size(FeatureToggleButtons.SIZE, FeatureToggleButtons.SIZE)
                .onClick(() -> open = open == null ? group : null)
                .tooltip(Component.literal("Box settings")
                        .append(Component.literal("\nPickups and restock for this box").withStyle(ChatFormatting.GRAY)))
                .visibleWhen(BoxSettingsPopup::available)
                .build());
    }

    /** The popup's controls, in {@link Control} order, and the popup; set at register. */
    private static final List<PanelElement> controls = new java.util.ArrayList<>();
    private static @Nullable Panel panel;

    /** The popup's controls, as a check names one to click. */
    public enum Control { NEVER, BEFORE, AFTER, MATCHING_ONLY, RESTOCK }

    /**
     * Where {@code control} sits on screen now, its centre in GUI pixels, or null while
     * the popup isn't placed. For the checks, which click it with the real mouse.
     */
    public static double @Nullable [] centerOf(Control control) {
        var r = panel == null ? null : panel.placedBounds();
        if (r == null) return null;
        PanelElement e = controls.get(control.ordinal());
        int pad = ScreenPanelAdapter.DEFAULT_PADDING;
        return new double[] {r.leftPos() + pad + e.getChildX() + e.getWidth() / 2.0,
                r.topPos() + pad + e.getChildY() + e.getHeight() / 2.0};
    }

    /** The popup. Once, at client init. */
    public static void register() {
        RadioGroup<BoxSettings.When> when = RadioGroup.state(() -> current().when(),
                v -> send(current().withWhen(v)));
        controls.add(Radio.builder(when, BoxSettings.When.OFF).label(Component.literal("Never")).build());
        controls.add(Radio.builder(when, BoxSettings.When.BEFORE).label(Component.literal("Before your inventory")).build());
        controls.add(Radio.builder(when, BoxSettings.When.AFTER).label(Component.literal("After your inventory")).build());
        controls.add(Checkbox.builder().label(Component.literal("Only items already in this box"))
                .state(() -> current().matchingOnly(), v -> send(current().withMatchingOnly(v)))
                // Matching only refines an Intake; with none there is nothing to refine.
                .disabledWhen(() -> current().when() == BoxSettings.When.OFF)
                .build());
        controls.add(Checkbox.builder().label(Component.literal("Restock from this box"))
                .state(() -> current().restock(), v -> send(current().withRestock(v)))
                .build());
        Flow pickups = Flow.builder().vertical().gap(4)
                .visibleWhen(IPConfig::fillShulkersWithPickups)
                .add(TextLabel.builder().text(Component.literal("Pickups go into this box").withStyle(ChatFormatting.DARK_GRAY)).build())
                .addAll(controls.subList(0, 4))
                .build();
        Flow restock = Flow.builder().vertical().gap(4)
                .visibleWhen(IPConfig::restockFromShulkers)
                .add(TextLabel.builder().text(Component.literal("Restock").withStyle(ChatFormatting.DARK_GRAY)).build())
                .add(controls.get(4))
                .build();
        panel = Panel.builder("inventoryplus:box_settings")
                .elements(List.of(Flow.builder().vertical().gap(4)
                        .add(TextLabel.builder().text(Component.literal("Shulker Box settings")).build())
                        .add(Divider.horizontal().build())
                        .add(pickups)
                        .add(Divider.horizontal().visibleWhen(() -> IPConfig.fillShulkersWithPickups()
                                && IPConfig.restockFromShulkers()).build())
                        .add(restock)
                        .add(Button.builder().label(Component.translatable("gui.done")).size(60, 20)
                                .onClick(() -> open = null).build())
                        .build()))
                .style(PanelStyle.RAISED)
                // Shown only while open: a modal counts while visible, and would hold the screen's input.
                .visibleWhen(() -> open != null && first() != null && available())
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.center()))
                .build()
                .modal()
                .onEscape(() -> open = null);
        new ScreenPanelAdapter(panel);
        // A new screen starts with the popup closed.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> ScreenEvents.remove(screen).register(s -> open = null));
    }

    /** The box's settings as the server synced them. */
    private static BoxSettings current() {
        Slot slot = first();
        AbstractContainerMenu menu = menu();
        return slot == null || menu == null ? BoxSettings.DEFAULT : BoxSettings.of(menu, slot);
    }

    private static void send(BoxSettings settings) {
        Slot slot = first();
        AbstractContainerMenu menu = menu();
        if (slot != null && menu != null) BoxSettingsClient.setOpen(menu, slot.index, settings);
    }

    private static @Nullable Slot first() {
        ResolvedSlotGroup g = open == null ? null : open.get();
        return g == null || g.slots().isEmpty() ? null : g.slots().get(0);
    }

    private static @Nullable AbstractContainerMenu menu() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.containerMenu;
    }
}
