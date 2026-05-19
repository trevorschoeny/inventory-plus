package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevorschoeny.menukit.core.Toggle;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Lock-edit toolbar-toggle factory + edit-mode lifecycle hook.
 *
 * <h3>Single sprite, two visual states</h3>
 *
 * MK's {@link Toggle#spriteLinked} renders the off state with the raw
 * sprite and the on state with the same sprite under MK's
 * HSL-lightness-inversion shader (hue + saturation preserved). One PNG
 * (per Phase 18q); on/off visual difference is automatic.
 *
 * <h3>State ownership</h3>
 *
 * {@code spriteLinked} is the consumer-owned-state variant — the
 * getter is {@link LockEditMode#isOn} and the toggle is
 * {@link LockEditMode#toggle}. MK reads/writes through those; the
 * mod's edit-mode state stays in its existing class.
 *
 * <h3>Always visible (within scope)</h3>
 *
 * No {@code .showWhen} — the toggle shows whenever its panel shows.
 * The toolbar panel's panel-level {@code showWhen} handles the
 * scope filter (e.g., excluding Creative). The Move Matching buttons
 * gate themselves via per-element {@code .showWhen} for the MM-screen
 * filter.
 */
public final class LockedSlotsButtons {

    private LockedSlotsButtons() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "locked_slot_edit");

    public static final int SIZE = 9;

    /**
     * Registers the edit-mode lifecycle reset — fires on every
     * {@code AFTER_INIT} so edit mode auto-disables when the player
     * closes one screen and opens another. Independent of the
     * toolbar's render path; called from
     * {@link com.trevorschoeny.inventoryplus.InventoryPlusClient}.
     */
    public static void registerLifecycle() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                LockEditMode.reset());
    }

    /**
     * Lock-edit toggle for the toolbar. Off state: lock icon as-is.
     * On state: HSL-inverted lock icon (via MK's sprite-toggle shader).
     */
    public static Toggle toolbarToggle(int x, int y) {
        return Toggle.spriteLinked(x, y, SIZE, SIZE,
                        LockEditMode::isOn,
                        LockEditMode::toggle,
                        TEXTURE)
                .tooltip(() -> Component.literal(
                        LockEditMode.isOn()
                                ? "Click to finish editing"
                                : "Click to edit locked slots"))
                .showWhen(IPConfig::lockedSlotsShowButton);
    }
}
