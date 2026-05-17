package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import com.trevorschoeny.menukit.core.Button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sort toolbar-button factories. Two variants — one per
 * container-toolbar — because the IP toolbar is per-container:
 * every sortable container on screen has its own toolbar with its
 * own Sort button that targets THAT container.
 *
 * <h3>Two button shapes</h3>
 *
 * <ul>
 *   <li>{@link #inventoryToolbarButton} — lives on the player-inventory
 *       toolbar; sorts the player's 3×9 main inv. Visibility is
 *       inherited from its panel (which shows whenever the player inv
 *       slot group exists — i.e., every non-creative container screen).
 *   </li>
 *   <li>{@link #externalToolbarButton} — lives on the external-container
 *       toolbar (anchored to chest/shulker/dispenser/hopper storage
 *       slot groups); sorts the external container. Visibility is
 *       inherited from its panel's category filter — only sortable
 *       external storages get the toolbar, so the button naturally
 *       hides on specialized UIs (furnace, anvil, etc.).</li>
 * </ul>
 *
 * <h3>Why per-container, not "smart targeting"</h3>
 *
 * Earlier iteration had one Sort button on the inv toolbar that
 * "smart-targeted" the external when present, else the inv. Trev
 * 2026-05-17: each container should have its own Sort, anchored above
 * its own slot group. Same mental model players already have for
 * "this button acts on the thing it's above." Generalizes cleanly to
 * future per-container buttons (sort-type cycler, etc.).
 *
 * <h3>Edit-mode gate</h3>
 *
 * Both buttons no-op when locked-slots edit mode is on, mirroring the
 * MM buttons — buttons stay visible but inert so users aren't
 * accidentally rearranging items while configuring locks.
 */
public final class SortButton {

    private SortButton() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "sort_button");

    public static final int SIZE = 9;

    /** Sort button for the player-inventory toolbar — sorts the 3×9 main inv. */
    public static Button inventoryToolbarButton(int x, int y) {
        return Button.sprite(x, y, SIZE, SIZE,
                        TEXTURE,
                        btn -> triggerSort(Target.INVENTORY))
                .tooltip(Component.literal("Sort"));
    }

    /** Sort button for an external-container toolbar — sorts the external container. */
    public static Button externalToolbarButton(int x, int y) {
        return Button.sprite(x, y, SIZE, SIZE,
                        TEXTURE,
                        btn -> triggerSort(Target.EXTERNAL))
                .tooltip(Component.literal("Sort"));
    }

    /** Which container the button targets — determined at factory time, not click time. */
    private enum Target { INVENTORY, EXTERNAL }

    /**
     * Click handler. Resolves anchor → identity → type → region, then
     * delegates to {@link Sorter} — same pipeline as
     * {@link SortKeybind#handleSortKey} but with the anchor synthesized
     * from menu walk instead of cursor hit-test.
     */
    private static void triggerSort(Target target) {
        if (LockEditMode.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
        AbstractContainerMenu menu = acs.getMenu();

        Slot anchor = target == Target.INVENTORY
                ? findInventoryAnchor(menu)
                : findExternalAnchor(menu);
        if (anchor == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] toolbar button (target={}) clicked but no anchor — no-op",
                    target);
            return;
        }

        ContainerIdentity identity = ContainerIdentity.fromHoveredSlot(anchor, menu);
        if (identity == null) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] toolbar button (target={}) clicked; anchor container={} → no identity",
                    target, anchor.container.getClass().getSimpleName());
            return;
        }

        SortType type = SortState.getType(identity);
        if (type == SortType.DISABLED) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] toolbar button (target={}) clicked; identity={} is DISABLED",
                    target, identity.key());
            return;
        }

        List<Slot> region = SortKeybind.collectRegion(menu, anchor);
        if (region.size() < 2) {
            InventoryPlusClient.LOGGER.debug(
                    "[sort] toolbar button (target={}) clicked; identity={} region only {} slot(s)",
                    target, identity.key(), region.size());
            return;
        }

        InventoryPlusClient.LOGGER.debug(
                "[sort] toolbar button (target={}) clicked; identity={} type={} region={} slots — sorting",
                target, identity.key(), type, region.size());
        try {
            Sorter.sort(menu, mc.gameMode, mc.player, region, type);
        } catch (UnsupportedOperationException e) {
            InventoryPlusClient.LOGGER.warn("[sort] {}", e.getMessage());
        }
    }

    /** First slot whose container is the player {@link Inventory} and containerSlot is 9–35. */
    private static @Nullable Slot findInventoryAnchor(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) {
                int cs = s.getContainerSlot();
                if (cs >= 9 && cs <= 35) return s;
            }
        }
        return null;
    }

    /** First slot whose container is not the player {@link Inventory}. */
    private static @Nullable Slot findExternalAnchor(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) {
                return s;
            }
        }
        return null;
    }
}
