package com.trevorschoeny.inventoryplus.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Provides access to the recipe book component on inventory screens
 * that extend {@link AbstractRecipeBookScreen}.
 *
 * <p>Used by Container Peek to close the recipe book when the peek panel
 * opens (since both occupy the left side of the screen) and restore it
 * when the peek panel closes.
 *
 * <p>Part of <b>Inventory Plus</b>.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface IPRecipeBookAccessor {

    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> inventoryPlus$getRecipeBookComponent();
}
