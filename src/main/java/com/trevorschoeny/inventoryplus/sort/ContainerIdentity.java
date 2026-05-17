package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.mixin.CompoundContainerAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

/**
 * Identifies a sortable container for per-container sort-state
 * persistence in {@link SortState}.
 *
 * <h3>Identity schemes</h3>
 *
 * <ul>
 *   <li><b>{@code playerinv}</b> — the player's 3×9 main inventory.
 *       Stable per-world.</li>
 *   <li><b>{@code enderchest}</b> — the player's ender chest. Stable
 *       per-world; same identity wherever the player opens it.</li>
 *   <li><b>{@code block:&lt;dim&gt;:&lt;x&gt;,&lt;y&gt;,&lt;z&gt;}</b> — any
 *       block-anchored simple container (chest, barrel, hopper,
 *       dispenser, dropper, placed shulker box).</li>
 * </ul>
 *
 * <h3>Shulker limitation</h3>
 *
 * Shulker boxes that get broken and replaced get a new block-position
 * identity at the new location → sort state effectively resets on
 * move. The closed-state-ID scheme described in
 * {@code IP features/shulker-portability.md} would fix this; not
 * implemented yet.
 *
 * <h3>Unsupported containers</h3>
 *
 * Returns {@code null} for specialized UIs (furnace, anvil, crafting
 * input/result, enchanting table, etc.). Caller treats null as "not
 * sortable; keybind no-ops."
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
     * region (specialized UI, hotbar, armor, etc.).
     *
     * <p>The {@code slot} is the one the player's cursor is over when
     * they press the sort keybind. {@code menu} is the open menu (only
     * used to disambiguate compound containers).
     */
    public static @Nullable ContainerIdentity fromHoveredSlot(Slot slot, AbstractContainerMenu menu) {
        Container container = slot.container;
        int containerSlot = slot.getContainerSlot();

        // Player's own inventory — only the 3×9 main inv (container-slot
        // 9-35) is sortable. Hotbar (0-8), armor (36-39), offhand (40)
        // are excluded by spec.
        if (container instanceof Inventory) {
            if (containerSlot >= 9 && containerSlot <= 35) {
                return new ContainerIdentity("playerinv");
            }
            return null;
        }

        // Ender chest — vanilla uses PlayerEnderChestContainer for the
        // open menu's container. Same identity wherever it's opened.
        if (container instanceof PlayerEnderChestContainer) {
            return new ContainerIdentity("enderchest");
        }

        // Block-anchored container — extract block position.
        BlockPos pos = blockPosOf(container);
        if (pos != null) {
            return blockIdentity(pos);
        }

        // Specialized UI (furnace, anvil, crafting, etc.) or unknown
        // container type — not sortable.
        return null;
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
            // Double chest — wraps two ChestBlockEntity halves. Use
            // container1's position for a stable, half-agnostic identity
            // (both halves of the same double chest resolve to the same
            // key regardless of which side the player clicked).
            CompoundContainerAccessor accessor = (CompoundContainerAccessor) compound;
            Container half1 = accessor.inventoryplus$getContainer1();
            if (half1 instanceof BlockEntity halfBE) {
                return halfBE.getBlockPos();
            }
            // Fallback: try the other half (shouldn't be needed for vanilla
            // double chests but defensive).
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
        } else {
            dim = "unknown";
        }
        return new ContainerIdentity(
                "block:" + dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }
}
