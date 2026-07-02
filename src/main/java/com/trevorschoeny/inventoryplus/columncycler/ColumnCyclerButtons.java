package com.trevorschoeny.inventoryplus.columncycler;

import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevorschoeny.menukit.core.Toggle;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Cycler-edit toolbar-toggle factory + edit-mode lifecycle hook.
 *
 * <p>Mirrors {@link com.trevorschoeny.inventoryplus.lockedslots.LockedSlotsButtons}:
 * MK's {@link Toggle#spriteLinked} renders off / on states from a single
 * sprite under the HSL-inversion shader; state ownership is the
 * consumer's (here, {@link ColumnCyclerEditMode}).
 *
 * <h3>Visibility gates</h3>
 *
 * Two gates stack:
 * <ul>
 *   <li><b>Feature enabled</b> — {@link IPConfig#columnCyclerEnabled}.
 *       When the Power Users master toggle is off, the button hides
 *       and the C keybind is a no-op. Defaults OFF.</li>
 *   <li><b>Show button</b> — {@link IPConfig#columnCyclerShowButton}.
 *       Even with the feature enabled, players can hide the button
 *       and use the C keybind exclusively. Defaults ON.</li>
 * </ul>
 *
 * <p>Both conditions must be true for the button to appear.
 */
public final class ColumnCyclerButtons {

    private ColumnCyclerButtons() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "cycle_slot_edit");

    public static final int SIZE = 9;

    /**
     * Registers the edit-mode lifecycle reset — fires on every
     * {@code AFTER_INIT} so edit mode auto-disables when the player
     * closes one screen and opens another.
     */
    public static void registerLifecycle() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ColumnCyclerEditMode.reset();
            // Per-inv-open scope for the pre-cycle lock memory — see
            // ColumnCycler#SESSION_PRE_LOCK. Opening any new screen
            // starts a fresh session.
            ColumnCycler.clearSessionPreLockState();
        });
    }

    /**
     * Cycler-edit toggle for the Power Users toolbar. Off state: cycle
     * icon as-is. On state: HSL-inverted via MK's sprite-toggle shader.
     */
    public static Toggle toolbarToggle(int x, int y) {
        return Toggle.spriteLinked(x, y, SIZE, SIZE,
                        ColumnCyclerEditMode::isOn,
                        // 2.0.0: the widget computes the new state off the linked
                        // supplier and hands it to us; set() applies it (incl. the
                        // edit-mode mutual exclusion) — no self-flip here.
                        ColumnCyclerEditMode::set,
                        TEXTURE)
                .tooltip(() -> Component.literal(
                        ColumnCyclerEditMode.isOn()
                                ? "Finish Editing"
                                : "Edit Cycle Slots"))
                .showWhen(() -> IPConfig.columnCyclerEnabled()
                        && IPConfig.columnCyclerShowButton());
    }
}
