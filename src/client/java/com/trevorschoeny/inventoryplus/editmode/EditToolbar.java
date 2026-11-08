package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.toolbar.FeatureToggleButtons;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.Toggle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Edit mode's control: the edit button left of Sort on the inventory's toolbar, the only
 * one on any screen. It edits every slot on the screen, a chest's too, so the container's
 * own toolbar has none. A shulker box's own settings are box
 * settings, not slot settings, so they live in its settings popup instead
 * ({@link com.trevorschoeny.inventoryplus.carried.BoxSettingsPopup}).
 */
public final class EditToolbar {

    private EditToolbar() {}

    private static final Identifier EDIT = Identifier.fromNamespaceAndPath("inventoryplus", "edit_button");

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

}
