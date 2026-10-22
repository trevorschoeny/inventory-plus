package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the block position the player most recently right-clicked,
 * and associates it with the {@link
 * net.minecraft.world.inventory.AbstractContainerMenu#containerId} of
 * the container screen that opens immediately after.
 *
 * <h3>Why this exists</h3>
 *
 * On the client, {@code AbstractContainerMenu.slots[i].container} for
 * chests, barrels, hoppers, dispensers, droppers, and shulker boxes
 * resolves to a {@link net.minecraft.world.SimpleContainer} (a
 * synthesized client-side mirror), not the underlying
 * {@code BlockEntity}. Block-position info isn't carried in the
 * {@code ClientboundOpenScreenPacket}, so there's no in-menu path from
 * the client to "which block opened this menu."
 *
 * <p>This tracker bridges that gap with the usual mod-side heuristic: capture the {@code BlockPos} from the
 * {@link UseBlockCallback} event (which fires when the player
 * right-clicks a block), and on the next {@code AFTER_INIT} for a
 * container screen, associate the captured pos with the menu's
 * {@code containerId}. Cleared when the screen closes.
 *
 * <h3>Accepted edge cases</h3>
 *
 * <ul>
 *   <li>A right-click that doesn't open a container (on dirt, say) stores a
 *       capture that is never associated, because no AFTER_INIT follows.
 *       The stale capture could attach to the next menu that does open, so
 *       a 1 second TTL limits the damage.</li>
 *   <li>A container opened some other way (another mod's command, a
 *       vehicle) has no capture, so {@link #getBlockPos} returns null.
 *       Sort still runs for those containers (see {@link ContainerIdentity})
 *       but uses a session-only identity that doesn't persist.</li>
 * </ul>
 */
public final class ContainerOpenTracker {

    private ContainerOpenTracker() {}

    /** Time window between right-click and screen-open to count as related. */
    private static final long ASSOCIATION_TTL_MS = 1000;

    private static @Nullable BlockPos lastClickedBlockPos = null;
    private static long lastClickedAtMs = 0;

    /** containerId to BlockPos for the open menu, cleared on close. */
    private static final Map<Integer, BlockPos> openMenuBlockPos = new HashMap<>();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            lastClickedBlockPos = hitResult.getBlockPos();
            lastClickedAtMs = System.currentTimeMillis();
            return InteractionResult.PASS;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            int containerId = acs.getMenu().containerId;
            settledMenu = acs.getMenu();   // whatever happens below, this menu's answer is final now
            long age = System.currentTimeMillis() - lastClickedAtMs;
            if (lastClickedBlockPos == null || age > ASSOCIATION_TTL_MS) return;
            openMenuBlockPos.put(containerId, lastClickedBlockPos);
            InventoryPlusClient.LOGGER.debug(
                    "[sort] associated containerId={} with blockPos={}",
                    containerId, lastClickedBlockPos);
            // Consume the capture so one right-click can't attach to several menus.
            lastClickedBlockPos = null;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Drop the association when the menu closes.
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            int containerId = acs.getMenu().containerId;
            ScreenEvents.remove(screen).register(closing -> {
                openMenuBlockPos.remove(containerId);
            });
        });
    }

    /**
     * The last menu whose screen finished init, so whose block (or none) is
     * known. Held by identity: container ids repeat (they start again in each
     * world), so an id cannot say which menu it was.
     */
    private static @Nullable AbstractContainerMenu settledMenu;

    /**
     * Whether {@link #getBlockPos} has its final answer for {@code menu}: its
     * screen has finished init, where the block is tied to it. Asked earlier (a
     * panel laid out during init, such as a container list placing slots), it
     * may still be null for a menu that does have a block.
     */
    public static boolean settled(AbstractContainerMenu menu) {
        return settledMenu == menu;
    }

    public static @Nullable BlockPos getBlockPos(int containerId) {
        return openMenuBlockPos.get(containerId);
    }

    /**
     * The {@link Block} backing the currently-open container menu, or null if no
     * container is open, none was opened via a tracked right-click, or the
     * position is unloaded.
     *
     * <p>The shared answer to "what container am I actually in" on the client,
     * where the open menu's slots wrap a generic {@code SimpleContainer}, not
     * the real BlockEntity, so the slot can't tell a chest from an ender chest
     * or a furnace. Container locks use it to send the ender chest to the
     * client store and placed simple containers to the shared channel. Sort
     * uses the position form above.
     *
     * <p>Client-only (reads {@link Minecraft}). Callers on the integrated-server
     * thread must not call it, and don't need to, since the real container is
     * visible there directly. Returns whatever block is at the tracked position, so callers
     * decide which block types they accept.
     */
    public static @Nullable Block openContainerBlock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return null;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) return null;
        BlockPos pos = getBlockPos(menu.containerId);
        if (pos == null) return null;
        return mc.level.getBlockState(pos).getBlock();
    }
}
