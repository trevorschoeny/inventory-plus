package com.trevorschoeny.inventoryplus.movematching;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.jetbrains.annotations.Nullable;

/**
 * Captures the player's most-recent block-use position so a freshly
 * opened simplecontainer screen can be associated with the correct
 * {@link ContainerKey}.
 *
 * <h3>Why a capture rather than a query</h3>
 *
 * Vanilla's {@code ClientboundOpenScreenPacket} doesn't carry block
 * coordinates; client-side, we don't natively know which chest was just
 * opened. The {@link UseBlockCallback} fires at right-click time and
 * pins the position before the server's open-screen response arrives.
 *
 * <h3>Fallback to hitResult</h3>
 *
 * When no recent use-block fire was captured (e.g., a mod opens a
 * container screen without {@link UseBlockCallback} firing), we fall
 * back to {@link Minecraft#hitResult} — the player's current look
 * target. Less robust (the player may have looked away by then) but
 * functional in most cases.
 *
 * <h3>Minecart variants</h3>
 *
 * Vehicle right-click (chest/hopper minecart, etc.) fires
 * {@code UseEntityCallback}, not {@code UseBlockCallback}. We don't
 * subscribe to that event for the smoke pass; minecart variants fall
 * back to no-key, which means they read the global default cycle and
 * setting changes don't persist. Filed as polish.
 */
public final class ContainerKeyResolver {

    private ContainerKeyResolver() {}

    /** Most recently captured use-block position; null until first use. */
    private static @Nullable BlockPos lastUsedBlock = null;

    public static void registerUseBlockCapture() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            // Capture regardless of interaction result — even cancelled
            // /no-op uses can produce screens client-side; over-capturing
            // is benign because the position is read once at screen open.
            lastUsedBlock = hit.getBlockPos();
            return InteractionResult.PASS;
        });
    }

    /**
     * Returns the best-available block position for keying the current
     * screen's non-player container, or {@code null} if none is
     * available.
     *
     * <p>Public for {@link SlotGroupDetector}'s use. Not part of the
     * mod's public API outside this package.
     */
    public static @Nullable BlockPos lastUsedBlockPosForKey() {
        if (lastUsedBlock != null) return lastUsedBlock;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof BlockHitResult bhr
                && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            return bhr.getBlockPos();
        }
        InventoryPlusClient.LOGGER.debug(
                "[move-matching] no block-pos candidate captured "
                + "— this container will use the global default cycle");
        return null;
    }
}
