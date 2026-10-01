package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.Equippable;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lines comparing the hovered item with what the player has on: armour
 * against the piece worn in its slot, a tool or weapon against the main-hand
 * item. One line per stat that differs ("Armor +2"), green for more, red for
 * less, under a header naming what it compares with. Lines, not a second
 * tooltip.
 *
 * <p>Stats are the stack's flat attribute modifiers for that slot, summed per
 * attribute (multipliers are left out: they don't add to a number a player
 * reads), and for tools the mining speed. Nothing shows when nothing differs,
 * or when the hovered stack is the very one being compared with.
 */
final class Compare {

    private Compare() {}

    static List<Component> lines(ItemStack stack, @Nullable Player player) {
        if (player == null || stack.isEmpty()) return List.of();
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            return against(stack, player.getItemBySlot(equippable.slot()), equippable.slot(),
                    "tooltip.inventoryplus.compare.worn");
        }
        if (stack.has(DataComponents.TOOL) || hasModifiers(stack, EquipmentSlot.MAINHAND)) {
            return against(stack, player.getMainHandItem(), EquipmentSlot.MAINHAND,
                    "tooltip.inventoryplus.compare.held");
        }
        return List.of();
    }

    private static List<Component> against(ItemStack stack, ItemStack other, EquipmentSlot slot, String header) {
        if (stack == other) return List.of();
        Map<String, Double> mine = stats(stack, slot);
        Map<String, Double> theirs = stats(other, slot);
        List<Component> out = new ArrayList<>();
        for (String key : union(mine, theirs)) {
            double diff = mine.getOrDefault(key, 0.0) - theirs.getOrDefault(key, 0.0);
            if (Math.abs(diff) < 1e-6) continue;
            String sign = diff > 0 ? "+" : "-";
            out.add(Component.translatable(key).append(" " + sign + Tooltips.number(Math.abs(diff)))
                    .withStyle(diff > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (out.isEmpty()) return out;
        out.add(0, Component.translatable(header).withStyle(ChatFormatting.GRAY));
        return out;
    }

    /** Translation key of each stat -> its value on {@code stack} in {@code slot}. */
    private static Map<String, Double> stats(ItemStack stack, EquipmentSlot slot) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (stack.isEmpty()) return out;
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .forEach(slot, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
                    if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) return;
                    out.merge(attribute.value().getDescriptionId(), modifier.amount(), Double::sum);
                });
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool != null && slot == EquipmentSlot.MAINHAND) {
            float speed = tool.defaultMiningSpeed();
            for (Tool.Rule rule : tool.rules()) if (rule.speed().isPresent()) speed = Math.max(speed, rule.speed().get());
            out.put("tooltip.inventoryplus.tool_stats.mining_speed_stat", (double) speed);
        }
        return out;
    }

    private static boolean hasModifiers(ItemStack stack, EquipmentSlot slot) {
        boolean[] any = {false};
        stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .forEach(slot, (attribute, modifier) -> any[0] = true);
        return any[0];
    }

    private static List<String> union(Map<String, Double> a, Map<String, Double> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String key : b.keySet()) if (!keys.contains(key)) keys.add(key);
        return keys;
    }
}
