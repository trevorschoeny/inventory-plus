package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.autoreplace.AutoReplace;
import com.trevorschoeny.menukit.config.GeneralOption;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link LivingEntity#onEquippedItemBroken(Item, EquipmentSlot)} to
 * detect when a tool breaks in the player's main hand and trigger auto-replace.
 *
 * <p>This method is called by vanilla's {@code ItemStack.applyDamage} via the
 * consumer callback in {@code hurtAndBreak}. By the time it fires:
 * <ol>
 *   <li>The item has been shrunk to 0 (stack consumed)</li>
 *   <li>The break animation/sound event has been queued</li>
 *   <li>The slot is empty — ready for a replacement</li>
 * </ol>
 *
 * <p>We inject at RETURN so vanilla's break logic (sound, particles, attribute
 * removal) completes fully before we modify the slot contents. This prevents
 * any interference with the break animation or attribute cleanup.
 *
 * <p>Only MAINHAND breaks are handled. Offhand tool use is rare (typically
 * shields or maps), and automatically replacing an offhand item could be
 * surprising and unwanted.
 */
@Mixin(LivingEntity.class)
public class IPAutoReplaceMixin {

    @Inject(method = "onEquippedItemBroken", at = @At("RETURN"))
    private void inventoryPlus$autoReplace(Item item, EquipmentSlot slot, CallbackInfo ci) {
        // Only handle main hand tool breaks — offhand replacement would be surprising
        if (slot != EquipmentSlot.MAINHAND) return;

        // Only applies to players, not mobs (LivingEntity is the parent of both)
        if (!((Object) this instanceof Player player)) return;

        // Server-side only — client doesn't manage inventory authoritatively
        if (player.level().isClientSide()) return;

        // Check the family-wide toggle. Uses the same GeneralOption descriptor
        // that InventoryPlusClient registers — the key "auto_replace_tools" is
        // the single source of truth for this setting.
        boolean enabled = MenuKit.family("trevmods")
                .getGeneral(new GeneralOption<>("auto_replace_tools", true, Boolean.class));
        if (!enabled) return;

        AutoReplace.onToolBroken(player, item);
    }
}
