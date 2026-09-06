package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.buttonmode.ModeState;
import com.trevorschoeny.inventoryplus.sort.ContainerIdentity;
import com.trevorschoeny.inventoryplus.sort.SortButton;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * Move Matching's mode state: global by default, pinnable to the open
 * container. Both buttons and both keybinds read {@link #current()}.
 *
 * <p>"This container" for Move Matching is the external container that
 * is open, since the operation always pairs the player inventory with it.
 * Its identity is resolved the way Sort resolves it, so a chest pinned
 * for one is the same chest for the other.
 */
public final class MoveMatchingModes {

    private MoveMatchingModes() {}

    public static final ModeState<MoveMatchingMode> MODE =
            ModeState.of("move-matching-mode", MoveMatchingMode.class, MoveMatchingMode.ALL);

    public static void load() {
        MODE.load();
    }

    /** Identity of the open external container, or null if none is open or it cannot be identified. */
    public static @Nullable ContainerIdentity currentIdentity() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        AbstractContainerMenu menu = acs.getMenu();
        Slot anchor = SortButton.findExternalAnchor(menu);
        return anchor == null ? null : ContainerIdentity.fromHoveredSlot(anchor, menu);
    }

    /** The mode in force right now: the open container's pin, else the global. */
    public static MoveMatchingMode current() {
        return MODE.effective(currentIdentity());
    }
}
