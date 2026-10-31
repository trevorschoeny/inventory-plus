package com.trevorschoeny.inventoryplus.carried;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.nested.CarriedPut;
import com.trevorschoeny.inventoryplus.nested.CarriedPut.Move;
import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import com.trevlar.slotkit.api.item.ItemScope;
import com.trevlar.slotkit.api.item.ItemSources;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's half of putting into carried containers ({@link CarriedPut}): it tells
 * the server the player's choices, and runs Move Matching in's first pass.
 *
 * <p>The choices are this client's settings, Reach and locks, so they're read here and
 * sent whenever they change (checked twice a second, and again on joining).
 */
public final class CarriedPutClient {

    private CarriedPutClient() {}

    private static @Nullable CarriedPut.Prefs sent;
    private static int seq;
    private static final Map<Integer, Runnable> PENDING = new HashMap<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.player.tickCount % 10 == 0) sync(mc.player);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> sent = null);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> PENDING.clear());
        ClientPlayNetworking.registerGlobalReceiver(CarriedPut.Done.TYPE, (payload, context) -> {
            Runnable then = PENDING.remove(payload.seq());
            if (then != null) then.run();
        });
    }

    /**
     * Whether this world's server can put into carried containers: always, outside a
     * world (the settings are the player's own), and only with Inventory Plus on the
     * server inside one.
     */
    public static boolean available() {
        return Minecraft.getInstance().getConnection() == null || CarriedFetch.serverHelps(CarriedPut.Prefs.TYPE);
    }

    /** Whether {@code move} may put into the place: its three Reach boxes. */
    private static boolean place(Move move, int place) {
        return switch (move) {
            case PICKUP -> place == CarriedPut.SHULKERS ? IPConfig.pickupIntoShulkers()
                    : place == CarriedPut.BUNDLES ? IPConfig.pickupIntoBundles() : IPConfig.pickupIntoEnderChest();
            case SHIFT_CLICK -> place == CarriedPut.SHULKERS ? IPConfig.shiftClickIntoShulkers()
                    : place == CarriedPut.BUNDLES ? IPConfig.shiftClickIntoBundles() : IPConfig.shiftClickIntoEnderChest();
            case MOVE_MATCHING -> place == CarriedPut.SHULKERS ? IPConfig.moveMatchingIntoShulkers()
                    : place == CarriedPut.BUNDLES ? IPConfig.moveMatchingIntoBundles() : IPConfig.moveMatchingIntoEnderChest();
        };
    }

    private static void sync(LocalPlayer player) {
        if (!CarriedFetch.serverHelps(CarriedPut.Prefs.TYPE)) return;
        int places = 0;
        List<Long> slots = new ArrayList<>(Move.values().length);
        for (Move move : Move.values()) {
            for (int p = 0; p < 3; p++) if (place(move, p)) places |= 1 << (move.ordinal() * 3 + p);
            // A slot the move may not put into keeps the container in it closed too.
            long mask = 0;
            for (int i = 0; i <= 40; i++) if (IPSlotOperations.allowsPlayerSlot(player, i, move.operation)) mask |= 1L << i;
            slots.add(mask);
        }
        int enderLocked = 0;
        for (int i = 0; i < LockedSlots.ENDER_SLOT_COUNT; i++) if (LockedSlots.isEnderLocked(i)) enderLocked |= 1 << i;
        CarriedPut.Prefs now = new CarriedPut.Prefs(places, slots, enderLocked);
        if (now.equals(sent)) return;
        ClientPlayNetworking.send(now);
        sent = now;
    }

    /** Whether Move Matching in puts into carried containers in this world. */
    public static boolean moveMatchingInto() {
        if (!CarriedFetch.serverHelps(CarriedPut.MoveIn.TYPE)) return false;
        for (int p = 0; p < 3; p++) if (place(Move.MOVE_MATCHING, p)) return true;
        return false;
    }

    /**
     * The item kinds in the carried shulkers and bundles Move Matching in may fill: they
     * count as already in the inventory for its match set. (The ender chest's contents
     * aren't known to the client.)
     */
    public static Set<Item> moveMatchingKinds(LocalPlayer player) {
        Set<Item> out = new HashSet<>();
        boolean shulkers = place(Move.MOVE_MATCHING, CarriedPut.SHULKERS);
        boolean bundles = place(Move.MOVE_MATCHING, CarriedPut.BUNDLES);
        if (!shulkers && !bundles) return out;
        ItemScope scope = ItemScope.inventory().shulkers(shulkers).bundles(bundles);
        for (ItemSources.Found f : ItemSources.find(player, scope, s -> true)) {
            if (f.path().depth() > 0 && IPSlotOperations.allowsPlayerSlot(player, f.path().slot(), Move.MOVE_MATCHING.operation)) {
                out.add(f.stack().getItem());
            }
        }
        return out;
    }

    /** Sends Move Matching in's first pass, and runs {@code then} once the server has answered. */
    public static void moveIn(int containerId, List<CarriedPut.Source> sources, Runnable then) {
        // The server judges the request by the choices it has, so make them current first.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) sync(player);
        int id = ++seq;
        PENDING.put(id, then);
        ClientPlayNetworking.send(new CarriedPut.MoveIn(containerId, sources, id));
    }
}
