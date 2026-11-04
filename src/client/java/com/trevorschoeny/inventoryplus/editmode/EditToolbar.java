package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.carried.IntakeClient;
import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.toolbar.FeatureToggleButtons;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Checkbox;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.element.Toggle;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.OutsideRegion;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.slotkit.api.group.CategoryPanels;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;
import com.trevlar.slotkit.api.group.SlotGroupCategory;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Edit mode's controls: the edit button left of Sort on every container toolbar, with
 * Save and Load (blueprints) beside it while the mode is on, and a shulker box's Intake
 * beside the box while the mode is on.
 *
 * <p>Each toolbar's buttons act on the container that toolbar sits above: the chest's
 * Save saves the chest, the inventory's saves the inventory. The edit button is one
 * mode for the whole screen on every toolbar.
 */
public final class EditToolbar {

    private EditToolbar() {}

    private static final Identifier EDIT = Identifier.fromNamespaceAndPath("inventoryplus", "edit_button");
    private static final Identifier SAVE = Identifier.fromNamespaceAndPath("inventoryplus", "blueprint_save_button");
    private static final Identifier LOAD = Identifier.fromNamespaceAndPath("inventoryplus", "blueprint_load_button");

    private static final int SIZE = FeatureToggleButtons.SIZE;

    /**
     * Save, Load and the edit button, left to right, for a toolbar whose container is
     * {@code slots} (read on each click, in slot order, as a blueprint numbers them).
     */
    public static List<PanelElement> buttons(Supplier<List<Slot>> slots) {
        return List.of(
                Button.builder().sprite(SAVE).size(SIZE, SIZE)
                        .onClick(() -> BlueprintDialogs.openSave(slots))
                        .tooltip(Component.literal("Save blueprint")
                                .append(Component.literal("\nThis container's locks and reservations, to load onto another")
                                        .withStyle(ChatFormatting.GRAY)))
                        .visibleWhen(EditMode::isOn)
                        .build(),
                Button.builder().sprite(LOAD).size(SIZE, SIZE)
                        .onClick(() -> BlueprintDialogs.openLoad(slots))
                        .tooltip(Component.literal("Load blueprint"))
                        .visibleWhen(EditMode::isOn)
                        .build(),
                Toggle.builder().sprite(EDIT).size(SIZE, SIZE)
                        .state(EditMode::isOn, v -> {
                            if (SettingsMenu.ctrlClickOpens(SettingsMenu.SLOTS)) return;
                            EditMode.set(v);
                        })
                        .tooltip(SettingsMenu.tooltip("Slots", () -> Component.literal("Edit slots: " + (EditMode.isOn() ? "On" : "Off"))
                                .append(Component.literal("\nLeft-click a slot to lock it, right-click to reserve it")
                                        .withStyle(ChatFormatting.GRAY))))
                        .visibleWhen(EditMode::available)
                        .build());
    }

    /** The open menu's slots of the player's main inventory and hotbar, in inventory order. */
    public static List<Slot> inventorySlots() {
        AbstractContainerMenu menu = openMenu();
        if (menu == null || Minecraft.getInstance().player == null) return List.of();
        return menu.slots.stream()
                .filter(s -> s.container == Minecraft.getInstance().player.getInventory() && s.getContainerSlot() < 36)
                .sorted(Comparator.comparingInt(Slot::getContainerSlot))
                .toList();
    }

    /** {@code group}'s slots in container order, or none where it isn't showing. */
    public static List<Slot> groupSlots(@Nullable ResolvedSlotGroup group) {
        return group == null ? List.of()
                : group.slots().stream().sorted(Comparator.comparingInt(Slot::getContainerSlot)).toList();
    }

    private static @Nullable AbstractContainerMenu openMenu() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.containerMenu;
    }

    // A shulker box's Intake

    /**
     * Beside each shulker box on screen, in edit mode: its Intake (Off, Before inventory,
     * After inventory) and Matching only. A placed box keeps it on its block and gives it
     * to the item when broken; a box opened from the inventory keeps it on its item.
     * Needs Inventory Plus on the server, which applies it.
     */
    public static void registerIntake() {
        CategoryPanels.declare(SlotGroupCategory.SHULKER_STORAGE, on -> Panel.builder(
                        "inventoryplus.edit.intake" + (on.index() == 0 ? "" : "." + on.index()))
                .elements(List.of(intakeControls(on::group)))
                .style(PanelStyle.RAISED)
                .in(ModeScope.BOTH, on.around(PanelPosition.region(OutsideRegion.LEFT_ALIGN_TOP)))
                .build()
                .visibleWhen(() -> EditMode.isOn() && IntakeClient.available()));
    }

    private static PanelElement intakeControls(Supplier<@Nullable ResolvedSlotGroup> group) {
        Flow.Builder column = Flow.builder().vertical().gap(1, 1)
                .add(TextLabel.builder().text(Component.literal("Intake")).build());
        for (Intake.When when : Intake.When.values()) {
            column.add(Toggle.builder().size(96, 14)
                    .label(Component.literal(label(when)))
                    .state(() -> current(group).when() == when, v -> {
                        if (v) send(group, new Intake(when, current(group).matchingOnly()));
                    })
                    .build());
        }
        column.add(Checkbox.builder()
                .label(Component.literal("Matching only"))
                .state(() -> current(group).matchingOnly(),
                        v -> send(group, new Intake(current(group).when(), v)))
                .build());
        return column.build();
    }

    static String label(Intake.When when) {
        return switch (when) {
            case OFF -> "Off";
            case BEFORE -> "Before inventory";
            case AFTER -> "After inventory";
        };
    }

    /** The box's Intake as the server synced it. */
    private static Intake current(Supplier<@Nullable ResolvedSlotGroup> group) {
        Slot slot = first(group);
        AbstractContainerMenu menu = openMenu();
        return slot == null || menu == null ? Intake.OFF : Intake.of(menu, slot);
    }

    private static void send(Supplier<@Nullable ResolvedSlotGroup> group, Intake intake) {
        Slot slot = first(group);
        AbstractContainerMenu menu = openMenu();
        if (slot == null || menu == null || !IntakeClient.available()) return;
        ClientPlayNetworking.send(new Intake.SetOpen(menu.containerId, slot.index, intake));
    }

    private static @Nullable Slot first(Supplier<@Nullable ResolvedSlotGroup> group) {
        ResolvedSlotGroup g = group.get();
        return g == null || g.slots().isEmpty() ? null : g.slots().get(0);
    }
}
