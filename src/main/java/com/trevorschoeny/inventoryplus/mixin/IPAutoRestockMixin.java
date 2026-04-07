package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.autorestock.AutoRestock;
import com.trevorschoeny.menukit.config.GeneralOption;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link ServerPlayer#tick()} to detect when a hotbar slot becomes
 * empty (item fully consumed/placed/thrown) and trigger autorestock.
 *
 * <p>We inject at RETURN of tick() — after all vanilla tick logic has run
 * (item use, block placement, etc.) — so we see the final state of the
 * inventory for this tick. This avoids restocking mid-tick where the item
 * might be further modified.
 *
 * <p>The {@code previousItems} array stores the Item in each hotbar slot from
 * the previous tick. When a slot transitions from non-null to empty, that's
 * our signal to restock. The array is per-player via {@code @Unique}, so each
 * ServerPlayer instance has its own tracking state.
 */
@Mixin(ServerPlayer.class)
public class IPAutoRestockMixin {

    // Tracks what Item was in the SELECTED hotbar slot last tick.
    // null means the slot was empty. Only the selected slot is tracked to
    // avoid false-triggers when the player intentionally empties other slots.
    @Unique
    private Item inventoryPlus$previousSelectedItem;

    @Inject(method = "tick", at = @At("RETURN"))
    private void inventoryPlus$autoRestockTick(CallbackInfo ci) {
        // Check the family-wide toggle. Uses the same GeneralOption descriptor
        // that InventoryPlusClient registers — the key "auto_restock" is the
        // single source of truth for this setting.
        boolean enabled = MenuKit.family("trevmods")
                .getGeneral(new GeneralOption<>("auto_restock", true, Boolean.class));
        if (!enabled) return;

        ServerPlayer self = (ServerPlayer)(Object) this;
        int selectedSlot = self.getInventory().getSelectedSlot();

        inventoryPlus$previousSelectedItem = AutoRestock.tick(
                self, inventoryPlus$previousSelectedItem, selectedSlot);
    }
}
