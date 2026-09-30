package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import com.trevlar.menukit.api.slot.SlotGroupId;
import com.trevlar.menukit.api.window.Address;
import com.trevlar.menukit.api.window.SlotRef;
import com.trevlar.menukit.api.window.KindTag;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * A persistable identity for a <b>created</b> slot (a MenuKit: Containers slot
 * such as an Inventory Max pocket or equipment slot): its MenuKit
 * {@link Address}, saved as {@link Address#asString()}.
 *
 * <h3>Why created slots need their own key</h3>
 *
 * <p>Locked Slots keys player locks by vanilla container-slot index. A created
 * slot's {@code getContainerSlot()} is an index into <em>its own</em> backing
 * storage, so it would collide with a vanilla index. Its stable identity is its
 * address: the panel and group it was declared in and its index there, never
 * its position in a menu (MenuKit 6.0.0 can change that).
 *
 * <h3>The saved form</h3>
 *
 * <p>MenuKit's own text form ({@code Address.asString}, 6.0.0), which MenuKit
 * keeps stable for exactly this. Before 6.0.0 there was no codec, and this
 * class wrote its own, {@code created:1:<family>:<scope>:<panel>:<decl>};
 * {@link #migrate} turns those into the codec's text on load, and the next save
 * writes only the new form.
 *
 * <h3>Thread</h3>
 *
 * <p>MenuKit: Containers' address rule dispatches a created slot by type and
 * never reads the menu on that branch, so a null menu is safe for the only case
 * this acts on, including on the integrated server thread.
 */
public final class CreatedSlotKey {

    private CreatedSlotKey() {}

    /** The pre-6.0.0 form this class wrote itself. Read on load, never written. */
    private static final String OLD_PREFIX = "created:1:";

    /**
     * The key for {@code slot} if it is a created slot, else null. Null also for
     * any slot whose address cannot be minted, so an unexpected MenuKit change
     * fails closed (unlockable) rather than keying garbage into the store.
     */
    public static @Nullable String of(Slot slot) {
        // The slot comes from the open screen, so it is on the player's open menu.
        // A created slot's address does not depend on the menu it sits in.
        Player player = Minecraft.getInstance().player;
        if (player == null) return null;
        Address address;
        try {
            address = SlotRef.of(player.containerMenu, slot, player).address();
        } catch (RuntimeException e) {
            return null;
        }
        if (address == null || address.kind() != KindTag.CREATED_SLOT) return null;
        return address.asString();
    }

    /**
     * A key as loaded: the codec's text unchanged, a pre-6.0.0 key converted to
     * it. The old key held the same parts the address does: the panel family
     * ({@code menukit:panel}), the scope (always {@code primary} for a created
     * slot), the panel id, and a declaration id of {@code groupId}, a NUL, and
     * the index. A key that does not read that way is kept as it was: it
     * matches no slot, so it locks nothing, and it is never lost.
     */
    static String migrate(String key) {
        if (!key.startsWith(OLD_PREFIX)) return key;
        // famNs : famPath : scope : panelNs : panelPath : declId (the rest)
        String[] p = key.substring(OLD_PREFIX.length()).split(":", 6);
        if (p.length == 6 && p[0].equals("menukit") && p[1].equals("panel") && p[2].equals("primary")) {
            int nul = p[5].lastIndexOf('\0');
            if (nul > 0) {
                try {
                    int index = Integer.parseInt(p[5].substring(nul + 1));
                    SlotGroupId.Created group = SlotGroupId.created(p[3] + ":" + p[4], p[5].substring(0, nul));
                    return Address.createdSlot(group, index).asString();
                } catch (RuntimeException ignored) {
                    // falls through to the warning below
                }
            }
        }
        InventoryPlusClient.LOGGER.warn("[locked-slots] kept a created-slot key it could not read: {}", key);
        return key;
    }
}
