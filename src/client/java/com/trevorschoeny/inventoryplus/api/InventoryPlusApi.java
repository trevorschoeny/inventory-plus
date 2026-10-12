package com.trevorschoeny.inventoryplus.api;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.window.BehaviorKey;
import com.trevlar.slotkit.api.window.TriBool;

import com.trevorschoeny.inventoryplus.autorestock.AutoRestockSearch;
import com.trevorschoeny.inventoryplus.autorestock.AutoRestockSuppression;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.CycleHudRegistry;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;
import com.trevorschoeny.inventoryplus.lockedslots.WorldIdentity;
import com.trevorschoeny.inventoryplus.sort.ContainerOpenTracker;
import com.trevorschoeny.inventoryplus.toolbar.FeatureToggleButtons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
 * {@link CycleView}, {@link CyclerDirection},
 * {@link CyclerOperation}. The registries behind them stay internal, and this
 * class exposes only the operations a companion actually needs. A registry's
 * other public methods are Inventory Plus talking to
 * itself, and promising them would freeze internals that have no business
 * being frozen.
 *
 * <p>Signatures here name only vanilla types, API types, and MenuKit's public
 * types, which a companion already depends on. A caller never has to name an
 * Inventory Plus internal to use this. The slot operation keys Inventory Plus performs are in
 * {@link InventoryPlusOperations}.
 *
 * <p>Client-side. Every method here is safe to call from the render thread
 * and meaningless off it.
 */
public final class InventoryPlusApi {

    private InventoryPlusApi() {}

    /** No source slot found. Returned by {@link #findRestockSource}. */
    public static final int NO_SOURCE = AutoRestockSearch.NONE;

    // Cyclers

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

    /**
     * {@link #quickMoveOut(int)} for a move that serves {@code take} on the slot
     * it empties and {@code put} where the item lands, so the move is judged as
     * that operation rather than as the cycler's own. A restock passes
     * {@link InventoryPlusOperations#RESTOCK_TAKE} and
     * {@link InventoryPlusOperations#RESTOCK_PUT}.
     */
    public static boolean quickMoveOut(int slot, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return HotbarCyclableRegistry.quickMoveOut(slot, take, put);
    }

    /**
     * {@link #extraSearchSlots}, keeping only the slots whose cycler would let
     * a quick-move out serving {@code take}/{@code put} use them. Pass this to
     * {@link #findRestockSource} when the chosen source will go through
     * {@link #quickMoveOut(int, BehaviorKey, BehaviorKey)}, so a slot that would
     * refuse is never chosen and the search moves on to the next candidate.
     */
    public static List<HotbarCyclable.ExtraSlot> extraSlotsToQuickMove(
            net.minecraft.world.entity.player.Player player, BehaviorKey<TriBool> take, BehaviorKey<TriBool> put) {
        return HotbarCyclableRegistry.extraSlotsToQuickMove(player, take, put);
    }

    /** Registers a source of cycle views for the shared cycler HUD. */
    public static void registerHudSource(CycleHudSource source) {
        CycleHudRegistry.register(source);
    }

    /** Plays the shared cycler HUD's slide animation for one of your cycles. */
    public static void playCycleAnimation(CycleHudSource source, int hotbarSlot, CyclerDirection direction) {
        CycleHudRegistry.fireCycleAnimation(source, hotbarSlot, direction);
    }

    // Restock

    /**
     * Finds a slot holding a stack that could restock {@code probe}, honouring
     * every rule Inventory Plus applies to its own restock: locked slots,
     * locked items, and the player's Use Locked Items setting. Returns
     * {@link #NO_SOURCE} when there is nothing suitable.
     *
     * <p>A returned index at or beyond the ordinary inventory range belongs to
     * a registered cycler; pass it to {@link #quickMoveOut}.
     *
     * <p>This is one method rather than a mirror of Inventory
     * Plus's internal search. The internal one takes a parameter naming which
     * of Inventory Plus's own features is asking, which is not a question a
     * companion can answer or should have to. Adding that parameter in 1.4.0
     * is what broke Inventory Max.
     */
    public static int findRestockSource(Inventory inventory, ItemStack probe,
                                        List<HotbarCyclable.ExtraSlot> extras) {
        return AutoRestockSearch.findSource(inventory, probe, AutoRestockSearch.NONE, extras);
    }

    /**
     * Tells Restock that a hotbar slot is being changed deliberately, so
     * the change is not mistaken for the held item running out. Call it
     * immediately before a swap you perform yourself.
     */
    public static void suppressRestockFor(int hotbarSlot) {
        AutoRestockSuppression.markExternalChange(hotbarSlot);
    }

    // Settings a companion should follow

    /** Whether the player has Inventory Plus's item restock switched on. */
    public static boolean isItemRestockEnabled() {
        return IPConfig.autoRestockItem();
    }

    /** Whether the player has the Column Cycler switched on. */
    public static boolean isColumnCyclerEnabled() {
        return IPConfig.columnCyclerEnabled();
    }

    // Shared client state

    /**
     * The block whose container the player currently has open, or null when
     * none is open or it cannot be identified.
     *
     * <p>Phrased as the question rather than the mechanism. It is currently
     * answered by watching the click that opened the container, which has
     * known gaps. If that is replaced by authoritative state from
     * the server, this signature does not change.
     */
    public static @Nullable Block openContainerBlock() {
        return ContainerOpenTracker.openContainerBlock();
    }

    /**
     * Opens Inventory Plus's settings menu on the tab {@code tabId}, over the
     * screen that is open now. For a companion's inventory button to open its
     * own tab on Ctrl-click, as Inventory Plus's buttons do.
     */
    public static void openSettings(String tabId) {
        SettingsMenu.openOn(tabId);
    }

    /**
     * Adds a feature's on/off switch to the inventory's right column, after
     * Inventory Plus's own, in the order companions call this (Inventory Max:
     * its tab order). It behaves as Inventory Plus's switches do. Left click
     * toggles {@code on} through {@code set}, Ctrl-click (Cmd on a Mac) opens
     * {@code tabId}, the tooltip reads "{@code name}: On" with {@code hint}
     * under it (left out by Compact tooltips), and it shows while
     * {@code shown}. {@code sprite} is a 9x9 GUI sprite. Call during client
     * init, because the column is built when the client starts.
     */
    public static void addFeatureSwitch(String tabId, String name, Identifier sprite, BooleanSupplier on,
                                        Consumer<Boolean> set, BooleanSupplier shown, @Nullable String hint) {
        FeatureToggleButtons.contribute(new FeatureToggleButtons.Contributed(
                tabId, name, sprite, on, set, shown, hint));
    }

    /**
     * A stable id for the world or server the player is in, for keying
     * per-world client state. Null when not in a world.
     */
    public static @Nullable String worldId() {
        return WorldIdentity.current(Minecraft.getInstance());
    }

    // The settings menu (companions add tabs with MenuKit's Tabs.addTo)

    /**
     * Asks before {@code action} in Inventory Plus's settings menu: a title, a
     * line of what it will do, then Cancel and Confirm. For a companion tab's
     * Reset to Defaults, so every reset in the menu asks the same way.
     */
    public static void confirmInSettings(String title, String body, Runnable action) {
        com.trevorschoeny.inventoryplus.settings.SettingsMenu.confirm(title, body, action);
    }

    /**
     * A reset that the settings menu's Reset everything also runs, so a
     * companion resets its own settings and keys with it. Call once at client init.
     */
    public static void registerSettingsReset(Runnable reset) {
        com.trevorschoeny.inventoryplus.settings.SettingsMenu.registerReset(reset);
    }

    /**
     * The settings menu as a screen on the tab {@code tabId}, returning to
     * {@code parent} on close. For a companion's Mod Menu entry.
     */
    public static Screen settingsScreen(@Nullable Screen parent, String tabId) {
        return SettingsMenu.create(parent, tabId);
    }

    /**
     * Takes {@code groups} out of what {@code operation} may use, in the
     * player's reach (as unticking their boxes in the Reach tab would). For a
     * companion upgrading a setting of its own that the reach now holds.
     */
    public static void denyInReach(BehaviorKey<?> operation, Collection<SlotGroupId> groups) {
        com.trevorschoeny.inventoryplus.lockgroups.Reach.setDenied(operation.id().toString(),
                groups.stream().map(SlotGroupId::asString).toList(), true);
    }
}
