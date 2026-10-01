package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * For a tool: its mining speed (the fastest of its rules, the speed it digs
 * the blocks it is for) and its tier. For anything enchantable: its
 * enchantability.
 *
 * <p>26.2 has no tier objects; tools are data. A tool's tier shows in the rule
 * that refuses drops from blocks it is too weak for, whose block tag is
 * {@code incorrect_for_<tier>_tool}. That tag names the tier; a tool with no
 * such rule shows speed alone.
 */
final class ToolStats {

    private ToolStats() {}

    private static final Pattern TIER_TAG = Pattern.compile("incorrect_for_([a-z0-9_]+)_tool");

    static List<Component> lines(ItemStack stack) {
        List<Component> out = new ArrayList<>();
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool != null) {
            float speed = tool.defaultMiningSpeed();
            String tier = null;
            for (Tool.Rule rule : tool.rules()) {
                if (rule.speed().isPresent()) speed = Math.max(speed, rule.speed().get());
                if (rule.correctForDrops().isPresent() && !rule.correctForDrops().get()) {
                    String found = tierOf(rule.blocks().unwrapKey().orElse(null));
                    if (found != null) tier = found;
                }
            }
            if (tier != null) {
                out.add(Tooltips.labelled("tooltip.inventoryplus.tool_stats.tier",
                        Component.translatableWithFallback("tooltip.inventoryplus.tool_stats.tier." + tier,
                                titleCase(tier))));
            }
            out.add(Tooltips.labelled("tooltip.inventoryplus.tool_stats.mining_speed", Tooltips.number(speed)));
        }
        Enchantable enchantable = stack.get(DataComponents.ENCHANTABLE);
        if (enchantable != null && enchantable.value() > 0) {
            out.add(Tooltips.labelled("tooltip.inventoryplus.tool_stats.enchantability",
                    String.valueOf(enchantable.value())));
        }
        return out;
    }

    private static @Nullable String tierOf(@Nullable TagKey<Block> tag) {
        if (tag == null) return null;
        Matcher m = TIER_TAG.matcher(tag.location().getPath());
        return m.matches() ? m.group(1) : null;
    }

    private static String titleCase(String id) {
        String s = id.replace('_', ' ');
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
