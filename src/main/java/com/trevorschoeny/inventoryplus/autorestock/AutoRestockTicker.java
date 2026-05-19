package com.trevorschoeny.inventoryplus.autorestock;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.config.IPConfig;

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
 * <h3>Two restock paths — both run every client tick</h3>
 *
 * <ol>
 *   <li><b>Durability-delta restock</b> (for damageable items): the core
 *       mechanism. Each tick, we compare every player-owned slot to the
 *       previous-tick snapshot. If a slot's current stack is a damageable
 *       item, sits at or below {@link #BEFORE_BREAK_THRESHOLD} remaining
 *       durability, AND its exact content (Item + components + damage
 *       value) was not present anywhere in the prev snapshot, the item
 *       just took durability damage — fire a swap from main inventory if
 *       a fresher same-Item replacement exists. The "exists in prev?"
 *       check is the migration filter: drag-drops just relocate an
 *       existing stack, so its post-move state matches a prev slot and
 *       suppresses the trigger.</li>
 *   <li><b>Non-damageable item refill</b> (for food / blocks / arrows /
 *       totems / etc. in the active hand or offhand): unchanged
 *       semantics. When the active slot's non-damageable stack runs out
 *       (count → 0), refill from main inventory with a same-Item stack.
 *       Tools and armor are NOT handled by this path — they go through
 *       the durability-delta path above, which means broken tools are
 *       deliberately not auto-refilled (the before-break swap should
 *       have replaced them before they broke; if it didn't, there was no
 *       fresher copy to swap in anyway).</li>
 * </ol>
 *
 * <h3>Snapshot — all 42 watched stacks, every tick</h3>
 *
 * The migration check needs visibility into the full player inventory
 * each tick. Snapshot is a flat 42-element {@code ItemStack[]} laid out
 * as:
 *
 * <pre>
 *   0–8     hotbar              (Inventory.items[0..8])
 *   9–35    main inventory      (Inventory.items[9..35])
 *   36      helmet              (EquipmentSlot.HEAD)
 *   37      chestplate          (EquipmentSlot.CHEST)
 *   38      leggings            (EquipmentSlot.LEGS)
 *   39      boots               (EquipmentSlot.FEET)
 *   40      offhand             (EquipmentSlot.OFFHAND)
 *   41      cursor              (player.containerMenu.getCarried())
 * </pre>
 *
 * The cursor entry catches stacks that are in mid-drag — picked up
 * from a slot but not yet dropped — so the migration check still finds
 * them. Copies are mandatory; vanilla mutates ItemStack state in place
 * at many call sites, so bare references wouldn't preserve the prior
 * tick's state.
 *
 * <h3>Active container gate</h3>
 *
 * We poll + fire only when {@code player.containerMenu ==
 * player.inventoryMenu} — that's HUD or the player's own E-key inventory
 * screen, both of which share the same container ID and slot mapping, so
 * our clicks land cleanly. For chest / anvil / crafting-table screens
 * the active container is something else and our hard-coded slot
 * indices don't apply, so we skip both polling and firing. Snapshot
 * freezes across those windows; damage taken inside still produces a
 * not-in-prev delta on the close tick.
 *
 * <h3>Slot model — 1.21.11</h3>
 *
 * Vanilla 1.21.11 keeps equipment off the {@code Inventory.armor} /
 * {@code Inventory.offhand} lists and on a unified {@code EntityEquipment}
 * accessed via {@link LocalPlayer#getItemBySlot(EquipmentSlot)}. We read
 * those slots through that API and write via {@link ClickType} packets
 * keyed off vanilla's InventoryMenu slot mapping (helmet=5, chest=6,
 * legs=7, boots=8, offhand button=40 for SWAP).
 *
 * <h3>Deferred</h3>
 *
 * Shulker / bundle as nested source, direct ammo pull for bows, and
 * highest-tier fallback search — all deferred per spec.
 */
public final class AutoRestockTicker {

    private AutoRestockTicker() {}

    /** Durability remaining at or below which a swap fires. Per spec verbatim. */
    private static final int BEFORE_BREAK_THRESHOLD = 10;

    // ─── Snapshot slot indices ────────────────────────────────────────────
    // 0–35  hotbar + main inventory   (via inv.getItem)
    // 36–39 armor (HEAD/CHEST/LEGS/FEET)
    // 40    offhand
    // 41    cursor — included so a stack in cursor-transit (between a
    //       pickup tick and a drop tick) is still findable in the prev
    //       snapshot. Without it, cursor-drag drops onto a watched slot
    //       look like brand-new damage values to the migration check.
    private static final int SNAPSHOT_SIZE   = 42;
    private static final int IDX_ARMOR_HEAD  = 36;
    private static final int IDX_ARMOR_CHEST = 37;
    private static final int IDX_ARMOR_LEGS  = 38;
    private static final int IDX_ARMOR_FEET  = 39;
    private static final int IDX_OFFHAND     = 40;
    private static final int IDX_CURSOR      = 41;

    // ─── InventoryMenu slot mapping (constant; well-known) ────────────────
    private static final int MENU_ARMOR_HEAD  = 5;
    private static final int MENU_ARMOR_CHEST = 6;
    private static final int MENU_ARMOR_LEGS  = 7;
    private static final int MENU_ARMOR_FEET  = 8;

    /**
     * SWAP click's {@code button} sentinel for routing the swap to the
     * offhand slot. Vanilla's {@code AbstractContainerMenu.doClick} for
     * SWAP treats {@code button == 40} as "swap into offhand" (vs.
     * {@code 0-8} for the hotbar slots).
     */
    private static final int SWAP_OFFHAND_KEY = 40;

    private static final ItemStack[] previousSnapshot = new ItemStack[SNAPSHOT_SIZE];
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

        // Skip when the active container isn't the player's own inventory
        // — see class javadoc. Snapshot freezes across that window.
        if (player.containerMenu != player.inventoryMenu) return;

        if (!initialized) {
            snapshot(player);
            initialized = true;
            return;
        }

        Inventory inv = player.getInventory();
        int selected = inv.getSelectedSlot();

        // ─── 1. Non-damageable item refill (food / blocks / arrows / etc.) ──
        // Active hand + offhand only. Damageable items are NOT handled here;
        // they go through the break-refill or durability-delta paths below.
        if (IPConfig.autoRestockItem()) {
            refillIfNonDamageableRanOut(gameMode, inv, player,
                    previousSnapshot[selected], inv.getItem(selected), selected);
            refillIfNonDamageableRanOut(gameMode, inv, player,
                    previousSnapshot[IDX_OFFHAND],
                    player.getItemBySlot(EquipmentSlot.OFFHAND),
                    SWAP_OFFHAND_KEY);
        }

        // ─── 2. On-break refill for damageable items (tool / shield / armor) ──
        // Fires when a watched slot's prev was damageable + non-empty and now
        // is empty, AND the prev stack can't be found anywhere in current
        // inventory (incl. cursor). That second check distinguishes a real
        // break from the player manually removing the item via drag/shift/etc.
        if (IPConfig.autoRestockTool()) {
            refillIfDamageableBroke(gameMode, inv, player,
                    previousSnapshot[selected], inv.getItem(selected), selected);
            refillIfDamageableBroke(gameMode, inv, player,
                    previousSnapshot[IDX_OFFHAND],
                    player.getItemBySlot(EquipmentSlot.OFFHAND),
                    SWAP_OFFHAND_KEY);
        }
        if (IPConfig.autoRestockArmor()) {
            refillIfArmorBroke(gameMode, inv, player, EquipmentSlot.HEAD,
                    previousSnapshot[IDX_ARMOR_HEAD]);
            refillIfArmorBroke(gameMode, inv, player, EquipmentSlot.CHEST,
                    previousSnapshot[IDX_ARMOR_CHEST]);
            refillIfArmorBroke(gameMode, inv, player, EquipmentSlot.LEGS,
                    previousSnapshot[IDX_ARMOR_LEGS]);
            refillIfArmorBroke(gameMode, inv, player, EquipmentSlot.FEET,
                    previousSnapshot[IDX_ARMOR_FEET]);
        }

        // ─── 3. Durability-delta restock (before-break) ─────────────────────
        // For each of the six watched slots, fire a swap iff the slot's
        // current stack just took a point of durability damage AND now sits
        // at or below the threshold. Gating: parent toggle (Tool/Armor)
        // AND its matching "Restock Before Break" sub-toggle. See
        // {@link #tookDamageThisTick} for the precise trigger predicate.

        if (IPConfig.autoRestockTool() && IPConfig.autoRestockToolBeforeBreak()) {
            // Mainhand — SWAP click is a true exchange; works whether the
            // hotbar slot ends up empty or occupied after the swap.
            tryDurabilitySwapHotbar(gameMode, inv, player, selected);
            tryDurabilitySwapOffhand(gameMode, inv, player);
        }

        if (IPConfig.autoRestockArmor() && IPConfig.autoRestockArmorBeforeBreak()) {
            tryDurabilitySwapArmor(gameMode, inv, player, EquipmentSlot.HEAD,  MENU_ARMOR_HEAD);
            tryDurabilitySwapArmor(gameMode, inv, player, EquipmentSlot.CHEST, MENU_ARMOR_CHEST);
            tryDurabilitySwapArmor(gameMode, inv, player, EquipmentSlot.LEGS,  MENU_ARMOR_LEGS);
            tryDurabilitySwapArmor(gameMode, inv, player, EquipmentSlot.FEET,  MENU_ARMOR_FEET);
        }

        snapshot(player);
    }

    // ─── Path 1: non-damageable refill ────────────────────────────────────

    /**
     * Fires when the active slot's stack just ran out and it was a
     * non-damageable item (food, block, arrow, etc.). Refills with the
     * first same-Item stack found in main inventory.
     *
     * @param targetClickKey  the SWAP button — 0–8 for a hotbar slot,
     *                        {@link #SWAP_OFFHAND_KEY} (40) for the
     *                        offhand slot.
     */
    private static void refillIfNonDamageableRanOut(MultiPlayerGameMode gameMode, Inventory inv,
                                                    LocalPlayer player, ItemStack prev,
                                                    ItemStack now, int targetClickKey) {
        if (prev == null || prev.isEmpty()) return;
        if (!now.isEmpty()) return;
        if (isDamageable(prev)) return;   // damageable items handled by path 2/3
        boolean isActiveHotbar = targetClickKey != SWAP_OFFHAND_KEY;
        int excludeSlot = isActiveHotbar ? targetClickKey : AutoRestockSearch.NONE;
        int source = AutoRestockSearch.findSource(inv, prev, excludeSlot);
        if (source == AutoRestockSearch.NONE) return;
        if (isActiveHotbar) {
            refillActiveHotbarSlot(gameMode, player, source, targetClickKey, "item-restock", prev);
        } else {
            gameMode.handleInventoryMouseClick(
                    player.inventoryMenu.containerId,
                    source, SWAP_OFFHAND_KEY, ClickType.SWAP, player);
            InventoryPlusClient.LOGGER.debug(
                    "[item-restock] offhand ← src[{}] ({})", source, prev.getItem());
        }
    }

    // ─── Path 2: on-break refill for damageable items ─────────────────────

    /**
     * Fires when a damageable item that occupied this slot last tick is now
     * gone. Distinguishes a real break (item destroyed, nowhere to be found)
     * from a player-driven removal (item migrated to cursor / inv / another
     * slot) via {@link #existsInCurrentInventory}.
     */
    private static void refillIfDamageableBroke(MultiPlayerGameMode gameMode, Inventory inv,
                                                LocalPlayer player, ItemStack prev,
                                                ItemStack now, int targetClickKey) {
        if (prev == null || prev.isEmpty()) return;
        if (!now.isEmpty()) return;
        if (!isDamageable(prev)) return;
        if (existsInCurrentInventory(prev, player)) {
            InventoryPlusClient.LOGGER.debug(
                    "[break-restock] suppress target={} — prev item still in inv (migration)",
                    targetClickKey);
            return;
        }
        boolean isActiveHotbar = targetClickKey != SWAP_OFFHAND_KEY;
        int excludeSlot = isActiveHotbar ? targetClickKey : AutoRestockSearch.NONE;
        int source = AutoRestockSearch.findSource(inv, prev, excludeSlot);
        if (source == AutoRestockSearch.NONE) {
            InventoryPlusClient.LOGGER.debug(
                    "[break-restock] no replacement for target={} item={}",
                    targetClickKey, prev.getItem());
            return;
        }
        if (isActiveHotbar) {
            refillActiveHotbarSlot(gameMode, player, source, targetClickKey, "break-restock", prev);
        } else {
            gameMode.handleInventoryMouseClick(
                    player.inventoryMenu.containerId,
                    source, SWAP_OFFHAND_KEY, ClickType.SWAP, player);
            InventoryPlusClient.LOGGER.debug(
                    "[break-restock] offhand ← src[{}] ({})", source, prev.getItem());
        }
    }

    /**
     * Armor-slot variant. QUICK_MOVE shift-clicks a same-Item armor piece
     * from main inventory; vanilla's quick-move routes Equippable items to
     * the matching empty armor slot, so we don't need the menu slot index.
     */
    private static void refillIfArmorBroke(MultiPlayerGameMode gameMode, Inventory inv,
                                           LocalPlayer player, EquipmentSlot slot,
                                           ItemStack prev) {
        if (prev == null || prev.isEmpty()) return;
        ItemStack now = player.getItemBySlot(slot);
        if (!now.isEmpty()) return;
        if (!isDamageable(prev)) return;
        if (existsInCurrentInventory(prev, player)) {
            InventoryPlusClient.LOGGER.debug(
                    "[break-restock] suppress armor[{}] — prev item still in inv (migration)",
                    slot);
            return;
        }
        int source = AutoRestockSearch.findArmorSource(inv, prev);
        if (source == AutoRestockSearch.NONE) {
            InventoryPlusClient.LOGGER.debug(
                    "[break-restock] no replacement for armor[{}] item={}",
                    slot, prev.getItem());
            return;
        }
        gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                source,
                0,
                ClickType.QUICK_MOVE,
                player);
        InventoryPlusClient.LOGGER.debug(
                "[break-restock] armor[{}] ← main[{}] ({})",
                slot, source, prev.getItem());
    }

    // ─── Path 3: durability-delta restock ─────────────────────────────────

    private static void tryDurabilitySwapHotbar(MultiPlayerGameMode gameMode, Inventory inv,
                                                LocalPlayer player, int selected) {
        ItemStack now = inv.getItem(selected);
        if (!tookDamageThisTick(now)) return;
        int source = AutoRestockSearch.findHigherDurability(inv, now, selected);
        if (source == AutoRestockSearch.NONE) {
            InventoryPlusClient.LOGGER.debug(
                    "[durability-restock] no replacement for hotbar[{}] item={}",
                    selected, now.getItem());
            return;
        }
        refillActiveHotbarSlot(gameMode, player, source, selected, "durability-restock", now);
    }

    private static void tryDurabilitySwapOffhand(MultiPlayerGameMode gameMode, Inventory inv,
                                                 LocalPlayer player) {
        ItemStack now = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!tookDamageThisTick(now)) return;
        int source = AutoRestockSearch.findHigherDurability(inv, now, AutoRestockSearch.NONE);
        if (source == AutoRestockSearch.NONE) {
            InventoryPlusClient.LOGGER.debug(
                    "[durability-restock] no replacement for offhand item={}",
                    now.getItem());
            return;
        }
        gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                source,
                SWAP_OFFHAND_KEY,
                ClickType.SWAP,
                player);
        InventoryPlusClient.LOGGER.debug(
                "[durability-restock] offhand ← src[{}] ({})",
                source, now.getItem());
    }

    /**
     * Armor swap uses a 3-click PICKUP exchange because QUICK_MOVE no-ops
     * when the destination armor slot is occupied and SWAP doesn't have
     * an armor-slot variant. Sequence:
     *
     * <ol>
     *   <li>PICKUP source slot — cursor picks up the fresh replacement.</li>
     *   <li>PICKUP armor slot — armor slot accepts the replacement (same
     *       Item, same Equippable target); cursor swaps to hold the worn
     *       piece.</li>
     *   <li>PICKUP source slot (now empty) — cursor drops the worn piece
     *       into the freed source slot.</li>
     * </ol>
     */
    private static void tryDurabilitySwapArmor(MultiPlayerGameMode gameMode, Inventory inv,
                                               LocalPlayer player, EquipmentSlot slot,
                                               int menuArmorSlot) {
        ItemStack now = player.getItemBySlot(slot);
        if (!tookDamageThisTick(now)) return;
        int source = AutoRestockSearch.findHigherDurabilityArmor(inv, now);
        if (source == AutoRestockSearch.NONE) {
            InventoryPlusClient.LOGGER.debug(
                    "[durability-restock] no replacement for armor[{}] item={}",
                    slot, now.getItem());
            return;
        }
        int containerId = player.inventoryMenu.containerId;
        gameMode.handleInventoryMouseClick(containerId, source,        0, ClickType.PICKUP, player);
        gameMode.handleInventoryMouseClick(containerId, menuArmorSlot, 0, ClickType.PICKUP, player);
        gameMode.handleInventoryMouseClick(containerId, source,        0, ClickType.PICKUP, player);
        InventoryPlusClient.LOGGER.debug(
                "[durability-restock] armor[{}] ← main[{}] ({})",
                slot, source, now.getItem());
    }

    // ─── Trigger predicate ────────────────────────────────────────────────

    /**
     * The single trigger predicate, per spec verbatim: did this item just
     * take durability damage AND is it now at or below threshold?
     *
     * <p>Conditions:
     *
     * <ol>
     *   <li>{@code now} is a damageable item ({@code maxDamage > 0}) with
     *       a current stack present.</li>
     *   <li>{@code now}'s remaining durability is ≤
     *       {@link #BEFORE_BREAK_THRESHOLD}.</li>
     *   <li>{@code now}'s exact content (Item + components + damage value)
     *       did <b>not</b> exist anywhere in the previous-tick snapshot.
     *       Real damage produces a damage value that didn't exist before;
     *       a drag-drop just relocates an existing stack and so its
     *       post-move state matches a prev slot.</li>
     * </ol>
     *
     * <p>That third condition is the migration filter — without it, the
     * post-move state of a player-driven drag would look indistinguishable
     * from a damage event.
     */
    private static boolean tookDamageThisTick(ItemStack now) {
        if (now == null || now.isEmpty() || now.getMaxDamage() <= 0) return false;
        int remaining = now.getMaxDamage() - now.getDamageValue();
        if (remaining <= 0 || remaining > BEFORE_BREAK_THRESHOLD) return false;
        boolean migrated = existsInPreviousSnapshot(now);
        InventoryPlusClient.LOGGER.debug(
                "[durability-restock] threshold-hit item={} dmg={} remaining={} existsInPrev={} → {}",
                now.getItem(), now.getDamageValue(), remaining, migrated,
                migrated ? "SUPPRESS (migration)" : "FIRE (damage event)");
        return !migrated;
    }

    /**
     * Scans the full 42-slot previous snapshot (41 player-owned slots +
     * the cursor) for a stack matching {@code stack} by Item AND all
     * components (which includes the {@code DAMAGE} component, so the
     * damage value is part of the comparison). Delegates to vanilla
     * {@link ItemStack#isSameItemSameComponents}.
     */
    private static boolean existsInPreviousSnapshot(ItemStack stack) {
        for (ItemStack prev : previousSnapshot) {
            if (prev == null || prev.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(prev, stack)) return true;
        }
        return false;
    }

    /**
     * Migration check for the on-break path: does an exact match for
     * {@code stack} exist anywhere in the player's <i>current</i>
     * inventory (hotbar, main inv, armor, offhand, cursor)? If yes, the
     * stack was relocated, not destroyed.
     */
    private static boolean existsInCurrentInventory(ItemStack stack, LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (ItemStack.isSameItemSameComponents(inv.getItem(i), stack)) return true;
        }
        if (ItemStack.isSameItemSameComponents(player.getItemBySlot(EquipmentSlot.HEAD), stack))    return true;
        if (ItemStack.isSameItemSameComponents(player.getItemBySlot(EquipmentSlot.CHEST), stack))   return true;
        if (ItemStack.isSameItemSameComponents(player.getItemBySlot(EquipmentSlot.LEGS), stack))    return true;
        if (ItemStack.isSameItemSameComponents(player.getItemBySlot(EquipmentSlot.FEET), stack))    return true;
        if (ItemStack.isSameItemSameComponents(player.getItemBySlot(EquipmentSlot.OFFHAND), stack)) return true;
        if (ItemStack.isSameItemSameComponents(player.containerMenu.getCarried(), stack))           return true;
        return false;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    /** True iff the stack is a damageable tool / armor / shield / weapon. */
    private static boolean isDamageable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getMaxDamage() > 0;
    }

    /**
     * Fills the player's active hotbar slot from {@code source}. If
     * {@code source} is another hotbar slot (0–8, by construction not the
     * active slot — the search excludes it), changes the selected slot
     * to {@code source} rather than moving the item — leaves both stacks
     * in place and just shifts the player's hand. This is the
     * cycling-friendly form that future Auto Swap / cycling features will
     * compose with directly. If {@code source} is in main inventory
     * (9–35), falls back to a standard SWAP click that moves the item
     * into the active hotbar slot.
     */
    private static void refillActiveHotbarSlot(MultiPlayerGameMode gameMode, LocalPlayer player,
                                               int source, int activeSlot,
                                               String logTag, ItemStack itemForLog) {
        if (source >= AutoRestockSearch.HOTBAR_START && source < AutoRestockSearch.MAIN_INV_START) {
            // Hotbar source — move selected. The search excluded activeSlot,
            // so source != activeSlot is guaranteed.
            player.getInventory().setSelectedSlot(source);
            InventoryPlusClient.LOGGER.debug(
                    "[{}] selected {} → {} (hotbar source, no item move) ({})",
                    logTag, activeSlot, source, itemForLog.getItem());
        } else {
            // Main inv source — standard SWAP into active slot.
            gameMode.handleInventoryMouseClick(
                    player.inventoryMenu.containerId,
                    source, activeSlot, ClickType.SWAP, player);
            InventoryPlusClient.LOGGER.debug(
                    "[{}] hotbar[{}] ← src[{}] (item swap) ({})",
                    logTag, activeSlot, source, itemForLog.getItem());
        }
    }

    /**
     * Snapshots all 42 watched stacks (hotbar + main inv + armor + offhand
     * + cursor) into {@link #previousSnapshot}. Copies are mandatory — see
     * class javadoc.
     */
    private static void snapshot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            previousSnapshot[i] = inv.getItem(i).copy();
        }
        previousSnapshot[IDX_ARMOR_HEAD]  = player.getItemBySlot(EquipmentSlot.HEAD).copy();
        previousSnapshot[IDX_ARMOR_CHEST] = player.getItemBySlot(EquipmentSlot.CHEST).copy();
        previousSnapshot[IDX_ARMOR_LEGS]  = player.getItemBySlot(EquipmentSlot.LEGS).copy();
        previousSnapshot[IDX_ARMOR_FEET]  = player.getItemBySlot(EquipmentSlot.FEET).copy();
        previousSnapshot[IDX_OFFHAND]     = player.getItemBySlot(EquipmentSlot.OFFHAND).copy();
        previousSnapshot[IDX_CURSOR]      = player.containerMenu.getCarried().copy();
    }
}
