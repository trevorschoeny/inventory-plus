package com.trevorschoeny.inventoryplus.lockgroups;

import com.trevorschoeny.inventoryplus.config.IPConfig;
import com.trevorschoeny.inventoryplus.lockeditems.LockKind;
import com.trevorschoeny.inventoryplus.lockeditems.LockedItems;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Item locks in the item's own tooltip (`plans/lock-groups.md`, "Tooltips"):
 * a line "Locked: <group>" per group on it, in the group's colour. Slot locks
 * name their group from the padlock instead, since an empty slot has no item
 * tooltip. Nothing while locks are paused.
 */
public final class LockTooltips {

    private LockTooltips() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!IPConfig.lockGroupsEnabled() || stack.isEmpty()) return;
            String exact = LockedItems.exactGroup(stack);
            if (exact != null) lines.add(line(Reach.resolve(exact, LockKind.EXACT)));
            String byId = LockedItems.itemGroup(stack);
            if (byId != null) lines.add(line(Reach.resolve(byId, LockKind.ITEM)));
        });
    }

    private static Component line(LockGroup group) {
        return Component.literal("Locked: " + group.name())
                .withStyle(Style.EMPTY.withColor(LockColours.argb(group) & 0xFFFFFF));
    }
}
