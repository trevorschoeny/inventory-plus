package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves a {@link ContainerKey} for the currently-open screen.
 *
 * <h3>How block positions are captured</h3>
 *
 * Vanilla's open-screen packet doesn't carry the originating block position;
 * client-side, we don't natively know which chest was opened. We track the
 * player's most-recent {@link UseBlockCallback} as the candidate "thing they
 * just interacted with", and read that when a screen opens shortly after.
 *
 * <p>{@link Minecraft#hitResult} is the cheap fallback when no recent
 * use-block fire was captured (e.g., screen opened by some path other than
 * right-clicking a block — none in vanilla today for simplecontainers, but
 * a robust default).
 *
 * <h3>Edge cases the smoke pass accepts</h3>
 *
 * <ul>
 *   <li><b>Minecart hoppers / minecart chests.</b> Vehicle right-click doesn't
 *       fire {@link UseBlockCallback}; we fall back to no-key (global
 *       default). Minecart variants are a low-frequency case; per-container
 *       persistence for them needs a {@code UseEntityCallback} hook layered
 *       on top — deferred.</li>
 *   <li><b>Multi-block-entity chests (double chests).</b> The position we
 *       capture is the half the player clicked. Both halves of a double
 *       chest end up with the same setting because the player only opens
 *       one half. Same-coords-across-dims edge case noted in
 *       {@link ContainerKey} javadoc.</li>
 * </ul>
 */
public final class ContainerKeyResolver {

    private ContainerKeyResolver() {}

    /**
     * The most-recent block position the player invoked
     * {@link UseBlockCallback} on. Captured at use-block fire time, then
     * read when the corresponding screen opens within the next few ticks.
     * Null until the first use-block fires; reset only when a new use fires.
     */
    private static @Nullable BlockPos lastUsedBlock = null;

    /**
     * Wires the use-block listener that captures position. Called once at
     * mod init.
     */
    public static void registerUseBlockCapture() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            // Capture the block position regardless of interaction result —
            // even cancelled/no-op uses can open menus on the client side
            // (e.g., gated containers that send an open-screen packet on
            // success). The capture is cheap; over-capturing is benign.
            lastUsedBlock = hit.getBlockPos();
            // We don't consume the event; return PASS so vanilla and other
            // mods handle it normally.
            return InteractionResult.PASS;
        });
    }

    /**
     * Resolves the {@link ContainerKey} for the given screen, or returns
     * {@code null} if the screen isn't an eligible simplecontainer (or no
     * block position could be captured for a block-backed flavor).
     *
     * <p>Resolution rules per {@code move-matching.md} §"Scope":
     * <ul>
     *   <li>{@link InventoryScreen} → {@link ContainerKey#PLAYER_INVENTORY}.</li>
     *   <li>Any other {@link AbstractContainerScreen} → use the last
     *       captured block pos to build {@link ContainerKey.Block} or
     *       {@link ContainerKey#ENDER_CHEST} (detected by checking whether
     *       the block at that position is an {@link EnderChestBlock}).</li>
     * </ul>
     */
    public static @Nullable ContainerKey resolve(Screen screen) {
        if (screen == null) return null;

        if (screen instanceof InventoryScreen) {
            return ContainerKey.PLAYER_INVENTORY;
        }

        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return null;
        }

        BlockPos pos = bestBlockPosCandidate();
        if (pos == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockState(pos).getBlock() instanceof EnderChestBlock) {
            return ContainerKey.ENDER_CHEST;
        }

        return new ContainerKey.Block(pos);
    }

    /**
     * Picks the best available block position — the use-block capture if
     * present, else the player's current hit-result block, else null.
     *
     * <p>Used-block is preferred over hit-result because the player may
     * have looked away from the block they just interacted with before the
     * screen finishes opening; the use-block capture pins the position at
     * interaction time.
     */
    private static @Nullable BlockPos bestBlockPosCandidate() {
        if (lastUsedBlock != null) return lastUsedBlock;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult bhr
                && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return bhr.getBlockPos();
        }
        InventoryPlusClient.LOGGER.debug(
                "[move-matching] no block-pos candidate for screen — "
                + "falling back to no-key (global default)");
        return null;
    }
}
