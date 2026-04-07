package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows XP mending to repair items in the Inventory Plus equipment slots.
 *
 * <p>Vanilla's {@code processMobExperience()} iterates equipped items and
 * applies mending repair. We inject at RETURN — if there's leftover XP
 * after vanilla's processing, we check our equipment slots for mending
 * items and repair them.
 */
@Mixin(EnchantmentHelper.class)
public class IPMendingMixin {

    @Inject(method = "processMobExperience", at = @At("RETURN"), cancellable = true)
    private static void inventoryPlus$mendEquipmentSlots(
            ServerLevel level, Entity attacker, Entity target, int xpAmount,
            CallbackInfoReturnable<Integer> cir) {

        // Only process for players
        if (!(target instanceof Player player)) return;

        int remainingXp = cir.getReturnValue();
        if (remainingXp <= 0) return;

        // Check equipment panel items for mending
        // Mending always runs on server
        MKContainer container = MenuKit.getContainerForPlayer("equipment", player.getUUID(), true);
        if (container == null) return;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (remainingXp <= 0) break;

            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !stack.isDamaged()) continue;

            // Apply mending — same logic vanilla uses
            int repaired = EnchantmentHelper.modifyDurabilityToRepairFromXp(level, stack, remainingXp);
            int xpUsed = remainingXp - repaired;

            if (xpUsed > 0) {
                // Repair the item
                int repairAmount = Math.min(xpUsed * 2, stack.getDamageValue());
                stack.setDamageValue(stack.getDamageValue() - repairAmount);
                container.setItem(slot, stack);
                remainingXp = repaired;
            }
        }

        cir.setReturnValue(remainingXp);
    }
}
