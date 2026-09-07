package com.trevorschoeny.inventoryplus.lockeditems;

import com.trevorschoeny.inventoryplus.config.IPConfig;

import java.util.function.BooleanSupplier;

/**
 * The automations that can be told to ignore item locks, and the setting
 * that answers for each (Trev 2026-09-06).
 *
 * <h3>Why only these features have a say</h3>
 *
 * <p>A lock stops the mod from <b>tidying an item away</b>: Sorting,
 * Move Matching and the cyclers all move an item somewhere the player
 * did not put it, so they honour locks unconditionally and have no
 * toggle here. Auto Tool Switch and Auto-Restock instead <b>hand the
 * item to the player</b>, which is usually the whole reason it was worth
 * locking. Every constant therefore defaults to using locked items, and
 * the toggle exists for the player who wants a locked item left strictly
 * alone.
 *
 * <p>Slot locks are unaffected either way. These settings say "use
 * locked items", not "ignore locked slots", and
 * {@code AutoRestockSearch.isProtectedSource} still refuses a locked slot
 * whatever is set here.
 *
 * <h3>One constant per restock, matching the settings that exist</h3>
 *
 * <p>The restock constants line up with the three gates the ticker
 * already reads ({@code autoRestockItem}, {@code autoRestockTool},
 * {@code autoRestockArmor}). The offhand durability swap has no gate of
 * its own, running under the Tool one, so it takes {@link #RESTOCK_TOOL}
 * rather than inventing a fourth setting the rest of the UI does not
 * have.
 */
public enum LockedItemUser {

    /** Auto Tool Switch picking a tool to put in the player's hand. */
    AUTO_TOOL_SWITCH(IPConfig::autoToolSwitchUsesLockedItems),

    /** Refilling a non-damageable stack that ran out (`autoRestockItem`). */
    RESTOCK_ITEM(IPConfig::autoRestockItemUsesLockedItems),

    /** Replacing a broken tool, and the durability swaps (`autoRestockTool`). */
    RESTOCK_TOOL(IPConfig::autoRestockToolUsesLockedItems),

    /** Replacing broken armour, and its durability swap (`autoRestockArmor`). */
    RESTOCK_ARMOR(IPConfig::autoRestockArmorUsesLockedItems);

    private final BooleanSupplier usesLockedItems;

    LockedItemUser(BooleanSupplier usesLockedItems) {
        this.usesLockedItems = usesLockedItems;
    }

    /**
     * True when this feature may take a locked item anyway. Read live
     * rather than cached, since the player can change it mid-session from
     * the settings screen.
     */
    public boolean usesLockedItems() {
        return usesLockedItems.getAsBoolean();
    }
}
