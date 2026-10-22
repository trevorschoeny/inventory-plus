package com.trevorschoeny.inventoryplus.checks;

import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.EphemeralStorage;

import net.fabricmc.api.ModInitializer;

/**
 * Two one-row groups for {@link ContainerListChecks}, standing in for boxes a
 * mod like Shulkers in Inventory opens in a chest's container list. Each is its
 * own container (SlotKit 2.1's separate containers). Both start closed (active
 * count 0), so they sit inert on every menu until the check opens them, and no
 * other check sees them.
 */
public class ListFixture implements ModInitializer {

    public static final SlotGroupCategory BOX = new SlotGroupCategory("inventoryplus_checks", "box");

    /** One row each, so the chest and both boxes fit on the check's screen without scrolling. */
    public static final int SIZE = 9;

    public static ParityGroup BOX_A, BOX_B;

    @Override
    public void onInitialize() {
        BOX_A = box("inventoryplus_checks:box_a");
        BOX_B = box("inventoryplus_checks:box_b");
    }

    private static ParityGroup box(String owner) {
        return ParityGroup.register(owner, SlotSpec.at("slots", BOX).count(SIZE)
                .storage(player -> EphemeralStorage.of(SIZE))
                .activeCount(0));
    }
}
