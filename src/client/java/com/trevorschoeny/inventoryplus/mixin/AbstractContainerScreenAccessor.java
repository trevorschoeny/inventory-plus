package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for {@link AbstractContainerScreen}'s protected
 * {@code leftPos} and {@code topPos}, the screen frame's top-left, which
 * Move Matching's slot-anchored button placement needs every frame.
 *
 * <h3>Why an accessor mixin</h3>
 *
 * These field references are <b>compile-time remapped</b> by the mixin
 * annotation processor, so they survive a real (intermediary-mapped) install.
 * The two earlier attempts both failed:
 * <ul>
 *   <li><b>Access widener</b>: wouldn't remap in this workspace's Loom 1.14
 *       and Mojmap setup ({@code Failed to remap 2 mods} at runClient).</li>
 *   <li><b>Runtime reflection by field name</b> ({@code getDeclaredField("leftPos")})
 *       sidesteps the remap pipeline, so the Mojmap names are looked up
 *       verbatim in production, where they don't exist. The result was
 *       {@code NoSuchFieldException}, broken button placement, and a crash on
 *       the cycler-keybind path. It shipped as 1.2.0 and was pulled the same
 *       day. Reflection by mapped name can never work outside the dev
 *       runtime.</li>
 * </ul>
 *
 * Accessor names carry the {@code inventoryPlus$} prefix so they can't collide
 * with MenuKit's own accessor on the same class ({@code mk$getLeftPos}).
 *
 * <h3>{@code hoveredSlot}</h3>
 *
 * Vanilla's own answer to "what slot is under the cursor", written once per
 * frame in {@code extractContents} by walking {@code menu.slots} and
 * checking {@code isActive()} then {@code isHovering()}. It is the same
 * resolution vanilla's click handling trusts, and the one MenuKit's
 * {@code getHoveredSlot} interception overwrites so a created slot wins
 * over the vanilla slot it covers. Read it instead of re-deriving hover
 * with a manual bounds scan: composited panels paint over vanilla slots
 * without touching their {@code isActive()}, so an independent scan finds
 * the covered vanilla slot again, while vanilla's own field agrees with
 * whichever resolution MenuKit installs.
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int inventoryPlus$getLeftPos();

    @Accessor("topPos")
    int inventoryPlus$getTopPos();

    @Accessor("hoveredSlot")
    @org.jetbrains.annotations.Nullable Slot inventoryPlus$getHoveredSlot();
}
