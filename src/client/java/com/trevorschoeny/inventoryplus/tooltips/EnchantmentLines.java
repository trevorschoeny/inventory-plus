package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds vanilla's enchantment lines in a tooltip by their translation key,
 * never by position: vanilla builds each from the enchantment's description
 * component ({@code enchantment.<namespace>.<path>}) with the level appended
 * as a sibling, so the line's own contents keep that key. Shared by
 * Enchantment descriptions and Sort enchantments.
 */
final class EnchantmentLines {

    private EnchantmentLines() {}

    /** The translation keys of every enchantment on {@code stack}, applied or stored (a book's). */
    static Set<String> keys(ItemStack stack) {
        Set<String> keys = new HashSet<>();
        add(keys, stack.get(DataComponents.ENCHANTMENTS));
        add(keys, stack.get(DataComponents.STORED_ENCHANTMENTS));
        return keys;
    }

    private static void add(Set<String> keys, @Nullable ItemEnchantments enchantments) {
        if (enchantments == null) return;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            String key = keyOf(enchantment.value().description());
            if (key != null) keys.add(key);
        }
    }

    /** The translation key a line was built from, or null for a literal or empty line. */
    static @Nullable String keyOf(Component line) {
        return line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    /** Whether {@code line} is one of the enchantment lines named by {@code keys}. */
    static boolean is(Component line, Set<String> keys) {
        String key = keyOf(line);
        return key != null && keys.contains(key);
    }
}
