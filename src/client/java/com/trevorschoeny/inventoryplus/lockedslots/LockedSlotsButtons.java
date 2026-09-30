package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.buttonmode.PressFeedback;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockgroups.LockColours;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;

import com.trevlar.menukit.api.element.Click;
import com.trevlar.menukit.api.element.Toggle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The lock button on the inventory toolbar (`plans/lock-groups.md`,
 * "Applying locks"): it shows which lock group {@code L} applies, and picks
 * another.
 *
 * <ul>
 *   <li><b>Left-click</b> selects the next group; <b>Shift + left-click</b>
 *       the previous one.</li>
 *   <li><b>Right-click</b> opens the settings menu on its Lock groups tab.</li>
 *   <li><b>Ctrl+click</b> (Cmd on a Mac) opens the same tab, as on every
 *       Inventory Plus button.</li>
 * </ul>
 *
 * <p>Lock edit mode, which the left-click used to toggle, is gone (Trev,
 * 2026-09-11): locks are applied with {@code L} alone. The group's colour on
 * the button comes with stage 3 of the Reach build, where colour becomes
 * settable; until then the tooltip names the group and a press flashes.
 *
 * <p>Shown unless the Lock groups tab's button toggle hides it; never hidden
 * automatically, since there are always at least two groups to choose from.
 */
public final class LockedSlotsButtons {

    private LockedSlotsButtons() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "locked_slot_edit");

    public static final int SIZE = 9;

    public static Toggle toolbarToggle(int x, int y) {
        PressFeedback feedback = new PressFeedback();
        // A sprite toggle whose state always reads off, so the icon never
        // inverts: the button selects, it does not hold a mode. The widget
        // still reports each left-click, which is the cycle.
        return Toggle.builder().sprite(TEXTURE).at(x, y).size(SIZE, SIZE)
                .state(() -> false, ignored -> {
                    if (SettingsMenu.ctrlClickOpens(SettingsMenu.LOCK_GROUPS)) return;
                    feedback.press();
                    Reach.cycleActive(Click.of(Click.LEFT).shift() ? -1 : 1);
                })
                .tooltip(() -> Component.literal("Lock group: " + Reach.active().name()
                                + (IPConfig.lockGroupsEnabled() ? "" : " (locks paused)"))
                        .append(Component.literal("\nClick: next group. Shift-click: previous.\nRight-click: edit groups.")
                                .withStyle(ChatFormatting.GRAY)))
                .onSecondaryClick(click -> {
                    if (click.isRight()) SettingsMenu.openOn(SettingsMenu.LOCK_GROUPS);
                })
                // The active group's colour, faint; the press flash over it.
                .tint(() -> {
                    int flash = feedback.tint();
                    return flash != 0 ? flash : LockColours.argb(Reach.active().colour(), 0x70);
                })
                .visibleWhen(IPConfig::lockedSlotsShowButton)
                .build();
    }
}
