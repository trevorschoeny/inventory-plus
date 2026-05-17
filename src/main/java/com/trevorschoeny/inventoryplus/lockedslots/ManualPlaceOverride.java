package com.trevorschoeny.inventoryplus.lockedslots;

/**
 * Thread-local flag set during a player's manual
 * {@link net.minecraft.world.inventory.ClickType#PICKUP} click. When
 * active, the {@link com.trevorschoeny.inventoryplus.mixin.SlotMayPlaceMixin}
 * lets {@link net.minecraft.world.inventory.Slot#mayPlace} run with
 * its normal value (i.e., allow placement into locked slots).
 *
 * <p>The override is set by the screen-mouse-click event listener
 * before vanilla dispatches the click, and cleared immediately after.
 * Bracket-pattern: set in {@code allowMouseClick}, clear in
 * {@code afterMouseClick}.
 *
 * <p>{@link ThreadLocal} because vanilla's render / click pipeline is
 * single-threaded but we want defensive scoping against any future
 * multi-thread access.
 */
public final class ManualPlaceOverride {

    private ManualPlaceOverride() {}

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void set() {
        ACTIVE.set(Boolean.TRUE);
    }

    public static void clear() {
        ACTIVE.set(Boolean.FALSE);
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }
}
