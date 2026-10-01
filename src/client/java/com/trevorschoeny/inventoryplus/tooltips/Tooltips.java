package com.trevorschoeny.inventoryplus.tooltips;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import com.trevlar.menukit.api.element.MKTooltip;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything Inventory Plus does to tooltips, one checkbox each (the Tooltips
 * tab; design in the Leadership drive, {@code plans/settings-menu.md},
 * "Tooltips"). One Fabric {@code ItemTooltipCallback}, and one provider class
 * per checkbox, applied here in the tab's order, each behind its own
 * setting:
 *
 * <ol>
 *   <li>Rewrites of vanilla's own lines, in place: sort the enchantments,
 *       then put a description under each (so it follows its line), then
 *       condense the trim lines.</li>
 *   <li>Added lines, each provider's block after a blank line: durability,
 *       food, food effects, tool stats, the comparison.</li>
 *   <li>The mod name, last.</li>
 * </ol>
 *
 * <p>The hide key is the other side of the firewall: MenuKit owns the one
 * seam every tooltip is drawn through, and Inventory Plus registers its
 * condition there. Turning the tab off turns all of it off.
 */
public final class Tooltips {

    private Tooltips() {}

    /** Call once from client init. */
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!IPConfig.tooltipsEnabled() || stack.isEmpty()) return;
            List<Component> result = apply(stack, lines, context.tickRate());
            lines.clear();
            lines.addAll(result);
        });
        // Hold the hide key (Space by default) to hide every tooltip, on any
        // screen. Read raw, so it works where vanilla releases key states.
        MKTooltip.hideWhen(() -> IPConfig.tooltipsEnabled() && IPConfig.hideTooltipsOnKey()
                && IPKeybinds.isHeld(IPKeybinds.HIDE_TOOLTIPS));
    }

    private static List<Component> apply(ItemStack stack, List<Component> vanilla, float tickRate) {
        List<Component> lines = new ArrayList<>(vanilla);
        if (IPConfig.tooltipSortEnchantments()) lines = SortEnchantments.apply(stack, lines);
        if (IPConfig.tooltipEnchantmentDescriptions()) lines = EnchantmentDescriptions.apply(stack, lines);
        if (IPConfig.tooltipCondenseTrims()) lines = CondenseTrims.apply(stack, lines);
        if (IPConfig.tooltipDurability()) block(lines, Durability.lines(stack));
        if (IPConfig.tooltipFood()) block(lines, Food.lines(stack));
        if (IPConfig.tooltipFoodEffects()) block(lines, FoodEffects.lines(stack, tickRate));
        if (IPConfig.tooltipToolStats()) block(lines, ToolStats.lines(stack));
        if (IPConfig.tooltipCompare()) block(lines, Compare.lines(stack, Minecraft.getInstance().player));
        if (IPConfig.tooltipModName()) lines.addAll(ModName.lines(stack));
        return lines;
    }

    /** Appends a provider's lines after a blank line, or nothing when it has none. */
    private static void block(List<Component> lines, List<Component> added) {
        if (added.isEmpty()) return;
        lines.add(Component.empty());
        lines.addAll(added);
    }

    // ── Shared by the providers ─────────────────────────────────────────

    /** A grey label from the lang file followed by a gold value: "Nutrition: 5". */
    static Component labelled(String key, String value) {
        return labelled(key, Component.literal(value));
    }

    static Component labelled(String key, Component value) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY)
                .append(value.copy().withStyle(ChatFormatting.GOLD));
    }

    /** A number as a player reads it: no trailing ".0", at most two decimals. */
    static String number(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "");
    }
}
