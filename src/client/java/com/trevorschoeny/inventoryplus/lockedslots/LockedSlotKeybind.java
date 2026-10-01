package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockgroups.LockGroup;
import com.trevorschoeny.inventoryplus.lockgroups.Reach;
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
 * Screen-scoped {@code L} keybind: applies the active lock group, the one the
 * lock button has selected, to whatever is under the cursor
 * (`plans/lock-groups.md`, "Applying locks").
 *
 * <ul>
 *   <li><b>Slot kind</b> locks the hovered slot, empty or not, with the
 *       L-drag to sweep several.</li>
 *   <li><b>Item or Exact kind</b> locks the hovered item. On an empty slot it
 *       does nothing: the group said item, so it never falls through to the
 *       slot.</li>
 *   <li>If that same group is already on the target, {@code L} takes it off;
 *       a different group of the same kind is replaced. Other kinds are
 *       untouched: one stack can carry a slot lock, an item lock and an exact
 *       lock at once.</li>
 *   <li>{@code L} stays a no-op on a cycle slot while "Lock the slots the
 *       cyclers use" pairs it.</li>
 * </ul>
 *
 * <p>Scoped via {@link ScreenKeyboardEvents} so {@code L} only fires inside
 * container screens.
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
                        // Paused locks are paused for L too: nothing applied that cannot be seen.
                        if (!IPConfig.lockGroupsEnabled()) return;
                        // GLFW auto-repeat fires afterKeyPress every repeat
                        // tick while L is held; ignore once an L-drag is
                        // active so we don't re-toggle the same slot on
                        // every repeat. The tick handler clears the drag
                        // on key release.
                        if (LockedSlotsDragController.isLKeyDragActive()) return;
                        if (!(innerScreen instanceof AbstractContainerScreen<?> currentAcs)) return;

                        Slot hovered = slotUnderMouse(currentAcs);
                        if (hovered == null) return;

                        LockGroup group = Reach.active();
                        if (group.kind().locksItems()) {
                            // The item path starts no drag, so it has no drag to
                            // suppress GLFW auto-repeat for it. Without this latch
                            // a held L would lock and unlock many times a second.
                            if (itemLockLatch) return;
                            ItemStack hoveredStack = hovered.getItem();
                            if (!hoveredStack.isEmpty()) {
                                itemLockLatch = LockedItems.apply(hoveredStack, group);
                            }
                            return;
                        }

                        if (!LockedSlots.isLockableHere(hovered)) return;
                        String now = LockedSlots.applyGroup(hovered, group.id());
                        // Hold L and sweep: every slot entered gets the first
                        // slot's new state, this group or none. Keyed by
                        // slot.index (menu-unique), so a container slot can't
                        // collide with a player slot.
                        LockedSlotsDragController.startLKeyDrag(hovered.index, now);
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
