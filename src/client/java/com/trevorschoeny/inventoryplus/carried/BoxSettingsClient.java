package com.trevorschoeny.inventoryplus.carried;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;
import com.trevorschoeny.inventoryplus.nested.CarriedPut;
import com.trevorschoeny.inventoryplus.rules.BoxSettings;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * The client's half of {@link BoxSettings}: reading a carried box's settings, asking the
 * server to change them, sending the player's "Fill shulker boxes with pickups" setting,
 * and the one-time migration of Reach's pickup choice onto the boxes the player carries.
 *
 * <p>A box's settings are stored on it by the server, so setting them needs Inventory
 * Plus on the server ({@link #available}).
 */
public final class BoxSettingsClient {

    private BoxSettingsClient() {}

    /** The fill setting the server last heard; null after joining, so the first tick sends it. */
    private static @Nullable Boolean sentFill;

    public static void register() {
        // Once a second: a world's first look, after the server has answered the join.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player != null && mc.player.tickCount % 20 == 0) migrate(mc);
            if (mc.player != null) syncFill();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> sentFill = null);
    }

    /** Tells the server the fill setting when it changed, on a server that hears it. */
    private static void syncFill() {
        boolean on = IPConfig.fillShulkersWithPickups();
        if (Boolean.valueOf(on).equals(sentFill) || !ClientPlayNetworking.canSend(CarriedPut.Fill.TYPE)) return;
        ClientPlayNetworking.send(new CarriedPut.Fill(on));
        sentFill = on;
    }

    /**
     * Asks the server to set the settings of the box whose slots the open menu shows,
     * {@code menuSlot} being one of them. False when it can't (no server mod).
     */
    public static boolean setOpen(AbstractContainerMenu menu, int menuSlot, BoxSettings settings) {
        if (!available()) return false;
        ClientPlayNetworking.send(new BoxSettings.SetOpen(menu.containerId, menuSlot, settings));
        return true;
    }

    /** Whether this world's server applies Intake: Inventory Plus is on it. */
    public static boolean available() {
        return ClientPlayNetworking.canSend(BoxSettings.Set.TYPE);
    }

    /** The BoxSettings of what sits in inventory slot {@code slot}: {@link Intake#OFF} for anything but a shulker box. */
    public static BoxSettings of(LocalPlayer player, int slot) {
        return BoxSettings.of(player.getInventory().getItem(slot));
    }

    /**
     * Asks the server to set the Intake of the shulker box in inventory slot
     * {@code slot} (0 to 35). Returns false when it can't: no box there, or a server
     * without Inventory Plus. The box shows the new Intake once the server has set it.
     */
    public static boolean set(LocalPlayer player, int slot, BoxSettings intake) {
        if (slot < 0 || slot > 35 || !available() || !BoxSettings.isBox(player.getInventory().getItem(slot))) return false;
        ClientPlayNetworking.send(new BoxSettings.Set(slot, intake));
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
        BoxSettings legacy = IPConfig.legacyPickupIntake();
        if (legacy == null || !available()) return;
        String world = WorldIdentity.current(mc);
        if (world == null || !IPConfig.needsIntakeMigration(world)) return;
        int n = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (BoxSettings.isBox(stack) && BoxSettings.of(stack).when() == BoxSettings.When.OFF
                    && set(mc.player, slot, legacy.withRestock(BoxSettings.of(stack).restock()))) n++;
        }
        IPConfig.markIntakeMigrated(world);
        InventoryPlusClient.LOGGER.info("[intake] {}: Reach's pickup choice set {} on {} carried box(es)", world, legacy, n);
    }
}
