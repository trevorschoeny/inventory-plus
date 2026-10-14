package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Puts each contiguous run of enchantment lines in alphabetical order by the
 * text the player reads, so it sorts correctly in any language. Nothing
 * outside a run moves. Runs before Enchantment descriptions, so each
 * description lands under its enchantment's new place.
 */
final class SortEnchantments {

    private SortEnchantments() {}

    static List<Component> apply(ItemStack stack, List<Component> lines) {
        Set<String> keys = EnchantmentLines.keys(stack);
        return keys.size() < 2 ? lines : sortRuns(lines, keys);
    }

    /** {@code lines} with each run of lines keyed in {@code keys} sorted by its text. */
    static List<Component> sortRuns(List<Component> lines, Set<String> keys) {
        List<Component> out = new ArrayList<>(lines);
        int i = 0;
        while (i < out.size()) {
            if (!EnchantmentLines.is(out.get(i), keys)) { i++; continue; }
            int end = i;
            while (end < out.size() && EnchantmentLines.is(out.get(end), keys)) end++;
            out.subList(i, end).sort(Comparator.comparing(Component::getString));
            i = end;
        }
        return out;
    }
}
