package com.trevorschoeny.inventoryplus.lockedslots;

import com.trevlar.menukit.window.Address;
import com.trevlar.menukit.window.ClientSlotAddressing;
import com.trevlar.menukit.window.KindTag;
import com.trevlar.menukit.window.OwnerRef;
import com.trevlar.menukit.window.OwnerScope;
import com.trevlar.menukit.window.Token;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

/**
 * A persistable identity for a <b>created</b> slot (a MenuKit: Containers slot
 * such as an Inventory Max pocket or equipment slot), derived from MenuKit's
 * {@link Address} and reduced to one opaque string.
 *
 * <h3>Why this class exists at all</h3>
 *
 * <p>Locked Slots keys player locks by vanilla container-slot index. A created
 * slot's {@code getContainerSlot()} is an index into <em>its own</em> backing
 * storage, so it would collide with a vanilla index. The stable identity of a
 * created slot is its MenuKit address: "menu-independent, identified purely by
 * its panel + declaration id" ({@code CreatedSlotAdapter}). That is what we key
 * on.
 *
 * <h3>Why the encoding is ours, and marked as interim</h3>
 *
 * <p>MenuKit 4.0.0 offers no consumer-facing way to <em>remember</em> a created
 * slot: {@link ClientSlotAddressing} is {@code @ApiStatus.Internal}, and
 * {@link Address} has no codec. IP cannot use MKC's own per-slot state channel
 * either, being MK-only by build rule. So this class flattens the five address
 * leaves itself, behind one method, so a future MenuKit codec can replace it
 * with a one-line change here and a load-time migration. Reported to MenuKit
 * 2026-09-07 as the persistence half of the substitutability gap.
 *
 * <p>The string is opaque. Nothing parses it back; equality on the string is
 * the identity. The leading {@code created:1:} is a format version so a later
 * encoding can be told apart from this one on load.
 *
 * <h3>Thread</h3>
 *
 * <p>Client only. MKC's installed address rule dispatches created slots by type
 * and never touches the menu on that branch, so a null menu is safe for the
 * only case we act on. We still resolve the live menu when one is open, so a
 * vanilla slot that somehow reaches here gets a real address rather than a
 * null dereference.
 */
public final class CreatedSlotKey {

    private CreatedSlotKey() {}

    /** Format version. Bump if the layout of the string changes. */
    private static final String PREFIX = "created:1:";

    /**
     * The key for {@code slot} if it is a created slot, else null. Null also
     * for any address whose owner is not the created-slot shape, so an
     * unexpected MenuKit change fails closed (unlockable) rather than keying
     * garbage into the store.
     */
    public static @Nullable String of(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        AbstractContainerMenu menu = mc != null && mc.player != null ? mc.player.containerMenu : null;
        Address address;
        try {
            address = ClientSlotAddressing.addressOf(menu, slot);
        } catch (RuntimeException e) {
            return null; // a vanilla slot with no menu to fall back on; not ours anyway
        }
        if (address == null || address.kind() != KindTag.CREATED_SLOT) return null;

        // created slot : NestedOwner(RootOwner(family, scope), RegToken(panel)) + DeclToken(id)
        if (!(address.owner() instanceof OwnerRef.NestedOwner nested)) return null;
        if (!(nested.parent() instanceof OwnerRef.RootOwner root)) return null;
        if (!(nested.parentToken() instanceof Token.RegToken panel)) return null;
        if (!(address.token() instanceof Token.DeclToken decl)) return null;

        return PREFIX
                + root.family().id() + ':'
                + scopeOf(root.scope()) + ':'
                + panel.regKey() + ':'
                + decl.declId();
    }

    private static String scopeOf(OwnerScope scope) {
        if (scope instanceof OwnerScope.Tab tab) return "tab=" + tab.tabId();
        if (scope instanceof OwnerScope.Sub sub) return "sub=" + sub.backingId();
        return "primary";
    }
}
