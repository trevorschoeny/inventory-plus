package com.trevorschoeny.inventoryplus;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKPanel;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

/**
 * Equipment panel — two passive equipment slots (elytra + totem) positioned
 * inside the inventory screen, blending seamlessly with vanilla's armor/offhand area.
 *
 * <p><b>Survival:</b> Slots appear above the offhand slot at x=77, forming a
 * vertical column (elytra at y=26, totem at y=44, offhand at y=62).
 *
 * <p><b>Creative inventory tab:</b> Slots appear near the offhand slot at
 * creative-specific coordinates.
 *
 * <p>Uses {@code Style.NONE} — no panel background. The slot insets render
 * with vanilla's slot style, looking like they've always been part of the inventory.
 *
 * <p>The slots persist automatically via MenuKit's player NBT system.
 * Passive behaviors (flight, death-save, mending, wing rendering) are handled
 * by separate mixins that read items via {@code MenuKit.getContainerForPlayer("equipment")}.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
public class EquipmentPanel {

    // Ghost icon sprite identifiers — 16×16 gray silhouettes shown when slots are empty.
    public static final Identifier ELYTRA_ICON =
            Identifier.fromNamespaceAndPath("trevs-mod", "container/slot/elytra");
    public static final Identifier TOTEM_ICON =
            Identifier.fromNamespaceAndPath("trevs-mod", "container/slot/totem");

    /**
     * Registers the equipment panel with MenuKit. Called from {@link InventoryPlus#init()}.
     */
    public static void register() {
        // Survival positions:
        //   Armor column:  x=8,  y=8/26/44/62
        //   Offhand:       x=77, y=62
        //   Our elytra:    x=77, y=26  (above offhand, aligned vertically)
        //   Our totem:     x=77, y=44  (between elytra and offhand)
        //
        // Creative inventory tab positions:
        //   Offhand:       x=35, y=20
        //   Armor:         (54,6) (54,33) (108,6) (108,33) — 2×2 grid
        //   Our elytra:    x=14, y=6   (left of character, near armor area)
        //   Our totem:     x=14, y=24  (below elytra)

        MKPanel.builder("equipment")
                .showIn(MKContext.SURVIVAL_INVENTORY, MKContext.CREATIVE_INVENTORY)
                .pos(76, 25)                     // survival: above offhand (77,62), same column
                .posFor(MKContext.CREATIVE_INVENTORY, 15, 10) // creative: left of offhand (35,20), stacked vertically
                .padding(0)                      // no padding — exact placement
                .autoSize()
                .style(MKPanel.Style.NONE)       // invisible background — blends with inventory
                .shiftClickIn(false)             // equipment slots are destination-only; items reach here via custom routing
                .shiftClickOut(true)             // allow shift-clicking items out of equipment slots

                // Slot 0: Passive elytra — grants flight without wearing in chest slot
                .slot(0, 0)
                    .container("equipment", 0)
                    .disabledWhen(() -> !InventoryPlusConfig.get().enableElytraSlot)
                    .filter(EquipmentPanel::isElytra)
                    .maxStack(1)
                    .ghostIcon(() -> ELYTRA_ICON)
                    .done()

                // Slot 1: Passive totem — saves from death without holding in hand
                .slot(0, 18)
                    .container("equipment", 1)
                    .disabledWhen(() -> !InventoryPlusConfig.get().enableTotemSlot)
                    .filter(stack -> stack.is(Items.TOTEM_OF_UNDYING))
                    .maxStack(1)
                    .ghostIcon(() -> TOTEM_ICON)
                    .done()

                .build();

        // ── Shift-click priority routes ─────────────────────────────────────
        // Without these, shift-clicking an elytra or totem would send them to
        // a random inventory slot because the equipment panel has shiftClickIn=false
        // (to prevent arbitrary items from landing here). Priority routes bypass
        // that flag for specific item types that have a "natural home" in this panel.

        // Elytra → equipment slot 0 (the passive elytra slot)
        MenuKit.shiftClickPriority(EquipmentPanel::isElytra, "equipment", 0);

        // Totem of Undying → equipment slot 1 (the passive totem slot)
        MenuKit.shiftClickPriority(stack -> stack.is(Items.TOTEM_OF_UNDYING), "equipment", 1);
    }

    /** Checks if an item is any type of elytra (vanilla or modded). */
    public static boolean isElytra(net.minecraft.world.item.ItemStack stack) {
        return stack.is(Items.ELYTRA);
    }
}
