package com.trevorschoeny.inventoryplus.nested;

import com.trevlar.slotkit.api.reserve.Reservation;
import com.trevlar.slotkit.api.reserve.Reservations;
import com.trevlar.slotkit.api.window.Automation;

import com.trevorschoeny.inventoryplus.rules.Intake;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Pickups into carried shulker boxes, by each box's {@link Intake}: the server's half.
 * Hooked at {@code Inventory.add} ({@code InventoryAddCarriedPutMixin}), so it covers
 * every item given to the inventory with no slot named: pickups, {@code /give}, a
 * crafting grid's returns.
 *
 * <p>The boxes set to Before inventory are filled first, then the inventory takes the
 * rest as vanilla does, then the boxes set to After inventory take what it had no room
 * for. Within one tier, the most specific home wins:
 * <ol>
 *   <li>a stack of the item that isn't full,</li>
 *   <li>an empty slot reserved for it,</li>
 *   <li>an empty slot in a Matching only box that already holds it,</li>
 *   <li>any other empty slot, in a box with Matching only off.</li>
 * </ol>
 * Inventory position breaks ties inside a step (hotbar first, then the main inventory).
 * Every slot is asked through {@link Automation#room}, so a box's reservations, its
 * locks and any other mod's automation rules hold here too.
 *
 * <p>Only boxes in the main inventory and hotbar take part, never a box inside another,
 * a bundle or the ender chest. A player whose client doesn't run Inventory Plus
 * ({@link SlotLocks#known}) gets vanilla.
 */
public final class CarriedPut {

    private CarriedPut() {}

    /** Payloads and receivers. Once, from the common initializer. */
    public static void register() {
        Intake.register();
    }

    /** One carried box and its contents as a working copy, written back after each put. */
    private record Box(ItemStack stack, Intake intake, NonNullList<ItemStack> items) {
        boolean holds(ItemStack item) {
            for (ItemStack s : items) if (ItemStack.isSameItem(s, item)) return true;
            return false;
        }
    }

    /**
     * Puts as much of {@code stack} as fits into the carried boxes whose Intake is
     * {@code when} ({@code BEFORE} or {@code AFTER}), shrinking it by what went in.
     * Returns how many went in.
     */
    public static int into(ServerPlayer player, ItemStack stack, Intake.When when) {
        if (stack.isEmpty() || when == Intake.When.OFF || player.isSpectator() || !SlotLocks.known(player)) return 0;
        // A shulker box never goes into another; vanilla's own rule for container items.
        if (!stack.getItem().canFitInsideContainerItems()) return 0;
        List<Box> boxes = boxes(player.getInventory(), when);
        if (boxes.isEmpty()) return 0;

        int before = stack.getCount();
        // 1. Stacks of it that aren't full.
        for (Box b : boxes) {
            for (int i = 0; i < b.items().size() && !stack.isEmpty(); i++) {
                if (ItemStack.isSameItemSameComponents(b.items().get(i), stack)) put(b, i, stack);
            }
        }
        // 2. Empty slots reserved for it.
        for (Box b : boxes) {
            for (int i = 0; i < b.items().size() && !stack.isEmpty(); i++) {
                if (!b.items().get(i).isEmpty()) continue;
                Reservation r = Reservations.get(b.stack(), i);
                if (!r.isNone() && r.matches(stack)) put(b, i, stack);
            }
        }
        // 3. Empty slots in Matching only boxes that hold it; 4. then any box that takes anything.
        // A slot reserved for something else refuses inside Automation.room.
        for (boolean matchingOnly : new boolean[] {true, false}) {
            for (Box b : boxes) {
                if (b.intake().matchingOnly() != matchingOnly || matchingOnly && !b.holds(stack)) continue;
                for (int i = 0; i < b.items().size() && !stack.isEmpty(); i++) {
                    if (b.items().get(i).isEmpty()) put(b, i, stack);
                }
            }
        }
        int moved = before - stack.getCount();
        if (moved > 0) player.getInventory().setChanged();
        return moved;
    }

    /** The boxes in the main inventory and hotbar set to {@code when}, hotbar first. */
    private static List<Box> boxes(Inventory inv, Intake.When when) {
        List<Box> out = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inv.getItem(slot);
            Intake intake = Intake.of(stack);
            if (intake.when() != when) continue;
            ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            NonNullList<ItemStack> items = NonNullList.withSize(Math.max(27, contents.size()), ItemStack.EMPTY);
            contents.copyInto(items);
            out.add(new Box(stack, intake, items));
        }
        return out;
    }

    /** Moves what slot {@code i} of {@code b} takes of {@code stack} there, and writes the box. */
    private static void put(Box b, int i, ItemStack stack) {
        // Room reads the box item itself (its reservations, locks and contents), so the
        // contents are written back after every put.
        int n = Automation.room(b.stack(), i, stack);
        if (n <= 0) return;
        ItemStack there = b.items().get(i);
        b.items().set(i, there.isEmpty() ? stack.copyWithCount(n) : there.copyWithCount(there.getCount() + n));
        stack.shrink(n);
        b.stack().set(DataComponents.CONTAINER, ItemContainerContents.fromItems(b.items()));
    }
}
