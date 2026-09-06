package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.buttonmode.ModeGestures;
import com.trevorschoeny.inventoryplus.buttonmode.PressFeedback;
import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockedslots.LockEditMode;

import com.trevlar.menukit.core.Button;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sort toolbar-button factories, one per container toolbar: the inventory
 * toolbar's sorts the player's 3×9, the external toolbar's sorts the
 * container it sits above.
 *
 * <p>The button carries Sort's mode ({@code features/button-modes.md}):
 * left-click sorts in the order in force, right-click changes the order,
 * shift+right-click goes back, middle-click pins the order to this
 * container. Which container is resolved the same way the click resolves
 * it, by walking the menu for the toolbar's anchor slot, so the tooltip,
 * the tint and the action all agree on what "this container" is.
 *
 * <p>Both buttons stay inert in locked-slots edit mode, gestures included.
 */
public final class SortButton {

    private SortButton() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "sort_button");

    public static final int SIZE = 9;

    public static Button inventoryToolbarButton(int x, int y) {
        return build(x, y, Target.INVENTORY);
    }

    public static Button externalToolbarButton(int x, int y) {
        return build(x, y, Target.EXTERNAL);
    }

    private enum Target { INVENTORY, EXTERNAL }

    private static Button build(int x, int y, Target target) {
        PressFeedback feedback = new PressFeedback();
        Supplier<@Nullable ContainerIdentity> identity = () -> currentIdentity(target);
        var gestures = ModeGestures.handler(SortState.MODE, identity, feedback);
        return Button.sprite(x, y, SIZE, SIZE, TEXTURE,
                        btn -> {
                            feedback.press();
                            triggerSort(target);
                        })
                .tooltip(ModeGestures.tooltip("Sort", SortState.MODE, identity))
                .onSecondaryClick(click -> {
                    if (LockEditMode.isOn()) return;
                    gestures.accept(click);
                })
                .tint(ModeGestures.tint(SortState.MODE, identity, feedback))
                .showWhen(IPConfig::sortShowButton);
    }

    /** The identity of the container this toolbar's button acts on, or null if none is open. */
    private static @Nullable ContainerIdentity currentIdentity(Target target) {
        AbstractContainerMenu menu = openMenu();
        if (menu == null) return null;
        Slot anchor = target == Target.INVENTORY ? findInventoryAnchor(menu) : findExternalAnchor(menu);
        return anchor == null ? null : ContainerIdentity.fromHoveredSlot(anchor, menu);
    }

    private static @Nullable AbstractContainerMenu openMenu() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> acs ? acs.getMenu() : null;
    }

    private static void triggerSort(Target target) {
        if (LockEditMode.isOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = openMenu();
        if (menu == null) return;

        Slot anchor = target == Target.INVENTORY ? findInventoryAnchor(menu) : findExternalAnchor(menu);
        if (anchor == null) return;
        ContainerIdentity identity = ContainerIdentity.fromHoveredSlot(anchor, menu);
        if (identity == null) return;

        SortType type = SortState.getType(identity);
        List<Slot> region = SortKeybind.collectRegion(menu, anchor);
        if (region.size() < 2) return;
        InventoryPlusClient.LOGGER.debug("[sort] button ({}): {} {} over {} slots",
                target, identity.key(), type, region.size());
        Sorter.sort(menu, mc.gameMode, mc.player, region, type);
    }

    /** First slot of the player's 3×9 main inventory (container slots 9–35). */
    public static @Nullable Slot findInventoryAnchor(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (s.container instanceof Inventory) {
                int cs = s.getContainerSlot();
                if (cs >= 9 && cs <= 35) return s;
            }
        }
        return null;
    }

    /** First slot whose container is not the player's {@link Inventory}. */
    public static @Nullable Slot findExternalAnchor(AbstractContainerMenu menu) {
        for (Slot s : menu.slots) {
            if (!(s.container instanceof Inventory)) return s;
        }
        return null;
    }
}
