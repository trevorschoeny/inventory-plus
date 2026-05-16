package com.trevorschoeny.inventoryplus.autorestock;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Per-tick auto-restock detection + action loop.
 *
 * <h3>What this watches</h3>
 *
 * Three slot categories per the {@code auto-restock.md} spec:
 * <ul>
 *   <li><b>Active hotbar slot</b> — the slot the player is currently holding.
 *       Other hotbar slots are deliberately NOT watched (spec §"Active slots
 *       covered": "Other hotbar slots are not auto-restocked — only the slot
 *       whose item is in active use.").</li>
 *   <li><b>Offhand</b> — gated by the offhand-restock toggle (spec default on).
 *       Hardcoded on for the 18b smoke pass; config UI is a later concern.</li>
 *   <li><b>Armor</b> — all four pieces, gated by the armor-restock toggle
 *       (spec default on). Hardcoded on for 18b.</li>
 * </ul>
 *
 * <h3>Detection method</h3>
 *
 * Each tick we snapshot the slots we care about, then compare next tick. A
 * "became empty" transition (was non-empty, is now empty) on a watched slot
 * fires the restock action. Per-tick polling at 20Hz is fast enough that the
 * empty frame is invisible to the player — they see the new stack appear
 * immediately.
 *
 * <p>The detection fires only when no screen is open ({@code mc.screen == null}).
 * Mid-screen item moves are player-driven inventory work; auto-restock would
 * conflict by sending its own clicks mid-drag. Restock activity resumes the
 * tick the screen closes.
 *
 * <h3>Why a tick poll rather than events</h3>
 *
 * Fabric exposes break events for blocks, but item-stack depletion is not
 * a discrete event on the client — vanilla decrements stacks at various
 * call sites and the client just sees the post-decrement state. Polling
 * the slots we care about is the simplest honest detection that works for
 * every depletion path (tool break, food eaten, blocks placed, arrows
 * fired, totem consumed, etc.).
 *
 * <h3>Slot model — what's "armor" / "offhand" in 1.21.11</h3>
 *
 * Vanilla 1.21.11 moved equipment off the {@code Inventory.armor} /
 * {@code Inventory.offhand} lists and onto a unified
 * {@code EntityEquipment} accessed via
 * {@link LocalPlayer#getItemBySlot(EquipmentSlot)}. Auto-restock reads
 * those slots through that API and writes via {@link ClickType} packets
 * keyed off vanilla's InventoryMenu slot mapping (helmet=5, chest=6,
 * legs=7, boots=8, offhand=45). The mapping is constant and well-known —
 * no MK involvement here.
 *
 * <h3>Out of scope for the 18b smoke pass</h3>
 *
 * <ul>
 *   <li>Proactive low-durability swap (spec §"Optional: proactive restock
 *       at low durability"; off by default in spec, deferred entirely).</li>
 *   <li>Shulker / bundle as nested source (spec §"Optional: shulkers/bundles
 *       as nested sources"; off by default in spec, deferred entirely).</li>
 *   <li>Direct ammo pull for bows (spec §"Direct ammo pull for bows"; off
 *       by default in spec, deferred entirely).</li>
 *   <li>Highest-tier fallback search (see {@link AutoRestockSearch} class
 *       javadoc — implementation deferred for 1.21.11 component API; same-
 *       material refill via pass 1 covers the common case).</li>
 *   <li>Locked-slots source-skip (locked-slots feature isn't in 18b; all
 *       main-inventory slots treated as unlocked sources).</li>
 *   <li>Equipment-slot restock (IPP feature; not in 18b).</li>
 * </ul>
 */
public final class AutoRestockTicker {

    private AutoRestockTicker() {}

    // ─── Configuration (hardcoded for 18b; config UI deferred) ────────────
    // Per spec: active-slot is always on; offhand + armor default on.

    private static final boolean OFFHAND_RESTOCK_ENABLED = true;
    private static final boolean ARMOR_RESTOCK_ENABLED = true;

    // ─── InventoryMenu slot mapping ───────────────────────────────────────
    //
    // The player's permanent inventory menu (containerId = 0) lays out
    // slots as:
    //   0      crafting result
    //   1-4    crafting input
    //   5      helmet      ← EquipmentSlot.HEAD
    //   6      chestplate  ← EquipmentSlot.CHEST
    //   7      leggings    ← EquipmentSlot.LEGS
    //   8      boots       ← EquipmentSlot.FEET
    //   9-35   main inventory (= Inventory.items[9-35])
    //   36-44  hotbar       (= Inventory.items[0-8])
    //   45     offhand     ← EquipmentSlot.OFFHAND
    // Source slots we use (main inventory) match between Inventory.items
    // index and InventoryMenu slot 1:1.

    private static final int MENU_SLOT_OFFHAND = 45;

    /**
     * SWAP click's {@code button} sentinel for routing the swap to the
     * offhand slot. Vanilla's {@code AbstractContainerMenu.doClick} for
     * SWAP treats {@code button == 40} as "swap into offhand" (vs.
     * {@code 0-8} for the hotbar slots).
     */
    private static final int SWAP_OFFHAND_KEY = 40;

    // ─── Per-tick snapshot of the watched slots ───────────────────────────
    //
    // We keep one previous-stack copy per watched slot. Copies are
    // mandatory — vanilla mutates stack counts in place at many call
    // sites, so holding a reference to a "previously non-empty" stack
    // doesn't preserve its prior state.

    private static final ItemStack[] previousHotbar = new ItemStack[9];
    private static ItemStack previousHelmet  = ItemStack.EMPTY;
    private static ItemStack previousChest   = ItemStack.EMPTY;
    private static ItemStack previousLegs    = ItemStack.EMPTY;
    private static ItemStack previousFeet    = ItemStack.EMPTY;
    private static ItemStack previousOffhand = ItemStack.EMPTY;
    private static boolean initialized = false;

    /**
     * Tick handler — registered against
     * {@code ClientTickEvents.END_CLIENT_TICK}. Runs at 20Hz.
     */
    public static void tick(Minecraft mc) {
        if (mc == null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (gameMode == null) return;

        // Skip while any screen is open — see class javadoc.
        if (mc.screen != null) {
            snapshot(player);
            return;
        }

        if (!initialized) {
            snapshot(player);
            initialized = true;
            return;
        }

        Inventory inv = player.getInventory();

        // ─── 1. Active hotbar slot ────────────────────────────────────────
        int selected = inv.getSelectedSlot();
        ItemStack prevActive = previousHotbar[selected];
        ItemStack currentActive = inv.getItem(selected);
        if (isBecameEmpty(prevActive, currentActive)) {
            int sourceSlot = AutoRestockSearch.findSource(inv, prevActive);
            if (sourceSlot != AutoRestockSearch.NONE) {
                // SWAP from source (main-inv slot, identity-mapped to
                // InventoryMenu slot) to hotbar key `selected`. After:
                // hotbar holds the source's stack; source slot is empty.
                gameMode.handleInventoryMouseClick(
                        player.inventoryMenu.containerId,
                        sourceSlot,
                        selected,
                        ClickType.SWAP,
                        player);
                InventoryPlusClient.LOGGER.debug(
                        "[auto-restock] hotbar[{}] ← main[{}] ({})",
                        selected, sourceSlot, prevActive.getItem());
            }
        }

        // ─── 2. Offhand ───────────────────────────────────────────────────
        if (OFFHAND_RESTOCK_ENABLED) {
            ItemStack currentOffhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
            if (isBecameEmpty(previousOffhand, currentOffhand)) {
                int sourceSlot = AutoRestockSearch.findSource(inv, previousOffhand);
                if (sourceSlot != AutoRestockSearch.NONE) {
                    gameMode.handleInventoryMouseClick(
                            player.inventoryMenu.containerId,
                            sourceSlot,
                            SWAP_OFFHAND_KEY,
                            ClickType.SWAP,
                            player);
                    InventoryPlusClient.LOGGER.debug(
                            "[auto-restock] offhand ← main[{}] ({})",
                            sourceSlot, previousOffhand.getItem());
                }
            }
        }

        // ─── 3. Armor ─────────────────────────────────────────────────────
        if (ARMOR_RESTOCK_ENABLED) {
            checkArmor(mc, player, gameMode, inv, EquipmentSlot.HEAD,
                    previousHelmet, 5);
            checkArmor(mc, player, gameMode, inv, EquipmentSlot.CHEST,
                    previousChest, 6);
            checkArmor(mc, player, gameMode, inv, EquipmentSlot.LEGS,
                    previousLegs, 7);
            checkArmor(mc, player, gameMode, inv, EquipmentSlot.FEET,
                    previousFeet, 8);
        }

        snapshot(player);
    }

    /**
     * Per-armor-slot empty check + restock. Uses QUICK_MOVE rather than
     * SWAP — shift-clicking an armor piece from main inventory routes it
     * to the matching empty armor slot via vanilla's
     * {@code InventoryMenu.quickMoveStack}. Saves us from threading the
     * (Equippable slot → menu slot) mapping through the click call.
     *
     * @param menuArmorSlot  the InventoryMenu slot number of this armor
     *                       piece (5 helmet, 6 chest, 7 legs, 8 boots).
     *                       Unused as a click target here (QUICK_MOVE
     *                       routes by item type, not destination slot),
     *                       but kept in the signature for clarity and so
     *                       future refactors to a PICKUP-PLACE shape have
     *                       the value handy.
     */
    private static void checkArmor(Minecraft mc, LocalPlayer player,
                                   MultiPlayerGameMode gameMode, Inventory inv,
                                   EquipmentSlot slot, ItemStack previousPiece,
                                   int menuArmorSlot) {
        ItemStack currentPiece = player.getItemBySlot(slot);
        if (!isBecameEmpty(previousPiece, currentPiece)) return;
        int sourceSlot = AutoRestockSearch.findArmorSource(inv, previousPiece);
        if (sourceSlot == AutoRestockSearch.NONE) return;
        gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                sourceSlot,
                /* mouseButton ignored for QUICK_MOVE */ 0,
                ClickType.QUICK_MOVE,
                player);
        InventoryPlusClient.LOGGER.debug(
                "[auto-restock] armor[{}] ← main[{}] ({})",
                slot, sourceSlot, previousPiece.getItem());
    }

    /**
     * Snapshots the slots we watch for next tick's became-empty comparison.
     * Copies are mandatory (see class javadoc).
     */
    private static void snapshot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            previousHotbar[i] = inv.getItem(i).copy();
        }
        previousHelmet  = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        previousChest   = player.getItemBySlot(EquipmentSlot.CHEST).copy();
        previousLegs    = player.getItemBySlot(EquipmentSlot.LEGS).copy();
        previousFeet    = player.getItemBySlot(EquipmentSlot.FEET).copy();
        previousOffhand = player.getItemBySlot(EquipmentSlot.OFFHAND).copy();
    }

    /**
     * The "stack just became empty" test. {@code prev} non-empty + {@code now}
     * empty is the signal. Other transitions don't trigger restock.
     */
    private static boolean isBecameEmpty(ItemStack prev, ItemStack now) {
        return prev != null && !prev.isEmpty() && now.isEmpty();
    }
}
