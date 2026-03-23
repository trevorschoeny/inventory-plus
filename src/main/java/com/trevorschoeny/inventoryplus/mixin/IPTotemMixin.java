package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.InventoryPlusConfig;
import com.trevorschoeny.menukit.MKContainer;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Checks the Inventory Plus equipment slot for a Totem of Undying when
 * the player would die.
 *
 * <p>Replicates vanilla's exact totem flow: check for DEATH_PROTECTION
 * component, consume the item, award stats + advancement, set health to 1,
 * apply effects, and broadcast the totem particle event (entity event 35).
 */
@Mixin(LivingEntity.class)
public class IPTotemMixin {

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"), cancellable = true)
    private void inventoryPlus$checkEquipmentTotem(DamageSource source,
                                                    CallbackInfoReturnable<Boolean> cir) {
        // If vanilla already found a totem, no need to check
        if (cir.getReturnValue()) return;
        if (!InventoryPlusConfig.get().enableTotemSlot) return;

        // Only applies to players
        if (!((Object) this instanceof Player player)) return;

        // Check the equipment panel's totem slot (index 1)
        boolean isServer = !player.level().isClientSide();
        MKContainer container = MenuKit.getContainerForPlayer("equipment", player.getUUID(), isServer);
        if (container == null) return;

        ItemStack totemStack = container.getItem(1);
        if (totemStack.isEmpty()) return;

        // Check for the DEATH_PROTECTION component (same check vanilla uses)
        DeathProtection deathProtection = totemStack.get(DataComponents.DEATH_PROTECTION);
        if (deathProtection == null) return;

        // ── Replicate vanilla's exact totem flow ──────────────────────────

        // 1. Copy and consume the totem
        ItemStack totemCopy = totemStack.copy();
        totemStack.shrink(1);
        container.setItem(1, totemStack);

        // 2. Award stats + trigger advancement (server only, matches vanilla)
        LivingEntity self = (LivingEntity)(Object) this;
        if (self instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.ITEM_USED.get(totemCopy.getItem()));
            CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, totemCopy);
            totemCopy.causeUseVibration(self, GameEvent.ITEM_INTERACT_FINISH);
        }

        // 3. Set health to 1 (CRITICAL — without this, death screen still shows)
        self.setHealth(1.0f);

        // 4. Apply death protection effects (regeneration, absorption, etc.)
        deathProtection.applyEffects(totemCopy, self);

        // 5. Broadcast totem particle animation (entity event 35)
        self.level().broadcastEntityEvent(self, (byte) 35);

        cir.setReturnValue(true);
    }
}
