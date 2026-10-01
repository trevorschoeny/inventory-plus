package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Under each enchantment line, one grey line saying what it does, from
 * {@code enchantment.<namespace>.<path>.desc} in the lang file. Every vanilla
 * enchantment has one; an enchantment whose key has no translation (a modded
 * one we know nothing about) gets no line rather than a raw key.
 */
final class EnchantmentDescriptions {

    private EnchantmentDescriptions() {}

    static List<Component> apply(ItemStack stack, List<Component> lines) {
        Set<String> keys = EnchantmentLines.keys(stack);
        if (keys.isEmpty()) return lines;
        List<Component> out = new ArrayList<>(lines.size() + keys.size());
        for (Component line : lines) {
            out.add(line);
            if (!EnchantmentLines.is(line, keys)) continue;
            String description = EnchantmentLines.keyOf(line) + ".desc";
            if (Language.getInstance().has(description)) {
                out.add(Component.literal(" ").append(Component.translatable(description))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return out;
    }
}
