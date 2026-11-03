package com.trevorschoeny.inventoryplus.rules;

import com.mojang.serialization.Codec;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.api.state.SlotStateChannel;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.SlotOperations;
import com.trevlar.slotkit.api.window.SlotRef;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slot locks, the half both sides share: what a lock refuses, and where it is stored
 * when it isn't the player's own.
 *
 * <h2>What a lock refuses</h2>
 *
 * <p>A locked slot takes only deliberate moves: a click, a drag, a number key or F, a
 * pocket move, and a shift-click out of it. It refuses everything that would fill or
 * empty it on its own: a shift-click into it, Sort, Move Matching, Collect, pickups,
 * dropping with Q, Restock taking from it, pick block, and automation (hoppers,
 * droppers, copper golems, through SlotKit's {@code AUTOMATION_PUT} and
 * {@code AUTOMATION_TAKE}). Inventory Plus's own row moves (the cyclers), Auto Tool
 * Switch and Restock refilling the slot still work, as a slot lock always allowed:
 * they keep the slot's own item where the player put it.
 *
 * <h2>Where a lock lives</h2>
 *
 * <ul>
 *   <li><b>The player's own slots</b> (inventory, ender chest, created slots such as
 *       pockets): in the client's config, so they work on any server. The client sends
 *       the inventory's and ender chest's as masks ({@link Masks}) to a server with
 *       Inventory Plus, which then holds them for pickups too.</li>
 *   <li><b>A placed container's slot, or a shulker box's</b>: SHARED slot state
 *       ({@link #CHANNEL}), so it holds for every player and for automation, and a box
 *       carries its locks wherever it goes. Needs Inventory Plus on the server.</li>
 * </ul>
 */
public final class SlotLocks {

    private SlotLocks() {}

    public static final Identifier ID = Identifier.fromNamespaceAndPath("inventoryplus", "lock");

    /**
     * A placed container's or a box's slot locks. Any player with the container open
     * may lock its slots, as anyone who can open a chest can empty it.
     */
    public static final SlotStateChannel<Boolean> CHANNEL = SlotState.register(ModeScope.BOTH, ID,
            Codec.BOOL, ByteBufCodecs.BOOL.cast(), false,
            SlotStateChannel.Visibility.SHARED, SlotStateChannel.CanWrite.ANY);

    /**
     * The operations a lock refuses, by id. Named as text so an operation of a mod that
     * isn't installed costs nothing. Anything not here gets through, as with every
     * lock since 1.5.0, so a new mod's deliberate move isn't refused by surprise.
     */
    private static final Set<String> REFUSED = Set.of(
            "menukit:shift_click_in", "menukit:collect", "menukit:world_pickup",
            "menukit:drop", "menukit:drop_stack",
            "menukit:automation_put", "menukit:automation_take",
            "inventoryplus:sort", "inventoryplus:move_matching_out", "inventoryplus:move_matching_in",
            "inventoryplus:restock_take", "inventoryplus:pick_block");

    /** Whether a lock refuses {@code operation}. */
    public static boolean refuses(BehaviorKey<?> operation) {
        return REFUSED.contains(operation.id().toString());
    }

    /**
     * Whether {@code ref}'s slot carries a SHARED lock: a placed container's slot, or one
     * inside a box. The player's own slots never do; their locks are the client's.
     */
    public static boolean sharedLocked(SlotRef ref) {
        if (ref.container() instanceof Inventory || ref.container() instanceof PlayerEnderChestContainer) return false;
        return CHANNEL.get(ref);
    }

    // The player's own locks, as the server knows them

    /**
     * The locks on a player's own slots, as their client last sent them: bit {@code i}
     * of {@code inventory} for inventory slot {@code i} (0 to 40), bit {@code i} of
     * {@code ender} for ender chest slot {@code i}. A player who never sent one (no
     * Inventory Plus on their client) has no locks here and no Intake.
     */
    public record Masks(long inventory, int ender) implements CustomPacketPayload {
        public static final Type<Masks> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "player_locks"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Masks> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Masks::inventory, ByteBufCodecs.VAR_INT, Masks::ender, Masks::new);

        @Override
        public Type<Masks> type() {
            return TYPE;
        }

        public boolean inventory(int slot) {
            return slot >= 0 && slot < 64 && (inventory >> slot & 1) != 0;
        }

        public boolean ender(int slot) {
            return slot >= 0 && slot < 32 && (ender >> slot & 1) != 0;
        }
    }

    private static final Map<UUID, Masks> MASKS = new ConcurrentHashMap<>();

    /** Whether {@code player}'s client has Inventory Plus: it sent its locks. */
    public static boolean known(ServerPlayer player) {
        return MASKS.containsKey(player.getUUID());
    }

    /** For the self-checks: set a player's locks as if their client had sent them. */
    public static void setMasks(UUID player, Masks masks) {
        MASKS.put(player, masks);
    }

    /** Payload, receiver and the one veto. Once, from the common initializer. */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Masks.TYPE, Masks.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Masks.TYPE,
                (payload, context) -> MASKS.put(context.player().getUUID(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MASKS.remove(handler.getPlayer().getUUID()));
        // Both modes, every side. On a client this judges what the client can see (a
        // chest's synced locks); the client's own slots are judged by its own veto.
        SlotOperations.veto(ModeScope.BOTH,
                (ref, group, operation) -> refuses(operation) && (sharedLocked(ref) || serverPlayerLocked(ref)));
    }

    /** A server-side player's own slot, locked by the masks their client sent. */
    private static boolean serverPlayerLocked(SlotRef ref) {
        if (ref.container() instanceof Inventory inv && inv.player instanceof ServerPlayer player) {
            Masks m = MASKS.get(player.getUUID());
            return m != null && m.inventory(ref.containerSlot());
        }
        if (ref.container() instanceof PlayerEnderChestContainer && ref.player() instanceof ServerPlayer player) {
            Masks m = MASKS.get(player.getUUID());
            return m != null && m.ender(ref.containerSlot());
        }
        return false;
    }
}
