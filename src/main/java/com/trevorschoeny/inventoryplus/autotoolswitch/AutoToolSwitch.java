package com.trevorschoeny.inventoryplus.autotoolswitch;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.config.IPKeybinds;
import com.trevorschoeny.inventoryplus.cyclable.CyclerOperation;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
// Entity is referenced by preAttackEntity's signature; LivingEntity for
// the post-cast match check; Monster for the hostile filter.
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Auto Tool Switch — on-hit auto-swap to the right tool/weapon for the
 * target, with optional auto-return when the action completes.
 *
 * <h3>Trigger</h3>
 *
 * Both block and entity paths invoke this class from mixins at the
 * HEAD of the relevant {@code MultiPlayerGameMode} methods — BEFORE
 * vanilla reads the player's main-hand item for destroy-speed or
 * attack-damage computation. Fabric's callbacks were used initially
 * but fired too late, per Trev's 2026-05-25 test.
 *
 * <ul>
 *   <li><b>Block mining</b> — {@code MultiPlayerGameModeBlockAttackMixin}
 *       at the HEAD of {@code startDestroyBlock} (survival) and
 *       {@code destroyBlock} (creative) → {@link #preAttackBlock}.</li>
 *   <li><b>Entity combat</b> — {@code MultiPlayerGameModeAttackMixin}
 *       at the HEAD of {@code attack(Player, Entity)} →
 *       {@link #preAttackEntity}.</li>
 * </ul>
 *
 * Commit-at-start: both paths early-return when an in-flight switch
 * exists, preventing re-triggers during sustained presses or chained
 * attacks within a cooldown window.
 *
 * <h3>Three-tier resolution</h3>
 *
 * Per {@link ToolFinder}, scans slots and stops at the first tier with
 * a matching item:
 * <ol>
 *   <li><b>Tier 1</b> — slot is in hotbar → just change selected slot.
 *       No item moves; cheapest possible action.</li>
 *   <li><b>Tier 2</b> — slot is in a cyclable hotbar position → change
 *       selected slot to the column's hotbar position + call
 *       {@link HotbarCyclableRegistry#bringToHotbar} to rotate the
 *       cycler. The cycler returns a {@link CyclerOperation} undo
 *       handle for auto-return.</li>
 *   <li><b>Tier 3</b> — slot is elsewhere in main inventory → SWAP
 *       click between the slot and the active hotbar slot.</li>
 * </ol>
 *
 * <h3>Sneak suppression</h3>
 *
 * While the player is holding Sneak (Shift), the switch is suppressed
 * entirely. Standard convention from the popular reference mod
 * "Automatic Tool Swap" — gives the player an always-available override.
 *
 * <h3>Auto-return (optional)</h3>
 *
 * When the return mode ({@link IPConfig#autoToolSwitchReturnMode}) isn't OFF, the mod captures
 * pre-switch state (previous selected slot + cycler undo handle or
 * inv-swap reversal info) into {@link #inFlight}, then restores it
 * when the action completes:
 * <ul>
 *   <li>Mining → on LMB release, detected via the attack key's
 *       held-state in {@link Minecraft#options}.</li>
 *   <li>Combat → when a full weapon-cooldown plus a small grace buffer
 *       has elapsed since the last qualifying attack (tracked via
 *       {@link #combatLastAttackTick}). Each follow-up swing in a combo
 *       pushes that timer forward through {@link #preAttackEntity}, so
 *       the weapon isn't returned between swings.</li>
 * </ul>
 *
 * <p>Reversal mechanics:
 * <ul>
 *   <li>Tier 1 → just restore previous selected slot.</li>
 *   <li>Tier 2 → restore previous selected slot + call
 *       {@code CyclerOperation.undo()}.</li>
 *   <li>Tier 3 → restore previous selected slot + reverse SWAP
 *       (same two slots, again).</li>
 * </ul>
 */
public final class AutoToolSwitch {

    private AutoToolSwitch() {}

    /**
     * In-flight state — null when no switch is currently active.
     * Set on switch (if auto-return is on); cleared on return. Visible
     * to both event handlers (for commit-at-start gating) and the tick
     * handler (for return triggering).
     */
    private static InFlight inFlight = null;

    /**
     * Delayed return state for the non-AUTOMATIC return modes (SNEAK /
     * HOTKEY_*). Kept SEPARATE from {@link #inFlight} on purpose: inFlight stays
     * exactly as before — short-lived, cleared the moment the action completes,
     * which keeps the anti-thrash commit-at-start guard and re-switching working
     * unchanged. When the action completes in a delayed mode, inFlight's captured
     * state moves here and survives past the action so a sneak / keybind can
     * return it later. Null when nothing is awaiting a delayed return.
     */
    private static InFlight pendingReturn = null;

    /** {@code player.tickCount} when the pending-return window opened (windowed modes). */
    private static int pendingReturnWindowStart = -1;

    /**
     * Tick (player.tickCount) of the most recent qualifying combat
     * attack. Updated on every mob-attack that passes the filters, even
     * when the commit-at-start guard skips re-switching. Used by the
     * combat auto-return grace window so a player swinging at near-max
     * cadence isn't returned BETWEEN swings (which would thrash).
     */
    private static int combatLastAttackTick = -1;

    /**
     * Extra ticks to wait after the weapon's attack-cooldown completes
     * before auto-returning in combat. Guards against the exact-cadence
     * race where the cooldown hits full on the same tick the player is
     * about to swing again. ~4 ticks (0.2s) is enough buffer without
     * the weapon lingering noticeably after the player truly stops.
     */
    private static final int COMBAT_RETURN_GRACE_TICKS = 4;

    // ─── Registration ────────────────────────────────────────────────

    /**
     * Register the tick listener. Call once from
     * {@code InventoryPlusClient.onInitializeClient}.
     *
     * <p>The actual attack-event hooks (block + entity) are wired via
     * mixins (registered in {@code inventoryplus.mixins.json}), NOT
     * here — they call {@link #preAttackBlock} / {@link #preAttackEntity}
     * directly to fire before vanilla's tool/weapon reads.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoToolSwitch::onClientTick);
    }

    // ─── Event handlers ──────────────────────────────────────────────

    /**
     * Block-path entry point — called by
     * {@code MultiPlayerGameModeBlockAttackMixin} at the HEAD of both
     * {@code startDestroyBlock} (survival) and {@code destroyBlock}
     * (creative), BEFORE vanilla reads the player's main-hand for
     * destroy-speed or break logic.
     *
     * <p>Public because the mixin lives in a sibling package and needs
     * to reach in. Returns void — the mixin's CallbackInfo doesn't
     * need an interaction result; we don't cancel the attack.
     */
    public static void preAttackBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Player player = mc.player;
        if (!IPConfig.autoToolSwitchEnabled()) return;
        // Never auto-switch in creative — instant-break + infinite items make it
        // pointless, and a switch there is just noise.
        if (player.isCreative()) return;
        // Sneak suppression — player wants exact control.
        if (player.isShiftKeyDown()) return;
        // Commit-at-start: skip if a switch is already in flight
        // (sustained mining through different materials stays on the
        // initially-picked tool).
        if (inFlight != null) return;

        BlockState blockState = player.level().getBlockState(pos);
        ToolFinder.Match match = ToolFinder.findBestForBlock(player.getInventory(), blockState);
        if (match == null) return;

        applySwitch(match, Action.MINING, mc);
    }

    /**
     * Combat-path entry point — called by
     * {@code MultiPlayerGameModeAttackMixin} at the HEAD of
     * {@code MultiPlayerGameMode.attack(Player, Entity)}, BEFORE
     * vanilla reads the player's main-hand item for damage. This
     * timing is the whole reason we use a mixin instead of Fabric's
     * {@code AttackEntityCallback} (which fires too late).
     *
     * <p>Public because the mixin lives in a sibling package and needs
     * to reach in. Returns {@code void} (the mixin's CallbackInfo
     * doesn't need an interaction result; we don't cancel the attack).
     */
    public static void preAttackEntity(Player player, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !player.getUUID().equals(mc.player.getUUID())) return;
        if (!IPConfig.autoToolSwitchEnabled()) return;
        // Never auto-switch in creative.
        if (player.isCreative()) return;
        // Weapons toggle gates the combat path entirely.
        if (!IPConfig.autoToolSwitchWeapons()) return;
        // Only LivingEntity targets — projectiles, items, etc. don't get
        // weapon switches.
        if (!(target instanceof LivingEntity living)) return;
        // Mob-target filter — hostile (Monster class) by default; All
        // Mobs sub-toggle widens to passive mobs too.
        boolean isHostile = target instanceof Monster;
        if (!isHostile && !IPConfig.autoToolSwitchAllMobs()) return;
        if (player.isShiftKeyDown()) return;
        // Record combat activity for the auto-return grace window —
        // BEFORE the commit-at-start guard, so every qualifying attack
        // (including follow-up swings in a combo) pushes the return
        // timer forward, even though we don't re-switch.
        combatLastAttackTick = player.tickCount;
        // Commit-at-start: don't re-evaluate while a combat switch is
        // still in flight (rare but possible if the player chains
        // attacks within a single cooldown window).
        if (inFlight != null) return;

        ToolFinder.Match match = ToolFinder.findBestForMob(player.getInventory(), living);
        if (match == null) return;

        applySwitch(match, Action.COMBAT, mc);
    }

    // ─── Switch application ──────────────────────────────────────────

    /**
     * Apply a tool/weapon switch to the player's active slot. Captures
     * pre-switch state into {@link #inFlight} when auto-return is on,
     * so {@link #onClientTick} can reverse later.
     */
    private static void applySwitch(ToolFinder.Match match, Action action, Minecraft mc) {
        Player player = mc.player;
        Inventory inv = player.getInventory();
        int previousSelected = inv.getSelectedSlot();
        int slot = match.slotIndex();

        InFlight state = new InFlight();
        state.action = action;
        state.previousSelected = previousSelected;

        if (slot >= 0 && slot <= 8) {
            // Tier 1: tool is in another hotbar slot. Just change
            // selected. No item moves.
            inv.setSelectedSlot(slot);
        } else {
            int cyclablePos = HotbarCyclableRegistry.cyclablePosition(slot);
            if (cyclablePos != -1) {
                // Tier 2: cyclable. Change selected to the column's
                // hotbar position, then ask the cycler to bring the
                // slot's item down. Cycler returns an undo handle.
                inv.setSelectedSlot(cyclablePos);
                state.cyclerOperation = HotbarCyclableRegistry.bringToHotbar(slot);
            } else {
                // Tier 3: inv slot, not in any cycler. SWAP click
                // exchanges the slot's item with the active hotbar slot.
                doInventorySwap(slot, previousSelected, mc);
                state.invSwapSlot = slot;
                state.invSwapHotbar = previousSelected;
                // Capture the tool we just swapped into the active slot,
                // so auto-return can verify it's still there before
                // reversing (guards against breakage / restock changing it).
                state.invSwapExpectedActiveItem = inv.getItem(previousSelected).copy();
            }
        }

        // Retain in-flight state whenever the return mode returns at all (any
        // mode but OFF). A new switch supersedes any still-pending return from a
        // prior switch — you keep that earlier tool, and this switch's
        // previous-state becomes the new thing we can return to.
        pendingReturn = null;
        inFlight = IPConfig.autoToolSwitchReturnMode().returnsAtAll() ? state : null;
    }

    /**
     * Execute a SWAP click — exchanges the source slot's item with the
     * hotbar slot at the given index. Vanilla universally accepts SWAP
     * via the always-open InventoryMenu, so this works during gameplay
     * (no UI open) on dedicated MP without a server companion mod.
     *
     * <p>The reverse SWAP (same source + same hotbar) restores the
     * original arrangement — used by auto-return for tier 3.
     */
    private static void doInventorySwap(int sourceContainerSlot, int hotbarSlot, Minecraft mc) {
        Player player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        int sourceMenuSlot = findMenuSlot(menu, sourceContainerSlot, player.getUUID());
        if (sourceMenuSlot < 0) return;
        gameMode.handleContainerInput(
                menu.containerId,
                sourceMenuSlot,
                hotbarSlot,   // button = target hotbar slot (0-8)
                ContainerInput.SWAP,
                player);
    }

    /**
     * Find the menu slot index for a given container slot in the local
     * player's inventory. Uses UUID equality (not reference equality)
     * to match across thread boundaries — same pattern as other IP
     * features (LockedSlots, ColumnCyclerRotator).
     */
    private static int findMenuSlot(AbstractContainerMenu menu, int containerSlot, UUID localUuid) {
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory inv)) continue;
            if (!inv.player.getUUID().equals(localUuid)) continue;
            if (slot.getContainerSlot() == containerSlot) return slot.index;
        }
        return -1;
    }

    // ─── Auto-return ─────────────────────────────────────────────────

    private static void onClientTick(Minecraft mc) {
        // Always drain the return keybind, even when nothing's switched, so a
        // stray press doesn't fire on the next switch.
        boolean keyReturn = drainReturnKeybind();

        Player player = mc.player;
        if (player == null) {
            // Lost the player (disconnect, dimension change, etc.) — discard
            // pending state; reversal would be unsafe anyway.
            inFlight = null;
            pendingReturn = null;
            return;
        }
        if (inFlight == null && pendingReturn == null) return;

        AutoSwitchReturnMode mode = IPConfig.autoToolSwitchReturnMode();

        // HOTKEY_ANYTIME: the keybind returns whatever's active right now —
        // a still-pending return, or an in-flight switch mid-action — no window.
        if (mode == AutoSwitchReturnMode.HOTKEY_ANYTIME && keyReturn) {
            if (pendingReturn != null) {
                doReturn(pendingReturn, mc);
                pendingReturn = null;
            } else if (inFlight != null) {
                doReturn(inFlight, mc);
                inFlight = null;
            }
            keyReturn = false; // consumed
        }

        // When the triggering action finishes, hand the captured state off to
        // pendingReturn — for ALL returning modes (AUTOMATIC waits out the window
        // too now). inFlight clears here, keeping the anti-thrash guard +
        // re-switching exactly as before.
        if (inFlight != null && isActionComplete(inFlight, mc, player)) {
            if (mode != AutoSwitchReturnMode.OFF) {
                // Snapshot the (now-settled) post-switch state as the baseline;
                // if it later drifts, the player took manual control → void it.
                inFlight.baselineSlot = player.getInventory().getSelectedSlot();
                inFlight.baselineItem = player.getInventory().getItem(inFlight.baselineSlot).copy();
                pendingReturn = inFlight;
                pendingReturnWindowStart = player.tickCount;
            }
            inFlight = null;
        }

        // Manual control voids the return: the instant the player's selected slot
        // or the switched tool's slot drifts from the post-switch baseline (scroll,
        // number-key, manual cycle, move, break), drop the pending return so the
        // keybind / auto-return does nothing.
        if (pendingReturn != null && divergedFromBaseline(player)) {
            pendingReturn = null;
        }

        // Pending return.
        if (pendingReturn != null) {
            // Windowed modes (AUTOMATIC + HOTKEY_TIMED): hold the window open while
            // the action has resumed, so it only counts down once you've truly
            // stopped — the return never fires mid-action.
            if (mode.isWindowed() && !isActionComplete(pendingReturn, mc, player)) {
                pendingReturnWindowStart = player.tickCount;
            }
            switch (mode) {
                case AUTOMATIC -> {
                    // Auto-return once the idle window elapses.
                    if (windowExpired(player)) {
                        doReturn(pendingReturn, mc);
                        pendingReturn = null;
                    }
                }
                case HOTKEY_TIMED -> {
                    // Keybind within the window returns; else the window passes
                    // and the tool stays (new baseline).
                    if (keyReturn) {
                        doReturn(pendingReturn, mc);
                        pendingReturn = null;
                    } else if (windowExpired(player)) {
                        pendingReturn = null;
                    }
                }
                case HOTKEY_ANYTIME -> {
                    if (keyReturn) {
                        doReturn(pendingReturn, mc);
                        pendingReturn = null;
                    }
                }
                default -> pendingReturn = null; // OFF mid-flight → drop
            }
        }
    }

    /**
     * Whether the switch's triggering action has finished: mining LMB released,
     * or a full weapon-cooldown + grace buffer elapsed since the last combat
     * attack (cadence-aware via the held weapon's own cooldown, so a combo isn't
     * cut between swings). This is the same completion test the original
     * auto-return used; it now also gates opening the delayed-return window.
     */
    private static boolean isActionComplete(InFlight state, Minecraft mc, Player player) {
        if (state.action == Action.MINING) {
            return !mc.options.keyAttack.isDown();
        }
        float cooldownTicks = player.getCurrentItemAttackStrengthDelay();
        int ticksSinceAttack = player.tickCount - combatLastAttackTick;
        return combatLastAttackTick >= 0 && ticksSinceAttack >= cooldownTicks + COMBAT_RETURN_GRACE_TICKS;
    }

    /** Whether the pending-return window has elapsed (config seconds → ticks). */
    private static boolean windowExpired(Player player) {
        int cooldownTicks = Math.max(1, IPConfig.autoToolSwitchReturnCooldownSeconds()) * 20;
        return player.tickCount - pendingReturnWindowStart >= cooldownTicks;
    }

    /**
     * Whether the player has manually changed state since the switch: the selected
     * slot moved off the baseline, or the switched tool's slot no longer holds
     * that tool (scroll, number-key, manual cycle, move, swap, break). Compared
     * against the post-switch baseline (the auto-switch state), so the switch's own
     * effect doesn't count; durability ticks don't count (same-item compare).
     */
    private static boolean divergedFromBaseline(Player player) {
        if (pendingReturn == null) return false;
        Inventory inv = player.getInventory();
        if (inv.getSelectedSlot() != pendingReturn.baselineSlot) return true;
        return !ItemStack.isSameItem(inv.getItem(pendingReturn.baselineSlot), pendingReturn.baselineItem);
    }

    /** Consume + drain the return keybind; true if it was pressed this tick. */
    private static boolean drainReturnKeybind() {
        boolean pressed = false;
        while (IPKeybinds.AUTO_SWITCH_RETURN.consumeClick()) pressed = true;
        return pressed;
    }

    /**
     * Reverse the switch captured in {@code state}. Restores selected
     * slot + undoes whichever tier was applied.
     */
    private static void doReturn(InFlight state, Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        // Restore previous selected slot. This applies for all tiers
        // (it's the natural inverse of changing selected in applySwitch).
        inv.setSelectedSlot(state.previousSelected);
        // Tier 2: reverse the cycler operation via the undo handle the
        // cycler gave us. The cycler is the only party that knows how
        // to reverse its own action.
        if (state.cyclerOperation != null) {
            state.cyclerOperation.undo();
        }
        // Tier 3: reverse the SWAP by SWAPping the same two slots again
        // — but ONLY if the active slot still holds the tool we swapped
        // in. If the tool broke, was consumed, or auto-restock changed
        // the active slot, a blind reverse SWAP would corrupt the
        // inventory (yanking an unrelated item back into the hotbar and
        // dumping the current item into the source slot). Best-effort
        // reversal, mirroring the cycler's documented drift tolerance.
        if (state.invSwapSlot >= 0 && state.invSwapHotbar >= 0) {
            ItemStack current = inv.getItem(state.invSwapHotbar);
            if (ItemStack.isSameItem(current, state.invSwapExpectedActiveItem)) {
                doInventorySwap(state.invSwapSlot, state.invSwapHotbar, mc);
            }
            // else: state drifted since the switch — leave it as-is.
        }
    }

    // ─── In-flight state ─────────────────────────────────────────────

    private enum Action { MINING, COMBAT }

    /**
     * Captured pre-switch state for auto-return. One field per tier's
     * reversal info; only the relevant ones are set per switch.
     */
    private static class InFlight {
        Action action;
        int previousSelected;
        /** Tier 2: cycler-provided undo handle. Null for tiers 1 and 3. */
        CyclerOperation cyclerOperation = null;
        /** Tier 3: container slot of the swapped item. -1 for tiers 1 and 2. */
        int invSwapSlot = -1;
        /** Tier 3: hotbar slot used in the swap. -1 for tiers 1 and 2. */
        int invSwapHotbar = -1;
        /**
         * Tier 3: the item swapped into the active hotbar slot at switch
         * time. Auto-return reverses only if the active slot still holds
         * this item (same item type) — drift guard against breakage /
         * restock. EMPTY for tiers 1 and 2.
         */
        ItemStack invSwapExpectedActiveItem = ItemStack.EMPTY;
        /**
         * Post-switch baseline, captured when the switch hands off to
         * pendingReturn (action complete, so settled — incl. pocket round-trip):
         * the selected slot + the item in it. If either later drifts, the player
         * took manual control and the pending return is voided. Compared against
         * THIS (the auto-switch state), not the pre-switch state.
         */
        int baselineSlot = -1;
        ItemStack baselineItem = ItemStack.EMPTY;
    }
}
