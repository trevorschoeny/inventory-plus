package com.trevorschoeny.inventoryplus.lockedslots;

import net.minecraft.world.inventory.Slot;

/**
 * Extension seam that lets a downstream mod supply lock state for slot kinds
 * IP cannot reach on its own.
 *
 * <h3>Why this exists</h3>
 *
 * IP locks its <i>own</i> client-side slots — the player's inventory/hotbar/
 * armor/offhand and their ender chest — directly in {@link LockedSlots}'
 * client store. But <b>placed-container</b> locks (chest, barrel, shulker,
 * hopper, dispenser, dropper) are <i>shared</i> and live in MenuKit's
 * server-authoritative per-slot channel. IP is a client-only mod and cannot
 * depend on menukit-containers, so it cannot read that channel itself.
 *
 * <p>The companion mod (IPP, which depends on both IP and menukit-containers)
 * implements this interface over the shared channel and registers it via
 * {@link LockedSlots#registerProvider}. IP's single enforcement predicate
 * {@link LockedSlots#isLockedSlot(Slot)} then dispatches container slots to
 * the provider — so every existing lock rule (shift-click block, Sorting /
 * Move Matching skip, the lock icon) honors container locks with no rule
 * rewritten. IP itself ships zero providers.
 *
 * <h3>Both read and write</h3>
 *
 * The seam carries writes as well as reads because the edit-mode lock UI
 * (the {@code L} keybind, the click-interceptor, the drag-controller) must be
 * able to <i>toggle</i> a container slot's lock, not just display it.
 *
 * <h3>Cross-thread</h3>
 *
 * Implementations are consulted on both the client (render) thread and, in
 * single-player, the integrated-server thread — the same cross-thread firing
 * that drives {@link LockedSlots#isLockable}. An implementation must therefore
 * resolve lock state consistently on both sides (MenuKit's channel does: it
 * reads the client cache on the render thread and the server store on the
 * server thread).
 */
public interface SlotLockProvider {

    /**
     * True if this provider owns the lock state for {@code slot}. The first
     * registered provider that {@code handles} a slot wins; IP's built-in
     * player + ender paths are checked before any provider.
     */
    boolean handles(Slot slot);

    /** Current lock state for a slot this provider {@link #handles}. */
    boolean isLocked(Slot slot);

    /** Sets the lock state for a slot this provider {@link #handles}. */
    void setLocked(Slot slot, boolean locked);
}
