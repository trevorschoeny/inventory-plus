package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * The status effects eating or drinking gives, each the way a potion lists
 * it (name, level, duration, coloured by whether it helps), with its chance
 * when under 100%. Read from the {@code CONSUMABLE} component's apply-effects
 * entries only, so suspicious stew's effects, kept in their own component,
 * stay hidden as vanilla intends; and a potion, which vanilla already lists,
 * gets nothing from here because its effects are not consume effects.
 */
final class FoodEffects {

    private FoodEffects() {}

    static List<Component> lines(ItemStack stack, float tickRate) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return List.of();
        List<Component> out = new ArrayList<>();
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect apply)) continue;
            for (MobEffectInstance instance : apply.effects()) out.add(line(instance, apply.probability(), tickRate));
        }
        return out;
    }

    private static Component line(MobEffectInstance instance, float chance, float tickRate) {
        MutableComponent line = PotionContents.getPotionDescription(instance.getEffect(), instance.getAmplifier());
        if (!instance.endsWithin(20)) {
            line = Component.translatable("potion.withDuration", line,
                    MobEffectUtil.formatDuration(instance, 1f, tickRate));
        }
        if (chance < 1f) {
            line = Component.translatable("tooltip.inventoryplus.food_effects.chance", line,
                    Math.round(chance * 100));
        }
        return line.withStyle(instance.getEffect().value().getCategory().getTooltipFormatting());
    }
}
