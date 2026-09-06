package com.trevorschoeny.inventoryplus.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla's creative-tab order as a rank per item type, for
 * {@link SortType#CATEGORY}: the first tab that lists the item, then its
 * position within that tab. Read from the game's own tabs rather than a
 * category list of our own, so it follows vanilla across versions.
 *
 * <p>Tab contents are built lazily by the client (the creative screen
 * triggers it), so this asks the game to build them first. That call is
 * cheap when nothing changed and returns true when it rebuilt, which is
 * when the table is recomputed: feature flags differ per world. Items in
 * no tab (operator-only, hidden) rank after everything.
 */
public final class CategoryOrder {

    private CategoryOrder() {}

    private static Map<Item, Integer> rank = Map.of();
    private static boolean built = false;

    /** Rank of the stack's item type; lower sorts first. */
    public static int rankOf(ItemStack stack) {
        ensureBuilt();
        return rank.getOrDefault(stack.getItem(), Integer.MAX_VALUE);
    }

    private static void ensureBuilt() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean rebuilt = CreativeModeTabs.tryRebuildTabContents(
                player.level().enabledFeatures(), false, player.level().registryAccess());
        if (built && !rebuilt) return;

        Map<Item, Integer> table = new HashMap<>();
        int next = 0;
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
            for (ItemStack shown : tab.getDisplayItems()) {
                // First tab containing the item wins; later appearances are ignored.
                if (table.putIfAbsent(shown.getItem(), next) == null) next++;
            }
        }
        rank = table;
        built = true;
    }
}
