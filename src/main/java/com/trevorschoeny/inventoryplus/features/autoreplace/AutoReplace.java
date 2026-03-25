package com.trevorschoeny.inventoryplus.features.autoreplace;

import com.trevorschoeny.inventoryplus.InventoryPlus;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Auto-replace: when a tool breaks in the player's main hand, automatically
 * equip the next tool of the same type from inventory.
 *
 * <p>Called from {@code IPAutoReplaceMixin} which hooks into
 * {@link net.minecraft.world.entity.LivingEntity#onEquippedItemBroken}.
 * That method fires after {@code hurtAndBreak} detects the item is fully
 * broken (durability exhausted) and has already consumed (shrunk) the stack.
 *
 * <p><b>Tool type detection</b> uses vanilla's item tags ({@code minecraft:pickaxes},
 * {@code minecraft:axes}, etc.) rather than class hierarchy. This is the
 * correct approach for 1.21.11+ where tools like swords and pickaxes are
 * plain {@code Item} instances configured via {@code Item.Properties}, not
 * dedicated subclasses.
 *
 * <p><b>Replacement priority</b>: among candidates of the same tool type,
 * we prefer tools with higher max durability. This naturally sorts by tier
 * (netherite 2031 > diamond 1561 > iron 250 > copper 190 > stone 131 > wood 59 > gold 32)
 * because vanilla's tier system assigns durability proportional to material quality.
 * Gold sorts last despite its high enchantability — which is correct: a gold
 * pickaxe breaks in 32 uses, so it's the worst replacement candidate.
 */
public final class AutoReplace {

    // The tool type tags we recognize, in no particular order.
    // Each broken tool is checked against these tags to determine its category.
    // If a tool matches multiple tags (unlikely but theoretically possible with
    // modded items), the first match wins — the order here doesn't matter much
    // since we're just finding the category, not ranking.
    private static final List<TagKey<Item>> TOOL_TAGS = List.of(
            ItemTags.PICKAXES,
            ItemTags.AXES,
            ItemTags.SHOVELS,
            ItemTags.SWORDS,
            ItemTags.HOES
    );

    // Main inventory range: slots 9-35 (27 slots, excluding hotbar 0-8).
    // We also search hotbar slots OTHER than the broken slot, because the
    // player might have a backup tool in another hotbar position.
    private static final int MAIN_INV_START = 9;
    private static final int MAIN_INV_END = 35; // inclusive

    /**
     * Attempts to replace a broken tool in the player's main hand.
     *
     * <p>Called when a tool breaks in {@link EquipmentSlot#MAINHAND}. The broken
     * item is already consumed (slot is empty) by the time this fires.
     *
     * @param player    the player whose tool just broke
     * @param brokenItem the Item that broke (not the stack — the stack is gone)
     */
    public static void onToolBroken(Player player, Item brokenItem) {
        // Creative players don't break tools — this shouldn't fire, but guard anyway
        if (player.isCreative()) return;
        if (player.isSpectator()) return;

        // Determine which tool category the broken item belonged to.
        // If it's not in any of our recognized tags (e.g., a fishing rod or
        // trident), we don't attempt replacement — those are specialty items
        // where automatic replacement might not match user intent.
        TagKey<Item> toolTag = getToolTag(brokenItem);
        if (toolTag == null) {
            return;
        }

        Inventory inv = player.getInventory();
        int selectedSlot = inv.getSelectedSlot();

        // Search inventory for the best replacement: same tool type, highest durability.
        int bestSlot = -1;
        int bestDurability = -1;

        // Search hotbar first (slots 0-8), skipping the selected slot (it's empty —
        // that's where the tool broke). Players often keep backup tools in hotbar.
        for (int i = 0; i < 9; i++) {
            if (i == selectedSlot) continue;
            int durability = evaluateCandidate(inv.getItem(i), toolTag);
            if (durability > bestDurability) {
                bestDurability = durability;
                bestSlot = i;
            }
        }

        // Then search main inventory (slots 9-35)
        for (int i = MAIN_INV_START; i <= MAIN_INV_END; i++) {
            int durability = evaluateCandidate(inv.getItem(i), toolTag);
            if (durability > bestDurability) {
                bestDurability = durability;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) {
            // No replacement found — the player is out of this tool type
            return;
        }

        // Swap the replacement into the selected hotbar slot.
        // The selected slot is empty (tool broke), so this is a simple move.
        ItemStack replacement = inv.getItem(bestSlot);
        inv.setItem(selectedSlot, replacement);
        inv.setItem(bestSlot, ItemStack.EMPTY);

        InventoryPlus.LOGGER.debug(
                "[AutoReplace] Replaced broken {} with {} from slot {}",
                brokenItem, replacement.getHoverName().getString(), bestSlot);
    }

    /**
     * Determines which tool category an item belongs to by checking against
     * vanilla's item tags. Returns null if the item isn't a recognized tool type.
     *
     * <p>Uses a temporary single-item stack for tag checking because
     * {@code ItemStack.is(TagKey)} is the non-deprecated API for tag membership.
     * The allocation cost is negligible since this only fires when a tool breaks
     * (not every tick).
     */
    private static TagKey<Item> getToolTag(Item item) {
        ItemStack probe = new ItemStack(item);
        for (TagKey<Item> tag : TOOL_TAGS) {
            if (probe.is(tag)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * Evaluates whether an ItemStack is a valid replacement candidate for the
     * given tool type. Returns the max durability if it qualifies, or -1 if not.
     *
     * <p>Max durability is used as the ranking metric because it directly
     * correlates with tool tier in vanilla (netherite > diamond > iron > etc.).
     * A tool with 0 remaining durability but max durability of 2031 would still
     * rank high — but that's fine because such a tool would break on next use
     * and trigger another replacement cycle.
     */
    private static int evaluateCandidate(ItemStack stack, TagKey<Item> requiredTag) {
        if (stack.isEmpty()) return -1;

        // Must be the same tool type (e.g., a pickaxe to replace a pickaxe)
        if (!stack.is(requiredTag)) return -1;

        // Use max durability as tier proxy. Items without durability (shouldn't
        // happen for tools, but defensive) get a score of 0 so they lose to
        // any real tool.
        return stack.getMaxDamage();
    }

    private AutoReplace() {} // Utility class — no instantiation
}
