package com.trevorschoeny.inventoryplus.checks;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.mixin.AbstractContainerScreenAccessor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** What the carried-container checks share: running on the server, and building and reading containers. */
final class CarriedItems {

    private CarriedItems() {}

    /**
     * Locks or unlocks the slot under the mouse. Directly, not through edit mode: the mode is
     * the inventory screen's only for now, and these checks lock containers' slots too.
     */
    static void editLock(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            Slot hovered = ((AbstractContainerScreenAccessor) mc.gui.screen()).inventoryPlus$getHoveredSlot();
            if (hovered == null) throw new AssertionError("no slot under the mouse to lock");
            LockedSlots.toggle(hovered);
        });
        ctx.waitTicks(5);
    }

    static void onServer(TestSingleplayerContext world, Consumer<ServerPlayer> action) {
        world.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().get(0)));
    }

    static net.minecraft.world.inventory.Slot menuSlot(net.minecraft.world.entity.player.Player player, int containerSlot) {
        return player.inventoryMenu.getSlot(PlayerMenuSlots.menuIndexOf(player.inventoryMenu, player, containerSlot));
    }

    // Building and reading items (as SlotKit's ItemSourcesChecks)

    static ItemStack shulker(Object... slotsAndStacks) {
        ItemStack[] items = new ItemStack[27];
        Arrays.fill(items, ItemStack.EMPTY);
        for (int i = 0; i < slotsAndStacks.length; i += 2) items[(int) slotsAndStacks[i]] = (ItemStack) slotsAndStacks[i + 1];
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(items)));
        return box;
    }

    static ItemStack bundle(ItemStack... items) {
        ItemStack b = new ItemStack(Items.BUNDLE);
        b.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(Arrays.stream(items).map(ItemStackTemplate::fromNonEmptyStack).toList()));
        return b;
    }

    static List<ItemStack> contents(ItemStack shulker) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < 27; i++) out.add(ItemStack.EMPTY);
        ItemContainerContents c = shulker.get(DataComponents.CONTAINER);
        List<ItemStack> read = c == null ? List.of() : c.itemCopies().toList();
        for (int i = 0; i < read.size(); i++) out.set(i, read.get(i));
        return out;
    }

    static List<ItemStack> bundleItems(ItemStack bundle) {
        BundleContents c = bundle.get(DataComponents.BUNDLE_CONTENTS);
        return c == null ? List.of() : c.itemCopies().toList();
    }

    static void clear(ServerPlayer p) {
        p.getInventory().clearContent();
        p.getEnderChestInventory().clearContent();
    }


    /** How many of {@code item} the player's hotbar and main inventory hold. */
    static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }
}
