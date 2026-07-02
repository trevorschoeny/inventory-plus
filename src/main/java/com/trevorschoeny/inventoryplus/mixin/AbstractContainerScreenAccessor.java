package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for {@link AbstractContainerScreen}'s protected
 * {@code leftPos}/{@code topPos} — the screen frame's top-left, needed by
 * move-matching's slot-anchored button placement every frame.
 *
 * <h3>Why an accessor mixin (the production-crash lesson, 1.2.0)</h3>
 *
 * These field references are <b>compile-time remapped</b> by the mixin
 * annotation processor, so they survive a real (intermediary-mapped) install.
 * The two prior attempts both failed structurally:
 * <ul>
 *   <li><b>Access widener</b> — wouldn't remap in this workspace's Loom 1.14 +
 *       Mojmap setup ({@code Failed to remap 2 mods} at runClient).</li>
 *   <li><b>Runtime reflection by field name</b> ({@code getDeclaredField("leftPos")})
 *       — "sidesteps the remap pipeline", which means the Mojmap names are
 *       looked up verbatim in production where they don't exist:
 *       {@code NoSuchFieldException}, broken button placement, and a crash on
 *       the cycler-keybind path. Shipped as 1.2.0, pulled same day. Reflection
 *       by mapped name can NEVER work outside the dev runtime.</li>
 * </ul>
 *
 * Accessor names carry the {@code inventoryPlus$} prefix so they can't collide
 * with MenuKit's own accessor on the same class ({@code mk$getLeftPos}).
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int inventoryPlus$getLeftPos();

    @Accessor("topPos")
    int inventoryPlus$getTopPos();
}
