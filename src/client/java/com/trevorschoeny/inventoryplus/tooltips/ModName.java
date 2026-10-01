package com.trevorschoeny.inventoryplus.tooltips;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The item's mod, blue italic, as the tooltip's last line: the display name
 * of the mod whose id is the item id's namespace, from Fabric's metadata;
 * "Minecraft" for vanilla items, and the bare namespace if no mod claims it.
 */
final class ModName {

    private ModName() {}

    static List<Component> lines(ItemStack stack) {
        String namespace = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        String name = namespace.equals("minecraft") ? "Minecraft"
                : FabricLoader.getInstance().getModContainer(namespace)
                        .map(mod -> mod.getMetadata().getName()).orElse(namespace);
        return List.of(Component.literal(name).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
    }
}
