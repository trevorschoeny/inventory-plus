package com.trevorschoeny.inventoryplus.tooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces vanilla's three armour-trim lines (the "Upgrade:" header, the
 * pattern, the material) with one: "Trim: Silence, Gold". Vanilla's pattern
 * line is rebuilt exactly as {@code ArmorTrim.addToTooltip} builds it and
 * found by equality, so the header just above it and the material line just
 * below are the ones removed; if the lines aren't where vanilla puts them,
 * nothing changes.
 */
final class CondenseTrims {

    private CondenseTrims() {}

    static List<Component> apply(ItemStack stack, List<Component> lines) {
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim == null) return lines;
        Component pattern = trim.pattern().value().copyWithStyle(trim.material());
        Component patternLine = CommonComponents.space().append(pattern);
        Component materialLine = CommonComponents.space().append(trim.material().value().description());
        int at = lines.indexOf(patternLine);
        if (at < 1 || at + 1 >= lines.size() || !lines.get(at + 1).equals(materialLine)) return lines;
        List<Component> out = new ArrayList<>(lines);
        out.subList(at - 1, at + 2).clear();
        out.add(at - 1, Component.translatable("tooltip.inventoryplus.trim", pattern,
                trim.material().value().description()).withStyle(ChatFormatting.GRAY));
        return out;
    }
}
