package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.columncycler.ColumnCycler;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItemModes;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.movematching.ScreenLayout;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Screen-scoped {@code L} keybind — locks or unlocks whatever is under
 * the cursor, in the kind the lock button's current stop selects.
 *
 * <h3>Three stops, one key</h3>
 *
 * <ul>
 *   <li><b>Unlocking always wins, and ignores the stop.</b> {@code L} on
 *       an already item-locked stack removes the lock whatever the button
 *       shows, so nobody has to match the stop to undo one
 *       (`plans/locked-items.md`).</li>
 *   <li><b>{@link LockKind#SLOT}</b> is the original behaviour below:
 *       toggle the hovered slot, with the L-drag to sweep several.</li>
 *   <li><b>{@link LockKind#ITEM} / {@link LockKind#EXACT}</b> lock the
 *       hovered stack by type or exactly. These answer on any slot holding
 *       an item, a container's included, because the lock travels with the
 *       item rather than the place. An empty slot has nothing to lock and
 *       does not fall through to locking the slot: the stop said item.</li>
 * </ul>
 *
 * <p>No-op when {@link LockEditMode} is on — edit mode uses click-to-
 * toggle instead; L is redundant during edit mode.
 *
 * <p>Hovering a non-lockable slot (crafting input, anvil input, etc.)
 * or empty UI → no-op.
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so {@code L} only fires
 * inside container screens. Promoting to a rebindable
 * {@link net.minecraft.client.KeyMapping} is filed in DEFERRED.md
 * alongside the I/O/S keybinds.
 */
public final class LockedSlotKeybind {

    private LockedSlotKeybind() {}

    /**
     * True while an item lock/unlock press is still being held down. See
     * the auto-repeat note at the call site; cleared by {@link #tick} on
     * release, the same trigger-released shape
     * {@link LockedSlotsDragController} uses to end an L-drag.
     */
    private static boolean itemLockLatch;

    /** Registered against {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void tick(Minecraft mc) {
        if (itemLockLatch && (mc == null || !InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_L))) {
            itemLockLatch = false;
        }
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register(
                    (innerScreen, event) -> {
                        if (!IPKeybinds.LOCK_SLOT.matches(event)) return;
                        // GLFW auto-repeat fires afterKeyPress every repeat
                        // tick while L is held; ignore once an L-drag is
                        // active so we don't re-toggle the same slot on
                        // every repeat. The tick handler clears the drag
                        // on key release.
                        if (LockedSlotsDragController.isLKeyDragActive()) return;
                        // L works regardless of edit mode — edit-mode click
                        // covers inv + hotbar only, so armor / offhand can
                        // only be locked via L. Keeping L always-on lets
                        // the player lock armor / offhand without exiting
                        // edit mode.
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Slot hovered = slotUnderMouse(currentAcs);
                        if (hovered == null) return;

                        // The stop alone decides which lock L acts on. Existing
                        // locks are not consulted: the three kinds are independent,
                        // so an item-locked stack must still accept a slot lock or
                        // an exact lock on top (Trev 2026-09-06).
                        ItemStack hoveredStack = hovered.getItem();
                        LockKind kind = LockedItemModes.current();
                        if (kind != LockKind.SLOT) {
                            // The item path starts no drag, so it has no drag to
                            // suppress GLFW auto-repeat for it. Without this latch
                            // a held L would lock and unlock many times a second.
                            if (itemLockLatch) return;
                            if (!hoveredStack.isEmpty()) {
                                itemLockLatch = LockedItems.toggle(hoveredStack, kind);
                            }
                            return;
                        }

                        if (!LockedSlots.isLockableHere(hovered)) return;
                        // When cycleSlotsLocked is ON, a cycle slot's lock
                        // state is bound to its cycle state — L can't toggle
                        // it independently. The player removes the lock by
                        // removing the cycle (C). When cycleSlotsLocked is
                        // OFF, cycle and lock are fully independent — L
                        // works normally on cycle slots. (Cycle slots are
                        // player-inv only; container/ender never match.)
                        if (IPConfig.cycleSlotsLocked() && ColumnCycler.isCycleSlot(hovered)) return;
                        // Unified dispatch: player + ender route to IP's
                        // client store, placed containers to the registered
                        // provider (IPP's shared channel).
                        LockedSlots.toggleSlot(hovered);
                        boolean newState = LockedSlots.isLockedSlot(hovered);
                        // Start an L-drag in non-edit mode so the user can
                        // hold L and sweep the cursor across more slots,
                        // coercing each to the first slot's new state. In
                        // edit mode the drag is the LMB-drag mechanic;
                        // L stays a single-slot toggle for armor/offhand
                        // reach. Keyed by slot.index (menu-unique) so a
                        // container slot can't collide with a player slot.
                        if (!LockEditMode.isOn()) {
                            LockedSlotsDragController.startLKeyDrag(hovered.index, newState);
                        }
                    });
        });
    }

    /**
     * The slot under the cursor, agreeing with vanilla's own resolution
     * rather than re-deriving it.
     *
     * <p>An earlier version of this walked {@code menu.slots} and bounds-
     * tested each one by hand, using {@code isActive()} to skip whatever a
     * created slot (an IM pocket) was covering. That worked only as long
     * as MenuKit's compositing happened to leave the covered vanilla slot
     * inactive; once MenuKit restored plain paint-over (composited panels
     * inside vanilla's slot pass, "the covered vanilla slot is active
     * again"), the independent scan found that vanilla slot first again —
     * {@code L} locked the inventory slot under the pocket rather than the
     * pocket itself (MenuKit, 2026-09-07).
     *
     * <p>{@link ScreenLayout#hoveredSlot} is vanilla's own per-frame answer,
     * which MenuKit's {@code getHoveredSlot} interception already resolves
     * a created slot ahead of the vanilla slot it covers for. Reading it
     * agrees with whatever compositing MenuKit does internally, without
     * this class needing to know what that is. The {@code isActive()}
     * check stays as a defensive no-op: vanilla's own resolution already
     * honours it before this ever sees the result.
     */
    private static @Nullable Slot slotUnderMouse(AbstractContainerScreen<?> acs) {
        Slot slot = ScreenLayout.hoveredSlot(acs);
        return (slot != null && slot.isActive()) ? slot : null;
    }
}
