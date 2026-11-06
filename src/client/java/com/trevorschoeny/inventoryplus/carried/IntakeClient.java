package com.trevorschoeny.inventoryplus.carried;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;
import com.trevorschoeny.inventoryplus.rules.Intake;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The client's half of {@link Intake}: reading a carried box's Intake, asking the
 * server to change it, and the one-time migration of Reach's pickup choice onto the
 * boxes the player carries.
 *
 * <p>Intake is the server's to apply (pickups land where the server puts them), so
 * setting one needs Inventory Plus on the server ({@link #available}).
 */
public final class IntakeClient {

    private IntakeClient() {}

    public static void register() {
        // Once a second: a world's first look, after the server has answered the join.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.player.tickCount % 20 == 0) migrate(mc);
        });
    }

    /** Whether this world's server applies Intake: Inventory Plus is on it. */
    public static boolean available() {
        return ClientPlayNetworking.canSend(Intake.Set.TYPE);
    }

    /** The Intake of what sits in inventory slot {@code slot}: {@link Intake#OFF} for anything but a shulker box. */
    public static Intake of(LocalPlayer player, int slot) {
        return Intake.of(player.getInventory().getItem(slot));
    }

    /**
     * Asks the server to set the Intake of the shulker box in inventory slot
     * {@code slot} (0 to 35). Returns false when it can't: no box there, or a server
     * without Inventory Plus. The box shows the new Intake once the server has set it.
     */
    public static boolean set(LocalPlayer player, int slot, Intake intake) {
        if (slot < 0 || slot > 35 || !available() || !Intake.isBox(player.getInventory().getItem(slot))) return false;
        ClientPlayNetworking.send(new Intake.Set(slot, intake));
        return true;
    }

    /**
     * Reach's migration: before 4.0.0, pickups put into carried shulker boxes by the
     * player's Reach choice, one choice for every box. That choice becomes the Intake
     * of each box carried the first time a world is joined with Inventory Plus on its
     * server; a box that already has an Intake keeps it. Boxes not carried then start
     * Off, as every new box does.
     */
    private static void migrate(Minecraft mc) {
        Intake legacy = IPConfig.legacyPickupIntake();
        if (legacy == null || !available()) return;
        String world = WorldIdentity.current(mc);
        if (world == null || !IPConfig.needsIntakeMigration(world)) return;
        int n = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (Intake.isBox(stack) && Intake.of(stack).when() == Intake.When.OFF && set(mc.player, slot, legacy)) n++;
        }
        IPConfig.markIntakeMigrated(world);
        InventoryPlusClient.LOGGER.info("[intake] {}: Reach's pickup choice set {} on {} carried box(es)", world, legacy, n);
    }
}
