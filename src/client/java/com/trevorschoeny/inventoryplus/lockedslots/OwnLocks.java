package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.rules.SlotLocks;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

/**
 * The player's own locks ({@link LockedSlots}, in config): the veto that holds them on
 * this client, and the masks that tell a server with Inventory Plus about them, so it
 * holds them for pickups too ({@link SlotLocks.Masks}). What a lock refuses is
 * {@link SlotLocks#refuses}, the same on both sides.
 *
 * <p>The veto judges only the local player, matched by UUID so the integrated server's
 * copy of that player counts too. With no acting player (a hopper), or another player
 * (a LAN guest of this host), it has no opinion: these locks are this player's alone.
 * The locks switch pauses them, and the masks sent say none while paused.
 */
public final class OwnLocks {

    private OwnLocks() {}

    private static @Nullable SlotLocks.Masks sent;

    /** Once, at client init. */
    public static void register() {
        SlotOperations.veto(ModeScope.BOTH, (ref, group, operation) ->
                IPConfig.locksEnabled() && SlotLocks.refuses(operation) && isLocalPlayer(ref.player()) && locked(ref));
        // Twice a second, and again on joining: only what changed is sent.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.player.tickCount % 10 == 0) sync();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> sent = null);
    }

    /** Sends the masks now if they changed, to a server that hears them. */
    public static void sync() {
        if (!ClientPlayNetworking.canSend(SlotLocks.Masks.TYPE)) return;
        SlotLocks.Masks now = masks();
        if (now.equals(sent)) return;
        ClientPlayNetworking.send(now);
        sent = now;
    }

    /** The player's own locks as masks, none while paused. */
    public static SlotLocks.Masks masks() {
        if (!IPConfig.locksEnabled()) return new SlotLocks.Masks(0, 0);
        long inventory = 0;
        for (int i = 0; i <= LockedSlots.MAX_PLAYER_CONTAINER_SLOT; i++) if (LockedSlots.isPlayerLocked(i)) inventory |= 1L << i;
        int ender = 0;
        for (int i = 0; i < LockedSlots.ENDER_SLOT_COUNT; i++) if (LockedSlots.isEnderLocked(i)) ender |= 1 << i;
        return new SlotLocks.Masks(inventory, ender);
    }

    private static boolean locked(SlotRef ref) {
        if (ref.slot() != null) return LockedSlots.isOwnLocked(ref.slot());
        // Off-menu (a pickup, no screen): the player's own slot by index.
        return ref.container() instanceof Inventory inv && isLocalPlayer(inv.player)
                && LockedSlots.isPlayerLocked(ref.containerSlot());
    }

    /** True for the player this client is, on either side of a single-player game. */
    private static boolean isLocalPlayer(@Nullable Player player) {
        if (player == null) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.player != null && player.getUUID().equals(mc.player.getUUID());
    }
}
