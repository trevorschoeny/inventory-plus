package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the block position the player most-recently right-clicked,
 * and associates it with the {@link
 * net.minecraft.world.inventory.AbstractContainerMenu#containerId} of
 * the container screen that opens immediately after.
 *
 * <h3>Why this exists</h3>
 *
 * On the client, {@code AbstractContainerMenu.slots[i].container} for
 * chests, barrels, hoppers, dispensers, droppers, and shulker boxes
 * resolves to a {@link net.minecraft.world.SimpleContainer} (a
 * synthesized client-side mirror), NOT the underlying
 * {@code BlockEntity}. Block-position info isn't carried in the
 * {@code ClientboundOpenScreenPacket}, so there's no in-menu path from
 * the client to "which block opened this menu."
 *
 * <p>This tracker bridges that gap with the standard mod-side
 * heuristic: capture the {@code BlockPos} from the
 * {@link UseBlockCallback} event (which fires when the player
 * right-clicks a block), and on the next {@code AFTER_INIT} for a
 * container screen, associate the captured pos with the menu's
 * {@code containerId}. Cleared when the screen closes.
 *
 * <h3>Edge cases (acceptable)</h3>
 *
 * <ul>
 *   <li>Right-click that doesn't open a container (e.g., on dirt) →
 *       capture is stored but never associated (the next AFTER_INIT
 *       won't fire). The stale capture would attach to the next
 *       genuinely-opened menu — bounded TTL (1 second) limits the
 *       blast radius.</li>
 *   <li>Container opens via non-click path (e.g., another mod's
 *       command, vehicle, etc.) → no capture available; {@link
 *       #getBlockPos} returns null. Sort still runs for those
 *       containers (per {@link ContainerIdentity}) but uses a
 *       session-only identity that doesn't persist.</li>
 * </ul>
 */
public final class ContainerOpenTracker {

    private ContainerOpenTracker() {}

    /** Time window between right-click and screen-open to count as related. */
    private static final long ASSOCIATION_TTL_MS = 1000;

    private static @Nullable BlockPos lastClickedBlockPos = null;
    private static long lastClickedAtMs = 0;

    /** containerId → BlockPos for the currently-open menu (cleared on close). */
    private static final Map<Integer, BlockPos> openMenuBlockPos = new HashMap<>();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            lastClickedBlockPos = hitResult.getBlockPos();
            lastClickedAtMs = System.currentTimeMillis();
            return InteractionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            long age = System.currentTimeMillis() - lastClickedAtMs;
            if (lastClickedBlockPos == null || age > ASSOCIATION_TTL_MS) return;
            int containerId = acs.getMenu().containerId;
            openMenuBlockPos.put(containerId, lastClickedBlockPos);
            InventoryPlusClient.LOGGER.debug(
                    "[sort] associated containerId={} with blockPos={}",
                    containerId, lastClickedBlockPos);
            // Consume the capture so a single right-click doesn't attach to
            // multiple menus (defensive against weird timing).
            lastClickedBlockPos = null;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Hook the screen's remove callback to drop the association
            // when the menu closes.
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            int containerId = acs.getMenu().containerId;
            ScreenEvents.remove(screen).register(closing -> {
                openMenuBlockPos.remove(containerId);
            });
        });
    }

    /** Returns the BlockPos associated with the given menu's containerId, or null. */
    public static @Nullable BlockPos getBlockPos(int containerId) {
        return openMenuBlockPos.get(containerId);
    }
}
