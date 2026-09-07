package com.trevorschoeny.inventoryplus.api;

import com.trevorschoeny.inventoryplus.autorestock.AutoRestockSearch;
import com.trevorschoeny.inventoryplus.autorestock.AutoRestockSuppression;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.CycleHudRegistry;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItemUser;
import com.trevorschoeny.inventoryplus.lockedslots.LockedSlots;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The entry points Inventory Plus offers to a companion mod.
 *
 * <h2>Stability contract</h2>
 *
 * <p>This package ({@code com.trevorschoeny.inventoryplus.api}) is the whole
 * of Inventory Plus's public surface. <b>Nothing in it changes shape without a
 * major version bump.</b> Everything outside it is internal and may be
 * renamed, moved, resignatured or deleted in any release, including a patch.
 *
 * <p>The contract exists because it was broken. Inventory Max reached into 16
 * internal classes across 8 packages, so a routine change to
 * {@code AutoRestockSearch.findSource}'s signature in Inventory Plus 1.4.0
 * turned into a {@code NoSuchMethodError} in a published Inventory Max, which
 * had to be hotfixed. That is the failure this package is shaped to prevent:
 * an internal refactor should not be able to reach a companion mod.
 *
 * <h2>What is here, and what is deliberately not</h2>
 *
 * <p>The types a companion <em>implements</em> or <em>passes</em> live here as
 * real types: {@link HotbarCyclable}, {@link CycleHudSource},
 * {@link SlotLockProvider}, {@link CycleView}, {@link CyclerDirection},
 * {@link CyclerOperation}. The registries behind them stay internal, and this
 * class exposes only the operations a companion actually needs. That is
 * deliberate: a registry's other public methods are Inventory Plus talking to
 * itself, and promising them would freeze internals that have no business
 * being frozen.
 *
 * <p>Signatures here name only vanilla types and API types. A caller never has
 * to name an Inventory Plus internal to use this, which is what keeps the
 * boundary honest rather than nominal.
 *
 * <p>Client-side. Inventory Plus is a client-only mod; every method here is
 * safe to call from the render thread and meaningless off it.
 */
public final class InventoryPlusApi {

    private InventoryPlusApi() {}

    /** No source slot found. Returned by {@link #findRestockSource}. */
    public static final int NO_SOURCE = AutoRestockSearch.NONE;

    // ── Cyclers ─────────────────────────────────────────────────────────

    /**
     * Registers a cycler that contributes slots to the hotbar cycle, so
     * Inventory Plus's searches can see them and quick-move through them.
     */
    public static void registerCyclable(HotbarCyclable cyclable) {
        HotbarCyclableRegistry.register(cyclable);
    }

    /** Every extra searchable slot contributed by registered cyclers. */
    public static List<HotbarCyclable.ExtraSlot> extraSearchSlots(net.minecraft.world.entity.player.Player player) {
        return HotbarCyclableRegistry.extraSearchSlots(player);
    }

    /**
     * Asks the owning cycler to move the stack at {@code slot} out to the
     * player, for a slot id that came from {@link #extraSearchSlots} or
     * {@link #findRestockSource}. False when {@code slot} is an ordinary
     * inventory index the caller should handle itself.
     */
    public static boolean quickMoveOut(int slot) {
        return HotbarCyclableRegistry.quickMoveOut(slot);
    }

    /** Registers a source of cycle views for the shared cycler HUD. */
    public static void registerHudSource(CycleHudSource source) {
        CycleHudRegistry.register(source);
    }

    /** Plays the shared cycler HUD's slide animation for one of your cycles. */
    public static void playCycleAnimation(CycleHudSource source, int hotbarSlot, CyclerDirection direction) {
        CycleHudRegistry.fireCycleAnimation(source, hotbarSlot, direction);
    }

    // ── Locks ───────────────────────────────────────────────────────────

    /**
     * Registers a provider that answers lock state for slot kinds Inventory
     * Plus cannot reach on its own.
     */
    public static void registerSlotLockProvider(SlotLockProvider provider) {
        LockedSlots.registerProvider(provider);
    }

    // ── Auto-Restock ────────────────────────────────────────────────────

    /**
     * Finds a slot holding a stack that could restock {@code probe}, honouring
     * every rule Inventory Plus applies to its own restock: locked slots,
     * locked items, and the player's Use Locked Items setting. Returns
     * {@link #NO_SOURCE} when there is nothing suitable.
     *
     * <p>A returned index at or beyond the ordinary inventory range belongs to
     * a registered cycler; pass it to {@link #quickMoveOut}.
     *
     * <p>This is deliberately one method rather than a mirror of Inventory
     * Plus's internal search. The internal one takes a parameter naming which
     * of Inventory Plus's own features is asking, which is not a question a
     * companion can answer or should have to; adding that parameter in 1.4.0
     * is precisely what broke Inventory Max.
     */
    public static int findRestockSource(Inventory inventory, ItemStack probe,
                                        List<HotbarCyclable.ExtraSlot> extras) {
        return AutoRestockSearch.findSource(inventory, probe, AutoRestockSearch.NONE, extras,
                LockedItemUser.RESTOCK_ITEM);
    }

    /**
     * Tells Auto-Restock that a hotbar slot is being changed deliberately, so
     * the change is not mistaken for the held item running out. Call it
     * immediately before a swap you perform yourself.
     */
    public static void suppressRestockFor(int hotbarSlot) {
        AutoRestockSuppression.markExternalChange(hotbarSlot);
    }

    // ── Settings a companion should follow ──────────────────────────────

    /** Whether the player has Inventory Plus's item restock switched on. */
    public static boolean isItemRestockEnabled() {
        return IPConfig.autoRestockItem();
    }

    /** Whether the player has the Column Cycler switched on. */
    public static boolean isColumnCyclerEnabled() {
        return IPConfig.columnCyclerEnabled();
    }

    // ── Shared client state ─────────────────────────────────────────────

    /**
     * The block whose container the player currently has open, or null when
     * none is open or it cannot be identified.
     *
     * <p>Phrased as the question rather than the mechanism on purpose. It is
     * currently answered by watching the click that opened the container,
     * which has known gaps; if that is replaced by authoritative state from
     * the server, this signature does not change.
     */
    public static @Nullable Block openContainerBlock() {
        return ContainerOpenTracker.openContainerBlock();
    }

    /**
     * A stable id for the world or server the player is in, for keying
     * per-world client state. Null when not in a world.
     */
    public static @Nullable String worldId() {
        return WorldIdentity.current(Minecraft.getInstance());
    }
}
