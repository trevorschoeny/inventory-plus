package com.trevorschoeny.inventoryplus.editmode;

import com.trevorschoeny.inventoryplus.blueprints.Blueprints;

import com.trevlar.menukit.api.dialog.EditDialog;
import com.trevlar.menukit.api.element.Button;
import com.trevlar.menukit.api.element.Flow;
import com.trevlar.menukit.api.element.ListView;
import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.TextField;
import com.trevlar.menukit.api.element.TextLabel;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.panel.PanelPosition;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.Placement;
import com.trevlar.menukit.api.panel.ScreenPanelAdapter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Edit mode's Save and Load: a blueprint is a container's reservations and locks, saved
 * from one container and loaded onto another ({@link Blueprints}).
 *
 * <ul>
 *   <li><b>Save</b> asks for a name (the next free "Blueprint N" filled in), then saves
 *       the slots of the toolbar's container. A name already in the library is replaced.</li>
 *   <li><b>Load</b> lists the library; a click loads that blueprint onto the toolbar's
 *       container and closes the list.</li>
 * </ul>
 * Both are dialogs over the open container screen, on every container screen, shown
 * only while one is asked for. Renaming and deleting are on the settings' Slots tab.
 */
public final class BlueprintDialogs {

    private BlueprintDialogs() {}

    private static final int LIST_WIDTH = 160;
    private static final int LIST_HEIGHT = 110;
    private static final int MUTED = 0xFFA0A0A0;

    /** The slots the open dialog is for, read when it acts; null while no dialog is up. */
    private static @Nullable Supplier<List<Slot>> saving;
    private static @Nullable Supplier<List<Slot>> loading;
    private static String draftName = "";

    public static void register() {
        Panel save = EditDialog.builder()
                .id("inventoryplus:blueprint_save")
                .title(Component.literal("Save blueprint"))
                .content(Flow.builder().vertical().gap(4)
                        .add(TextLabel.builder().text(Component.literal("Save these slot edits as a blueprint to copy onto other containers.")).build())
                        .add(TextField.builder().size(LIST_WIDTH, 20).maxLength(48)
                                .state(() -> draftName, v -> draftName = v)
                                .onSubmit(v -> save())
                                .build())
                        .build())
                .saveLabel(Component.literal("Save"))
                .onSave(BlueprintDialogs::save)
                .onCancel(BlueprintDialogs::close)
                .build()
                .visibleWhen(() -> saving != null && EditMode.isOn());
        new ScreenPanelAdapter(save);

        Panel load = Panel.builder("inventoryplus:blueprint_load")
                .style(PanelStyle.DARK)
                .in(ModeScope.BOTH, Placement.screen(PanelPosition.center()))
                .elements(List.of(Flow.builder().vertical().gap(4)
                        .add(TextLabel.builder().color(TextLabel.COLOR_LIGHT).shadow(true)
                                .text(Component.literal("Load blueprint")).build())
                        .add(ScrollContainer.builder().size(LIST_WIDTH, LIST_HEIGHT).content(List.of(list())).build())
                        .add(Button.builder().label(Component.translatable("gui.cancel")).size(LIST_WIDTH, 16)
                                .onClick(BlueprintDialogs::close).build())
                        .build()))
                .build()
                .modal()
                .onEscape(BlueprintDialogs::close)
                .visibleWhen(() -> loading != null && EditMode.isOn());
        new ScreenPanelAdapter(load);
    }

    private static PanelElement list() {
        return ListView.<String>builder()
                .items(() -> loading == null ? List.of() : Blueprints.names())
                .key(name -> name)
                .row(name -> Button.builder().label(Component.literal(name)).size(LIST_WIDTH - 8, 16)
                        .onClick(() -> load(name)).build())
                .gap(1)
                .empty(TextLabel.builder().color(MUTED).text(Component.literal("No blueprints yet. Save one first.")).build())
                .build();
    }

    /** Whether a dialog is up. Escape is the dialog's then. */
    public static boolean open() {
        return saving != null || loading != null;
    }

    /** Save pressed on a toolbar whose container is {@code slots}. */
    public static void openSave(Supplier<List<Slot>> slots) {
        loading = null;
        draftName = freshName();
        saving = slots;
    }

    /** Load pressed on a toolbar whose container is {@code slots}. */
    public static void openLoad(Supplier<List<Slot>> slots) {
        saving = null;
        loading = slots;
    }

    public static void close() {
        saving = null;
        loading = null;
    }

    /** "Blueprint N", the first N not in the library. */
    static String freshName() {
        List<String> names = Blueprints.names();
        int n = 1;
        while (names.contains("Blueprint " + n)) n++;
        return "Blueprint " + n;
    }

    private static void save() {
        LocalPlayer player = Minecraft.getInstance().player;
        Supplier<List<Slot>> slots = saving;
        close();
        if (player == null || slots == null) return;
        String name = draftName.isBlank() ? freshName() : draftName.strip();
        Blueprints.save(Blueprints.capture(name, player, slots.get()), player.registryAccess());
    }

    private static void load(String name) {
        LocalPlayer player = Minecraft.getInstance().player;
        Supplier<List<Slot>> slots = loading;
        close();
        if (player == null || slots == null) return;
        Blueprints.get(name, player.registryAccess()).ifPresent(b -> Blueprints.apply(b, player, slots.get()));
    }
}
