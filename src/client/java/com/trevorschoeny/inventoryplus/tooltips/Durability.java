package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** "Durability: 120 / 250 (48%)", green above half, yellow from a quarter to half, red below. */
final class Durability {

    private Durability() {}

    static List<Component> lines(ItemStack stack) {
        if (!stack.isDamageableItem()) return List.of();
        int max = stack.getMaxDamage();
        int remaining = max - stack.getDamageValue();
        int percent = (int) ((remaining / (float) max) * 100);
        ChatFormatting colour = percent > 50 ? ChatFormatting.GREEN
                : percent >= 25 ? ChatFormatting.YELLOW
                : ChatFormatting.RED;
        return List.of(Component.translatable("tooltip.inventoryplus.item_tips.durability")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(remaining + " / " + max + " (" + percent + "%)").withStyle(colour)));
    }
}
