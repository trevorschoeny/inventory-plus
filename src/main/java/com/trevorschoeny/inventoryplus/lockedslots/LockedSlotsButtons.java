package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.movematching.MoveMatchingButtons;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.core.PanelPosition;
import com.trevorschoeny.menukit.core.PanelStyle;
import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.core.SlotGroupRegion;
import com.trevorschoeny.menukit.core.Toggle;
import com.trevorschoeny.menukit.inject.SlotGroupPanelAdapter;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Registers the lock-edit toggle as a MenuKit panel anchored above the
 * player's main inventory's 3×9 grid, right-aligned to the grid's right
 * edge, sitting to the left of any Move Matching panel.
 *
 * <h3>Single sprite, two visual states</h3>
 *
 * MK's {@link Toggle#spriteLinked} renders the off state with the raw
 * sprite and the on state with the same sprite under MK's
 * HSL-lightness-inversion shader (hue and saturation preserved,
 * lightness flipped). One PNG ships; the toggled visual is automatic.
 * Phase 18q (MK 2026-05-16) added this — replaces the previous
 * {@code locked_slot_edit_off.png} / {@code locked_slot_edit_on.png}
 * pair with a single {@code locked_slot_edit.png}.
 *
 * <h3>State ownership</h3>
 *
 * {@code spriteLinked} is the consumer-owned-state variant — the
 * getter is {@link LockEditMode#isOn} and the toggle is
 * {@link LockEditMode#toggle}. MK reads/writes through those; the
 * mod's edit-mode state stays in its existing class.
 *
 * <h3>Screen scope</h3>
 *
 * Visible on every {@link AbstractContainerScreen} that exposes the
 * player main inv except creative-mode (which has its own per-tab
 * layout incompatible with our panel). Panel.showWhen handles the
 * gate; {@link SlotGroupCategory#PLAYER_INVENTORY} on the adapter
 * already excludes screens without the player inv.
 *
 * <h3>Edit mode lifecycle</h3>
 *
 * {@link LockEditMode#reset} is called on every {@code AFTER_INIT} so
 * edit-mode auto-disables when the player closes one screen and opens
 * another — the Option-A lifecycle Trev picked 2026-05-16.
 */
public final class LockedSlotsButtons {

    private LockedSlotsButtons() {}

    // MK's Toggle.spriteLinked uses graphics.blitSprite — Identifier
    // resolves through the GUI sprite atlas
    // (assets/inventoryplus/textures/gui/sprites/locked_slot_edit.png).
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "locked_slot_edit");

    public static final int SIZE = 9;

    public static void register() {
        // Edit mode resets on every screen open — pre-screen state
        // doesn't leak forward. Independent of the panel's render path.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                LockEditMode.reset());

        Toggle lockEdit = Toggle.spriteLinked(0, 0, SIZE, SIZE,
                        LockEditMode::isOn,
                        LockEditMode::toggle,
                        TEXTURE)
                .tooltip(() -> Component.literal(
                        LockEditMode.isOn()
                                ? "Click to finish editing"
                                : "Click to edit locked slots"));

        List<PanelElement> elements = List.of(lockEdit);

        Panel panel = new Panel("inventoryplus.lock_edit",
                elements,
                /*visible=*/ true,
                PanelStyle.NONE,
                PanelPosition.BODY,
                /*toggleKey=*/ -1);
        panel.showWhen(() -> {
            Screen screen = Minecraft.getInstance().screen;
            return screen instanceof AbstractContainerScreen<?>
                    && !(screen instanceof CreativeModeInventoryScreen);
        });

        new SlotGroupPanelAdapter(panel,
                        SlotGroupRegion.TOP_ALIGN_RIGHT,
                        MoveMatchingButtons.GAP_ABOVE)
                .on(SlotGroupCategory.PLAYER_INVENTORY);
    }
}
