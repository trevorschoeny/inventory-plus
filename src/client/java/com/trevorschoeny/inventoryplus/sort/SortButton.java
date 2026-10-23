package com.trevorschoeny.inventoryplus.sort;

import com.trevorschoeny.inventoryplus.settings.SettingsMenu;
import com.trevorschoeny.inventoryplus.InventoryPlusClient;
import com.trevorschoeny.inventoryplus.buttonmode.ModeGestures;
import com.trevorschoeny.inventoryplus.buttonmode.PressFeedback;
import com.trevorschoeny.inventoryplus.config.IPConfig;

import com.trevlar.menukit.api.element.Button;
import com.trevlar.slotkit.api.group.ResolvedSlotGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Sort toolbar-button factories, one per container toolbar: the inventory
 * toolbar's sorts the player's 3x9, the external toolbar's sorts the
 * container it sits above.
 *
 * <p>The button carries Sort's mode: left-click sorts in the order in force, right-click changes the order,
 * shift+right-click goes back, middle-click pins the order to this
 * container (on a container's toolbar only). Which container is resolved the
 * same way the click resolves it, from the slots the toolbar sits on (the main
 * inventory, or the toolbar's own group), so the tooltip, the tint and the
 * action all agree on what "this container" is.
 */
public final class SortButton {

    private SortButton() {}

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("inventoryplus", "sort_button");

    public static final int SIZE = 9;

    public static Button inventoryToolbarButton(int x, int y) {
        return build(x, y, false, menu -> {
            Slot anchor = findInventoryAnchor(menu);
            return anchor == null ? List.of() : SortKeybind.collectRegion(menu, anchor);
        });
    }

    /** Sorts {@code group}, read on each use: the group the toolbar sits on now, or null where it isn't showing. */
    public static Button externalToolbarButton(int x, int y, Supplier<@Nullable ResolvedSlotGroup> group) {
        return build(x, y, true, menu -> {
            ResolvedSlotGroup g = group.get();
            return g == null ? List.of() : g.slots();
        });
    }

    /** {@code region}: the slots this button sorts on the open menu, empty for none. */
    private static Button build(int x, int y, boolean pinHere, Function<AbstractContainerMenu, List<Slot>> region) {
        PressFeedback feedback = new PressFeedback();
        Supplier<@Nullable ContainerIdentity> identity = () -> currentIdentity(region);
        // Only a container's own Sort pins (pinHere), not the inventory's.
        var gestures = ModeGestures.handler(SortState.MODE, identity, pinHere, feedback);
        return Button.builder().sprite(TEXTURE).at(x, y).size(SIZE, SIZE)
                .onClick(() -> {
                    if (SettingsMenu.ctrlClickOpens(SettingsMenu.SORT)) return;
                    feedback.press();
                    triggerSort(region);
                })
                .tooltip(SettingsMenu.tooltip("Sort",
                        ModeGestures.tooltip(t -> "Sort by " + t.label(), t -> "Sort: " + t.label(),
                                SortState.MODE, identity, pinHere)))
                .onSecondaryClick(gestures::accept)
                .tint(ModeGestures.tint(SortState.MODE, identity, feedback))
                // Creative's inventory tab sorts too: SlotKit runs Sort's clicks
                // through creative's own click path there (fluorite 29ecf6b).
                .visibleWhen(() -> IPConfig.sortEnabled() && IPConfig.sortShowButton())
                .build();
    }

    /** The identity of the container this toolbar's button acts on, or null if none is open. */
    private static @Nullable ContainerIdentity currentIdentity(Function<AbstractContainerMenu, List<Slot>> region) {
        AbstractContainerMenu menu = openMenu();
        if (menu == null) return null;
        List<Slot> slots = region.apply(menu);
        return slots.isEmpty() ? null : ContainerIdentity.fromHoveredSlot(slots.get(0), menu);
    }

    private static @Nullable AbstractContainerMenu openMenu() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> acs ? acs.getMenu() : null;
    }

    private static void triggerSort(Function<AbstractContainerMenu, List<Slot>> regionOf) {
        if (!IPConfig.sortEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = openMenu();
        if (menu == null) return;

        List<Slot> region = regionOf.apply(menu);
        if (region.size() < 2) return;
        ContainerIdentity identity = ContainerIdentity.fromHoveredSlot(region.get(0), menu);
        if (identity == null) return;

        SortType type = SortState.getType(identity);
        InventoryPlusClient.LOGGER.debug("[sort] button: {} {} over {} slots",
                identity.key(), type, region.size());
        Sorter.sort(menu, mc.gameMode, mc.player, region, type);
    }

    /** First slot of the player's 3x9 main inventory (container slots 9 to 35). */
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
