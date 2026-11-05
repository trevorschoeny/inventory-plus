package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.carried.IntakeClient;
import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.toolbar.FeatureToggleButtons;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Toggle;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Edit mode's controls: the edit button left of Sort on every container toolbar, and on
 * a shulker box's toolbar its Intake buttons too. The edit button is one mode for the
 * whole screen on every toolbar.
 */
public final class EditToolbar {

    private EditToolbar() {}

    private static final Identifier EDIT = Identifier.fromNamespaceAndPath("inventoryplus", "edit_button");
    private static final Identifier BEFORE = Identifier.fromNamespaceAndPath("inventoryplus", "intake_before_button");
    private static final Identifier AFTER = Identifier.fromNamespaceAndPath("inventoryplus", "intake_after_button");
    private static final Identifier MATCHING = Identifier.fromNamespaceAndPath("inventoryplus", "intake_matching_button");

    private static final int SIZE = FeatureToggleButtons.SIZE;

    /** The edit button. */
    public static List<PanelElement> buttons() {
        return List.of(
                Toggle.builder().sprite(EDIT).size(SIZE, SIZE)
                        .state(EditMode::isOn, v -> {
                            if (SettingsMenu.ctrlClickOpens(SettingsMenu.SLOTS)) return;
                            EditMode.set(v);
                        })
                        .tooltip(SettingsMenu.tooltip("Slots", () -> Component.literal("Edit slots: " + (EditMode.isOn() ? "On" : "Off"))
                                .append(Component.literal("\nLock and reserve this screen's slots")
                                        .withStyle(ChatFormatting.GRAY))))
                        .visibleWhen(EditMode::available)
                        .build());
    }

    private static @Nullable AbstractContainerMenu openMenu() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.containerMenu;
    }

    // A shulker box's Intake

    /**
     * A shulker box's Intake, as three buttons on its toolbar in edit mode: Before
     * inventory and After inventory (one at most is lit; clicking the lit one turns
     * Intake off), and Matching only. A placed box keeps it on its block and gives it to
     * the item when broken; a box opened from the inventory keeps it on its item. Needs
     * Inventory Plus on the server, which applies it.
     */
    public static List<PanelElement> intakeButtons(Supplier<@Nullable ResolvedSlotGroup> group) {
        return List.of(
                whenButton(group, Intake.When.BEFORE, BEFORE, "Intake: before inventory",
                        "Pickups go into this box first. Your inventory takes what doesn't fit."),
                whenButton(group, Intake.When.AFTER, AFTER, "Intake: after inventory",
                        "Pickups go into this box only once your inventory is full."),
                Toggle.builder().sprite(MATCHING).size(SIZE, SIZE)
                        .state(() -> current(group).matchingOnly(),
                                v -> send(group, new Intake(current(group).when(), v)))
                        .tooltip(Component.literal("Intake: matching only")
                                .append(Component.literal("\nOnly items this box already holds go in")
                                        .withStyle(ChatFormatting.GRAY)))
                        // Matching only refines an Intake; with Intake off there is nothing to refine.
                        .visibleWhen(() -> EditMode.isOn() && IntakeClient.available()
                                && current(group).when() != Intake.When.OFF)
                        .build());
    }

    private static PanelElement whenButton(Supplier<@Nullable ResolvedSlotGroup> group, Intake.When when,
                                           Identifier sprite, String title, String body) {
        return Toggle.builder().sprite(sprite).size(SIZE, SIZE)
                .state(() -> current(group).when() == when,
                        v -> send(group, new Intake(v ? when : Intake.When.OFF, current(group).matchingOnly())))
                .tooltip(Component.literal(title).append(Component.literal("\n" + body).withStyle(ChatFormatting.GRAY)))
                .visibleWhen(() -> EditMode.isOn() && IntakeClient.available())
                .build();
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
