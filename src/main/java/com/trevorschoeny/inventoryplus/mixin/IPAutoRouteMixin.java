package com.trevorschoeny.inventoryplus.mixin;

import com.trevorschoeny.inventoryplus.features.autoroute.AutoRoute;
import com.trevorschoeny.menukit.GeneralOption;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into {@link Inventory#add(ItemStack)} to intercept item pickup and
 * route items into shulker boxes or bundles that already contain them.
 *
 * <p>{@code Inventory.add(ItemStack)} is called by vanilla whenever an item
 * enters the player's inventory — primarily from {@code ItemEntity.playerTouch}
 * (ground item pickup) but also from hoppers, command blocks, and other sources.
 * By injecting at HEAD, we get first crack at the incoming item before vanilla
 * places it into a loose inventory slot.
 *
 * <p>If the item is fully consumed by container routing, we set the stack to
 * empty and return true (item was "added" — into a container). If partially
 * consumed, vanilla handles the remainder normally. If no routing occurs,
 * vanilla proceeds as if we weren't here.
 *
 * <p>Gated behind the family-wide "autofill_enabled" toggle — the same toggle
 * that controls the keybind-triggered autofill. This makes sense because both
 * features manage the relationship between loose items and container items.
 *
 * <p>Server-side only — item pickup is authoritative on the server. The client
 * never calls {@code Inventory.add} for real item acquisition.
 */
@Mixin(Inventory.class)
public abstract class IPAutoRouteMixin {

    @Shadow
    @Final
    public Player player;

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void inventoryPlus$routePickup(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // Server-side only — client doesn't manage inventory authoritatively.
        // This also prevents double-processing in singleplayer where both
        // client and server Inventory instances exist.
        if (player.level().isClientSide()) return;

        // Guard: respect the family-wide "autofill_enabled" toggle.
        // Same toggle used by AutoFill — both features manage the relationship
        // between loose inventory items and container contents.
        boolean enabled = MenuKit.family("trevmods")
                .getGeneral(new GeneralOption<>("autofill_enabled", true, Boolean.class));
        if (!enabled) return;

        // Skip empty stacks (defensive — vanilla shouldn't call add with empty)
        if (stack.isEmpty()) return;

        // Attempt to route the item into containers. AutoRoute modifies
        // the stack count in-place as items are consumed by containers.
        boolean fullyConsumed = AutoRoute.routePickup((Inventory)(Object) this, stack);

        if (fullyConsumed) {
            // All items were routed into containers. Set the stack empty
            // (defensive — AutoRoute already shrunk it to 0) and return true
            // to tell vanilla "the item was handled, don't add it to inventory."
            stack.setCount(0);
            cir.setReturnValue(true);
        }
        // If not fully consumed, fall through to vanilla's add() which will
        // place the remaining items (with reduced count) into a loose slot.
    }
}
