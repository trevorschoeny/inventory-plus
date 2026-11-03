package com.trevorschoeny.inventoryplus.checks;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.slot.CreatedSlot;
import com.trevlar.slotkit.api.slot.ParityGroup;
import com.trevlar.slotkit.api.slot.SlotSpec;
import com.trevlar.slotkit.api.storage.EphemeralStorage;
import com.trevlar.slotkit.api.storage.Storage;
import com.trevlar.slotkit.api.window.Address;
import com.trevlar.slotkit.api.window.GatingContext;
import com.trevlar.slotkit.api.window.SlotGate;
import com.trevlar.slotkit.api.window.Window;

import net.fabricmc.api.ModInitializer;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Groups for {@link ContainerListChecks}, standing in for what a mod like Open
 * Anywhere opens in a chest's container list: two one-row shulker boxes and a
 * bundle. Each is its own container (SlotKit 2.1's separate containers). All
 * start closed (active count 0), so they sit inert on every menu until the
 * check opens them, and no other check sees them.
 */
public class ListFixture implements ModInitializer {

    /** One row each, so the chest and the boxes fit on the check's screen without scrolling. */
    public static final int SIZE = 9;

    public static ParityGroup BOX_A, BOX_B, BUNDLE;

    @Override
    public void onInitialize() {
        BOX_A = group("inventoryplus_checks:box_a", SlotGroupCategory.SHULKER_STORAGE, player -> EphemeralStorage.of(SIZE));
        BOX_B = group("inventoryplus_checks:box_b", SlotGroupCategory.SHULKER_STORAGE, player -> EphemeralStorage.of(SIZE));
        BUNDLE = group("inventoryplus_checks:bundle", SlotGroupCategory.BUNDLE_STORAGE, player -> new BundleLike());
        for (int i = 0; i < SIZE; i++) Window.slot(ModeScope.BOTH, Address.createdSlot(BUNDLE.id(), i)).gate(BUNDLE_RULES);
    }

    private static ParityGroup group(String owner, SlotGroupCategory category,
                                     java.util.function.Function<Player, Storage> storage) {
        return ParityGroup.register(owner, SlotSpec.at("slots", category).count(SIZE)
                .storage(storage)
                .activeCount(0));
    }

    /**
     * An opened bundle's storage, as Open Anywhere's behaves: its stacks in
     * order from slot 0, and a stack taken out closes the gap, moving the ones
     * after it up.
     */
    private static final class BundleLike implements Storage {
        private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

        @Override public ItemStack getStack(int i) { return items.get(i); }

        @Override public void setStack(int i, ItemStack stack) {
            items.set(i, stack);
            pack();
        }

        @Override public int size() { return SIZE; }

        @Override public void markDirty() { pack(); }

        private void pack() {
            List<ItemStack> kept = items.stream().filter(s -> !s.isEmpty()).toList();
            for (int j = 0; j < SIZE; j++) items.set(j, j < kept.size() ? kept.get(j) : ItemStack.EMPTY);
        }
    }

    /**
     * Open Anywhere's bundle rules, less the weight: the slot after the last
     * stack adds one; a filled slot never grows, and swaps only for a
     * different item; the slots past the add slot take nothing.
     */
    private static final SlotGate BUNDLE_RULES = new SlotGate() {
        @Override public boolean mayPlace(ItemStack stack, GatingContext ctx) {
            Slot slot = ctx.slot();
            CreatedSlot created = slot == null ? null : CreatedSlot.of(slot);
            if (created == null) return false;
            int stacks = 0;
            while (stacks < SIZE && !slot.container.getItem(stacks).isEmpty()) stacks++;
            int index = created.getLocalIndex();
            if (index == stacks) return true;
            return index < stacks && !ItemStack.isSameItemSameComponents(slot.container.getItem(index), stack);
        }

        @Override public boolean mayPickup(Player player, GatingContext ctx) {
            return true;
        }
    };
}
