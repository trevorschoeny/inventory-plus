package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MKContainerDef;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops items from the Inventory Plus equipment slots when the player dies.
 *
 * <p>Hooks into {@code Player.dropEquipment()} — the same method vanilla uses
 * to drop inventory on death. Matches vanilla's exact flow:
 * <ol>
 *   <li>Check {@code keepInventory} gamerule — if true, items stay</li>
 *   <li>Destroy items with Curse of Vanishing (same as vanilla)</li>
 *   <li>Drop remaining items as entities in the world</li>
 *   <li>Clear the equipment slots</li>
 * </ol>
 */
@Mixin(Player.class)
public class IPDeathDropsMixin {

    @Inject(method = "dropEquipment", at = @At("RETURN"))
    private void inventoryPlus$dropEquipmentOnDeath(ServerLevel level, CallbackInfo ci) {
        Player player = (Player)(Object) this;

        // Respect keepInventory gamerule — matches vanilla's exact check
        // GameRules.get() returns Object, cast to Boolean (same as vanilla bytecode)
        Object keepInvValue = level.getGameRules().get(GameRules.KEEP_INVENTORY);
        if (keepInvValue instanceof Boolean keepInv && keepInv) {
            return;
        }

        // Drop all items from all MenuKit containers for this player
        var containers = MenuKit.getAllContainersForPlayer(player.getUUID(), true);
        if (containers == null) return;

        for (var entry : containers.entrySet()) {
            // Only drop player-bound containers — instance-bound items belong to the block
            MKContainerDef cDef = MenuKit.getContainerDef(entry.getKey());
            if (cDef != null && cDef.binding() != MKContainerDef.BindingType.PLAYER) continue;

            MKContainer container = entry.getValue();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;

                // Destroy items with Curse of Vanishing (same as vanilla's
                // destroyVanishingCursedItems — checks PREVENT_EQUIPMENT_DROP enchantment)
                if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    container.setItem(slot, ItemStack.EMPTY);
                    continue;
                }

                // Drop the item as an entity in the world
                player.drop(stack, true, false);
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
