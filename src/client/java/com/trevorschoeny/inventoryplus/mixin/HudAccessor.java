package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for {@link Hud}'s private {@code extractSlot}, the method that
 * emits one hotbar item into the HUD's render state.
 *
 * <p>Hotbar Sets draws its HUD layers' items through this, so each one gets vanilla's own
 * pop-scale and decorations (count, durability bar), exactly as a hotbar slot does. The
 * method returns early on an empty stack, so an empty cell costs nothing.
 *
 * <p>Compile-time remapped like {@link AbstractContainerScreenAccessor}; the
 * {@code inventoryPlus$} prefix keeps it clear of other mods' invokers on
 * the same class.
 */
@Mixin(Hud.class)
public interface HudAccessor {

    @Invoker("extractSlot")
    void inventoryPlus$extractSlot(GuiGraphicsExtractor graphics, int x, int y,
                                   DeltaTracker delta, Player player, ItemStack stack, int seed);
}
