package com.trevorschoeny.inventoryplus.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.state.SlotState;
import com.trevlar.slotkit.api.state.SlotStateChannel;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.Locale;

/**
 * A shulker box's Intake: whether pickups go into it while it is in the player's
 * inventory, and when. Set per box and stored on it, so it travels with the box.
 *
 * <ul>
 *   <li>{@link When#OFF}: never (the default).</li>
 *   <li>{@link When#BEFORE}: before the inventory; the inventory takes what the box doesn't.</li>
 *   <li>{@link When#AFTER}: only what the inventory has no room for.</li>
 * </ul>
 *
 * <p>{@code matchingOnly} keeps a box to the items it already holds (or has a slot
 * reserved for), so a cobblestone box takes cobblestone and a box with it off takes
 * anything. Pickups only: a shift-click or other move goes into a box only while its
 * slots are on screen. Automation ignores Intake; it honors reservations and locks.
 * Where pickups land is the server's, so Intake needs Inventory Plus on the server
 * ({@link com.trevorschoeny.inventoryplus.nested.CarriedPut}).
 */
public record Intake(When when, boolean matchingOnly) {

    public enum When { OFF, BEFORE, AFTER }

    public static final Intake OFF = new Intake(When.OFF, false);

    public Intake {
        // Off has no Matching only: one value for "no Intake", so writing it clears the box.
        if (when == When.OFF) matchingOnly = false;
    }

    private static final Codec<When> WHEN_CODEC = Codec.STRING.xmap(
            s -> When.valueOf(s.toUpperCase(Locale.ROOT)), w -> w.name().toLowerCase(Locale.ROOT));

    public static final Codec<Intake> CODEC = RecordCodecBuilder.create(i -> i.group(
            WHEN_CODEC.fieldOf("when").forGetter(Intake::when),
            Codec.BOOL.optionalFieldOf("matching_only", false).forGetter(Intake::matchingOnly)
    ).apply(i, Intake::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Intake> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(n -> When.values()[Math.floorMod(n, When.values().length)], When::ordinal),
            Intake::when,
            ByteBufCodecs.BOOL, Intake::matchingOnly,
            Intake::new);

    /**
     * Stored as SHARED slot state so it rides SlotKit's portable path: on the item, in
     * the placed box's block entity, back on the item when broken in survival. A plain
     * custom-data key would be dropped by the shulker box's loot table.
     * ponytail: a box-level value kept on slot 0's entry, since SlotKit stores per-slot
     * values only; a box-level SHARED value in SlotKit is the upgrade if a second
     * box-level setting ever appears.
     */
    private static final SlotStateChannel<Intake> CHANNEL = SlotState.register(ModeScope.BOTH,
            Identifier.fromNamespaceAndPath("inventoryplus", "intake"), CODEC, STREAM_CODEC, OFF,
            SlotStateChannel.Visibility.SHARED, SlotStateChannel.CanWrite.DENY);

    /** Whether {@code stack} is one shulker box, the only thing that has an Intake. */
    public static boolean isBox(ItemStack stack) {
        return stack.getCount() == 1 && stack.getItem() instanceof BlockItem block
                && block.getBlock() instanceof ShulkerBoxBlock;
    }

    /** {@code box}'s Intake; {@link #OFF} for anything that isn't a shulker box. Either side. */
    public static Intake of(ItemStack box) {
        return isBox(box) ? CHANNEL.get(box, 0) : OFF;
    }

    /** Sets {@code box}'s Intake. Server side; the caller puts the item back where it lives. */
    public static void set(ItemStack box, Intake intake) {
        if (isBox(box)) CHANNEL.set(box, 0, intake);
    }

    // The client's request

    /** Set the Intake of the shulker box in the player's inventory slot {@code slot} (0 to 35). */
    public record Set(int slot, Intake intake) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(Identifier.fromNamespaceAndPath("inventoryplus", "set_intake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Set> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Set::slot, STREAM_CODEC, Set::intake, Set::new);

        @Override
        public Type<Set> type() {
            return TYPE;
        }
    }

    /** Payload and receiver. Once, from the common initializer. */
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Set.TYPE, Set.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Set.TYPE,
                (payload, context) -> context.server().execute(() -> apply(context.player(), payload)));
    }

    /** A client's request: only a box in its own main inventory or hotbar. */
    public static void apply(ServerPlayer player, Set request) {
        if (request.slot() < 0 || request.slot() > 35 || player.isSpectator()) return;
        ItemStack box = player.getInventory().getItem(request.slot());
        if (!isBox(box)) return;
        set(box, request.intake());
        player.getInventory().setChanged();
    }
}
