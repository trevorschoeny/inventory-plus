package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.InventoryPlusConfig;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporarily swaps the equipment panel elytra into the chest slot during
 * updateFallFlying() so vanilla's slot-scanning damage logic works.
 *
 * <p>Without this, {@link IPCanGlideMixin} makes canGlide() return true, but
 * updateFallFlying() filters EquipmentSlot.VALUES for glide-capable items and
 * finds zero — crashing in Util.getRandom() with "Bound must be positive".
 *
 * <p>The swap is scoped to just updateFallFlying(), so chest armor protection
 * during other tick logic (e.g. damage calculations) is unaffected. Works
 * regardless of whether the player is wearing chest armor.
 */
@Mixin(LivingEntity.class)
public class IPFallFlyingMixin {

    @Unique private ItemStack inventoryPlus$savedChestItem = ItemStack.EMPTY;
    @Unique private boolean inventoryPlus$isSwapped = false;

    /**
     * Before updateFallFlying: save the current chest item and put an elytra
     * copy in so vanilla's EquipmentSlot filter finds a glide-capable item.
     */
    @Inject(method = "updateFallFlying", at = @At("HEAD"))
    private void inventoryPlus$swapElytraIn(CallbackInfo ci) {
        // Only applies to players, not other LivingEntities
        if (!((Object) this instanceof Player player)) return;
        if (player.level().isClientSide()) return; // Server only
        if (!InventoryPlusConfig.get().enableElytraSlot) return;

        MKContainer container = MenuKit.getContainerForPlayer("equipment", player.getUUID(), true);
        if (container == null) return;

        ItemStack elytra = container.getItem(0);
        if (elytra.isEmpty() || !LivingEntity.canGlideUsing(elytra, EquipmentSlot.CHEST)) return;

        // Save whatever is in the chest slot (armor or empty) and put elytra copy in.
        // Using a copy so saves mid-method always capture the elytra in our container.
        inventoryPlus$savedChestItem = player.getItemBySlot(EquipmentSlot.CHEST);
        player.setItemSlot(EquipmentSlot.CHEST, elytra.copy());
        inventoryPlus$isSwapped = true;
    }

    /**
     * After updateFallFlying: sync any damage vanilla applied to the elytra copy
     * back to our equipment container, then restore the original chest item.
     */
    @Inject(method = "updateFallFlying", at = @At("RETURN"))
    private void inventoryPlus$swapElytraOut(CallbackInfo ci) {
        if (!inventoryPlus$isSwapped) return;

        Player player = (Player)(Object) this;
        MKContainer container = MenuKit.getContainerForPlayer("equipment", player.getUUID(), true);

        if (container != null) {
            // Sync damage from the chest copy back to the container's elytra.
            // Vanilla's updateFallFlying may have called hurtAndBreak on the copy.
            ItemStack chestCopy = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chestCopy.isEmpty()) {
                ItemStack realElytra = container.getItem(0);
                if (!realElytra.isEmpty() && chestCopy.isDamaged()) {
                    realElytra.setDamageValue(chestCopy.getDamageValue());
                    container.setItem(0, realElytra);
                }
            } else {
                // Chest copy broke (elytra fully damaged) — clear our slot too
                container.setItem(0, ItemStack.EMPTY);
            }
        }

        // Restore original chest contents (armor or empty)
        player.setItemSlot(EquipmentSlot.CHEST, inventoryPlus$savedChestItem);
        inventoryPlus$savedChestItem = ItemStack.EMPTY;
        inventoryPlus$isSwapped = false;
    }
}
