package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The entity's own equipment pass: what its tick runs to apply a changed item's attribute modifiers. */
@Mixin(LivingEntity.class)
public interface LivingEntityEquipmentAccessor {

    @Invoker("detectEquipmentUpdates")
    void inventoryPlus$detectEquipmentUpdates();
}
