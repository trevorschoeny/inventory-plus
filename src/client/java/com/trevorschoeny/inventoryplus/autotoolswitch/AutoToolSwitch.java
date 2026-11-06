package com.trevorschoeny.inventoryplus.autotoolswitch;

import com.trevorschoeny.inventoryplus.api.CyclerOperation;
import com.trevorschoeny.inventoryplus.api.PlayerMenuSlots;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.cyclable.HotbarCyclableRegistry;
import com.trevorschoeny.inventoryplus.operations.IPSlotOperations;

import static com.trevorschoeny.inventoryplus.api.InventoryPlusOperations.AUTO_TOOL_SWITCH;

import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.window.SlotOperations;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.state.BlockState;


/**
 * Auto Tool Switch: on hit, swaps the right tool or weapon for the target
 * into the hand, switching back one second after the player stops.
 *
 * <h3>Trigger</h3>
 *
 * Both block and entity paths invoke this class from mixins at the
 * HEAD of the relevant {@code MultiPlayerGameMode} methods, before
 * vanilla reads the player's main-hand item for destroy-speed or
 * attack-damage computation. Fabric's callbacks fire too late for that.
 *
 * <ul>
 *   <li><b>Block mining:</b> {@code MultiPlayerGameModeBlockAttackMixin}
 *       at the HEAD of {@code startDestroyBlock} (survival) and
 *       {@code destroyBlock} (creative) calls {@link #preAttackBlock}.</li>
 *   <li><b>Entity combat:</b> {@code MultiPlayerGameModeAttackMixin}
 *       at the HEAD of {@code attack(Player, Entity)} calls
 *       {@link #preAttackEntity}.</li>
 * </ul>
 *
 * While a switch is in flight, combat doesn't re-switch (chained attacks in
 * one cooldown keep the weapon). Mining does when the player, still holding
 * the button, turns to a block the tool in hand doesn't suit: the first
 * switch is undone and the right tool for the new block is switched in, so
 * the switch back still lands on what was held before any of it.
 *
 * <h3>Three-tier resolution</h3>
 *
 * Per {@link ToolFinder}, scans slots and stops at the first tier with
 * a matching item:
 * <ol>
 *   <li><b>Tier 1:</b> the slot is in the hotbar, so just change the
 *       selected slot. No item moves.</li>
 *   <li><b>Tier 2:</b> the slot is in a cyclable hotbar position. Change
 *       the selected slot to the column's hotbar position and call
 *       {@link HotbarCyclableRegistry#bringToHotbar} to rotate the
 *       cycler. The cycler returns a {@link CyclerOperation} undo
 *       handle for auto-return.</li>
 *   <li><b>Tier 3:</b> the slot is elsewhere in main inventory. SWAP
 *       click between the slot and the active hotbar slot.</li>
 * </ol>
 *
 * <h3>Sneak suppression</h3>
 *
 * While the player is holding Sneak (Shift), the switch is suppressed
 * entirely, following the convention of the "Automatic Tool Swap" mod. It
 * gives the player an always-available override. It is the one choice here
 * ({@link IPConfig#autoToolSwitchSneakPauses}, on by default).
 *
 * <h3>Weapon in hand</h3>
 *
 * Hitting a block with a sword, spear, mace or trident in hand switches
 * nothing ({@link IPConfig#autoToolSwitchKeepWeapon}, on by default): in a
 * fight, a swing that lands on the ground shouldn't swap the weapon away and
 * dig. Axes count as tools here.
 *
 * <h3>Auto-return</h3>
 *
 * The switch always goes back {@link #RETURN_DELAY_TICKS} after the player
 * stops. The mod captures pre-switch state (previous selected slot, plus the cycler undo handle or
 * inventory-swap reversal info) into {@link #inFlight}, then restores it
 * when the action completes:
 * <ul>
 *   <li>Mining: on LMB release, detected via the attack key's
 *       held state in {@link Minecraft#options}.</li>
 *   <li>Combat: when a full weapon cooldown plus a small grace buffer
 *       has elapsed since the last qualifying attack (tracked via
 *       {@link #combatLastAttackTick}). Each follow-up swing in a combo
 *       pushes that timer forward through {@link #preAttackEntity}, so
 *       the weapon isn't returned between swings.</li>
 * </ul>
 *
 * <p>Reversal mechanics:
 * <ul>
 *   <li>Tier 1: restore the previous selected slot.</li>
 *   <li>Tier 2: restore the previous selected slot and call
 *       {@code CyclerOperation.undo()}.</li>
 *   <li>Tier 3: restore the previous selected slot and reverse the SWAP
 *       (same two slots, again).</li>
 * </ul>
 */
public final class AutoToolSwitch {

    private AutoToolSwitch() {}

    /**
     * In-flight state, null when no switch is active. Set on switch and
     * cleared when the action completes. Read by the event handlers
     * (for commit-at-start gating) and the tick handler (for the return).
     */
    private static InFlight inFlight = null;

    /**
     * Delayed return state, waiting out {@link #RETURN_DELAY_TICKS} before
     * going back to the previous tool. Kept separate from {@link #inFlight} on purpose: inFlight
     * stays short-lived, cleared the moment the action completes, which keeps
     * the anti-thrash commit-at-start guard and re-switching working. When the
     * action completes, inFlight's captured state moves here and survives past
     * the action so the delay can run out. Null when
     * nothing is awaiting a delayed return.
     */
    private static InFlight pendingReturn = null;

    /** {@code player.tickCount} when the pending-return delay last started. */
    private static int pendingReturnWindowStart = -1;

    /**
     * Tick (player.tickCount) of the most recent qualifying combat
     * attack. Updated on every mob-attack that passes the filters, even
     * when the commit-at-start guard skips re-switching. Used by the
     * combat auto-return grace window so a player swinging at near-max
     * cadence isn't returned between swings (which would thrash).
     */
    private static int combatLastAttackTick = -1;

    /**
     * Extra ticks to wait after the weapon's attack-cooldown completes
     * before auto-returning in combat. Guards against the exact-cadence
     * race where the cooldown hits full on the same tick the player is
     * about to swing again. 4 ticks (0.2s) is enough buffer without
     * the weapon lingering noticeably after the player truly stops.
     */
    private static final int COMBAT_RETURN_GRACE_TICKS = 4;

    /** How long after the player stops the switch goes back: one second. */
    private static final int RETURN_DELAY_TICKS = 20;

    /**
     * Register the tick listener. Call once from
     * {@code InventoryPlusClient.onInitializeClient}.
     *
     * <p>The attack hooks (block and entity) are mixins (registered in
     * {@code inventoryplus.mixins.json}), not wired here. They call
     * {@link #preAttackBlock} and {@link #preAttackEntity} directly to fire
     * before vanilla's tool and weapon reads.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoToolSwitch::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(CarriedTools::tick);
    }

    /**
     * Block-path entry point, called by
     * {@code MultiPlayerGameModeBlockAttackMixin} at the HEAD of both
     * {@code startDestroyBlock} (survival) and {@code destroyBlock}
     * (creative), before vanilla reads the player's main hand for
     * destroy-speed or break logic. Never cancels the attack.
     */
    public static void preAttackBlock(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Player player = mc.player;
        if (!IPConfig.autoToolSwitchEnabled()) return;
        // Never auto-switch in creative: instant-break and infinite items make
        // it pointless, and a switch there is just noise.
        if (!ModeScope.SURVIVAL.includes(player)) return;
        // Sneak suppression: the player wants exact control.
        if (IPConfig.autoToolSwitchSneakPauses() && player.isShiftKeyDown()) return;
        BlockState blockState = player.level().getBlockState(pos);

        if (inFlight != null) {
            // A weapon switched in for a fight stays through a stray block hit.
            if (inFlight.action != Action.MINING) return;
            // Still holding the button, turned to another block (vanilla starts a
            // new break for it). The tool in hand suits it: keep mining.
            if (ToolFinder.isMatchingToolForBlock(player.getInventory().getSelectedItem(), blockState)) return;
            // It doesn't: undo the first switch, so this one starts from what the
            // player held before either and the switch back returns to that.
            doReturn(inFlight, mc);
            inFlight = null;
        } else if (IPConfig.autoToolSwitchKeepWeapon() && isFightingWeapon(player.getInventory().getSelectedItem())) {
            return;
        }

        ToolFinder.Match match = ToolFinder.findBestForBlock(player.getInventory(), blockState);
        if (match == null) {
            // No tool for it in the inventory, and none in the hand: one from a carried
            // container, if the Containers boxes allow (CarriedTools switches once it lands).
            if (!ToolFinder.isMatchingToolForBlock(player.getInventory().getSelectedItem(), blockState)
                    && player instanceof LocalPlayer local) {
                CarriedTools.forBlock(local, pos, blockState);
            }
            return;
        }

        applySwitch(match, Action.MINING, mc);
    }

    /**
     * Combat-path entry point, called by
     * {@code MultiPlayerGameModeAttackMixin} at the HEAD of
     * {@code MultiPlayerGameMode.attack(Player, Entity)}, before
     * vanilla reads the player's main-hand item for damage. That timing
     * is why this is a mixin rather than Fabric's
     * {@code AttackEntityCallback}, which fires too late. Never cancels
     * the attack.
     */
    public static void preAttackEntity(Player player, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !player.getUUID().equals(mc.player.getUUID())) return;
        if (!IPConfig.autoToolSwitchEnabled()) return;
        if (!ModeScope.SURVIVAL.includes(player)) return;
        // Only LivingEntity targets. Projectiles, items and so on don't get
        // weapon switches.
        if (!(target instanceof LivingEntity living)) return;
        // Hostile mobs only: hitting a cow or a villager is rarely a fight.
        if (!(target instanceof Monster)) return;
        if (IPConfig.autoToolSwitchSneakPauses() && player.isShiftKeyDown()) return;
        // Record combat activity for the auto-return grace window before
        // the commit-at-start guard, so every qualifying attack (including
        // follow-up swings in a combo) pushes the return timer forward,
        // even though it doesn't re-switch.
        combatLastAttackTick = player.tickCount;
        // Commit-at-start: don't re-evaluate while a combat switch is
        // still in flight (rare but possible if the player chains
        // attacks within a single cooldown window).
        if (inFlight != null) return;

        ToolFinder.Match match = ToolFinder.findBestForMob(player.getInventory(), living);
        if (match == null) {
            // No weapon in the inventory or the hand: one from a carried container; the
            // next swing finds it.
            if (!ToolFinder.isWeapon(player.getInventory().getSelectedItem()) && player instanceof LocalPlayer local) {
                CarriedTools.forMob(local);
            }
            return;
        }

        applySwitch(match, Action.COMBAT, mc);
    }

    /**
     * Apply a tool or weapon switch to the player's active slot. Captures
     * pre-switch state into {@link #inFlight} when it switches back at all,
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
            // Tier 1: the tool is in another hotbar slot. Just change the
            // selection. No item moves.
            inv.setSelectedSlot(slot);
        } else {
            int cyclablePos = HotbarCyclableRegistry.cyclablePosition(slot);
            if (cyclablePos != -1) {
                // Tier 2: cyclable. Change the selection to the column's
                // hotbar position, then ask the cycler to bring the
                // slot's item down. The cycler returns an undo handle.
                inv.setSelectedSlot(cyclablePos);
                state.cyclerOperation = HotbarCyclableRegistry.bringToHotbar(slot, AUTO_TOOL_SWITCH, AUTO_TOOL_SWITCH);
            } else {
                // Tier 3: an inventory slot in no cycler. A SWAP click
                // exchanges the slot's item with the active hotbar slot.
                // The source was screened by ToolFinder. The hand's slot gives
                // up its item and takes the tool, so it is asked here. If it
                // refuses, there is no switch at all and nothing to return from.
                if (!IPSlotOperations.allowsPlayerSlot(player, previousSelected, AUTO_TOOL_SWITCH)) return;
                doInventorySwap(slot, previousSelected, mc);
                state.invSwapSlot = slot;
                state.invSwapHotbar = previousSelected;
                // Capture the tool just swapped into the active slot, so
                // auto-return can verify it's still there before reversing
                // (guards against breakage or restock changing it).
                state.invSwapExpectedActiveItem = inv.getItem(previousSelected).copy();
            }
        }

        // Retain in-flight state whenever it switches back at all. A new
        // switch supersedes any still-pending return from a prior switch: the
        // player keeps that earlier tool, and this switch's previous state
        // becomes the new thing to return to.
        pendingReturn = null;
        inFlight = state;
    }

    /** A weapon held for fighting: sword, spear, mace or trident. Axes are tools here. */
    private static boolean isFightingWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }

    /**
     * Execute a SWAP click, exchanging the source slot's item with the
     * hotbar slot at the given index. Vanilla universally accepts SWAP
     * via the always-open InventoryMenu, so this works during gameplay
     * (no UI open) on dedicated MP without a server companion mod.
     *
     * <p>The reverse SWAP (same source and hotbar slot) restores the
     * original arrangement, which is how auto-return undoes tier 3.
     */
    private static void doInventorySwap(int sourceContainerSlot, int hotbarSlot, Minecraft mc) {
        Player player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;
        int sourceMenuSlot = PlayerMenuSlots.menuIndexOf(menu, player, sourceContainerSlot);
        if (sourceMenuSlot < 0) return;
        // Judged as Auto Tool Switch, not as the hotbar-swap key it looks like.
        // The auto-return swap comes back through here too.
        SlotOperations.as(AUTO_TOOL_SWITCH, () -> gameMode.handleContainerInput(
                menu.containerId,
                sourceMenuSlot,
                hotbarSlot,   // button = target hotbar slot (0-8)
                ContainerInput.SWAP,
                player));
    }


    // Auto-return

    private static void onClientTick(Minecraft mc) {
        Player player = mc.player;
        if (player == null) {
            // Lost the player (disconnect, dimension change and so on). Discard
            // pending state, since reversal would be unsafe anyway.
            inFlight = null;
            pendingReturn = null;
            return;
        }
        if (inFlight == null && pendingReturn == null) return;

        // When the triggering action finishes, hand the captured state off to
        // pendingReturn. inFlight clears here, which keeps re-switching working.
        if (inFlight != null && isActionComplete(inFlight, mc, player)) {
            // Snapshot the now-settled post-switch state as the baseline. If it
            // later drifts, the player took manual control, which voids the return.
            inFlight.baselineSlot = player.getInventory().getSelectedSlot();
            inFlight.baselineItem = player.getInventory().getItem(inFlight.baselineSlot).copy();
            pendingReturn = inFlight;
            pendingReturnWindowStart = player.tickCount;
            inFlight = null;
        }

        // Manual control voids the return: the instant the player's selected slot
        // or the switched tool's slot drifts from the post-switch baseline (scroll,
        // number key, manual cycle, move, break), drop the pending return.
        if (pendingReturn != null && divergedFromBaseline(player)) {
            pendingReturn = null;
        }

        if (pendingReturn != null) {
            // Hold the window open while the action has resumed, so it only
            // counts down once the player has truly stopped and the return
            // never fires mid-action. Then return once it has passed.
            if (!isActionComplete(pendingReturn, mc, player)) {
                pendingReturnWindowStart = player.tickCount;
            } else if (player.tickCount - pendingReturnWindowStart >= RETURN_DELAY_TICKS) {
                doReturn(pendingReturn, mc);
                pendingReturn = null;
            }
        }
    }

    /**
     * Whether the switch's triggering action has finished: mining LMB released,
     * or a full weapon cooldown plus the grace buffer elapsed since the last
     * combat attack (cadence-aware via the held weapon's own cooldown, so a
     * combo isn't cut between swings). Also gates opening the delayed-return
     * window.
     */
    private static boolean isActionComplete(InFlight state, Minecraft mc, Player player) {
        if (state.action == Action.MINING) {
            return !mc.options.keyAttack.isDown();
        }
        float cooldownTicks = player.getCurrentItemAttackStrengthDelay();
        int ticksSinceAttack = player.tickCount - combatLastAttackTick;
        return combatLastAttackTick >= 0 && ticksSinceAttack >= cooldownTicks + COMBAT_RETURN_GRACE_TICKS;
    }

    /**
     * Whether the player has manually changed state since the switch: the selected
     * slot moved off the baseline, or the switched tool's slot no longer holds
     * that tool (scroll, number key, manual cycle, move, swap, break). Compared
     * against the post-switch baseline (the auto-switch state), so the switch's own
     * effect doesn't count, and durability ticks don't count (same-item compare).
     */
    private static boolean divergedFromBaseline(Player player) {
        if (pendingReturn == null) return false;
        Inventory inv = player.getInventory();
        if (inv.getSelectedSlot() != pendingReturn.baselineSlot) return true;
        return !ItemStack.isSameItem(inv.getItem(pendingReturn.baselineSlot), pendingReturn.baselineItem);
    }

    /**
     * Reverse the switch captured in {@code state}. Restores the selected
     * slot and undoes whichever tier was applied.
     */
    private static void doReturn(InFlight state, Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        inv.setSelectedSlot(state.previousSelected);
        // Tier 2: reverse the cycler operation via its undo handle. The
        // cycler is the only party that knows how to reverse its own action.
        if (state.cyclerOperation != null) {
            state.cyclerOperation.undo();
        }
        // Tier 3: reverse the SWAP by swapping the same two slots again,
        // but only if the active slot still holds the tool that was swapped
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
            // Otherwise the state drifted since the switch. Leave it as is.
        }
    }

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
         * this item (same item type), a drift guard against breakage or
         * restock. EMPTY for tiers 1 and 2.
         */
        ItemStack invSwapExpectedActiveItem = ItemStack.EMPTY;
        /**
         * Post-switch baseline, captured when the switch hands off to
         * pendingReturn (action complete, so settled, including a pocket
         * round-trip): the selected slot and the item in it. If either later
         * drifts, the player took manual control and the pending return is
         * voided. Compared against this (the auto-switch state), not the
         * pre-switch state.
         */
        int baselineSlot = -1;
        ItemStack baselineItem = ItemStack.EMPTY;
    }
}
