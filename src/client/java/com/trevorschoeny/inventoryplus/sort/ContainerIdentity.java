package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.mixin.CompoundContainerAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import org.jetbrains.annotations.Nullable;

/**
 * Identifies a sortable container for per-container sort-state
 * persistence in {@link SortState}.
 *
 * <h3>Identity schemes</h3>
 *
 * <ul>
 *   <li><b>{@code playerinv}</b>: the player's 3x9 main inventory. Stable
 *       per world.</li>
 *   <li><b>{@code enderchest}</b>: the player's ender chest. Stable per
 *       world, and the same wherever the player opens it.</li>
 *   <li><b>{@code block:&lt;dim&gt;:&lt;x&gt;,&lt;y&gt;,&lt;z&gt;}</b>: any
 *       block-anchored simple container (chest, barrel, hopper,
 *       dispenser, dropper, placed shulker box).</li>
 * </ul>
 *
 * <h3>Shulker limitation</h3>
 *
 * A shulker box that is broken and placed again gets a new block-position
 * identity at the new location, so its sort state resets when it moves. An
 * identity stored on the closed box would fix this, and isn't implemented.
 *
 * <h3>Unsupported containers</h3>
 *
 * Returns {@code null} for specialized UIs (furnace, anvil, crafting input
 * and result, enchanting table). The caller treats null as not sortable,
 * and the key does nothing.
 */
public final class ContainerIdentity {

    private final String key;

    private ContainerIdentity(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return "ContainerIdentity[" + key + "]";
    }

    /**
     * Resolves the identity for the sortable region that contains the
     * given slot, or {@code null} if the slot isn't part of a sortable
     * region (specialized UI, hotbar, armor).
     *
     * <p>The {@code slot} is the one the player's cursor is over when
     * they press the sort keybind. {@code menu} is the open menu (only
     * used to disambiguate compound containers).
     */
    public static @Nullable ContainerIdentity fromHoveredSlot(Slot slot, AbstractContainerMenu menu) {
        Container container = slot.container;
        int containerSlot = slot.getContainerSlot();

        // In the player's own inventory only the 3x9 main grid (container
        // slots 9-35) is sortable, never the hotbar (0-8), armor (36-39) or
        // offhand (40).
        if (container instanceof Inventory) {
            if (containerSlot >= 9 && containerSlot <= 35) {
                return new ContainerIdentity("playerinv");
            }
            return null;
        }

        // Vanilla uses PlayerEnderChestContainer for an open ender chest.
        // Same identity wherever it's opened.
        if (container instanceof PlayerEnderChestContainer) {
            return new ContainerIdentity("enderchest");
        }

        // For external (non-player-inv) containers, only sort if the
        // menu is a recognized simple-container type. This excludes
        // specialized UIs (furnace, anvil, enchanting, crafting,
        // beacon, brewing, loom, stonecutter and so on) whose slots carry
        // semantic meaning that sorting would scramble.
        if (!isSortableContainerMenu(menu)) return null;

        // Direct BlockEntity-backed container (rare on the client; mostly
        // a server-side or single-player edge case).
        BlockPos pos = blockPosOf(container);
        if (pos != null) {
            return blockIdentity(pos);
        }

        // SimpleContainer-backed menus (the common client-side case for
        // chests, barrels, hoppers, dispensers, droppers, shulker boxes)
        // don't expose a BlockPos directly. Fall back to the open-click
        // tracker that captured the BlockPos via UseBlockCallback when
        // the player right-clicked to open the container.
        BlockPos trackedPos = ContainerOpenTracker.getBlockPos(menu.containerId);
        if (trackedPos != null) {
            return blockIdentity(trackedPos);
        }

        // A sortable menu type with no BlockPos was opened some other way
        // (other mods, commands). A session-only identity lets sort still
        // run without persisting anything.
        return new ContainerIdentity("session:" + menu.containerId);
    }

    /**
     * True if the given menu is a vanilla simple-container UI that this mod
     * sorts. A fixed list, so a new sortable menu is added here.
     *
     * <p>Public so the Sort toolbar button, which has no cursor target, can
     * use it to gate its visibility and click.
     */
    public static boolean isSortableContainerMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu      // chest, barrel, ender chest
                || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu
                || menu instanceof DispenserMenu;
    }

    /**
     * True if this identity is persistent (survives game restart and world
     * reload). Session identities are never written to disk.
     */
    public boolean isPersistent() {
        return !key.startsWith("session:");
    }

    /**
     * Walks a {@link Container} to find its anchoring block position.
     * Handles direct block entities and double chests (which wrap two
     * halves in a {@link CompoundContainer}).
     */
    private static @Nullable BlockPos blockPosOf(Container container) {
        if (container instanceof BlockEntity be) {
            return be.getBlockPos();
        }
        if (container instanceof CompoundContainer compound) {
            // A double chest wraps two ChestBlockEntity halves. Use
            // container1's position so both halves resolve to the same key,
            // whichever side the player clicked.
            CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
            Container half1 = accessor.inventoryplus$getContainer1();
            if (half1 instanceof BlockEntity halfBE) {
                return halfBE.getBlockPos();
            }
            // Try the other half. Vanilla double chests shouldn't need this.
            Container half2 = accessor.inventoryplus$getContainer2();
            if (half2 instanceof BlockEntity halfBE) {
                return halfBE.getBlockPos();
            }
            return null;
        }
        return null;
    }

    private static ContainerIdentity blockIdentity(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        String dim;
        if (player != null && player.level() != null) {
            dim = player.level().dimension().identifier().toString();
            pos = canonical(player.level().getBlockState(pos), pos);
        } else {
            dim = "unknown";
        }
        return new ContainerIdentity(
                "block:" + dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    /**
     * One identity per double chest, whichever half was clicked. A double
     * chest is two block positions and one inventory; without this, a pin
     * or a lock set from the left half vanished when the right half was
     * opened. The canonical half is the lexicographically smaller
     * position, which is stable under joining and splitting. Locked slots
     * and Sort's pins both rely on it.
     */
    static BlockPos canonical(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ChestBlock)) return pos;
        if (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return pos;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        int c = Integer.compare(pos.getX(), other.getX());
        if (c == 0) c = Integer.compare(pos.getY(), other.getY());
        if (c == 0) c = Integer.compare(pos.getZ(), other.getZ());
        return c <= 0 ? pos : other;
    }

    /**
     * The persistent block identity of the container an open menu shows, or
     * null when the menu is not a simple container or the opening block is
     * unknown (a non-click open path). Menu-level, so Locked Slots can key a
     * whole screen once rather than per hovered slot.
     */
    public static @Nullable ContainerIdentity forMenu(AbstractContainerMenu menu) {
        if (!isSortableContainerMenu(menu)) return null;
        BlockPos tracked = ContainerOpenTracker.getBlockPos(menu.containerId);
        return tracked == null ? null : blockIdentity(tracked);
    }

    /** The identity a block at {@code pos} would have, for pruning when it is broken. */
    public static ContainerIdentity forBlock(BlockPos pos) {
        return blockIdentity(pos);
    }

    /**
     * The persistent key of a container, derived from the container itself
     * rather than the client's open-click tracker, or null if it is not
     * block-anchored.
     *
     * <p><b>This works on either thread.</b> On the integrated
     * server thread {@code slot.container} is the real {@link BlockEntity} (or
     * the {@link CompoundContainer} wrapping two of them), so position and
     * dimension are both readable without touching {@link Minecraft}. The
     * client's menu wraps a bare container with no block identity, which is why
     * the render thread still goes through the tracker. Both paths canonicalise
     * the same way, so a double chest cannot resolve to two different keys
     * depending on which thread asked.
     *
     * <p>Added for 1.5.1: 1.5.0 gated placed-container locks on the render
     * thread, so the authoritative server-side move could not see them.
     */
    public static @Nullable String keyForContainer(Container container) {
        BlockPos pos = blockPosOf(container);
        if (pos == null) return null;
        Level level = levelOf(container);
        if (level == null) return null;
        BlockPos canon = canonical(level.getBlockState(pos), pos);
        return "block:" + level.dimension().identifier() + ":"
                + canon.getX() + "," + canon.getY() + "," + canon.getZ();
    }

    /** The level behind a block-anchored container, on either thread. */
    private static @Nullable Level levelOf(Container container) {
        if (container instanceof BlockEntity be) return be.getLevel();
        if (container instanceof CompoundContainer compound) {
            CompoundContainerAccessor a = (CompoundContainerAccessor) compound;
            if (a.inventoryplus$getContainer1() instanceof BlockEntity be) return be.getLevel();
            if (a.inventoryplus$getContainer2() instanceof BlockEntity be) return be.getLevel();
        }
        return null;
    }
}
