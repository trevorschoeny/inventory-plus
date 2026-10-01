package com.trevorschoeny.inventoryplus.itemtips;

import com.trevorschoeny.inventoryplus.config.IPConfig;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item Tips: extra tooltip lines for a tool's durability and a food's
 * nutrition and saturation. Moved here from MenuKit, which dropped it in
 * 6.0.0 (Trev, 2026-09-27: an Inventory Plus feature with its own tab and a
 * switch). It behaves exactly as MenuKit's did; only the words moved to the
 * lang file.
 */
public final class ItemTips {

    private ItemTips() {}

    /** Appends the lines to every tooltip while the setting is on. Call once from client init. */
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (IPConfig.tooltipsEnabled() && IPConfig.itemTipsEnabled()) lines.addAll(lines(stack));
        });
    }

    /** The lines for {@code stack}: durability, then food, each after a blank line. Empty if neither applies. */
    public static List<Component> lines(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<Component> tips = new ArrayList<>();
        tips.addAll(durability(stack));
        tips.addAll(food(stack));
        return tips;
    }

    /** "Durability: 120 / 250 (48%)", green above half, yellow from a quarter to half, red below. */
    private static List<Component> durability(ItemStack stack) {
        if (!stack.isDamageableItem()) return List.of();
        int max = stack.getMaxDamage();
        int remaining = max - stack.getDamageValue();
        int percent = (int) ((remaining / (float) max) * 100);
        ChatFormatting colour = percent > 50 ? ChatFormatting.GREEN
                : percent >= 25 ? ChatFormatting.YELLOW
                : ChatFormatting.RED;
        Component line = Component.translatable("tooltip.inventoryplus.item_tips.durability")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(remaining + " / " + max + " (" + percent + "%)").withStyle(colour));
        return List.of(Component.empty(), line);
    }

    /**
     * Nutrition, and effective saturation: nutrition times the saturation
     * multiplier times two, since {@code FoodProperties.saturation()} is a
     * multiplier, not the points restored.
     */
    private static List<Component> food(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return List.of();
        String saturation = String.format(Locale.ROOT, "%.1f", food.nutrition() * food.saturation() * 2f);
        return List.of(Component.empty(),
                gold("tooltip.inventoryplus.item_tips.nutrition", String.valueOf(food.nutrition())),
                gold("tooltip.inventoryplus.item_tips.saturation", saturation));
    }

    private static Component gold(String key, String value) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.GOLD));
    }
}
