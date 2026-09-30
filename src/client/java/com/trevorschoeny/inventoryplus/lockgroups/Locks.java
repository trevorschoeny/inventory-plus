package com.trevorschoeny.inventoryplus.lockgroups;

import com.trevlar.menukit.api.window.SlotOperations;
import com.trevlar.menukit.api.window.SlotRef;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The one lock question and the one veto (`plans/reach.md`, "Enforcement:
 * one veto").
 *
 * <h2>{@link #on}: which lock groups are on this slot</h2>
 *
 * <p>The Slot-kind lock on the slot (stored in the player, ender, created or
 * container store; derived from a cycler; or a companion's shared container
 * lock, judged as Slot lock), plus the Item and Exact locks matching the
 * stack in it. It replaces {@code isLocked(int)}, {@code isLockedSlot(Slot)},
 * {@code LockedItems.isLocked} and {@code LockedItems.blocks(user)}, which
 * disagreed with each other, so every feature now gets the same answer.
 *
 * <h2>The veto</h2>
 *
 * <p>Registered once at client init. For a slot and an operation: if the
 * operation may not use the slot's group, deny; if any lock group on the slot
 * or its stack stops the operation, deny; otherwise no opinion. A lock can
 * only subtract from what the slot's author allows; deny wins
 * (deny-overrides).
 *
 * <p>Client first (Trev, 2026-09-27): the veto judges only the local player,
 * matched by UUID so the integrated server's copy of that player counts too.
 * With no acting player (a hopper), or another player (a LAN guest of this
 * host), it has no opinion, since this record is this player's alone.
 *
 * <p>The slot's group comes from MenuKit itself: {@code allows} resolves it
 * once per question and hands it to every registered {@link
 * SlotOperations.GroupVeto}, so no veto re-derives it.
 */
public final class Locks {

    private Locks() {}

    /** Registers the veto. Once, at client init. */
    public static void registerVeto() {
        SlotOperations.veto((ref, group, operation) -> {
            if (!isLocalPlayer(ref.player())) return false;
            String op = operation.id().toString();
            if (group != null && Reach.denies(op, group.asString())) return true;
            // The Lock groups switch pauses every lock without deleting any;
            // the slot groups' own reach still holds.
            if (!IPConfig.lockGroupsEnabled()) return false;
            for (String lock : on(ref)) {
                if (Reach.lockDenies(lock, op)) return true;
            }
            return false;
        });
    }

    /**
     * The ids of the lock groups on {@code ref}'s slot and on the stack in it,
     * each resolved (a lock whose group is gone counts as its kind's default).
     * Empty when nothing is locked.
     */
    public static List<String> on(SlotRef ref) {
        List<String> groups = new ArrayList<>(3);
        // The Slot-kind lock: stored, and implicit (a cycler's derived lock or
        // a companion's shared one, both Slot lock). Deny-overrides, so both
        // count when a stored custom group sits on a cycle slot.
        String stored = null;
        boolean implicit = false;
        if (ref.slot() != null) {
            stored = LockedSlots.storedGroup(ref.slot());
            implicit = LockedSlots.isImplicitlyLocked(ref.slot());
        } else if (ref.container() instanceof Inventory inv && isLocalPlayer(inv.player)) {
            // Off-menu (world pickup, no screen): the player's own slot by index.
            stored = LockedSlots.playerStoredGroup(ref.containerSlot());
            implicit = LockedSlots.isDerivedLocked(ref.containerSlot());
        }
        if (stored != null) groups.add(Reach.resolve(stored, LockKind.SLOT).id());
        if (implicit && !groups.contains(Reach.SLOT_LOCK)) groups.add(Reach.SLOT_LOCK);

        ItemStack stack = ref.container().getItem(ref.containerSlot());
        if (!stack.isEmpty()) {
            String item = LockedItems.itemGroup(stack);
            if (item != null) groups.add(Reach.resolve(item, LockKind.ITEM).id());
            String exact = LockedItems.exactGroup(stack);
            if (exact != null) groups.add(Reach.resolve(exact, LockKind.EXACT).id());
        }
        return groups;
    }

    /** True for the player this client is, on either side of a single-player game. */
    private static boolean isLocalPlayer(@Nullable Player player) {
        if (player == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && player.getUUID().equals(mc.player.getUUID());
    }
}
