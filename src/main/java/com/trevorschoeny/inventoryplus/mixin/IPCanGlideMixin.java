package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.InventoryPlusConfig;
import com.trevorschoeny.menukit.container.MKContainer;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows elytra flight from the Inventory Plus equipment slot.
 *
 * <p>Hooks into Player.canGlide() on BOTH client and server. When vanilla
 * doesn't find an elytra in standard slots, checks our equipment slot.
 * This handles flight start, flight continuation, and client prediction.
 *
 * <p>The companion mixin {@link IPFallFlyingMixin} handles the server-side
 * chest-slot swap during updateFallFlying() so vanilla's damage logic works.
 */
@Mixin(Player.class)
public class IPCanGlideMixin {

    /**
     * If vanilla doesn't find a glide-eligible item in standard equipment slots,
     * check our equipment panel's elytra slot. Works on both client and server
     * so flight start AND client prediction both work correctly.
     */
    @Inject(method = "canGlide", at = @At("RETURN"), cancellable = true)
    private void inventoryPlus$checkEquipmentElytra(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (!InventoryPlusConfig.get().enableElytraSlot) return;

        Player player = (Player)(Object) this;

        // Must match vanilla's preconditions — canGlide returns false for these
        if (player.onGround() || player.isPassenger()
                || player.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)) return;

        // Don't interfere with creative flight
        if (player.getAbilities().flying) return;
        boolean isServer = !player.level().isClientSide();
        MKContainer container = MenuKit.getContainerForPlayer("equipment", player.getUUID(), isServer);
        if (container == null) return;

        ItemStack elytra = container.getItem(0);
        if (!elytra.isEmpty() && LivingEntity.canGlideUsing(elytra, EquipmentSlot.CHEST)) {
            cir.setReturnValue(true);
        }
    }
}
