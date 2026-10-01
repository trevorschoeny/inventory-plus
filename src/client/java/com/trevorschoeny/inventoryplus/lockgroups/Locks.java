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
 * container store, or a companion's shared container lock, judged as Slot
 * lock), the Item and Exact locks matching the stack in it, and the feature
 * reach groups the slot is in now (a cycle slot reports Column Cycler by
 * name, not an implicit Slot lock; Trev, 2026-09-30). It replaces {@code isLocked(int)}, {@code isLockedSlot(Slot)},
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
            boolean denied = denies(ref, group, operation);
            if (denied && PROBED.contains(operation.id().toString())) probe(ref, group, operation);
            return denied;
        });
    }

    // ponytail: [reach-probe] diagnostic for the rare "can't place, reopening
    // fixes it" report (Designer brief 2026-09-30). Logs every player gesture
    // this veto refuses, on whichever thread asks, so a client refusal the
    // server would allow (or the reverse) shows as a line on one side only.
    // Remove once the cause is found.
    private static final java.util.Set<String> PROBED = java.util.Set.of(
            "menukit:click_put", "menukit:click_take", "menukit:drag_fill",
            "menukit:hotbar_swap", "menukit:offhand_swap", "menukit:shift_click_out");

    private static void probe(SlotRef ref, @Nullable com.trevlar.menukit.api.slot.SlotGroupId group,
                              com.trevlar.menukit.api.window.BehaviorKey<?> operation) {
        com.trevorschoeny.inventoryplus.InventoryPlusClient.LOGGER.info(
                "[reach-probe] DENY thread={} op={} menu={} slot={} containerSlot={} container={} group={} on={} key={}",
                Thread.currentThread().getName(), operation.id(),
                ref.menu() == null ? "-" : ref.menu().containerId,
                ref.slot() == null ? "-" : ref.slot().index, ref.containerSlot(),
                ref.container().getClass().getSimpleName(), group, on(ref),
                ref.slot() == null ? "-" : LockedSlots.probeContainerKey(ref.slot()));
    }

    private static boolean denies(SlotRef ref, @Nullable com.trevlar.menukit.api.slot.SlotGroupId group,
                                  com.trevlar.menukit.api.window.BehaviorKey<?> operation) {
        {
            if (!isLocalPlayer(ref.player())) return false;
            String op = operation.id().toString();
            if (group != null && Reach.denies(op, group.asString())) return true;
            List<String> on = on(ref);
            // Feature reach groups are reach, like slot groups: the Lock groups
            // switch does not pause them.
            for (String key : on) {
                if (Reach.isFeatureKey(key) && Reach.featureDenies(key, op)) return true;
            }
            // The Lock groups switch pauses every lock without deleting any;
            // the slot groups' own reach still holds.
            if (!IPConfig.lockGroupsEnabled()) return false;
            for (String lock : on) {
                if (!Reach.isFeatureKey(lock) && Reach.lockDenies(lock, op)) return true;
            }
            return false;
        }
    }

    /**
     * The ids of the lock groups on {@code ref}'s slot and on the stack in it,
     * each resolved (a lock whose group is gone counts as its kind's default),
     * then the keys of the feature reach groups the slot is in
     * ({@link Reach#isFeatureKey}). Empty when nothing applies.
     */
    public static List<String> on(SlotRef ref) {
        List<String> groups = new ArrayList<>(3);
        // The Slot-kind lock: stored, or a companion's shared one (Slot lock).
        // Deny-overrides, so both count when they sit on one slot.
        String stored = null;
        boolean implicit = false;
        List<String> features = List.of();
        if (ref.slot() != null) {
            stored = LockedSlots.storedGroup(ref.slot());
            implicit = LockedSlots.isImplicitlyLocked(ref.slot());
            if (LockedSlots.isLockable(ref.slot())) features = Reach.featuresOf(ref.slot().getContainerSlot());
        } else if (ref.container() instanceof Inventory inv && isLocalPlayer(inv.player)) {
            // Off-menu (world pickup, no screen): the player's own slot by index.
            stored = LockedSlots.playerStoredGroup(ref.containerSlot());
            features = Reach.featuresOf(ref.containerSlot());
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
        groups.addAll(features);
        return groups;
    }

    /** True for the player this client is, on either side of a single-player game. */
    private static boolean isLocalPlayer(@Nullable Player player) {
        if (player == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && player.getUUID().equals(mc.player.getUUID());
    }
}
