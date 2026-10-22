package com.trevorschoeny.inventoryplus.checks;

import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.slotkit.api.group.SlotGroupCategory;
import com.trevlar.slotkit.api.group.SlotGroupId;
import com.trevlar.slotkit.api.slot.SlotGroupElement;

import net.fabricmc.api.ClientModInitializer;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * A chest's container list with the chest and {@link ListFixture}'s two boxes,
 * shown only while {@link #on} (a check sets it), so the other checks' chests
 * keep vanilla's screen.
 */
public class ListFixtureClient implements ClientModInitializer {

    public static volatile boolean on = false;

    @Override
    public void onInitializeClient() {
        ContainerRegion.section("inventoryplus_checks:chest", ModeScope.BOTH)
                .title(() -> Component.translatable("container.chest"))
                .content(SlotGroupElement.of(SlotGroupId.category(SlotGroupCategory.CHEST_STORAGE)))
                .on(ContainerScreen.class)
                .visibleWhen(() -> on)
                .declare();
        ContainerRegion.section("inventoryplus_checks:box_a", ModeScope.BOTH)
                .title(() -> Component.literal("Box A"))
                .content(SlotGroupElement.of(ListFixture.BOX_A.id()))
                .on(ContainerScreen.class)
                .order(10)
                .visibleWhen(() -> on)
                .declare();
        ContainerRegion.section("inventoryplus_checks:box_b", ModeScope.BOTH)
                .title(() -> Component.literal("Box B"))
                .content(SlotGroupElement.of(ListFixture.BOX_B.id()))
                .on(ContainerScreen.class)
                .order(20)
                .visibleWhen(() -> on)
                .declare();
    }
}
