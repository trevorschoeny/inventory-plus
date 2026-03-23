package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.InventoryPlusConfig;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders elytra wings on the player when an elytra is in the
 * Inventory Plus equipment slot.
 *
 * <p>Vanilla's {@code WingsLayer.submit()} checks {@code renderState.chestEquipment}
 * for an equippable item with an asset ID. If vanilla wouldn't render wings
 * (no elytra in chest slot), we check our equipment slot and set the chest
 * equipment field so vanilla renders the wings naturally.
 *
 * <p>CLIENT-ONLY mixin.
 */
@Mixin(WingsLayer.class)
public class IPWingsLayerMixin {

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"))
    private void inventoryPlus$injectEquipmentElytra(
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            int light,
            HumanoidRenderState renderState,
            float yRot, float xRot,
            CallbackInfo ci) {

        if (!InventoryPlusConfig.get().enableElytraSlot) return;

        // If chest already has elytra, vanilla handles it
        if (!renderState.chestEquipment.isEmpty()) {
            Equippable equippable = renderState.chestEquipment.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.assetId().isPresent()) return;
        }

        // Check equipment slot for elytra
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        MKContainer container = MenuKit.getClientContainer(mc.player.getUUID(), "equipment");
        if (container == null) return;

        ItemStack elytra = container.getItem(0);
        if (!elytra.isEmpty()) {
            Equippable equippable = elytra.get(DataComponents.EQUIPPABLE);
            if (equippable != null && equippable.assetId().isPresent()) {
                // Set the chest equipment so vanilla renders the wings
                renderState.chestEquipment = elytra;
            }
        }
    }
}
