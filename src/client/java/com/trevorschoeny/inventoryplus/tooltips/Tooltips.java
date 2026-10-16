package com.trevorschoeny.inventoryplus.tooltips;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;

import com.trevlar.menukit.api.element.MKTooltip;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Everything Inventory Plus does to tooltips, one checkbox each on the
 * Tooltips tab. One Fabric {@code ItemTooltipCallback}, and one provider class
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
 * <p>The hide key works differently. MenuKit owns the one seam every tooltip
 * is drawn through, and Inventory Plus registers its
 * condition there. Turning the tab off turns all of it off.
 */
public final class Tooltips {

    private Tooltips() {}

    /** Where the mouse was when the open screen opened, in window pixels. */
    private static double heldAtX, heldAtY;
    /** True from a screen opening until the mouse first moves. */
    private static boolean held;

    /** Whether the hold is still on: the mouse hasn't moved since the screen opened. */
    private static boolean stillHeld() {
        if (!held) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.xpos() != heldAtX || mc.mouseHandler.ypos() != heldAtY) held = false;
        return held;
    }

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
        // Hide until the mouse moves (Trev, 2026-10-15): a screen that opens
        // shows no tooltip until the mouse leaves where it was when it opened,
        // so the slot that happens to sit under the cursor doesn't pop one up.
        // The cursor itself is never moved.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            heldAtX = client.mouseHandler.xpos();
            heldAtY = client.mouseHandler.ypos();
            held = true;
        });
        MKTooltip.hideWhen(() -> IPConfig.tooltipsEnabled() && IPConfig.tooltipHideUntilMouseMoves()
                && stillHeld());
        // Dock tooltips: every tooltip vanilla would place beside the cursor sits
        // at the bottom centre of the screen, or the top centre when the cursor
        // would be inside it there (MenuKit 6.2.0 avoidCoverWhen).
        MKTooltip.avoidCoverWhen(() -> IPConfig.tooltipsEnabled() && IPConfig.tooltipDock());
        // Keep on screen and scroll have no settings: both are on whenever the
        // Tooltips switch is. Keep on screen is one predicate for both of
        // MenuKit's halves, wrapping a tooltip wider than the screen and
        // clamping its top.
        // Scroll: the wheel moves a long tooltip on screen. MenuKit clamps the
        // offset to the tooltip, so one that fits never moves, and pins the
        // title. Wheel down (negative) moves the tooltip up, bringing its lower
        // lines into view, as a list scrolls. Never consumes the wheel.
        BooleanSupplier keepOnScreen = IPConfig::tooltipsEnabled;
        MKTooltip.wrapWhen(keepOnScreen);
        MKTooltip.clampWhen(keepOnScreen);
        MKTooltip.onWheel((horizontal, vertical) -> {
            if (!IPConfig.tooltipsEnabled()) return;
            MKTooltip.scrollBy((int) Math.round(horizontal * SCROLL_STEP),
                    (int) Math.round(vertical * SCROLL_STEP));
        });
    }

    // Two tooltip lines (10 px each) per wheel notch. Tune this if scrolling
    // feels slow or jumpy.
    private static final int SCROLL_STEP = 20;

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

    // Shared by the providers

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
