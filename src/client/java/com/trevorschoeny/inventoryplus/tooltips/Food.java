package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Nutrition, and effective saturation: nutrition times the saturation
 * multiplier times two, since {@code FoodProperties.saturation()} is a
 * multiplier, not the points restored.
 */
final class Food {

    private Food() {}

    static List<Component> lines(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return List.of();
        String saturation = String.format(Locale.ROOT, "%.1f", food.nutrition() * food.saturation() * 2f);
        return List.of(
                Tooltips.labelled("tooltip.inventoryplus.item_tips.nutrition", String.valueOf(food.nutrition())),
                Tooltips.labelled("tooltip.inventoryplus.item_tips.saturation", saturation));
    }
}
